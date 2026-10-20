package com.huerte.remotecloner.serial

/** The sketches bundled in assets/firmware, built for the ATmega328P (Arduino Uno and Nano). */
enum class Firmware(val short: String, val title: String, val asset: String, val blurb: String) {
    COMBINED(
        "Combined", "Combined (one board)", "firmware/combined_atmega328p.hex",
        "Capture and transmit on one board. Receiver on D11, IR LED on D3. Fills both roles in the app.",
    ),
    RECEIVER(
        "Receiver", "Receiver only (capture)", "firmware/receiver_atmega328p.hex",
        "Captures signals from your remote. IR receiver output on D11.",
    ),
    SENDER(
        "Sender", "Sender only (transmit)", "firmware/sender_atmega328p.hex",
        "Replays signals. IR LED on D3 through a transistor.",
    ),
}
