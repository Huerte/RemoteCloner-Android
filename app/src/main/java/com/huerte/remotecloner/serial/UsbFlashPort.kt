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

    /**
     * Discards all bytes currently in the USB receive buffer.
     * Uses 50 ms read slices and up to 100 loops (5 s ceiling) to handle verbose bootloader banners
     * that can be larger than what a short single read would consume.
     */
    override fun drain() {
        var guard = 0
        while (guard++ < 100 && port.read(buf, DRAIN_SLICE_MS) > 0) { /* discard */ }
    }

    /**
     * Reconfigures the serial port to [baud] baud, 8N1.
     * Sleeps briefly afterwards so the USB-serial chip (CH340, FTDI, CP210x) has time to apply
     * the new line settings before the first write arrives.
     */
    override fun setBaud(baud: Int) {
        port.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        Thread.sleep(BAUD_SETTLE_MS)
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
        /** Read slice for drain() calls, in ms. Large enough to capture a full USB frame. */
        const val DRAIN_SLICE_MS = 50
        /** Time to wait after setBaud for the USB-serial chip to apply the new line config. */
        const val BAUD_SETTLE_MS = 50L
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
