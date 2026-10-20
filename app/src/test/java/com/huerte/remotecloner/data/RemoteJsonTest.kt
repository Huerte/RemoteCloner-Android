package com.huerte.remotecloner.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteJsonTest {
    private val windowsFile = """
        {
          "notes": "Living room TV",
          "keys": {
            "POWER": {"proto": 8, "name": "NEC", "addr": 0, "cmd": 69, "khz": 38, "raw": [9000, 4500, 560, 560]},
            "MUTE": {"khz": 36, "raw": [1000, 2000], "note": "raw only"}
          },
          "macros": {"Movie": "POWER, 500, MUTE"}
        }
    """.trimIndent()

    @Test
    fun parsesWindowsFile() {
        val r = RemoteJson.parse(windowsFile)
        assertEquals("Living room TV", r.notes)
        assertEquals(setOf("POWER", "MUTE"), r.keys.keys)
        assertEquals("POWER, 500, MUTE", r.macros["Movie"])
        val power = r.keys.getValue("POWER")
        assertEquals(8, power.proto)
        assertEquals("NEC", power.name)
        assertEquals(69, power.cmd)
        assertEquals(listOf(9000, 4500, 560, 560), power.raw)
        assertTrue(power.decodable)
        val mute = r.keys.getValue("MUTE")
        assertNull(mute.proto)
        assertEquals(36, mute.khz)
        assertEquals("raw only", mute.note)
        assertFalse(mute.decodable)
    }

    @Test
    fun migratesPlainKeyMap() {
        val r = RemoteJson.parse("""{"POWER": {"proto": 1, "name": "NEC", "addr": 1, "cmd": 2, "khz": 38, "raw": [1, 2]}}""")
        assertEquals(setOf("POWER"), r.keys.keys)
        assertEquals("", r.notes)
        assertTrue(r.macros.isEmpty())
    }

    @Test
    fun roundTripKeepsEverything() {
        val r = RemoteJson.parse(windowsFile)
        assertEquals(r, RemoteJson.parse(RemoteJson.encode(r)))
    }

    @Test
    fun encodeEscapesNonAscii() {
        val text = RemoteJson.encode(Remote(notes = "salas \u00f1"))
        assertTrue(text.all { it.code < 127 })
        assertEquals("salas \u00f1", RemoteJson.parse(text).notes)
    }

    @Test
    fun withKeyKeepsPositionOnReplaceAndMovesOnRename() {
        val a = IrCode(raw = listOf(1, 2))
        val b = IrCode(raw = listOf(3, 4))
        val keys = linkedMapOf("A" to a, "B" to a, "C" to a)
        assertEquals(listOf("A", "B", "C"), keys.withKey("B", "B", b).keys.toList())
        assertEquals(b, keys.withKey("B", "B", b).getValue("B"))
        assertEquals(listOf("A", "C", "D"), keys.withKey("B", "D", b).keys.toList())
    }

    @Test
    fun namesAreCleaned() {
        assertEquals("POWER_ON", keyName("  power on "))
        assertEquals("Living room TV", safeName(" Living room TV/../ "))
    }
}
