package com.huerte.remotecloner.ui

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.huerte.remotecloner.serial.Bridge
import com.huerte.remotecloner.serial.Firmware
import com.huerte.remotecloner.serial.FlashPort
import com.huerte.remotecloner.serial.HexImage
import com.huerte.remotecloner.serial.Role
import com.huerte.remotecloner.serial.Stk500v1
import com.huerte.remotecloner.serial.openFlashPort
import com.huerte.remotecloner.serial.openTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [id] is the current USB device name; [key] is vendor:product, which survives re-plugging. */
data class DeviceOption(val id: String, val label: String, val key: String)

data class RoleUi(
    val selectedId: String? = null,
    val connected: Boolean = false,
    val busy: Boolean = false,
    val status: String = "Not connected",
)

/** State of the "Set up board" card. [progress] is null while there is nothing to measure; [ok] is null until a run ends. */
data class FlashUi(
    val selectedId: String? = null,
    val firmware: Firmware = Firmware.COMBINED,
    val running: Boolean = false,
    val progress: Float? = null,
    val ok: Boolean? = null,
    val status: String = "Pick a board and firmware, then tap Install.",
)

data class ConnectionUiState(
    val devices: List<DeviceOption> = emptyList(),
    val baud: Int = 115200,
    val roles: Map<Role, RoleUi> = Role.entries.associateWith { RoleUi() },
    val log: List<String> = emptyList(),
    val message: String? = null,
    val flash: FlashUi = FlashUi(),
)

class ConnectionViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Application = application
    private val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val drivers = mutableMapOf<String, UsbSerialDriver>()
    private val prefs = context.getSharedPreferences("remotecloner", Context.MODE_PRIVATE)

    /** Role + device pairs already tried by [autoConnect], so a failing board is not retried on every resume. */
    private val tried = mutableSetOf<String>()

    private val _state = MutableStateFlow(ConnectionUiState())
    val state: StateFlow<ConnectionUiState> = _state.asStateFlow()

    /** Shared with the Clone, Library and Control tabs. */
    val bridge = Bridge(log = ::appendLog)

    /** Role waiting for the USB permission dialog, and the device it picked. */
    private var pending: Pair<Role, String>? = null

    /** Device waiting for the USB permission dialog because the user tapped Install firmware. */
    private var pendingFlash: String? = null

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            val flashId = pendingFlash
            if (flashId != null) {
                pendingFlash = null
                val flashDriver = drivers[flashId]
                if (granted && flashDriver != null) {
                    startFlash(flashDriver)
                } else {
                    updateFlash { it.copy(running = false, ok = false, status = "USB permission denied") }
                    setMessage("USB permission denied.")
                }
                return
            }
            val (role, id) = pending ?: return
            pending = null
            val driver = drivers[id]
            if (granted && driver != null) {
                startConnect(role, driver)
            } else {
                updateRole(role) { it.copy(busy = false, status = "USB permission denied") }
                setMessage("USB permission denied.")
            }
        }
    }

    /** Unplugging a board does not close its port by itself, so drop it here or the status would stay green. */
    private val detachReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            if (intent.action != UsbManager.ACTION_USB_DEVICE_DETACHED) return
            val device = IntentCompat.getParcelableExtra(intent, UsbManager.EXTRA_DEVICE, UsbDevice::class.java) ?: return
            val wasConnected = bridge.disconnectDevice(device.deviceName)
            tried.removeAll { it.endsWith(":${device.deviceName}") }
            if (wasConnected) {
                appendLog("! ${device.deviceName} unplugged")
                setMessage("A board was unplugged.")
            }
            refresh()
        }
    }

    init {
        ContextCompat.registerReceiver(
            context,
            detachReceiver,
            IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED),
            ContextCompat.RECEIVER_EXPORTED,     // protected system broadcast, only the system can send it
        )
        ContextCompat.registerReceiver(
            context,
            permissionReceiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        refresh()
    }

    fun refresh() {
        val found = UsbSerialProber.getDefaultProber().findAllDrivers(usb)
        drivers.clear()
        found.forEach { drivers[it.device.deviceName] = it }
        val options = found.map { DeviceOption(it.device.deviceName, describe(it.device), deviceKey(it.device)) }
        _state.update { s ->
            val taken = mutableSetOf<String>()
            val roles = Role.entries.mapIndexed { index, role ->
                val ui = s.roles.getValue(role)
                val keep = ui.selectedId?.takeIf { id -> options.any { it.id == id } }
                // No current pick: prefer the board used for this role last time, else go by position.
                val saved = prefs.getString(prefKey(role), null)
                val remembered = options.firstOrNull { it.key == saved && it.id !in taken }
                val fallback = remembered ?: options.getOrNull(index) ?: options.lastOrNull()
                val pick = keep ?: fallback?.id
                pick?.let(taken::add)
                role to ui.copy(selectedId = pick)
            }.toMap()
            val flashPick = s.flash.selectedId?.takeIf { id -> options.any { it.id == id } } ?: options.firstOrNull()?.id
            s.copy(devices = options, roles = roles, flash = s.flash.copy(selectedId = flashPick))
        }
        syncConnected()
    }

    /**
     * Reconnects the boards used last time, like the Windows app does at start. Only boards that are
     * plugged in and already have USB permission are opened, so no permission dialog pops up by itself.
     */
    fun autoConnect() {
        val s = _state.value
        val plan = Role.entries.mapNotNull { role ->
            val saved = prefs.getString(prefKey(role), null) ?: return@mapNotNull null
            if (bridge.has(role) || s.roles.getValue(role).busy) return@mapNotNull null
            val option = s.devices.firstOrNull { it.id == s.roles.getValue(role).selectedId && it.key == saved }
                ?: return@mapNotNull null
            val driver = drivers[option.id] ?: return@mapNotNull null
            if (!usb.hasPermission(driver.device) || !tried.add("${role.key}:${option.id}")) return@mapNotNull null
            role to driver
        }
        if (plan.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            for ((role, driver) in plan) {
                if (!bridge.has(role)) connectNow(role, driver, quiet = true)   // a combined sketch may have filled it
            }
        }
    }

    fun setBaud(baud: Int) = _state.update { it.copy(baud = baud) }

    fun select(role: Role, id: String) = updateRole(role) { it.copy(selectedId = id) }

    fun toggle(role: Role) {
        if (_state.value.flash.running) {
            setMessage("Wait for the firmware update to finish.")
            return
        }
        if (bridge.has(role)) {
            bridge.disconnect(role)
            syncConnected()
            return
        }
        val id = _state.value.roles.getValue(role).selectedId
        val driver = id?.let { drivers[it] }
        if (id == null || driver == null) {
            setMessage("Choose a USB device first.")
            return
        }
        if (usb.hasPermission(driver.device)) {
            startConnect(role, driver)
        } else {
            pending = role to id
            updateRole(role) { it.copy(busy = true, status = "Waiting for USB permission...") }
            requestUsbPermission(driver.device)
        }
    }

    private fun requestUsbPermission(device: UsbDevice) {
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        usb.requestPermission(device, PendingIntent.getBroadcast(context, 0, intent, flags))
    }

    fun selectFlashDevice(id: String) = updateFlash { it.copy(selectedId = id) }

    fun selectFirmware(firmware: Firmware) = updateFlash { it.copy(firmware = firmware) }

    /** Installs the chosen firmware on the chosen board, asking for USB permission first if needed. */
    fun install() {
        val f = _state.value.flash
        if (f.running) return
        if (_state.value.roles.values.any { it.busy }) {
            setMessage("Wait for the board to finish connecting.")
            return
        }
        val id = f.selectedId
        val driver = id?.let { drivers[it] }
        if (id == null || driver == null) {
            setMessage("Choose a USB device first.")
            return
        }
        if (usb.hasPermission(driver.device)) {
            startFlash(driver)
        } else {
            pendingFlash = id
            updateFlash { it.copy(running = true, ok = null, progress = null, status = "Waiting for USB permission...") }
            requestUsbPermission(driver.device)
        }
    }

    private fun startFlash(driver: UsbSerialDriver) {
        val firmware = _state.value.flash.firmware
        updateFlash { it.copy(running = true, ok = null, progress = null, status = "Preparing...") }
        viewModelScope.launch(Dispatchers.IO) { flashNow(driver, firmware) }
    }

    /** Blocking. Runs on the IO dispatcher. Errors end up in the status line, the log and the snackbar. */
    private fun flashNow(driver: UsbSerialDriver, firmware: Firmware) {
        val name = driver.device.deviceName
        var port: FlashPort? = null
        try {
            val text = context.assets.open(firmware.asset).bufferedReader().use { it.readText() }
            val image = HexImage.parse(text)
            // The board is about to restart, so let go of it if a role is using it.
            if (bridge.disconnectDevice(name)) {
                appendLog("! $name disconnected to update it")
                syncConnected()
            }
            appendLog("Installing ${firmware.title} (${image.size} bytes) on $name")
            val opened = usb.openFlashPort(driver, Stk500v1.DEFAULT_BAUDS.first())
            port = opened
            Stk500v1(opened, log = ::appendLog).flash(image) { phase, done, total ->
                updateFlash {
                    it.copy(
                        status = if (total > 0) "$phase $done of $total" else "$phase...",
                        progress = if (total > 0) done.toFloat() / total else null,
                    )
                }
            }
            updateFlash {
                it.copy(running = false, ok = true, progress = 1f, status = "Installed ${firmware.short}. Connect the board above to use it.")
            }
            setMessage("Firmware installed.")
        } catch (e: Exception) {
            val why = e.message ?: e.javaClass.simpleName
            appendLog("! $why")
            updateFlash { it.copy(running = false, ok = false, progress = null, status = why) }
            setMessage(why)
        } finally {
            port?.close()
            // Let auto-connect try this board again now that it has (new) firmware. `tried` is only touched on the main thread.
            viewModelScope.launch { tried.removeAll { it.endsWith(":$name") } }
        }
    }

    fun ping() {
        if (!bridge.connected) {
            setMessage("Connect a board first.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                bridge.ping()
            } catch (e: Exception) {
                appendLog("! ${e.message ?: e.javaClass.simpleName}")
                bridge.disconnectAll()
                setMessage("Lost the connection. Reconnect the board.")
                syncConnected()
            }
        }
    }

    fun clearLog() = _state.update { it.copy(log = emptyList()) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun startConnect(role: Role, driver: UsbSerialDriver) {
        viewModelScope.launch(Dispatchers.IO) { connectNow(role, driver, quiet = false) }
    }

    /** Opens the board and checks its sketch. [quiet] keeps failures out of the snackbar (log and status only). */
    private suspend fun connectNow(role: Role, driver: UsbSerialDriver, quiet: Boolean) {
        updateRole(role) { it.copy(busy = true, status = "Connecting...") }
        val baud = _state.value.baud
        try {
            val transport = usb.openTransport(driver, baud)
            bridge.connect(role, transport, driver.device.deviceName)
            prefs.edit().putString(prefKey(role), deviceKey(driver.device)).apply()
        } catch (e: Exception) {
            val why = e.message ?: e.javaClass.simpleName
            appendLog("! $why")
            updateRole(role) { it.copy(status = why) }
            if (!quiet) setMessage(why)
        } finally {
            updateRole(role) { it.copy(busy = false) }
            syncConnected()
        }
    }

    private fun prefKey(role: Role) = "port_${role.key}"

    private fun deviceKey(device: UsbDevice) = String.format("%04x:%04x", device.vendorId, device.productId)

    /** A combined sketch can connect both roles at once, so always re-read the bridge. */
    private fun syncConnected() = _state.update { s ->
        s.copy(roles = s.roles.mapValues { (role, ui) ->
            val on = bridge.has(role)
            ui.copy(
                connected = on,
                status = when {
                    on -> "Connected"
                    ui.connected -> "Not connected"
                    else -> ui.status
                },
            )
        })
    }

    private fun updateRole(role: Role, f: (RoleUi) -> RoleUi) = _state.update { s ->
        s.copy(roles = s.roles + (role to f(s.roles.getValue(role))))
    }

    private fun updateFlash(f: (FlashUi) -> FlashUi) = _state.update { s -> s.copy(flash = f(s.flash)) }

    private fun setMessage(text: String) = _state.update { it.copy(message = text) }

    private fun appendLog(line: String) = _state.update { s ->
        s.copy(log = (s.log + line).takeLast(MAX_LOG_LINES))
    }

    private fun describe(device: UsbDevice): String {
        val name = runCatching { device.productName }.getOrNull()
        return String.format("%s (%04X:%04X)", name ?: "USB serial", device.vendorId, device.productId)
    }

    override fun onCleared() {
        runCatching { context.unregisterReceiver(permissionReceiver) }
        runCatching { context.unregisterReceiver(detachReceiver) }
        bridge.disconnectAll()
    }

    private companion object {
        const val ACTION_USB_PERMISSION = "com.huerte.remotecloner.USB_PERMISSION"
        const val MAX_LOG_LINES = 500
    }
}
