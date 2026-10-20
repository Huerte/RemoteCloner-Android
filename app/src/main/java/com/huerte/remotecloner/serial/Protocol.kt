package com.huerte.remotecloner.serial

import com.huerte.remotecloner.data.IrCode
import com.huerte.remotecloner.data.SendMode

/** Parsing and formatting for the sketches' serial protocol. Pure functions, no I/O. */
object Protocol {

    /** "DEC <protocol> <name...> <address> <command>" */
    fun applyDec(line: String, base: IrCode): IrCode {
        val p = line.trim().split(Regex("\\s+"))
        require(p.size >= 5) { "Unexpected reply: $line" }
        return base.copy(
            proto = p[1].toInt(),
            name = p.subList(2, p.size - 2).joinToString(" "),
            addr = p[p.size - 2].toInt(),
            cmd = p.last().toInt(),
        )
    }

    /** "RAW <khz> <count> <us,us,us,...>" */
    fun applyRaw(line: String, base: IrCode): IrCode {
        val parts = line.trim().split(" ", limit = 4)
        require(parts.size == 4) { "Unexpected reply: $line" }
        return base.copy(
            khz = parts[1].toInt(),
            raw = parts[3].split(",").map { it.trim().toInt() },
        )
    }

    /** Commands to send for [code]. Decoded mode falls back to raw when the protocol is unknown. */
    fun sendLines(code: IrCode, mode: SendMode, repeats: Int): List<String> {
        if (mode == SendMode.DECODED && code.decodable) {
            return listOf("D ${code.proto} ${code.addr ?: 0} ${code.cmd ?: 0} $repeats")
        }
        require(code.raw.isNotEmpty()) { "This key has no raw timings." }
        val line = "S ${code.khz} ${code.raw.size} " + code.raw.joinToString(",")
        return List(repeats + 1) { line }
    }
}
