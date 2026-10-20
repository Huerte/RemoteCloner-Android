package com.huerte.remotecloner.serial

class HexException(message: String) : Exception(message)

/** A firmware image. [bytes] starts at address 0 and gaps are filled with 0xFF, the erased state of flash. */
class HexImage(val bytes: ByteArray) {
    val size: Int get() = bytes.size

    companion object {
        private const val MAX_IMAGE_BYTES = 256 * 1024

        /** Parses Intel HEX text (as written by avr-objcopy or the Arduino IDE). Line endings may be LF or CRLF. */
        fun parse(text: String): HexImage {
            val chunks = mutableListOf<Pair<Int, ByteArray>>()
            var base = 0
            var sawEof = false
            var lineNo = 0
            for (raw in text.lineSequence()) {
                lineNo++
                val line = raw.trim()
                if (line.isEmpty()) continue
                if (sawEof) throw HexException("Line $lineNo: data after the end-of-file record.")
                if (line[0] != ':') throw HexException("Line $lineNo: a HEX record must start with ':'.")
                val rec = decode(line.substring(1), lineNo)
                if (rec.size < 5) throw HexException("Line $lineNo: record is too short.")
                val len = rec[0].toInt() and 0xFF
                if (rec.size != len + 5) throw HexException("Line $lineNo: record length does not match its data.")
                var sum = 0
                for (b in rec) sum += b.toInt() and 0xFF
                if ((sum and 0xFF) != 0) throw HexException("Line $lineNo: checksum error.")
                val addr = ((rec[1].toInt() and 0xFF) shl 8) or (rec[2].toInt() and 0xFF)
                val data = rec.copyOfRange(4, 4 + len)
                when (val type = rec[3].toInt() and 0xFF) {
                    0x00 -> if (len > 0) chunks += (base + addr) to data
                    0x01 -> sawEof = true
                    0x02 -> base = needWord(data, lineNo) shl 4
                    0x04 -> base = needWord(data, lineNo) shl 16
                    0x03, 0x05 -> Unit      // start address, not needed to program flash
                    else -> throw HexException("Line $lineNo: unsupported record type $type.")
                }
            }
            if (chunks.isEmpty()) throw HexException("The HEX file contains no program data.")
            val end = chunks.maxOf { (a, d) -> a + d.size }
            if (end > MAX_IMAGE_BYTES) throw HexException("The HEX file is larger than $MAX_IMAGE_BYTES bytes.")
            val out = ByteArray(end) { 0xFF.toByte() }
            for ((a, d) in chunks) d.copyInto(out, a)
            return HexImage(out)
        }

        private fun needWord(data: ByteArray, lineNo: Int): Int {
            if (data.size != 2) throw HexException("Line $lineNo: address record must hold 2 bytes.")
            return ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        }

        private fun decode(hex: String, lineNo: Int): ByteArray {
            if (hex.length % 2 != 0) throw HexException("Line $lineNo: odd number of hex digits.")
            return ByteArray(hex.length / 2) { i ->
                val hi = Character.digit(hex[2 * i], 16)
                val lo = Character.digit(hex[2 * i + 1], 16)
                if (hi < 0 || lo < 0) throw HexException("Line $lineNo: invalid hex digit.")
                ((hi shl 4) or lo).toByte()
            }
        }
    }
}
