package com.huerte.remotecloner.data

import java.io.File

/** One JSON file per remote inside [dir]. Names must already be cleaned with [safeName]. */
class RemoteStore(private val dir: File) {
    init {
        dir.mkdirs()
    }

    private fun file(name: String) = File(dir, "$name.json")

    fun names(): List<String> =
        dir.listFiles { f -> f.isFile && f.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()

    fun exists(name: String): Boolean = file(name).exists()

    fun load(name: String): Remote = RemoteJson.parse(readText(name))

    fun save(name: String, remote: Remote) = writeText(name, RemoteJson.encode(remote))

    fun create(name: String) = save(name, Remote())

    fun delete(name: String) {
        file(name).delete()
    }

    fun rename(old: String, new: String) {
        file(old).renameTo(file(new))
    }

    fun duplicate(old: String, new: String) {
        file(old).copyTo(file(new), overwrite = true)
    }

    fun readText(name: String): String = file(name).readText(Charsets.UTF_8)

    fun writeText(name: String, text: String) = file(name).writeText(text, Charsets.UTF_8)
}
