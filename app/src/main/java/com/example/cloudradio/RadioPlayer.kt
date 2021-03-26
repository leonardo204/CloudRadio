package com.example.cloudradio

import android.media.MediaPlayer
import android.os.AsyncTask
import android.util.Log
import java.io.IOException

/*
  radio player 는 streaming 이라서 resume / pause 가 아니라 start / stop 만 가능
 */
object RadioPlayer {

    var initialized: Boolean = false

    lateinit var mMediaPlayer: MediaPlayer

    fun init() {
        Log.d(onairTag, "RadioPlayer init")
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
            Log.d(onairTag, "IOException: "+e)
            ret = false
        } catch (e:  IllegalStateException) {
            Log.d(onairTag, "IllegalStateException: "+e)
            ret = false
        } catch (e: Exception) {
            Log.d(onairTag, "exception: "+e)
            ret = false
        } finally {
            return ret
        }
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