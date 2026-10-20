package com.huerte.remotecloner.serial

import com.huerte.remotecloner.data.IrCode
import com.huerte.remotecloner.data.SendMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProtocolTest {
    @Test
    fun parsesDecLineWithSpacesInName() {
        val c = Protocol.applyDec("DEC 8 NEC Extended 4660 69", IrCode())
        assertEquals(8, c.proto)
        assertEquals("NEC Extended", c.name)
        assertEquals(4660, c.addr)
        assertEquals(69, c.cmd)
    }

    @Test
    fun parsesRawLine() {
        val c = Protocol.applyRaw("RAW 38 4 9000,4500,560,560", IrCode(proto = 8))
        assertEquals(38, c.khz)
        assertEquals(listOf(9000, 4500, 560, 560), c.raw)
        assertEquals(8, c.proto)
    }

    @Test
    fun rawModeRepeatsTheSendLine() {
        val code = IrCode(raw = listOf(100, 200, 300))
        assertEquals(listOf("S 38 3 100,200,300", "S 38 3 100,200,300"), Protocol.sendLines(code, SendMode.RAW, 1))
    }

    @Test
    fun decodedModeUsesDLineOnlyWhenProtocolKnown() {
        val known = IrCode(proto = 8, name = "NEC", addr = 1, cmd = 2, raw = listOf(1, 2))
        assertEquals(listOf("D 8 1 2 3"), Protocol.sendLines(known, SendMode.DECODED, 3))
        val unknown = known.copy(name = "UNKNOWN")
        assertEquals(listOf("S 38 2 1,2"), Protocol.sendLines(unknown, SendMode.DECODED, 0))
    }

    @Test
    fun keyWithoutTimingsCannotBeSentRaw() {
        assertThrows(IllegalArgumentException::class.java) {
            Protocol.sendLines(IrCode(), SendMode.RAW, 0)
        }
    }
}
