package hu.regelo.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import hu.regelo.app.push.PushTokens
import kotlinx.coroutines.launch

class RegeloApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        hu.regelo.app.util.ErrorReporter.installCrashHandler()
        hu.regelo.app.util.ErrorReporter.uploadPendingCrash()
        PushTokens.createChannel(this)
        AppGraph.appScope.launch { runCatching { PDFBoxResourceLoader.init(this@RegeloApplication) } }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                AppGraph.onAppForeground()
                if (AppGraph.auth.userId != null) {
                    AppGraph.messages.resume()
                    AppGraph.appScope.launch {
                        hu.regelo.app.util.ServerClock.sync()
                        runCatching { AppGraph.official.refresh() }
                    }
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                AppGraph.messages.pause()
            }
        })
        AppGraph.appScope.launch {
            runCatching {
                AppGraph.db.books().pruneCache(System.currentTimeMillis() - 30L * 24 * 3600 * 1000)
                AppGraph.db.chapters().pruneOrphans()
            }
        }
    }

    /** Borítók gyorsítótárazva: offline is látszanak. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(150L * 1024 * 1024).build() }
        .crossfade(true)
        .respectCacheHeaders(false)
        .build()
}
