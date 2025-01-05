/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.reference.browser

import android.app.Application
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mozilla.components.browser.state.action.SystemAction
import mozilla.components.concept.engine.webextension.isUnsupported
import mozilla.components.feature.addons.update.GlobalAddonDependencyProvider
import mozilla.components.support.base.log.Log
import mozilla.components.support.base.log.sink.AndroidLogSink
import mozilla.components.support.ktx.android.content.isMainProcess
import mozilla.components.support.ktx.android.content.runOnlyInMainProcess
import mozilla.components.support.rustlog.RustLog
import mozilla.components.support.webextensions.WebExtensionSupport
import org.mozilla.reference.browser.ext.isCrashReportActive
import java.util.concurrent.TimeUnit

open class BrowserApplication : Application() {
    val components by lazy { Components(this) }

    override fun onCreate() {
        super.onCreate()
        setupCrashReporting(this)
        setupLogging()

        if (!isMainProcess()) {
            return
        }

        components.core.engine.warmUp()
        restoreBrowserState()

        GlobalAddonDependencyProvider.initialize(
            components.core.addonManager,
            components.core.addonUpdater,
        )
        WebExtensionSupport.initialize(
            runtime = components.core.engine,
            store = components.core.store,
            onNewTabOverride = { _, engineSession, url ->
                val tabId = components.useCases.tabsUseCases.addTab(
                    url = url,
                    selectTab = true,
                    engineSession = engineSession,
                )
                tabId
            },
            onCloseTabOverride = { _, sessionId ->
                components.useCases.tabsUseCases.removeTab(sessionId)
            },
            onSelectTabOverride = { _, sessionId ->
                components.useCases.tabsUseCases.selectTab(sessionId)
            },
            onExtensionsLoaded = { extensions ->
                components.core.addonUpdater.registerForFutureUpdates(extensions)

                val checker = components.core.supportedAddonsChecker
                if (extensions.any { it.isUnsupported() }) {
                    checker.registerForChecks()
                } else {
                    checker.unregisterForChecks()
                }
            },
            onUpdatePermissionRequest = components.core.addonUpdater::onUpdatePermissionRequest,
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        runOnlyInMainProcess {
            components.core.store.dispatch(SystemAction.LowMemoryAction(level))
            components.core.icons.onTrimMemory(level)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun restoreBrowserState() = GlobalScope.launch(Dispatchers.Main) {
        val store = components.core.store
        val sessionStorage = components.core.sessionStorage

        components.useCases.tabsUseCases.restore(sessionStorage)

        // Now that we have restored our previous state (if there's one) let's setup auto saving the state while
        // the app is used.
        sessionStorage.autoSave(store)
            .periodicallyInForeground(interval = 30, unit = TimeUnit.SECONDS)
            .whenGoingToBackground()
            .whenSessionsChange()
    }

    companion object {
        const val NON_FATAL_CRASH_BROADCAST = "org.mozilla.reference.browser"
    }
}

private fun setupLogging() {
    // We want the log messages of all builds to go to Android logcat
    Log.addSink(AndroidLogSink())
    RustLog.enable()
}

@OptIn(DelicateCoroutinesApi::class)
private fun setupCrashReporting(application: BrowserApplication) {
    GlobalScope.launch(Dispatchers.IO) {
        if (isCrashReportActive) {
            application
                .components
                .analytics
                .crashReporter.install(application)
        }
    }
}

