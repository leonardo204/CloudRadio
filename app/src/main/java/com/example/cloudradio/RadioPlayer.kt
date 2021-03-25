package com.example.cloudradio

import android.media.MediaPlayer
import android.util.Log

/*
  radio player 는 streaming 이라서 resume / pause 가 아니라 start / stop 만 가능
 */
object RadioPlayer {

    var initialized: Boolean = false

    lateinit var mMediaPlayer: MediaPlayer

//    var mPosition: Int = 0

    var KBS_CLASSIC_FM = "https://1fm.gscdn.kbs.co.kr/1fm_192_1.m3u8?Expires=1616669502&Policy=eyJTdGF0ZW1lbnQiOlt7IlJlc291cmNlIjoiaHR0cHM6Ly8xZm0uZ3NjZG4ua2JzLmNvLmtyLzFmbV8xOTJfMS5tM3U4IiwiQ29uZGl0aW9uIjp7IkRhdGVMZXNzVGhhbiI6eyJBV1M6RXBvY2hUaW1lIjoxNjE2NjY5NTAyfX19XX0_&Signature=YErRYtA6MoVFSv8fJNvO7hIFeToA6jJP9nRSR2haXmE0N9hRePfdbRaORW1d6ntAT8PwlR70z2OPNffbXJq1HJsTnOnCHWSN7SMEloh0YftRbww5heRg3DpPIbeHGW-t9jW4-8vyPCjh4UB5ejajP7000sVFcKdTL2-DckYEToqnMPXGSBQ5A3IVZYpazkgBQeZny1IbXjU9SPp3C7XkC6MY-mVvT2IK7VQW7j9RXqqgpmq1RZDZYcOdJjxy2vKlVKebgC58qI~fqSApU298rZmdcBzjK1UCLmk2Nzy5ohCVCX6nRfFRlsEV7nUrvM8ykbDFehQBGF9TIGTxrbdnYQ__&Key-Pair-Id=APKAICDSGT3Y7IXGJ3TA"

    fun init() {
        if ( !initialized ) {
            mMediaPlayer = MediaPlayer()
        }
        initialized = true
    }

    fun play(channelString: String) {
        mMediaPlayer.setDataSource(channelString)
        mMediaPlayer.prepare()
        mMediaPlayer.start()
    }

    fun stop() {
        if ( mMediaPlayer.isPlaying ) {
            Log.d(onairTag, "RadioPlayer stopped")
            mMediaPlayer.stop()
            mMediaPlayer.release()
            mMediaPlayer = MediaPlayer()
        }
    }

    fun isPlaying(): Boolean {
        return mMediaPlayer.isPlaying
    }

//    fun resume() {
//        if ( !mMediaPlayer.isPlaying ) {
//            Log.d(onairTag, "RadioPlayer resume")
//            mMediaPlayer.seekTo(mPosition)
//            mMediaPlayer.start()
//        }
//    }
//
//    fun pause() {
//        if ( mMediaPlayer.isPlaying ) {
//            Log.d(onairTag, "RadioPlayer paused")
//            mMediaPlayer.pause()
//            mPosition = mMediaPlayer.currentPosition
//        }
//    }
}