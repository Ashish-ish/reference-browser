package org.mozilla.reference.browser

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import mozilla.components.concept.push.PushProcessor
import mozilla.components.support.rusthttp.RustHttpConfig
import mozilla.components.support.rustlog.RustLog
import org.mozilla.reference.browser.push.PushFxaIntegration
import org.mozilla.reference.browser.push.WebPushEngineIntegration

class InitializationProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val context = context ?: return false
        val components = (context.applicationContext as BrowserApplication).components

        setupRustConfig(components)
        setupPushIntegration(components)

        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            components.core.fileUploadsDirCleaner.cleanUploadsDirectory()
        }
        return true
    }

    private fun setupRustConfig(components: Components) {
        RustHttpConfig.setClient(lazy { components.core.client })
        RustLog.enable()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun setupPushIntegration(components: Components) {
        components.push.feature?.let {
            PushProcessor.install(it)
            WebPushEngineIntegration(components.core.engine, it).start()
            GlobalScope.launch(Dispatchers.IO) {
                PushFxaIntegration(it, lazy { components.backgroundServices.accountManager }).launch()
                it.initialize()
            }
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
