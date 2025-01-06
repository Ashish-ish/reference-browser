package org.mozilla.reference.browser.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.InetAddress

class ResourcePrefetcher {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var context: Context

    fun initialize(context: Context) {
        this.context = context
        prefetchDNS()
    }

    private fun prefetchDNS() {
        scope.launch {
            // Prefetch DNS for Wikipedia domains
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cm.getNetworkCapabilities(cm.activeNetwork)?.let { capabilities ->
                    if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                        prefetchHost("wikipedia.org")
                        prefetchHost("wikimedia.org")
                        prefetchHost("mediawiki.org")
                    }
                }
            }
        }
    }

    private fun prefetchHost(host: String) {
        try {
            InetAddress.getAllByName(host)
        } catch (e: Exception) {
            // Handle DNS lookup failure
        }
    }
}