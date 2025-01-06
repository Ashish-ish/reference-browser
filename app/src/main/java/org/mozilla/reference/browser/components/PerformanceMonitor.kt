package org.mozilla.reference.browser.components

import android.util.Log

class PerformanceMonitor {
    private var pageLoadStart: Long = 0

    fun startPageLoad() {
        pageLoadStart = System.nanoTime()
    }

    fun endPageLoad() {
        val loadTime = (System.nanoTime() - pageLoadStart) / 1_000_000
        Log.d("Performance", "Page load took $loadTime ms")
    }
}