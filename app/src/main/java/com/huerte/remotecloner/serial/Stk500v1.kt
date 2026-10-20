package com.huerte.remotecloner.serial

import java.io.ByteArrayOutputStream

class FlashException(message: String) : Exception(message)

/** Called as flashing advances. [total] is 0 while the phase has no page count (for example while connecting). */
typealias FlashProgress = (phase: String, done: Int, total: Int) -> Unit

/**
 * Programs an Arduino Uno or Nano (ATmega328P) through its bootloader, the same job avrdude does on a PC.
 * Optiboot (Uno, new Nano) and the older Nano bootloader both speak STK500 version 1.
 *
 * Everything goes through [FlashPort], [sleep] and [now], so the whole flow can be tested against a fake board.
 * All calls block, so run them off the main thread.
 */
class Stk500v1(
    private val port: FlashPort,
    private val log: (String) -> Unit = {},
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    /** Bytes that arrived beyond what the last read asked for. */
    private var carry = ByteArray(0)

    /**
     * Full flow: find the bootloader (trying each baud rate in [bauds]), check the chip, write [image],
     * read it back to verify, then start the new program.
     */
    fun flash(
        image: HexImage,
        bauds: List<Int> = DEFAULT_BAUDS,
        progress: FlashProgress = { _, _, _ -> },
    ) {
        if (image.size > MAX_APP_BYTES) {
            throw FlashException("This firmware is ${image.size} bytes, more than the $MAX_APP_BYTES an Uno or Nano can hold.")
        }
        progress("Connecting", 0, 0)
        var found = false
        for (baud in bauds) {
            log("Looking for the bootloader at $baud baud")
            port.setBaud(baud)
            reset()
            if (sync()) {
                log("Bootloader found at $baud baud")
                found = true
                break
            }
            log("No reply at $baud baud")
        }
        if (!found) {
            throw FlashException(
                "No reply from the bootloader. This works with Arduino Uno and Nano boards (ATmega328P). " +
                    "Check that the cable carries data, then try again. " +
                    "If it still fails, press the board's reset button right after tapping Install."
            )
        }
        checkChip()
        enterProgMode()
        program(image, progress)
        verify(image, progress)
        leave()
        log("Done, the board is restarting with the new firmware")
    }

    /**
     * Pulses DTR and RTS like avrdude does for Arduino boards. The falling edge on DTR is coupled to the
     * reset pin through a capacitor, so the chip restarts and its bootloader waits for a few seconds for us.
     */
    fun reset() {
        carry = ByteArray(0)
        port.setLines(dtr = false, rts = false)
        sleep(50)
        port.setLines(dtr = true, rts = true)
        sleep(100)
        port.setLines(dtr = false, rts = false)
        sleep(50)
        port.drain()
    }

    /** True once the bootloader answers the handshake. Retries for about two seconds. */
    fun sync(attempts: Int = SYNC_ATTEMPTS): Boolean {
        repeat(attempts) {
            port.drain()
            carry = ByteArray(0)
            port.write(byteArrayOf(GET_SYNC, EOP))
            val r = readExact(2, SYNC_TIMEOUT_MS)
            if (r.size == 2 && r[0] == INSYNC && r[1] == OK) return true
        }
        return false
    }

    /**
     * Refuses a chip that is clearly not an ATmega328 family part. A bootloader that does not answer the
     * signature request is let through, because some old ones do not implement it.
     */
    private fun checkChip() {
        port.write(byteArrayOf(READ_SIGN, EOP))
        val r = readExact(5, 1000)
        if (r.size == 5 && r[0] == INSYNC && r[4] == OK) {
            val sig = r.copyOfRange(1, 4)
            val text = sig.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            if (sig.contentEquals(SIG_328P) || sig.contentEquals(SIG_328)) {
                log("Chip signature $text (ATmega328)")
            } else {
                throw FlashException("The chip on this board reports signature $text, not an ATmega328P. Nothing was written.")
            }
        } else {
            log("Could not read the chip signature, continuing")
            port.drain()
            carry = ByteArray(0)
        }
    }

    private fun enterProgMode() {
        port.write(byteArrayOf(ENTER_PROGMODE, EOP))
        expectOk("starting the update")
    }

    private fun program(image: HexImage, progress: FlashProgress) {
        val data = image.bytes
        val pages = (data.size + PAGE_SIZE - 1) / PAGE_SIZE
        for (p in 0 until pages) {
            val from = p * PAGE_SIZE
            val page = ByteArray(PAGE_SIZE) { i -> if (from + i < data.size) data[from + i] else ERASED }
            loadAddress(from)
            val cmd = ByteArray(PAGE_SIZE + 5)
            cmd[0] = PROG_PAGE
            cmd[1] = (PAGE_SIZE shr 8).toByte()
            cmd[2] = (PAGE_SIZE and 0xFF).toByte()
            cmd[3] = MEM_FLASH
            page.copyInto(cmd, 4)
            cmd[PAGE_SIZE + 4] = EOP
            port.write(cmd)
            expectOk("writing page ${p + 1} of $pages")
            progress("Writing", p + 1, pages)
        }
    }

    private fun verify(image: HexImage, progress: FlashProgress) {
        val data = image.bytes
        val pages = (data.size + PAGE_SIZE - 1) / PAGE_SIZE
        for (p in 0 until pages) {
            val from = p * PAGE_SIZE
            loadAddress(from)
            port.write(byteArrayOf(READ_PAGE, (PAGE_SIZE shr 8).toByte(), (PAGE_SIZE and 0xFF).toByte(), MEM_FLASH, EOP))
            val r = readExact(PAGE_SIZE + 2, 2000)
            if (r.size < PAGE_SIZE + 2) throw FlashException("The board stopped answering while checking page ${p + 1} of $pages.")
            if (r[0] != INSYNC || r[PAGE_SIZE + 1] != OK) throw FlashException("The board gave a bad reply while checking page ${p + 1}.")
            for (i in 0 until PAGE_SIZE) {
                val want = if (from + i < data.size) data[from + i] else ERASED
                if (r[1 + i] != want) {
                    throw FlashException(
                        "Verify failed at address 0x%04X. The bootloader is still intact, so you can try again.".format(from + i)
                    )
                }
            }
            progress("Verifying", p + 1, pages)
        }
    }

    private fun leave() {
        port.write(byteArrayOf(LEAVE_PROGMODE, EOP))
        expectOk("finishing the update")
    }

    /** Addresses are in 16-bit words, so the byte address is halved. */
    private fun loadAddress(byteAddress: Int) {
        val word = byteAddress / 2
        port.write(byteArrayOf(LOAD_ADDRESS, (word and 0xFF).toByte(), ((word shr 8) and 0xFF).toByte(), EOP))
        expectOk("setting the address 0x%04X".format(byteAddress))
    }

    private fun expectOk(what: String, timeoutMs: Long = 2000) {
        val r = readExact(2, timeoutMs)
        if (r.size < 2) throw FlashException("The board stopped answering while $what. The bootloader is still intact, so you can try again.")
        if (r[0] != INSYNC || r[1] != OK) {
            val got = r.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            throw FlashException("The board rejected the request while $what (reply $got).")
        }
    }

    /** Reads exactly [n] bytes, or fewer if [timeoutMs] runs out first. Surplus bytes are kept for the next read. */
    private fun readExact(n: Int, timeoutMs: Long): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(carry)
        carry = ByteArray(0)
        val end = now() + timeoutMs
        while (out.size() < n) {
            val left = end - now()
            if (left <= 0) break
            out.write(port.read(minOf(left, 100L).toInt()))
        }
        val all = out.toByteArray()
        if (all.size <= n) return all
        carry = all.copyOfRange(n, all.size)
        return all.copyOfRange(0, n)
    }

    companion object {
        /** Optiboot (Uno, new Nano) runs at 115200, the older Nano bootloader at 57600. */
        val DEFAULT_BAUDS = listOf(115200, 57600)

        /** The ATmega328P has 32 KB of flash. 30720 leaves room for the largest (old, 2 KB) bootloader. */
        const val MAX_APP_BYTES = 30720

        const val PAGE_SIZE = 128
        private const val SYNC_ATTEMPTS = 10
        private const val SYNC_TIMEOUT_MS = 200L

        private const val INSYNC: Byte = 0x14
        private const val OK: Byte = 0x10
        private const val EOP: Byte = 0x20
        private const val GET_SYNC: Byte = 0x30
        private const val ENTER_PROGMODE: Byte = 0x50
        private const val LEAVE_PROGMODE: Byte = 0x51
        private const val LOAD_ADDRESS: Byte = 0x55
        private const val PROG_PAGE: Byte = 0x64
        private const val READ_PAGE: Byte = 0x74
        private const val READ_SIGN: Byte = 0x75
        private const val MEM_FLASH: Byte = 0x46      // 'F'
        private const val ERASED: Byte = 0xFF.toByte()

        private val SIG_328P = byteArrayOf(0x1E, 0x95.toByte(), 0x0F)
        private val SIG_328 = byteArrayOf(0x1E, 0x95.toByte(), 0x14)
    }
}
