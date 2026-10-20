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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.huerte.remotecloner.serial.Bridge
import com.huerte.remotecloner.serial.Role
import com.huerte.remotecloner.serial.openTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeviceOption(val id: String, val label: String)

data class RoleUi(
    val selectedId: String? = null,
    val connected: Boolean = false,
    val busy: Boolean = false,
    val status: String = "Not connected",
)

data class ConnectionUiState(
    val devices: List<DeviceOption> = emptyList(),
    val baud: Int = 115200,
    val roles: Map<Role, RoleUi> = Role.entries.associateWith { RoleUi() },
    val log: List<String> = emptyList(),
    val message: String? = null,
)

class ConnectionViewModel(application: Application) : AndroidViewModel(application) {
    private val context: Application = application
    private val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val drivers = mutableMapOf<String, UsbSerialDriver>()

    private val _state = MutableStateFlow(ConnectionUiState())
    val state: StateFlow<ConnectionUiState> = _state.asStateFlow()

    /** Shared with the Clone, Library and Control tabs. */
    val bridge = Bridge(log = ::appendLog)

    /** Role waiting for the USB permission dialog, and the device it picked. */
    private var pending: Pair<Role, String>? = null

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            val (role, id) = pending ?: return
            pending = null
            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
            val driver = drivers[id]
            if (granted && driver != null) {
                startConnect(role, driver)
            } else {
                updateRole(role) { it.copy(busy = false, status = "USB permission denied") }
                setMessage("USB permission denied.")
            }
        }
    }

    init {
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
        val options = found.map { DeviceOption(it.device.deviceName, describe(it.device)) }
        _state.update { s ->
            val roles = Role.entries.mapIndexed { index, role ->
                val ui = s.roles.getValue(role)
                val keep = ui.selectedId?.takeIf { id -> options.any { it.id == id } }
                val fallback = options.getOrNull(index) ?: options.lastOrNull()
                role to ui.copy(selectedId = keep ?: fallback?.id)
            }.toMap()
            s.copy(devices = options, roles = roles)
        }
        syncConnected()
    }

    fun setBaud(baud: Int) = _state.update { it.copy(baud = baud) }

    fun select(role: Role, id: String) = updateRole(role) { it.copy(selectedId = id) }

    fun toggle(role: Role) {
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
            val intent = Intent(ACTION_USB_PERMISSION).setPackage(context.packageName)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            usb.requestPermission(driver.device, PendingIntent.getBroadcast(context, 0, intent, flags))
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
        updateRole(role) { it.copy(busy = true, status = "Connecting...") }
        val baud = _state.value.baud
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val transport = usb.openTransport(driver, baud)
                bridge.connect(role, transport, driver.device.deviceName)
            } catch (e: Exception) {
                val why = e.message ?: e.javaClass.simpleName
                appendLog("! $why")
                updateRole(role) { it.copy(status = why) }
                setMessage(why)
            } finally {
                updateRole(role) { it.copy(busy = false) }
                syncConnected()
            }
        }
    }

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
        bridge.disconnectAll()
    }

    private companion object {
        const val ACTION_USB_PERMISSION = "com.huerte.remotecloner.USB_PERMISSION"
        const val MAX_LOG_LINES = 500
    }
}
