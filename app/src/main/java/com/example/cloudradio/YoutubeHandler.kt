package com.example.cloudradio

import android.util.Log
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener

var youtubeHandlerTag = "CR_YoutubeHandler"

object YoutubeHandler: AbstractYouTubePlayerListener() {
    override fun onApiChange(youTubePlayer: YouTubePlayer) {
        super.onApiChange(youTubePlayer)
        Log.d(youtubeHandlerTag, "onApiChange")
    }

    override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
        super.onCurrentSecond(youTubePlayer, second)
        //Log.d(youtubeHandlerTag, "onCurrentSecond: "+second)
    }

    override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
        super.onError(youTubePlayer, error)
        Log.d(youtubeHandlerTag, "onError: "+error)
    }

    override fun onPlaybackQualityChange(
        youTubePlayer: YouTubePlayer,
        playbackQuality: PlayerConstants.PlaybackQuality
    ) {
        super.onPlaybackQualityChange(youTubePlayer, playbackQuality)
        Log.d(youtubeHandlerTag, "onPlaybackQualityChange")
    }

    override fun onPlaybackRateChange(
        youTubePlayer: YouTubePlayer,
        playbackRate: PlayerConstants.PlaybackRate
    ) {
        super.onPlaybackRateChange(youTubePlayer, playbackRate)
        Log.d(youtubeHandlerTag, "onPlaybackRateChange")
    }

    override fun onReady(youTubePlayer: YouTubePlayer) {
        super.onReady(youTubePlayer)
        Log.d(youtubeHandlerTag, "youtube onReady!")
        OnAir.youtubePlayer = youTubePlayer
    }

    override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
        super.onStateChange(youTubePlayer, state)
        Log.d(youtubeHandlerTag, "onStateChange: "+state)
        OnAir.setYoutubeState( state )
    }

    override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
        super.onVideoDuration(youTubePlayer, duration)
        Log.d(youtubeHandlerTag, "onVideoDuration: "+duration)
    }

    override fun onVideoId(youTubePlayer: YouTubePlayer, videoId: String) {
        super.onVideoId(youTubePlayer, videoId)
        Log.d(youtubeHandlerTag, "onVideoId: "+videoId)
    }

    override fun onVideoLoadedFraction(youTubePlayer: YouTubePlayer, loadedFraction: Float) {
        super.onVideoLoadedFraction(youTubePlayer, loadedFraction)
        //Log.d(youtubeHandlerTag, "onVideoLoadedFraction: "+loadedFraction)
    }
}