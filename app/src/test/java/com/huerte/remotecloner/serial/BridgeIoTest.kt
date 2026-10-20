package com.huerte.remotecloner.serial

import com.huerte.remotecloner.data.IrCode
import com.huerte.remotecloner.data.SendMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

private class ScriptedTransport(vararg replies: String) : Transport {
    private val queue = ArrayDeque(replies.toList())
    val written = mutableListOf<String>()
    override var isOpen = true
    override fun writeLine(text: String) { written += text }
    override fun readLine(timeoutMs: Long): String = queue.removeFirstOrNull() ?: ""
    override fun drain() {}
    override fun close() { isOpen = false }
}

class BridgeIoTest {
    private fun bridge() = Bridge(log = {}, settleMs = 0)

    @Test
    fun learnReturnsDecodedAndRawData() {
        val b = bridge()
        val t = ScriptedTransport("PONG RECEIVER", "OK learning", "DEC 8 NEC 0 69", "RAW 38 4 9000,4500,560,560")
        b.connect(Role.RECEIVER, t, "usb0")
        val code = b.learn({ false }, timeoutMs = 2000)!!
        assertEquals("NEC", code.name)
        assertEquals(69, code.cmd)
        assertEquals(listOf(9000, 4500, 560, 560), code.raw)
        assertEquals(listOf("P", "L"), t.written)
    }

    @Test
    fun learnCancelledSendsCancelAndReturnsNull() {
        val b = bridge()
        val t = ScriptedTransport("PONG RECEIVER")
        b.connect(Role.RECEIVER, t, "usb0")
        assertNull(b.learn({ true }, timeoutMs = 2000))
        assertEquals(listOf("P", "L", "C"), t.written)
    }

    @Test
    fun learnOverflowThrows() {
        val b = bridge()
        b.connect(Role.RECEIVER, ScriptedTransport("PONG RECEIVER", "OK learning", "ERR overflow"), "usb0")
        assertThrows(IllegalStateException::class.java) { b.learn({ false }, timeoutMs = 2000) }
    }

    @Test
    fun learnNeedsReceiver() {
        assertThrows(IllegalStateException::class.java) { bridge().learn({ false }) }
    }

    @Test
    fun sendRawRepeatsAndReturnsReply() {
        val b = bridge()
        val t = ScriptedTransport("PONG SENDER", "OK sent", "OK sent")
        b.connect(Role.SENDER, t, "usb1")
        val reply = b.send(IrCode(raw = listOf(1, 2)), SendMode.RAW, repeats = 1)
        assertEquals("OK sent", reply)
        assertEquals(listOf("P", "S 38 2 1,2", "S 38 2 1,2"), t.written)
    }

    @Test
    fun sendNeedsSender() {
        assertThrows(IllegalStateException::class.java) { bridge().send(IrCode(raw = listOf(1, 2))) }
    }
}
