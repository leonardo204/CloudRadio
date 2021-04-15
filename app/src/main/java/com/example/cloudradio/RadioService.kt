package com.example.cloudradio

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants

var radioServiceTag = "CR_RadioService"

enum class RESULT {
    PLAY_FAILED, PLAY_SUCCESS, DESTROYED
}

enum class SERVICE_TYPE {
    YOUTUBE, RADIO
}

class RadioService : Service() {

    companion object {
        var mFilename: String? = null

        private var instance: RadioService? = null

        fun getInstance(): RadioService =
            instance ?: synchronized(this) {
                instance ?: RadioService().also {
                    instance = it
                }
            }
    }

    var mVideoId: String? = null
    var mPlayType: SERVICE_TYPE? = null
    var mAddress: String? = null

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun startForegroundService(filename: String) {
        val notification = RadioNotification.createNotification( filename,  true)
        startForeground(RadioNotification.NOTIFICATION_ID, notification)
    }

    private fun stopForegroundService() {
        mFilename?.let {
            stopForeground(true)
            stopSelf()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        Log.d(radioServiceTag, "RadioService onStartCommand action: ${intent?.action}")

        RadioNotification.init(this)

        when(intent?.action) {
            Constants.ACTION.STARTFOREGROUND_ACTION -> {
                Log.d(radioServiceTag, "start foreground services")
                startForegroundService(intent?.getStringExtra("name"))
                var serviceName = intent?.getStringExtra("serviceName")
                when(serviceName) {
                    "youtube" -> mPlayType = SERVICE_TYPE.YOUTUBE
                    "radio" -> mPlayType = SERVICE_TYPE.RADIO
                }
                return controllService(intent)
            }
            Constants.ACTION.STOPFOREGROUND_ACTION -> {
                Log.d(radioServiceTag, "stop foreground services")
                stopForegroundService()
                OnAir.setYoutubeStateManual(PlayerConstants.PlayerState.UNSTARTED)
            }
            Constants.ACTION.PLAY_ACTION -> {
                Log.d(radioServiceTag, "play action file(${mFilename})")
                mFilename?.let {
                    playStopMedia(true)
                    RadioNotification.updateNotification(it, true)
                }
            }
            Constants.ACTION.PAUSE_ACTION -> {
                Log.d(radioServiceTag, "pause action file(${mFilename})")
                mFilename?.let {
                    playStopMedia(false)
                    RadioNotification.updateNotification(it, false)
                }
            }
            Constants.ACTION.NOTI_UPDATE_PLAY -> {
                mFilename?.let {
                    RadioNotification.updateNotification(it, true)
                }
            }
            Constants.ACTION.NOTI_UPDATE_PAUSE -> {
                mFilename?.let {
                    RadioNotification.updateNotification(it, false)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun playStopMedia(action: Boolean) {

        Log.d(radioServiceTag, "playStopMedia type: ${mPlayType}  action: ${action}")

        if ( mPlayType == null ) return

        when(mPlayType) {
            SERVICE_TYPE.YOUTUBE -> {
                if ( action ) {
                    if ( OnAir.youtubeState == PlayerConstants.PlayerState.PLAYING ) {
                        Log.d(radioServiceTag, "Alreay playing")
                        return
                    }

                    if ( OnAir.youtubeState == PlayerConstants.PlayerState.PAUSED ) {
                        Log.d(radioServiceTag, "playing success")
                        OnAir.youtubePlayer?.play()
                        mFilename?.let { it1 -> OnAir.updateButtonText(it1, RADIO_BUTTON.PLAYING_MESSAGE.getMessage(), true) }
                    } else {
                        mVideoId?.let { OnAir.youtubePlayer?.loadVideo(it, 0.0f) }
                    }
                    OnAir.setYoutubeStateManual(PlayerConstants.PlayerState.PLAYING)

                } else {
                    Log.d(radioServiceTag, "pause success")
                    OnAir.youtubePlayer?.pause()
                    mFilename?.let { it1 -> OnAir.updateButtonText(it1, RADIO_BUTTON.PAUSED_MESSAGE.getMessage(), true) }
                    OnAir.setYoutubeStateManual(PlayerConstants.PlayerState.PAUSED)
                }
            }
            SERVICE_TYPE.RADIO -> {
                mAddress?.let {
                    if (action) {
                        // play 요청
                        if (RadioPlayer.isPlaying()) {
                            Log.d(radioServiceTag, "Alreay playing")
                            return
                        } else {
                            try {
                                if ( !RadioPlayer.play(it) ) {
                                    // play 했지만 실패
                                    RadioPlayer.stop()
                                    mFilename?.let { it1 -> OnAir.updateButtonText(it1, RADIO_BUTTON.STOPPED_MESSAGE.getMessage(), true) }
                                    mFilename?.let { it1 -> sendCallback(it1, RESULT.PLAY_FAILED) }
                                    return
                                } else {
                                    Log.d(radioServiceTag, "playing success")
                                }
                            } catch (e: Exception) {
                                Log.d(radioServiceTag, "error: "+e.message)
                            }
                            // play 성공
                            mFilename?.let { it1 -> sendCallback(it1, RESULT.PLAY_SUCCESS) }
                            mFilename?.let { it1 -> OnAir.updateButtonText(it1, RADIO_BUTTON.PLAYING_MESSAGE.getMessage(), true) }
                        }
                    } else {
                        // stop 요청
                        if (RadioPlayer.isPlaying()) {
                            Log.d(radioServiceTag, "stop success")
                            RadioPlayer.stop()
                        }
                        mFilename?.let { it1 -> OnAir.updateButtonText(it1, RADIO_BUTTON.STOPPED_MESSAGE.getMessage(), true) }
                    }
                }
            }
        }
    }

    private fun controllService(intent: Intent?): Int {
        mFilename = intent?.getStringExtra("name")

        // youtube 요청인 경우 처리
        when(mPlayType) {
            SERVICE_TYPE.YOUTUBE -> {
                mVideoId = intent?.getStringExtra("videoId")
                Log.d(radioServiceTag, "onStartCommand: $mVideoId")

                MainActivity.youtubeView?.enableBackgroundPlayback(true)
                mVideoId?.let { OnAir.youtubePlayer?.loadVideo(it, 0.0f) }
                mFilename?.let { it1 -> sendCallback(it1, RESULT.PLAY_SUCCESS) }
                OnAir.setYoutubeStateManual(PlayerConstants.PlayerState.PLAYING)

                return START_NOT_STICKY
            }
            SERVICE_TYPE.RADIO -> {
                mAddress = intent?.getStringExtra("address")

                // address 가 null 이어서 실패
                // 실패되는 정보에 대한 channel map 을 임의로 생성하여 callback 에 담아 보낸다
                if ( mAddress == null ) {
                    mFilename?.let { it1 -> sendCallback(it1, RESULT.PLAY_FAILED) }
                    return START_STICKY
                }

                Log.d(radioServiceTag, "start service filename: "+mFilename)
                Log.d(radioServiceTag, "start service httpAddress: "+mAddress)

                // address 가 valid 하지만 재생 실패되는 경우
                // 이 경우도 실패에 대한 callback 을 전달한다.
                try {
                    mAddress?.let {
                        if ( !RadioPlayer.play(it) ) {
                            RadioPlayer.stop()
                            mFilename?.let { it1 -> sendCallback(it1, RESULT.PLAY_FAILED) }
                            return START_NOT_STICKY
                        }
                    }
                } catch ( e: Exception ) {
                    Log.d(radioServiceTag, "error: "+e.message)
                }

                // success callback
                mFilename?.let { it1 -> sendCallback(it1, RESULT.PLAY_SUCCESS) }
            }
            else -> mFilename?.let { sendCallback(it, RESULT.PLAY_FAILED) }
        }
        return START_NOT_STICKY
    }

    private fun sendCallback(filename: String, result: RESULT) {
        OnAir.notifyRadioServiceStatus(filename, result)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(radioServiceTag, "RadioService onCreate")
    }

    override fun onDestroy() {
        super.onDestroy()
        mFilename?.let {
            Log.d(radioServiceTag, "RadioService onDestroy")
//            RadioPlayer.stop()

            // listener 가 너무 늦게 불러져서-_- 강제로 pause 이벤트 전달
            OnAir.setYoutubeStateManual(PlayerConstants.PlayerState.UNSTARTED)
//            OnAir.youtubePlayer?.pause()

            sendCallback(it, RESULT.DESTROYED)
        }

        mFilename = null
        mVideoId = null
        mPlayType = null
        mAddress = null

        RadioNotification.destroy()
    }
}