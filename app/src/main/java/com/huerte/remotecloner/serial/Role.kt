package com.huerte.remotecloner.serial

/** The two Arduino boards. Each runs its own sketch (ir_receiver / ir_sender). */
enum class Role(val title: String, val key: String) {
    RECEIVER("Receiver (capture)", "receiver"),
    SENDER("Sender (transmit)", "sender"),
}
