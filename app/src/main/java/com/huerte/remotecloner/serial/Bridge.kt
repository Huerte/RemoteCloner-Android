package com.huerte.remotecloner.serial

import com.huerte.remotecloner.data.IrCode
import com.huerte.remotecloner.data.SendMode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/** One open serial port. A board running the combined legacy sketch fills both roles. */
class Link(val transport: Transport, val tag: String) {
    val lock = ReentrantLock()
}

/**
 * Port of the Windows app's Bridge. All calls block, so run them off the main thread.
 *
 * Protocol: 115200 baud, newline terminated. "P" -> "PONG RECEIVER" / "PONG SENDER".
 */
class Bridge(
    private val log: (String) -> Unit,
    private val settleMs: Long = 2000,
) {
    private val links = ConcurrentHashMap<Role, Link>()

    fun has(role: Role): Boolean = links[role]?.transport?.isOpen == true

    val connected: Boolean get() = Role.entries.any { has(it) }

    /** Adds a line to the serial log. */
    fun note(text: String) = log(text)

    /** Opens [transport] for [role] and checks that the board runs the matching sketch. */
    fun connect(role: Role, transport: Transport, tag: String) {
        disconnect(role)
        val link = Link(transport, tag)
        val reply = try {
            Thread.sleep(settleMs)          // the board resets when the port opens
            transport.drain()
            cmd(link, "P", 2000)
        } catch (e: Exception) {
            transport.close()
            throw e
        }

        val found: Set<Role> = when {
            "PONG RECEIVER" in reply -> setOf(Role.RECEIVER)
            "PONG SENDER" in reply -> setOf(Role.SENDER)
            "PONG" in reply -> Role.entries.toSet()   // combined legacy sketch
            else -> {
                transport.close()
                throw IllegalStateException(
                    "No reply from the board. Flash the firmware from the Windows app " +
                        "and check that the baud rate is 115200."
                )
            }
        }
        if (role !in found) {
            transport.close()
            val other = found.first()
            throw IllegalStateException(
                "This board runs the ${other.key} sketch. Select it as the ${other.key} port instead."
            )
        }
        for (r in found) {
            disconnect(r)
            links[r] = link
        }
    }

    fun disconnect(role: Role) {
        val link = links.remove(role) ?: return
        if (links.values.none { it === link }) link.transport.close()
    }

    fun disconnectAll() = Role.entries.forEach(::disconnect)

    private fun write(link: Link, text: String) {
        log("[${link.tag}] > ${text.take(120)}")
        link.transport.writeLine(text)
    }

    private fun read(link: Link, timeoutMs: Long): String {
        val line = link.transport.readLine(timeoutMs)
        if (line.isNotEmpty()) log("[${link.tag}] < ${line.take(120)}")
        return line
    }

    /** Sends one command and returns the first reply line, or "" on timeout. */
    fun cmd(link: Link, text: String, timeoutMs: Long = 1000): String {
        if (!link.lock.tryLock(timeoutMs + 1000, TimeUnit.MILLISECONDS)) return ""
        try {
            write(link, text)
            return read(link, timeoutMs)
        } finally {
            link.lock.unlock()
        }
    }

    fun ping(): List<String> {
        val seen = mutableListOf<Link>()
        for (link in links.values) if (seen.none { it === link }) seen += link
        return seen.map { cmd(it, "P") }
    }

    /**
     * Puts the receiver in learning mode and waits for one capture.
     * Returns null on timeout or when [isCancelled] turns true.
     */
    fun learn(isCancelled: () -> Boolean, timeoutMs: Long = 20_000): IrCode? {
        val link = links[Role.RECEIVER] ?: throw IllegalStateException("The receiver board is not connected.")
        link.lock.lock()
        try {
            link.transport.drain()
            write(link, "L")
            var code = IrCode()
            val end = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < end && !isCancelled()) {
                val l = read(link, 400)
                when {
                    l.startsWith("DEC") -> code = Protocol.applyDec(l, code)
                    l.startsWith("RAW") -> return Protocol.applyRaw(l, code)
                    l.startsWith("ERR") -> throw IllegalStateException(l)
                }
            }
            write(link, "C")
            read(link, 500)
            return null
        } finally {
            link.lock.unlock()
        }
    }

    /** Replays [code] on the sender board. Returns the last reply line ("OK sent" on success). */
    fun send(code: IrCode, mode: SendMode = SendMode.RAW, repeats: Int = 0): String {
        val link = links[Role.SENDER] ?: throw IllegalStateException("The sender board is not connected.")
        var reply = ""
        for (line in Protocol.sendLines(code, mode, repeats)) {
            val raw = line.startsWith("S")
            reply = cmd(link, line, if (raw) 2000 else 1000)
            if (raw && repeats > 0) Thread.sleep(50)
        }
        return reply
    }
}
