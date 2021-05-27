package com.zerolive.cloudradio

import android.content.Intent
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.session.MediaSessionCompat
import android.view.KeyEvent

object MediaSessoinCallback : MediaSessionCompat.Callback() {
    override fun onCommand(command: String?, extras: Bundle?, cb: ResultReceiver?) {
        super.onCommand(command, extras, cb)
        CRLog.d("onCommand: ${command}")
    }

    override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
        var keyEvent = mediaButtonEvent?.extras?.get(Intent.EXTRA_KEY_EVENT) as KeyEvent
        CRLog.d("onMediaButtonEvent() action:${mediaButtonEvent.action} keyEvent: ${keyEvent}")
        if ( keyEvent.action == KeyEvent.ACTION_DOWN && mediaButtonEvent.action == Intent.ACTION_MEDIA_BUTTON ) {
            CRLog.d("media key: ${keyEvent.keyCode}")
            when(keyEvent.keyCode) {
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_FORWARD,
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
                KeyEvent.KEYCODE_MEDIA_STEP_FORWARD-> { requestNext() }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                KeyEvent.KEYCODE_MEDIA_REWIND-> { requestPrevious() }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> { requestPause() }
                KeyEvent.KEYCODE_MEDIA_PLAY -> { requestPlay() }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { requestPlayPause() }
            }
        }

        return super.onMediaButtonEvent(mediaButtonEvent)
    }
    private fun requestNext() {
        CRLog.d("requestNext()")
        OnAir.requestPlayNext()
    }
    private fun requestPrevious() {
        CRLog.d("requestPrevious()")
        OnAir.requestPlayPrevious()
    }
    private fun requestPlayPause() {
        CRLog.d("requestPlayPause()")
        if ( OnAir.isPlayingRadioService() ) {
            OnAir.requestStopRadioService()
        } else {
            OnAir.requestStartRadioService()
        }
    }
    private fun requestPause() {
        if ( OnAir.isPlayingRadioService() ) {
            OnAir.requestStopRadioService()
        }
    }
    private fun requestPlay() {
        if ( !OnAir.isPlayingRadioService() ) {
            OnAir.requestStartRadioService()
        }
    }

//    override fun onPlay() {
//        super.onPlay()
//        CRLog.d("onPlay()")
//        MainActivity.mMediaSession?.isActive = true
//    }
//
//    override fun onPause() {
//        super.onPause()
//        CRLog.d("onPause()")
//        MainActivity.mMediaSession?.isActive = false
//    }
//
//    override fun onStop() {
//        super.onStop()
//        CRLog.d("onStop()")
//        MainActivity.mMediaSession?.isActive = false
//    }


}