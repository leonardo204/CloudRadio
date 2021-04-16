package com.example.cloudradio

import android.media.MediaPlayer
import android.os.AsyncTask
import android.util.Log
import java.io.IOException

var radioPlayerTag = "CR_RadioPlayer"

/*
  radio player 는 streaming 이라서 resume / pause 가 아니라 start / stop 만 가능
 */
object RadioPlayer {

    var initialized: Boolean = false

    lateinit var mMediaPlayer: MediaPlayer

    fun init() {
        CRLog.d( "RadioPlayer init")
        if ( !initialized ) {
            mMediaPlayer = MediaPlayer()
        }
        initialized = true
    }

    fun play(channelString: String): Boolean {
        var ret = false
        try {
            mMediaPlayer.setDataSource(channelString)
            mMediaPlayer.prepare()
            mMediaPlayer.start()
            ret = true
        } catch (e: IOException) {
            CRLog.d( "IOException: "+e)
            ret = false
        } catch (e:  IllegalStateException) {
            CRLog.d( "IllegalStateException: "+e)
            ret = false
        } catch (e: Exception) {
            CRLog.d( "exception: "+e)
            ret = false
        } finally {
            return ret
        }
    }

    fun stop() {
        if ( mMediaPlayer.isPlaying ) {
            CRLog.d( "RadioPlayer stopped")
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