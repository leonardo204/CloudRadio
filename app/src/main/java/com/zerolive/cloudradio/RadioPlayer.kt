package com.zerolive.cloudradio

import android.media.MediaPlayer
import java.io.IOException

var radioPlayerTag = "CR_RadioPlayer"

/*
  radio player 는 streaming 이라서 resume / pause 가 아니라 start / stop 만 가능
 */
object RadioPlayer {

    var initialized: Boolean = false

    var mMediaPlayer: MediaPlayer? = null

    fun init() {
        CRLog.d("RadioPlayer init")
        if ( !initialized ) {
            mMediaPlayer = MediaPlayer()
        }
        initialized = true
    }

    fun play(channelString: String): Boolean {
        var ret = false
        try {
            mMediaPlayer?.let {
                it.setDataSource(channelString)
                it.prepare()
                it.start()
                ret = true
            }
        } catch (e: IOException) {
            CRLog.d("IOException: " + e)
            ret = false
        } catch (e:  IllegalStateException) {
            CRLog.d("IllegalStateException: " + e)
            ret = false
        } catch (e: Exception) {
            CRLog.d("exception: " + e)
            ret = false
        } finally {
            return ret
        }
    }

    fun stop() {
        if ( isPlaying() ) {
            CRLog.d("RadioPlayer stopped")
            mMediaPlayer?.let {
                it.stop()
                it.release()
                mMediaPlayer = MediaPlayer()
            }
        }
    }

    fun isPlaying(): Boolean {
        mMediaPlayer?.let {
            return it.isPlaying
        }
        return false
    }

//    fun resume() {
//        if ( !mMediaPlayer.isPlaying ) {
//            CRLog.d( "RadioPlayer resume")
//            mMediaPlayer.seekTo(mPosition)
//            mMediaPlayer.start()
//        }
//    }
//
//    fun pause() {
//        if ( mMediaPlayer.isPlaying ) {
//            CRLog.d( "RadioPlayer paused")
//            mMediaPlayer.pause()
//            mPosition = mMediaPlayer.currentPosition
//        }
//    }
}