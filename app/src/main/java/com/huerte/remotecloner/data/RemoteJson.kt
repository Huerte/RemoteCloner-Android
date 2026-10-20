package com.huerte.remotecloner.data

import org.json.JSONArray
import org.json.JSONObject

/** Reads and writes the Windows app's remote format: {"notes": "", "keys": {KEY: code}, "macros": {name: text}}. */
object RemoteJson {

    fun parse(text: String): Remote {
        val root = JSONObject(text)
        val newFormat = root.has("keys")                 // older files are a plain {KEY: code} map
        val keysObj = if (newFormat) root.getJSONObject("keys") else root
        val keys = LinkedHashMap<String, IrCode>()
        for (k in keysObj.keys()) keys[k] = parseCode(keysObj.getJSONObject(k))
        val macros = LinkedHashMap<String, String>()
        if (newFormat) {
            root.optJSONObject("macros")?.let { m -> for (k in m.keys()) macros[k] = m.optString(k, "") }
        }
        return Remote(
            notes = if (newFormat) root.optString("notes", "") else "",
            keys = keys,
            macros = macros,
        )
    }

    fun encode(remote: Remote): String {
        val keys = JSONObject()
        remote.keys.forEach { (k, c) -> keys.put(k, codeToJson(c)) }
        val macros = JSONObject()
        remote.macros.forEach { (k, v) -> macros.put(k, v) }
        val root = JSONObject()
            .put("notes", remote.notes)
            .put("keys", keys)
            .put("macros", macros)
        return asciiOnly(root.toString(2))
    }

    private fun parseCode(o: JSONObject): IrCode {
        val raw = o.optJSONArray("raw")?.let { a -> List(a.length()) { a.optInt(it) } } ?: emptyList()
        return IrCode(
            proto = o.intOrNull("proto"),
            name = if (o.has("name") && !o.isNull("name")) o.getString("name") else null,
            addr = o.intOrNull("addr"),
            cmd = o.intOrNull("cmd"),
            khz = o.optInt("khz", 38),
            raw = raw,
            note = o.optString("note", ""),
        )
    }

    private fun codeToJson(c: IrCode): JSONObject {
        val o = JSONObject()
        c.proto?.let { o.put("proto", it) }
        c.name?.let { o.put("name", it) }
        c.addr?.let { o.put("addr", it) }
        c.cmd?.let { o.put("cmd", it) }
        o.put("khz", c.khz)
        o.put("raw", JSONArray(c.raw))
        if (c.note.isNotEmpty()) o.put("note", c.note)
        return o
    }

    private fun JSONObject.intOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    /** Same as Python's json.dumps default, so the Windows app reads the file whatever its locale encoding. */
    private fun asciiOnly(s: String): String = buildString {
        for (ch in s) if (ch.code > 126) append("\\u%04x".format(ch.code)) else append(ch)
    }
}
