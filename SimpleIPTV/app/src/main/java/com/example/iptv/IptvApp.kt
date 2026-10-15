package com.example.iptv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class IptvApp : Application(), ImageLoaderFactory {
    // Giới hạn cache logo để giữ RAM thấp trên TV Box yếu
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.08).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("logos")).maxSizeBytes(50L * 1024 * 1024).build() }
        .allowRgb565(true)
        .crossfade(false)
        .build()
}
