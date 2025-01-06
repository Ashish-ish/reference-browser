package org.mozilla.reference.browser.components

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import mozilla.components.concept.engine.EngineView
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class WikipediaOptimizer {
    private val preloadedResources = mutableSetOf<String>()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var cache: DiskCache

    val commonResources by lazy {
        listOf(
            "https://wikipedia.org/static/images/mobile/copyright/wikipedia.png",
            "https://wikipedia.org/static/css/mobile.css",
            "https://wikipedia.org/static/images/mobile/copyright/wikipedia-wordmark.png",
            "https://wikipedia.org/static/js/mobile.js"
        )
    }

    fun initialize(diskCache: DiskCache) {
        this.cache = diskCache
        preloadCommonResources()
    }

    private fun preloadCommonResources() {

        coroutineScope.launch {
            commonResources.forEach { url ->
                if (!preloadedResources.contains(url)) {
                    try {
                        preloadResource(url)
                        preloadedResources.add(url)
                    } catch (e: Exception) {
                    }
                }
            }
        }
    }

    private suspend fun preloadResource(url: String) = withContext(Dispatchers.IO) {
        // Check if already cached
        if (cache.get(url) != null) return@withContext

        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val content = connection.inputStream.use { input ->
                    when {
                        url.endsWith(".css") -> {
                            input.bufferedReader().use { it.readText() }
                        }

                        url.endsWith(".js") -> {
                            input.bufferedReader().use { it.readText() }
                        }

                        else -> {
                            input.readBytes()
                        }
                    }
                }

                when (content) {
                    is String -> cache.putString(url, content)
                    is ByteArray -> cache.put(url, content)
                }
            }
        } catch (e: Exception) {
            throw e
        }
    }

    private val _preloadProgress = MutableStateFlow(0f)
    val preloadProgress: StateFlow<Float> = _preloadProgress.asStateFlow()

    // Method to start preloading with progress tracking
    fun startPreloading() {
        coroutineScope.launch {
            val totalResources = commonResources.size
            var loadedResources = 0

            commonResources.forEach { url ->
                try {
                    preloadResource(url)
                    loadedResources++
                    _preloadProgress.value = loadedResources.toFloat() / totalResources
                } catch (e: Exception) {
                }
            }
        }
    }
}