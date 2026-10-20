package com.huerte.remotecloner.serial

/** Minimal line-based serial pipe. Keeps [Bridge] free of Android classes so it can be unit tested. */
interface Transport {
    val isOpen: Boolean

    /** Writes [text] followed by a newline. */
    fun writeLine(text: String)

    /** Returns the next non-empty line, or "" if nothing arrived within [timeoutMs]. */
    fun readLine(timeoutMs: Long): String

    /** Throws away anything already received. */
    fun drain()

    fun close()
}
