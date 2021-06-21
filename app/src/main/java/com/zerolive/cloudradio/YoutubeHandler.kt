package com.zerolive.cloudradio

import android.os.SystemClock
import android.support.v4.media.session.PlaybackStateCompat
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener

var youtubeHandlerTag = "CR_YoutubeHandler"

object YoutubeHandler: AbstractYouTubePlayerListener() {
    override fun onApiChange(youTubePlayer: YouTubePlayer) {
        super.onApiChange(youTubePlayer)
        CRLog.d("onApiChange")
    }

    override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
        super.onCurrentSecond(youTubePlayer, second)
        //CRLog.d( "onCurrentSecond: "+second)
        OnAir.setCurrentSecond(second)
        val state = PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_PLAYING,
                OnAir.getCurrentSecond(),
                1.0f,
                SystemClock.elapsedRealtime()

            )
            .setActions(MainActivity.getInstance().getFullActions())
            .build()
        MainActivity.mMediaSession?.setPlaybackState(state)
    }

    override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
        super.onError(youTubePlayer, error)
        CRLog.d("onError: " + error)
    }

    override fun onPlaybackQualityChange(
        youTubePlayer: YouTubePlayer,
        playbackQuality: PlayerConstants.PlaybackQuality
    ) {
        super.onPlaybackQualityChange(youTubePlayer, playbackQuality)
        CRLog.d("onPlaybackQualityChange")
    }

    override fun onPlaybackRateChange(
        youTubePlayer: YouTubePlayer,
        playbackRate: PlayerConstants.PlaybackRate
    ) {
        super.onPlaybackRateChange(youTubePlayer, playbackRate)
        CRLog.d("onPlaybackRateChange")
    }

    override fun onReady(youTubePlayer: YouTubePlayer) {
        super.onReady(youTubePlayer)
        CRLog.d("youtube onReady!")
        OnAir.youtubePlayer = youTubePlayer
    }

    override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
        super.onStateChange(youTubePlayer, state)
        CRLog.d("onStateChange: " + state)
        OnAir.setYoutubeState(state)
        CRLog.d(""+Thread.dumpStack())
    }

    override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
        super.onVideoDuration(youTubePlayer, duration)
        CRLog.d("onVideoDuration: " + duration)
        OnAir.mDuration = (duration*1000).toLong()

        // metadata
        // duration 이 가장 늦을 수도 있어, 여기에서도 설정해준다
        OnAir.setMetadata()
    }

    override fun onVideoId(youTubePlayer: YouTubePlayer, videoId: String) {
        super.onVideoId(youTubePlayer, videoId)
        CRLog.d("onVideoId: " + videoId)
    }

    override fun onVideoLoadedFraction(youTubePlayer: YouTubePlayer, loadedFraction: Float) {
        super.onVideoLoadedFraction(youTubePlayer, loadedFraction)
        //CRLog.d( "onVideoLoadedFraction: "+loadedFraction)
    }
}