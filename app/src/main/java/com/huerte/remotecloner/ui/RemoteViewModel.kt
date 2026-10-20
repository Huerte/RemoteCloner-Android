package com.huerte.remotecloner.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huerte.remotecloner.data.IrCode
import com.huerte.remotecloner.data.PRESETS
import com.huerte.remotecloner.data.Remote
import com.huerte.remotecloner.data.RemoteJson
import com.huerte.remotecloner.data.RemoteStore
import com.huerte.remotecloner.data.SendMode
import com.huerte.remotecloner.data.keyName
import com.huerte.remotecloner.data.safeName
import com.huerte.remotecloner.data.withKey
import com.huerte.remotecloner.serial.Bridge
import com.huerte.remotecloner.serial.Role
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

enum class Tab(val label: String) {
    CLONE("Clone"), LIBRARY("Library"), CONTROL("Control"), CONNECTION("Connection")
}

enum class Tone { IDLE, WAITING, SUCCESS, ERROR }

data class CloneUi(
    val keyName: String = "",
    val tone: Tone = Tone.IDLE,
    val text: String = "Enter a key name, then choose Listen for signal.",
    val listening: Boolean = false,
    val pending: IrCode? = null,
    val auto: Boolean = true,
    val batchText: String = "",
    val askOverwrite: String? = null,
)

data class PendingImport(val name: String, val text: String)

data class RemoteUiState(
    val names: List<String> = emptyList(),
    val current: String? = null,
    val remote: Remote? = null,
    val message: String? = null,
    val navigate: Tab? = null,
    val importing: PendingImport? = null,
    val clone: CloneUi = CloneUi(),
    val sendMode: SendMode = SendMode.RAW,
    val repeats: Int = 0,
    val sendNote: String = "",
)

