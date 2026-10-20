package com.huerte.remotecloner.serial

/** Raw byte pipe to an Arduino bootloader. Keeps [Stk500v1] free of Android classes so it can be unit tested. */
interface FlashPort {
    /** Writes all of [data]. */
    fun write(data: ByteArray)

    /** Returns whatever arrived within [timeoutMs], or an empty array when nothing did. */
    fun read(timeoutMs: Int): ByteArray

    /** Throws away anything already received. */
    fun drain()

    fun setBaud(baud: Int)

    /** Sets the DTR and RTS lines. On Arduino boards a DTR edge resets the chip into its bootloader. */
    fun setLines(dtr: Boolean, rts: Boolean)

    fun close()
}
