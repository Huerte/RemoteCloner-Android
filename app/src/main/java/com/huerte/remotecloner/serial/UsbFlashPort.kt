package com.huerte.remotecloner.serial

import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import java.io.IOException

class UsbFlashPort(private val port: UsbSerialPort) : FlashPort {
    private val buf = ByteArray(1024)

    override fun write(data: ByteArray) {
        port.write(data, WRITE_TIMEOUT_MS)
    }

    override fun read(timeoutMs: Int): ByteArray {
        val n = port.read(buf, timeoutMs)
        return if (n > 0) buf.copyOf(n) else ByteArray(0)
    }

    override fun drain() {
        var guard = 0
        while (guard++ < 50 && port.read(buf, 20) > 0) { /* discard */ }
    }

    override fun setBaud(baud: Int) {
        port.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
    }

    override fun setLines(dtr: Boolean, rts: Boolean) {
        // Not every USB-serial chip supports both lines, so a failure here is not fatal.
        runCatching { port.setDTR(dtr) }
        runCatching { port.setRTS(rts) }
    }

    override fun close() {
        runCatching { port.close() }
    }

    private companion object {
        const val WRITE_TIMEOUT_MS = 2000
    }
}

/** Opens the first port of [driver] at [baud], 8N1, for flashing. Needs USB permission to be granted already. */
fun UsbManager.openFlashPort(driver: UsbSerialDriver, baud: Int): FlashPort {
    val connection = openDevice(driver.device)
        ?: throw IOException("Could not open the USB device. Permission may have been denied.")
    val port = driver.ports.first()
    try {
        port.open(connection)
        port.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
    } catch (e: Exception) {
        runCatching { port.close() }
        throw e
    }
    return UsbFlashPort(port)
}
