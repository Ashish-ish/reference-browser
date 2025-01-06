package org.mozilla.reference.browser.components

import java.io.File
import java.nio.charset.Charset

class DiskCache private constructor(
    private val baseDir: File,
    private val maxSize: Long
) {
    private val cacheDir = File(baseDir, "browser_cache")
    private val entriesDir = File(cacheDir, "entries")
    
    init {
        entriesDir.mkdirs()
    }

    fun put(key: String, data: ByteArray) {
        val file = File(entriesDir, key.hashCode().toString())
        file.outputStream().use { it.write(data) }
        maintainCacheSize()
    }

    fun get(key: String): ByteArray? {
        val file = File(entriesDir, key.hashCode().toString())
        return if (file.exists()) file.readBytes() else null
    }

    fun putString(key: String, content: String) {
        put(key, content.toByteArray(Charset.forName("UTF-8")))
    }

    fun getString(key: String): String? {
        return get(key)?.let {
            String(it, Charset.forName("UTF-8"))
        }
    }

    private fun maintainCacheSize() {
        if (getCacheSize() > maxSize) {
            entriesDir.listFiles()
                ?.sortedBy { it.lastModified() }
                ?.take((entriesDir.listFiles()?.size ?: 0) / 2)
                ?.forEach { it.delete() }
        }
    }

    private fun getCacheSize(): Long =
        entriesDir.listFiles()?.sumOf { it.length() } ?: 0L

    class Builder {
        private var baseDir: File? = null
        private var maxSize: Long = 50 * 1024 * 1024L

        fun setBaseDirectory(dir: File): Builder {
            baseDir = dir
            return this
        }

        fun setMaxCacheSize(size: Long): Builder {
            maxSize = size
            return this
        }

        fun build(): DiskCache {
            requireNotNull(baseDir) { "Base directory must be set" }
            return DiskCache(baseDir!!, maxSize)
        }
    }
}