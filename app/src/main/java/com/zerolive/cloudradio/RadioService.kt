package com.zerolive.cloudradio

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
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
    }

    var mVideoId: String? = null
    var mPlayType: SERVICE_TYPE? = null
    var mAddress: String? = null

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun startForegroundService(filename: String) {
        val notification = RadioNotification.createNotification(filename, true)
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

        mFilename = intent?.getStringExtra("name")
        CRLog.d("RadioService: ${intent?.action} for $mFilename / Onair(${OnAir.mCurrnetPlayFilename})")

        RadioNotification.init(this)

        when(intent?.action) {
            Constants.ACTION.STARTFOREGROUND_ACTION -> {
                CRLog.d("start foreground services")
                startForegroundService(intent?.getStringExtra("name"))
                var serviceName = intent?.getStringExtra("serviceName")
                when (serviceName) {
                    "youtube" -> mPlayType = SERVICE_TYPE.YOUTUBE
                    "radio" -> mPlayType = SERVICE_TYPE.RADIO
                }
                return controllService(intent)
            }
            Constants.ACTION.STOPFOREGROUND_ACTION -> {
                CRLog.d("stop foreground services")
                stopForegroundService()
                OnAir.setYoutubeStateManual(PlayerConstants.PlayerState.UNSTARTED)
            }
            Constants.ACTION.PLAY_ACTION -> {
                CRLog.d("play action file(${OnAir.mCurrnetPlayFilename})")
                OnAir.mCurrnetPlayFilename?.let {
                    playStopMedia(it, true)
                    RadioNotification.updateNotification(it, true)
                }
            }
            Constants.ACTION.PAUSE_ACTION -> {
                CRLog.d("pause action file(${OnAir.mCurrnetPlayFilename})")
                OnAir.mCurrnetPlayFilename?.let {
                    playStopMedia(it, false)
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

    private fun playStopMedia(filename: String, action: Boolean) {

        CRLog.d("playStopMedia type: ${mPlayType}  file: ${filename}  action: ${action}")

        if ( mPlayType == null ) return

        when(mPlayType) {
            SERVICE_TYPE.YOUTUBE -> {
                if (action) {
                    if (OnAir.mYoutubeState == PlayerConstants.PlayerState.PLAYING) {
                        CRLog.d("Alreay playing")
                        return
                    }

                    if (OnAir.mYoutubeState == PlayerConstants.PlayerState.PAUSED) {
                        CRLog.d("playing success")
                        OnAir.youtubePlayer?.play()
                        OnAir.updateOnAirButtonText(
                            filename,
                            RADIO_BUTTON.PLAYING_MESSAGE.getMessage(),
                            true
                        )
                    } else {
                        mVideoId?.let { OnAir.youtubePlayer?.loadVideo(it, 0.0f) }
                    }
                    OnAir.mRadioStatus = RADIO_STATUS.PLAYING
                    OnAir.setYoutubeStateManual(PlayerConstants.PlayerState.PLAYING)

                } else {
                    CRLog.d("pause success")
                    OnAir.youtubePlayer?.pause()
                    OnAir.mRadioStatus = RADIO_STATUS.PAUSED
                    OnAir.updateOnAirButtonText(
                        filename,
                        RADIO_BUTTON.PAUSED_MESSAGE.getMessage(),
                        true
                    )
                    OnAir.setYoutubeStateManual(PlayerConstants.PlayerState.PAUSED)
                }
            }
            SERVICE_TYPE.RADIO -> {
                mAddress?.let {
                    if (action) {
                        // play 요청
                        if (RadioPlayer.isPlaying()) {
                            CRLog.d("Alreay playing")
                            return
                        } else {
                            try {
                                if (!RadioPlayer.play(it)) {
                                    // play 했지만 실패
                                    RadioPlayer.stop()
                                    OnAir.updateOnAirButtonText(
                                        filename,
                                        RADIO_BUTTON.STOPPED_MESSAGE.getMessage(),
                                        true
                                    )
                                    sendCallback(filename, RESULT.PLAY_FAILED)
                                    return
                                } else {
                                    CRLog.d("playing success")
                                }
                            } catch (e: Exception) {
                                CRLog.d("error: " + e.message)
                            }
                            // play 성공
                            OnAir.mRadioStatus = RADIO_STATUS.PLAYING
                            sendCallback(filename, RESULT.PLAY_SUCCESS)
                            OnAir.updateOnAirButtonText(
                                filename,
                                RADIO_BUTTON.PLAYING_MESSAGE.getMessage(),
                                true
                            )
                        }
                    } else {
                        // stop 요청
                        if (RadioPlayer.isPlaying()) {
                            CRLog.d("stop success")
                            OnAir.mRadioStatus = RADIO_STATUS.STOPPED
                            RadioPlayer.stop()
                        }
                        OnAir.updateOnAirButtonText(
                            filename,
                            RADIO_BUTTON.STOPPED_MESSAGE.getMessage(),
                            true
                        )
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
                CRLog.d("onStartCommand: $mVideoId")

                MainActivity.youtubeView?.enableBackgroundPlayback(true)
                mVideoId?.let { OnAir.youtubePlayer?.loadVideo(it, 0.0f) }
                mFilename?.let { it1 -> sendCallback(it1, RESULT.PLAY_SUCCESS) }
                OnAir.setYoutubeStateManual(PlayerConstants.PlayerState.PLAYING)
                OnAir.mRadioStatus = RADIO_STATUS.PLAYING

                return START_NOT_STICKY
            }
            SERVICE_TYPE.RADIO -> {
                mAddress = intent?.getStringExtra("address")

                // address 가 null 이어서 실패
                // 실패되는 정보에 대한 channel map 을 임의로 생성하여 callback 에 담아 보낸다
                if (mAddress == null) {
                    mFilename?.let { it1 -> sendCallback(it1, RESULT.PLAY_FAILED) }
                    return START_STICKY
                }

                CRLog.d("start service filename: " + mFilename)
                CRLog.d("start service httpAddress: " + mAddress)

                // address 가 valid 하지만 재생 실패되는 경우
                // 이 경우도 실패에 대한 callback 을 전달한다.
                try {
                    mAddress?.let {
                        if (!RadioPlayer.play(it)) {
                            RadioPlayer.stop()
                            mFilename?.let { it1 -> sendCallback(it1, RESULT.PLAY_FAILED) }
                            return START_NOT_STICKY
                        }
                    }
                } catch (e: Exception) {
                    CRLog.d("error: " + e.message)
                }
                OnAir.mRadioStatus = RADIO_STATUS.PLAYING
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
        CRLog.d("RadioService onCreate")
    }

    override fun onDestroy() {
        super.onDestroy()
        mFilename?.let {
            CRLog.d("RadioService onDestroy")
            OnAir.mRadioStatus = RADIO_STATUS.STOPPED
            RadioPlayer.stop()

            // listener 가 너무 늦게 불러져서-_- 강제로 pause 이벤트 전달
            OnAir.setYoutubeStateManual(PlayerConstants.PlayerState.UNSTARTED)
            OnAir.youtubePlayer?.pause()

            sendCallback(it, RESULT.DESTROYED)
        }

        mFilename = null
        mVideoId = null
        mPlayType = null
        mAddress = null

        RadioNotification.destroy()
    }
}