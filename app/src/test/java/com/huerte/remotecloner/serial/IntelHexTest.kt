package com.huerte.remotecloner.serial

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Builds one valid HEX record with the right checksum. */
internal fun hexRecord(addr: Int, type: Int, data: List<Int>): String {
    val bytes = listOf(data.size, addr shr 8, addr and 0xFF, type) + data
    val sum = (-(bytes.sum()) and 0xFF)
    return ":" + (bytes + sum).joinToString("") { "%02X".format(it and 0xFF) }
}

class IntelHexTest {
    private val eof = hexRecord(0, 1, emptyList())

    @Test
    fun parsesDataRecords() {
        val text = hexRecord(0, 0, listOf(1, 2, 3, 4)) + "\r\n" + hexRecord(4, 0, listOf(5, 6)) + "\r\n" + eof + "\r\n"
        val img = HexImage.parse(text)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), img.bytes)
    }

    @Test
    fun gapsAreFilledWithErasedFlash() {
        val img = HexImage.parse(hexRecord(0, 0, listOf(1)) + "\n" + hexRecord(3, 0, listOf(9)) + "\n" + eof)
        assertArrayEquals(byteArrayOf(1, -1, -1, 9), img.bytes)
    }

    @Test
    fun extendedLinearAddressMovesTheBase() {
        val text = hexRecord(0, 4, listOf(0, 1)) + "\n" + hexRecord(0, 0, listOf(7)) + "\n" + eof
        val img = HexImage.parse(text)
        assertEquals(0x10001, img.size)
        assertEquals(7, img.bytes[0x10000].toInt())
    }

    @Test
    fun badChecksumIsRejected() {
        val good = hexRecord(0, 0, listOf(1, 2))
        val bad = good.dropLast(2) + "00"
        val e = assertThrows(HexException::class.java) { HexImage.parse(bad + "\n" + eof) }
        assertTrue(e.message!!.contains("checksum"))
    }

    @Test
    fun garbageIsRejected() {
        assertThrows(HexException::class.java) { HexImage.parse("hello") }
        assertThrows(HexException::class.java) { HexImage.parse(":0G") }
        assertThrows(HexException::class.java) { HexImage.parse(eof) }              // no program data
        assertThrows(HexException::class.java) { HexImage.parse("") }
    }

    @Test
    fun dataAfterEofIsRejected() {
        assertThrows(HexException::class.java) { HexImage.parse(eof + "\n" + hexRecord(0, 0, listOf(1))) }
    }

    @Test
    fun bundledFirmwareParsesAndFitsTheChip() {
        for (fw in Firmware.entries) {
            val file = File("src/main/assets/${fw.asset}")
            assertTrue("missing ${file.path}", file.exists())
            val img = HexImage.parse(file.readText())
            assertTrue("${fw.name} is empty", img.size > 1000)
            assertTrue("${fw.name} is ${img.size} bytes", img.size <= Stk500v1.MAX_APP_BYTES)
            // The AVR reset vector is a JMP (0x940C) at address 0.
            assertEquals(0x0C, img.bytes[0].toInt() and 0xFF)
            assertEquals(0x94, img.bytes[1].toInt() and 0xFF)
        }
    }
}
