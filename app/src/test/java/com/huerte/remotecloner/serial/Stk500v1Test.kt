package com.huerte.remotecloner.serial

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A simulated Arduino with an Optiboot-style STK500v1 bootloader and a virtual clock.
 * It only starts listening after a DTR falling edge (the board reset) and only at [answersAtBaud].
 */
private class FakeBoard(
    private val answersAtBaud: Int = 115200,
    private val signature: ByteArray = byteArrayOf(0x1E, 0x95.toByte(), 0x0F),
    private val chunk: Int = 1000,
    private val garbageAfterReset: ByteArray = ByteArray(0),
    private val corruptReadAt: Int = -1,
    private val dieAfterPages: Int = -1,
) : FlashPort {
    val flash = ByteArray(32768) { 0xFF.toByte() }
    val bauds = mutableListOf<Int>()
    var clock = 0L
    var resets = 0
    var programmedPages = 0
    var left = false
    var closed = false

    private var baud = 0
    private var dtr = false
    private var listening = false
    private var address = 0
    private val inbuf = ArrayList<Byte>()
    private val outbuf = ArrayList<Byte>()
    private var reads = 0

    val sleep: (Long) -> Unit = { clock += it }
    val now: () -> Long = { clock }

    override fun write(data: ByteArray) {
        if (!listening || baud != answersAtBaud) return
        for (b in data) inbuf += b
        process()
    }

    override fun read(timeoutMs: Int): ByteArray {
        if (outbuf.isEmpty()) {
            clock += timeoutMs
            return ByteArray(0)
        }
        val n = minOf(chunk, outbuf.size)
        val out = ByteArray(n) { outbuf[it] }
        repeat(n) { outbuf.removeAt(0) }
        return out
    }

    override fun drain() = outbuf.clear()

    override fun setBaud(baud: Int) {
        this.baud = baud
        bauds += baud
    }

    override fun setLines(dtr: Boolean, rts: Boolean) {
        if (dtr && !this.dtr) {                       // DTR asserted: the reset capacitor pulses the reset pin
            resets++
            listening = true
            inbuf.clear()
            outbuf.clear()
            address = 0
            garbageAfterReset.forEach { outbuf += it }
        }
        this.dtr = dtr
    }

    override fun close() {
        closed = true
    }

    private fun ok() {
        outbuf += 0x14
        outbuf += 0x10
    }

    private fun process() {
        while (inbuf.isNotEmpty()) {
            val cmd = inbuf[0].toInt() and 0xFF
            val need = when (cmd) {
                0x30, 0x50, 0x51, 0x75 -> 2
                0x55 -> 4
                0x74 -> 5
                0x64 -> if (inbuf.size >= 3) 4 + (((inbuf[1].toInt() and 0xFF) shl 8) or (inbuf[2].toInt() and 0xFF)) + 1 else return
                else -> { inbuf.removeAt(0); continue }
            }
            if (inbuf.size < need) return
            val c = ByteArray(need) { inbuf[it] }
            repeat(need) { inbuf.removeAt(0) }
            if (c.last() != 0x20.toByte()) continue                    // bad end-of-packet: no reply
            when (cmd) {
                0x30, 0x50 -> ok()
                0x51 -> { left = true; ok() }
                0x55 -> {
                    address = ((c[1].toInt() and 0xFF) or ((c[2].toInt() and 0xFF) shl 8)) * 2
                    ok()
                }
                0x75 -> { outbuf += 0x14; signature.forEach { outbuf += it }; outbuf += 0x10 }
                0x64 -> {
                    if (dieAfterPages in 0..programmedPages) return
                    val len = c.size - 5
                    for (i in 0 until len) flash[address + i] = c[4 + i]
                    programmedPages++
                    ok()
                }
                0x74 -> {
                    val len = ((c[1].toInt() and 0xFF) shl 8) or (c[2].toInt() and 0xFF)
                    outbuf += 0x14
                    for (i in 0 until len) {
                        var v = flash[address + i]
                        if (corruptReadAt == reads && i == 5) v = (v.toInt() xor 0x01).toByte()
                        outbuf += v
                    }
                    reads++
                    outbuf += 0x10
                }
            }
        }
    }
}

class Stk500v1Test {
    private fun image(size: Int = 1000): HexImage = HexImage(ByteArray(size) { (it * 7 + 3).toByte() })

    private fun flasher(board: FakeBoard, logs: MutableList<String> = mutableListOf()) =
        Stk500v1(board, log = { logs += it }, sleep = board.sleep, now = board.now)

    private fun assertProgrammed(board: FakeBoard, img: HexImage) {
        assertArrayEquals(img.bytes, board.flash.copyOf(img.size))
    }

