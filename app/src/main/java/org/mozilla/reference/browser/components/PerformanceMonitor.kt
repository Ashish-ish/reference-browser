package org.mozilla.reference.browser.components

import android.util.Log

// Add this to test the before and after of page, cold start, loading time
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