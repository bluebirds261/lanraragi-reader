package com.lanraragi.reader

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import com.lanraragi.reader.data.ArchiveAwareDiskCache
import com.lanraragi.reader.data.api.ApiClient
import com.lanraragi.reader.di.AppContainer
import com.lanraragi.reader.ui.ArchiveImageFetcher
import kotlinx.coroutines.launch
import java.io.File

class LanraragiApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // 启动时把持久化设置同步到运行时配置
        container.applicationScope.launch {
            container.settingsRepository.applyToRuntime()
        }

        // Coil 共用带鉴权的 OkHttp 客户端，图片请求自动带 Authorization 头
        val imageLoader = ImageLoader.Builder(this)
            .okHttpClient(ApiClient.okHttpClient)
            .diskCache {
                ArchiveAwareDiskCache(
                    delegate = DiskCache.Builder()
                        .directory(File(cacheDir, "image_cache"))
                        .build(),
                    context = this,
                )
            }
            .components {
                add(ArchiveImageFetcher.Factory())
            }
            .crossfade(true)
            .build()
        Coil.setImageLoader(imageLoader)
    }
}
