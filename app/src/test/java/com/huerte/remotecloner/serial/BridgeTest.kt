package com.huerte.remotecloner.serial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeTransport(vararg replies: String) : Transport {
    private val queue = ArrayDeque(replies.toList())
    val written = mutableListOf<String>()
    override var isOpen = true

    override fun writeLine(text: String) { written += text }
    override fun readLine(timeoutMs: Long): String = queue.removeFirstOrNull() ?: ""
    override fun drain() {}
    override fun close() { isOpen = false }
}

class BridgeTest {
    private fun bridge() = Bridge(log = {}, settleMs = 0)

    @Test
    fun receiverBoardConnectsAsReceiver() {
        val b = bridge()
        val t = FakeTransport("PONG RECEIVER")
        b.connect(Role.RECEIVER, t, "usb0")
        assertTrue(b.has(Role.RECEIVER))
        assertFalse(b.has(Role.SENDER))
        assertEquals(listOf("P"), t.written)
    }

    @Test
    fun unpluggedDeviceDropsOnlyItsOwnRoles() {
        val b = bridge()
        val t = FakeTransport("PONG RECEIVER")
        b.connect(Role.RECEIVER, t, "usb0")
        assertFalse(b.disconnectDevice("usb9"))
        assertTrue(b.has(Role.RECEIVER))
        assertTrue(b.disconnectDevice("usb0"))
        assertFalse(b.has(Role.RECEIVER))
        assertFalse(t.isOpen)
    }

    @Test
    fun senderBoardOnReceiverSlotIsRejectedAndClosed() {
        val b = bridge()
        val t = FakeTransport("PONG SENDER")
        val e = assertThrows(IllegalStateException::class.java) { b.connect(Role.RECEIVER, t, "usb0") }
        assertTrue(e.message!!.contains("sender"))
        assertFalse(t.isOpen)
        assertFalse(b.connected)
    }

    @Test
    fun combinedLegacySketchFillsBothRoles() {
        val b = bridge()
        b.connect(Role.RECEIVER, FakeTransport("PONG"), "usb0")
        assertTrue(b.has(Role.RECEIVER))
        assertTrue(b.has(Role.SENDER))
    }

    @Test
    fun silentBoardFailsAndClosesPort() {
        val b = bridge()
        val t = FakeTransport()
        assertThrows(IllegalStateException::class.java) { b.connect(Role.SENDER, t, "usb0") }
        assertFalse(t.isOpen)
    }

    @Test
    fun sharedPortStaysOpenUntilBothRolesDisconnect() {
        val b = bridge()
        val t = FakeTransport("PONG")
        b.connect(Role.RECEIVER, t, "usb0")
        b.disconnect(Role.RECEIVER)
        assertTrue(t.isOpen)
        b.disconnect(Role.SENDER)
        assertFalse(t.isOpen)
    }

    @Test
    fun pingAsksEachDistinctBoardOnce() {
        val b = bridge()
        val shared = FakeTransport("PONG", "PONG")
        b.connect(Role.RECEIVER, shared, "usb0")
        val replies = b.ping()
        assertEquals(1, replies.size)
        assertEquals(listOf("P", "P"), shared.written)
    }
}
