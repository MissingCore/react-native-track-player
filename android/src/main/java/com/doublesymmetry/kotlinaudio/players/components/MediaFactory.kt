package com.doublesymmetry.kotlinaudio.players.components

import android.content.Context
import android.net.Uri
import android.os.Build
import com.doublesymmetry.kotlinaudio.utils.isUriLocalFile
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.drm.DrmSessionManagerProvider
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DataSpec
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.LoadErrorHandlingPolicy
import com.google.android.exoplayer2.upstream.RawResourceDataSource
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.SimpleCache

class MediaFactory (
    private val context: Context,
    private val cache: SimpleCache?
) : MediaSource.Factory {

    companion object {
        private const val DEFAULT_USER_AGENT = "react-native-track-player"
    }

    private val mediaFactory = DefaultMediaSourceFactory(context)

    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
        return mediaFactory.setDrmSessionManagerProvider(drmSessionManagerProvider)
    }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
        return mediaFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
    }

    override fun getSupportedTypes(): IntArray {
        return mediaFactory.supportedTypes
    }

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {

        val userAgent = mediaItem.mediaMetadata.extras?.getString("user-agent") ?: DEFAULT_USER_AGENT
        val headers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mediaItem.mediaMetadata.extras?.getSerializable("headers", HashMap::class.java)
        } else {
            mediaItem.mediaMetadata.extras?.getSerializable("headers")
        }
        val resourceId = mediaItem.mediaMetadata.extras?.getInt("resource-id")
        // HACK: why are these capitalized?
        val resourceType = mediaItem.mediaMetadata.extras?.getString("type")?.lowercase()
        val uri = Uri.parse(mediaItem.mediaMetadata.extras?.getString("uri")!!)
        val factory: DataSource.Factory = when {
            resourceId != 0 && resourceId != null -> {
                val raw = RawResourceDataSource(context)
                raw.open(DataSpec(uri))
                DataSource.Factory { raw }
            }
            isUriLocalFile(uri) -> {
                DefaultDataSource.Factory(context)
            }
            else -> {
                val tempFactory = DefaultHttpDataSource.Factory().apply {
                    setUserAgent(userAgent)
                    setAllowCrossProtocolRedirects(true)

                    headers?.let {
                        setDefaultRequestProperties(it as HashMap<String, String>)
                    }
                }

                enableCaching(tempFactory)
            }
        }

        return when (resourceType) {
            "dash" -> createDashSource(mediaItem, factory)
            "hls" -> createHlsSource(mediaItem, factory)
            "smoothstreaming" -> createSsSource(mediaItem, factory)
            else -> createProgressiveSource(mediaItem, factory)
        }
    }

    private fun createDashSource(mediaItem: MediaItem, factory: DataSource.Factory?): MediaSource {
        return DashMediaSource.Factory(DefaultDashChunkSource.Factory(factory!!), factory)
            .createMediaSource(mediaItem)
    }

    private fun createHlsSource(mediaItem: MediaItem, factory: DataSource.Factory?): MediaSource {
        return HlsMediaSource.Factory(factory!!)
            .createMediaSource(mediaItem)
    }

    private fun createSsSource(mediaItem: MediaItem, factory: DataSource.Factory?): MediaSource {
        return SsMediaSource.Factory(DefaultSsChunkSource.Factory(factory!!), factory)
            .createMediaSource(mediaItem)
    }

    private fun createProgressiveSource(
        mediaItem: MediaItem,
        factory: DataSource.Factory
    ): ProgressiveMediaSource {
        return ProgressiveMediaSource.Factory(
            factory, DefaultExtractorsFactory()
                .setConstantBitrateSeekingEnabled(true)
        )
            .createMediaSource(mediaItem)
    }

    private fun enableCaching(factory: DataSource.Factory): DataSource.Factory {
        if (cache == null) {
            return factory
        } else {
            return CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(factory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }
    }
}