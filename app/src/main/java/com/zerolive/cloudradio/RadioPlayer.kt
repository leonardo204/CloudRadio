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
                it.prepareAsync()
//                it.start()
                it.setOnPreparedListener(object: MediaPlayer.OnPreparedListener {
                    override fun onPrepared(mp: MediaPlayer?) {
                        CRLog.d("onPrepared. ${mp}")
                        mp?.start()
                    }
                })
                it.setOnErrorListener(object: MediaPlayer.OnErrorListener {
                    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
                        CRLog.d("onError ${what} ${extra}")
                        OnAir.mCurrentPlayFilename?.let { OnAir.notifyRadioServiceStatus(it, RESULT.PLAY_FAILED) }
                        return false
                    }
                })
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
}