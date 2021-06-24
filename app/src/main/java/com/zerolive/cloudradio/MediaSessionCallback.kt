package com.zerolive.cloudradio

import android.content.Intent
import android.os.Bundle
import android.os.ResultReceiver
import android.os.SystemClock
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent

object MediaSessoinCallback : MediaSessionCompat.Callback() {
    override fun onCommand(command: String?, extras: Bundle?, cb: ResultReceiver?) {
        super.onCommand(command, extras, cb)
        CRLog.d("onCommand: ${command}")
    }

    override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
        val keyEvent = mediaButtonEvent?.extras?.get(Intent.EXTRA_KEY_EVENT) as KeyEvent
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
            OnAir.requestStopPauseRadioService()
            setStatePaused()
        } else {
            OnAir.requestStartRadioService()
            setStatePlaying()
        }
    }
    private fun requestPause() {
        if ( OnAir.isPlayingRadioService() ) {
            OnAir.requestStopPauseRadioService()
            setStatePaused()
        }
    }
    private fun requestPlay() {
        if ( !OnAir.isPlayingRadioService() ) {
            OnAir.requestStartRadioService()
            setStatePlaying()
        }
    }

    private fun setStatePaused() {
//        OnAir.setMetadata()

        val state = PlaybackStateCompat.Builder()
            .setState(
                PlaybackStateCompat.STATE_PAUSED,
                OnAir.getCurrentSecond(),
                1.0f,
                SystemClock.elapsedRealtime()
            )
            .setActions(MainActivity.getInstance().getFullActions())
            .build()

        MainActivity.mMediaSession?.setPlaybackState(state)
    }

    private fun setStatePlaying() {
//        OnAir.setMetadata()

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

    override fun onPlay() {
        super.onPlay()
        CRLog.d("onPlay()")
    }

    override fun onPause() {
        super.onPause()
        CRLog.d("onPause()")
    }

    override fun onStop() {
        super.onStop()
        CRLog.d("onStop()")
    }
}