/** Library, Clone and Control logic. Remotes live as JSON files in the app's private storage. */
class RemoteViewModel(application: Application, private val bridge: Bridge) : AndroidViewModel(application) {
    private val context: Application = application
    private val store = RemoteStore(File(context.filesDir, "remotes"))
    private val prefs = context.getSharedPreferences("remotecloner", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(RemoteUiState())
    val state: StateFlow<RemoteUiState> = _state.asStateFlow()

    private var learnJob: Job? = null
    private val cancelled = AtomicBoolean(false)
    private var queue: List<String> = emptyList()
    private var batchOn = false

    init {
        reload(prefs.getString("remote", null))
    }

    // ---------------------------------------------------------------- shared

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun consumeNavigate() = _state.update { it.copy(navigate = null) }

    private fun message(text: String) = _state.update { it.copy(message = text) }

    private fun reload(preferred: String? = _state.value.current) {
        val names = store.names()
        val cur = preferred?.takeIf { it in names } ?: names.firstOrNull()
        val remote = cur?.let { name ->
            runCatching { store.load(name) }.getOrElse {
                message("Could not read \"$name\": ${it.message}")
                null
            }
        }
        prefs.edit().putString("remote", cur).apply()
        _state.update { it.copy(names = names, current = cur, remote = remote) }
    }

    private fun edit(f: (Remote) -> Remote) {
        val cur = _state.value.current ?: return
        val updated = f(_state.value.remote ?: return)
        store.save(cur, updated)
        _state.update { it.copy(remote = updated) }
    }

    /** A board is needed for the action: if it is missing, say so and jump to the Connection tab. */
    private fun needBoard(role: Role): Boolean {
        if (bridge.has(role)) return true
        _state.update { it.copy(message = "Connect the ${role.key} board first.", navigate = Tab.CONNECTION) }
        return false
    }

    // ---------------------------------------------------------------- remotes

    fun select(name: String) = reload(name)

    fun newRemote(raw: String) {
        val n = safeName(raw)
        if (n.isEmpty()) return
        if (store.exists(n)) return message("A remote with that name already exists.")
        store.create(n)
        reload(n)
    }

    fun renameRemote(raw: String) {
        val old = _state.value.current ?: return
        val n = safeName(raw)
        if (n.isEmpty() || n == old) return
        if (store.exists(n)) return message("A remote with that name already exists.")
        store.rename(old, n)
        reload(n)
    }

    fun duplicateRemote(raw: String) {
        val old = _state.value.current ?: return
        val n = safeName(raw)
        if (n.isEmpty()) return
        if (store.exists(n)) return message("A remote with that name already exists.")
        store.duplicate(old, n)
        reload(n)
    }

    fun deleteRemote() {
        val n = _state.value.current ?: return
        store.delete(n)
        reload(null)
    }

    fun setNotes(text: String) = edit { it.copy(notes = text) }

    fun importFrom(uri: Uri) {
        val text = try {
            context.contentResolver.openInputStream(uri)!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            return message("Import failed: ${e.message}")
        }
        try {
            RemoteJson.parse(text)
        } catch (e: Exception) {
            return message("Import failed: this is not a valid remote file (${e.message}).")
        }
        val name = safeName(displayName(uri).substringBeforeLast('.')).ifEmpty { "Imported" }
        if (store.exists(name)) {
            _state.update { it.copy(importing = PendingImport(name, text)) }
        } else {
            finishImport(name, text)
        }
    }

    fun confirmImport() {
        val p = _state.value.importing ?: return
        _state.update { it.copy(importing = null) }
        finishImport(p.name, p.text)
    }

    fun cancelImport() = _state.update { it.copy(importing = null) }

    private fun finishImport(name: String, text: String) {
        store.writeText(name, text)          // copied as is, same as the Windows app
        reload(name)
    }

    fun exportTo(uri: Uri) {
        val n = _state.value.current ?: return
        try {
            context.contentResolver.openOutputStream(uri)!!.use { it.write(store.readText(n).toByteArray(Charsets.UTF_8)) }
            message("Exported $n.")
        } catch (e: Exception) {
            message("Export failed: ${e.message}")
        }
    }

    private fun displayName(uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { return it }
            }
        }
        return uri.lastPathSegment ?: "Imported"
    }

    // ---------------------------------------------------------------- keys

    fun saveKey(old: String?, name: String, code: IrCode) = edit { it.copy(keys = it.keys.withKey(old, name, code)) }

    fun renameKey(old: String, raw: String) {
        val n = keyName(raw)
        val keys = _state.value.remote?.keys ?: return
        if (n.isEmpty() || n == old) return
        if (n in keys) return message("A key named $n already exists.")
        edit { r -> r.copy(keys = r.keys.entries.associate { (if (it.key == old) n else it.key) to it.value }) }
    }

    fun duplicateKey(k: String) {
        val code = _state.value.remote?.keys?.get(k) ?: return
        edit { it.copy(keys = it.keys.withKey(null, k + "_COPY", code)) }
    }

    fun deleteKey(k: String) = edit { it.copy(keys = it.keys - k) }

    fun testKey(k: String) {
        val code = _state.value.remote?.keys?.get(k) ?: return
        if (!needBoard(Role.SENDER)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.send(code)
                message("Sent $k.")
            } catch (e: Exception) {
                message("Send failed: ${e.message}")
            }
        }
    }

    fun relearn(k: String) {
        updateClone { it.copy(keyName = k) }
        _state.update { it.copy(navigate = Tab.CLONE) }
        startLearn()
    }

    // ---------------------------------------------------------------- control

    fun setSendMode(mode: SendMode) = _state.update { it.copy(sendMode = mode) }

    fun setRepeats(n: Int) = _state.update { it.copy(repeats = n.coerceIn(0, 10)) }

    fun fire(k: String) {
        val code = _state.value.remote?.keys?.get(k) ?: return
        if (!needBoard(Role.SENDER)) return
        val mode = _state.value.sendMode
        val repeats = _state.value.repeats
        viewModelScope.launch(Dispatchers.IO) {
            val note = try {
                val r = bridge.send(code, mode, repeats)
                if (r.startsWith("OK")) "Sent $k." else "$k: no confirmation from the sender (${r.ifEmpty { "no reply" }})."
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
            _state.update { it.copy(sendNote = note) }
        }
    }

    fun saveMacro(name: String, text: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        edit { it.copy(macros = it.macros + (n to text)) }
    }

    fun deleteMacro(name: String) = edit { it.copy(macros = it.macros - name) }

    /** Steps are comma separated: key names, or numbers meaning a delay in ms. A key wins over a number. */
    fun runMacro(text: String) {
        val keys = _state.value.remote?.keys ?: return
        if (!needBoard(Role.SENDER)) return
        val mode = _state.value.sendMode
        val repeats = _state.value.repeats
        val steps = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                for (s in steps) {
                    val code = keys[keyName(s)]
                    val ms = if (s.all { it.isDigit() }) s.toLongOrNull() else null
                    when {
                        code != null -> {
                            bridge.send(code, mode, repeats)
                            delay(150)
                        }
                        ms != null -> delay(ms)
                        else -> bridge.note("! unknown macro step: $s")
                    }
                }
            } catch (e: Exception) {
                bridge.note("! ${e.message}")
            }
        }
    }

    // ---------------------------------------------------------------- clone

    private fun updateClone(f: (CloneUi) -> CloneUi) = _state.update { it.copy(clone = f(it.clone)) }

    private fun cloneNote(tone: Tone, text: String) = updateClone { it.copy(tone = tone, text = text) }

    fun setKeyName(text: String) = updateClone { it.copy(keyName = text) }

    fun setAuto(on: Boolean) = updateClone { it.copy(auto = on) }

    fun setBatchText(text: String) = updateClone { it.copy(batchText = text) }

    fun insertPresets() = updateClone { it.copy(batchText = PRESETS.joinToString("\n")) }

    fun dismissOverwrite() = updateClone { it.copy(askOverwrite = null) }

    fun confirmOverwrite() {
        dismissOverwrite()
        startLearn(force = true)
    }

    fun startLearn(force: Boolean = false) {
        val name = keyName(_state.value.clone.keyName)
        if (learnJob?.isActive == true || name.isEmpty()) return
        val remote = _state.value.remote
        if (remote == null) {
            cloneNote(Tone.ERROR, "Create a remote first with New remote.")
            return
        }
        if (!needBoard(Role.RECEIVER)) return
        if (name in remote.keys && !batchOn && !force) {
            updateClone { it.copy(askOverwrite = name) }
            return
        }
        updateClone {
            it.copy(
                keyName = name,
                pending = null,
                listening = true,
                tone = Tone.WAITING,
                text = "Listening. Point the original remote at the receiver and press $name (20 s timeout).",
            )
        }
        cancelled.set(false)
        learnJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                onLearned(bridge.learn({ cancelled.get() }))
            } catch (e: Exception) {
                batchOn = false
                queue = emptyList()
                updateClone {
                    it.copy(listening = false, tone = Tone.ERROR, text = "Error: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    fun stopLearn() {
        batchOn = false
        queue = emptyList()
        cancelled.set(true)
    }

    private fun onLearned(code: IrCode?) {
        val wasCancelled = cancelled.get()
        if (code == null) {
            batchOn = false
            queue = emptyList()
            updateClone {
                it.copy(
                    listening = false,
                    tone = if (wasCancelled) Tone.IDLE else Tone.ERROR,
                    text = if (wasCancelled) "Listening cancelled."
                    else "No signal received. Hold the remote 5-10 cm from the receiver and try again.",
                )
            }
            return
        }
        updateClone { it.copy(listening = false, pending = code, tone = Tone.SUCCESS, text = "Signal captured.") }
        if (_state.value.clone.auto) savePending()
    }

    fun savePending() {
        val c = _state.value.clone
        val code = c.pending ?: return
        val name = keyName(c.keyName)
        if (name.isEmpty()) return
        saveKey(name, name, code)             // an existing key keeps its position
        updateClone { it.copy(pending = null, tone = Tone.SUCCESS, text = "Saved $name.") }
        if (batchOn && queue.isNotEmpty()) {
            val next = queue.first()
            queue = queue.drop(1)
            updateClone { it.copy(keyName = next) }
            viewModelScope.launch {
                delay(400)
                startLearn()
            }
        } else {
            batchOn = false
            updateClone { it.copy(keyName = "") }
        }
    }

    fun discardPending() = updateClone { it.copy(pending = null) }

    fun testPending() {
        val code = _state.value.clone.pending ?: return
        if (!needBoard(Role.SENDER)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.send(code)
                cloneNote(Tone.IDLE, "Test signal sent.")
            } catch (e: Exception) {
                cloneNote(Tone.ERROR, "Error: ${e.message}")
            }
        }
    }

    fun startBatch() {
        val names = _state.value.clone.batchText.lines().filter { it.isNotBlank() }.map(::keyName)
        if (names.isEmpty()) return
        queue = names.drop(1)
        batchOn = true
        updateClone { it.copy(keyName = names.first()) }
        startLearn()
    }

    override fun onCleared() {
        cancelled.set(true)
    }
}
