package com.huerte.remotecloner.data

/**
 * One captured key. Field names match the Windows app's JSON (proto, name, addr, cmd, khz, raw, note),
 * so remotes can be moved between the two apps. A raw-only capture has no proto/name/addr/cmd.
 */
data class IrCode(
    val proto: Int? = null,
    val name: String? = null,
    val addr: Int? = null,
    val cmd: Int? = null,
    val khz: Int = 38,
    val raw: List<Int> = emptyList(),
    val note: String = "",
) {
    /** True when the sender can replay it with the compact "D" command. */
    val decodable: Boolean get() = proto != null && !name.isNullOrEmpty() && name != "UNKNOWN"

    val protocolLabel: String get() = name ?: "RAW"

    fun summary(): String =
        "Protocol ${name ?: "RAW"}, address ${addr ?: "-"}, command ${cmd ?: "-"}\n${raw.size} pulses at $khz kHz"
}

data class Remote(
    val notes: String = "",
    val keys: Map<String, IrCode> = emptyMap(),
    val macros: Map<String, String> = emptyMap(),
)

enum class SendMode { RAW, DECODED }

val PRESETS: List<String> = listOf(
    "POWER", "MUTE", "VOL_UP", "VOL_DOWN", "CH_UP", "CH_DOWN", "UP", "DOWN", "LEFT",
    "RIGHT", "OK", "MENU", "BACK", "HOME", "INPUT",
) + (0..9).map { it.toString() }

fun keyName(text: String): String = text.trim().uppercase().replace(" ", "_")

/** Remote names become file names, so keep letters, digits, space, underscore and dash only. */
fun safeName(text: String): String =
    text.trim().filter { it.isLetterOrDigit() || it == ' ' || it == '_' || it == '-' }

/**
 * Returns a copy with [code] stored under [name]. If [old] is the same as [name] the key keeps its
 * position, otherwise [old] is removed and the key goes to the end.
 */
fun Map<String, IrCode>.withKey(old: String?, name: String, code: IrCode): Map<String, IrCode> {
    val out = LinkedHashMap<String, IrCode>()
    for ((k, v) in this) {
        when {
            k == old -> if (name == old) out[k] = code
            k != name -> out[k] = v
        }
    }
    if (name !in out) out[name] = code
    return out
}
