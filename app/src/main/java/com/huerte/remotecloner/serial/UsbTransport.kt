package com.huerte.remotecloner.serial

import android.hardware.usb.UsbManager
import android.os.SystemClock
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import java.io.IOException

class UsbTransport(private val port: UsbSerialPort) : Transport {
    private val buf = ByteArray(1024)
    private val pending = StringBuilder()

    override val isOpen: Boolean get() = port.isOpen

    override fun writeLine(text: String) {
        port.write((text + "\n").toByteArray(Charsets.US_ASCII), WRITE_TIMEOUT_MS)
    }

    override fun readLine(timeoutMs: Long): String {
        val end = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            val nl = pending.indexOf("\n")
            if (nl >= 0) {
                val line = pending.substring(0, nl).trim()
                pending.delete(0, nl + 1)
                if (line.isNotEmpty()) return line
                continue
            }
            val left = end - SystemClock.elapsedRealtime()
            if (left <= 0) return ""
            val n = port.read(buf, minOf(left, 200L).toInt())
            if (n > 0) pending.append(String(buf, 0, n, Charsets.ISO_8859_1))
        }
    }

    override fun drain() {
        pending.clear()
        var guard = 0
        while (guard++ < 50 && port.read(buf, 50) > 0) { /* discard */ }
    }

    override fun close() {
        runCatching { port.close() }
    }

    private companion object {
        const val WRITE_TIMEOUT_MS = 1000
    }
}

/** Opens the first port of [driver] at [baud], 8N1. Needs USB permission to be granted already. */
fun UsbManager.openTransport(driver: UsbSerialDriver, baud: Int): Transport {
    val connection = openDevice(driver.device)
        ?: throw IOException("Could not open the USB device. Permission may have been denied.")
    val port = driver.ports.first()
    try {
        port.open(connection)
        port.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        // Some CDC boards stay silent until DTR is asserted. Not every chip supports it.
        runCatching { port.setDTR(true) }
        runCatching { port.setRTS(true) }
    } catch (e: Exception) {
        runCatching { port.close() }
        throw e
    }
    return UsbTransport(port)
}