    @Test
    fun flashesVerifiesAndStartsTheProgram() {
        val board = FakeBoard()
        val img = image(1000)
        val steps = mutableListOf<Triple<String, Int, Int>>()
        flasher(board).flash(img) { phase, done, total -> steps += Triple(phase, done, total) }

        assertProgrammed(board, img)
        assertTrue(board.left)
        assertEquals(1, board.resets)
        assertEquals(8, board.programmedPages)                         // 1000 bytes = 8 pages of 128
        assertEquals(Triple("Connecting", 0, 0), steps.first())
        assertEquals(Triple("Writing", 8, 8), steps.first { it.first == "Writing" && it.second == 8 })
        assertEquals(Triple("Verifying", 8, 8), steps.last())
    }

    @Test
    fun lastPartialPageIsPaddedWithErasedBytes() {
        val board = FakeBoard()
        val img = image(130)                                          // 128 + 2
        flasher(board).flash(img)
        assertProgrammed(board, img)
        assertEquals(0xFF, board.flash[130].toInt() and 0xFF)
        assertEquals(2, board.programmedPages)
    }

    @Test
    fun fallsBackToTheOldNanoBootloaderBaudRate() {
        val board = FakeBoard(answersAtBaud = 57600)
        val img = image(300)
        flasher(board).flash(img)
        assertEquals(listOf(115200, 57600), board.bauds)
        assertEquals(2, board.resets)                                 // each attempt resets the board again
        assertProgrammed(board, img)
    }

    @Test
    fun toleratesBytesArrivingOneAtATimeAndStartupGarbage() {
        val board = FakeBoard(chunk = 1, garbageAfterReset = byteArrayOf(0x00, 0xFF.toByte(), 0x14))
        val img = image(400)
        flasher(board).flash(img)
        assertProgrammed(board, img)
    }

    @Test
    fun noBootloaderGivesAHelpfulError() {
        val board = FakeBoard(answersAtBaud = -1)
        val e = assertThrows(FlashException::class.java) { flasher(board).flash(image()) }
        assertTrue(e.message!!.contains("No reply"))
        assertTrue(e.message!!.contains("reset button"))
        assertEquals(listOf(115200, 57600), board.bauds)
        assertFalse(board.left)
    }

    @Test
    fun aDifferentChipIsRefusedBeforeAnythingIsWritten() {
        val board = FakeBoard(signature = byteArrayOf(0x1E, 0x94.toByte(), 0x06))   // ATmega168
        val e = assertThrows(FlashException::class.java) { flasher(board).flash(image()) }
        assertTrue(e.message!!.contains("1E 94 06"))
        assertEquals(0, board.programmedPages)
    }

    @Test
    fun anOversizedImageNeverTouchesTheBoard() {
        val board = FakeBoard()
        val e = assertThrows(FlashException::class.java) { flasher(board).flash(image(40000)) }
        assertTrue(e.message!!.contains("40000"))
        assertEquals(0, board.resets)
        assertTrue(board.bauds.isEmpty())
    }

    @Test
    fun verifyCatchesACorruptedWrite() {
        val board = FakeBoard(corruptReadAt = 1)
        val e = assertThrows(FlashException::class.java) { flasher(board).flash(image(500)) }
        assertTrue(e.message!!.contains("Verify failed"))
        assertTrue(e.message!!.contains("0x0085"))                    // second page (0x80) + byte 5
        assertFalse(board.left)
    }

    @Test
    fun aBoardThatStopsAnsweringMidwayReportsIt() {
        val board = FakeBoard(dieAfterPages = 2)
        val e = assertThrows(FlashException::class.java) { flasher(board).flash(image(1000)) }
        assertTrue(e.message!!.contains("stopped answering"))
        assertEquals(2, board.programmedPages)
    }

    @Test
    fun resetPulsesDtrLowThenHighThenLow() {
        val calls = mutableListOf<Pair<Boolean, Boolean>>()
        val port = object : FlashPort {
            override fun write(data: ByteArray) {}
            override fun read(timeoutMs: Int) = ByteArray(0)
            override fun drain() {}
            override fun setBaud(baud: Int) {}
            override fun setLines(dtr: Boolean, rts: Boolean) { calls += dtr to rts }
            override fun close() {}
        }
        Stk500v1(port, sleep = {}).reset()
        assertEquals(listOf(false to false, true to true, false to false), calls)
    }

    @Test
    fun theBundledFirmwareFlashesEndToEnd() {
        for (fw in Firmware.entries) {
            val img = HexImage.parse(File("src/main/assets/${fw.asset}").readText())
            val board = FakeBoard()
            flasher(board).flash(img)
            assertProgrammed(board, img)
            assertTrue("${fw.name} did not restart", board.left)
        }
    }
}
