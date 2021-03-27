package com.example.cloudradio

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat

enum class RESULT {
    PLAY_FAILED, PLAY_SUCCESS, DESTROYED
}

class RadioService : Service() {

    companion object {
        private const val NOTIFICATION_DOWNLOAD_ID = 1
        private const val NOTIFICATION_COMPLETE_ID = 2
        private const val CHANNEL_ID = "RadioService"
        private const val CHANNEL_NAME = "default"
        private const val CHANNEL_DESCRIPTION = "This is default notification channel"
    }

    private var filename: String? = null

    private val notificationManager @SuppressLint("ServiceCast")
    get() = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(onairTag, "RadioService onStartCommand")
        startForeground(NOTIFICATION_DOWNLOAD_ID, createRadioNotification())

        if ( intent?.action.equals(Constants.ACTION.STARTFOREGROUND_ACTION) ) {
            Log.d(onairTag, "start foreground services")
        } else if ( intent?.action.equals(Constants.ACTION.STOPFOREGROUND_ACTION) ) {
            Log.d(onairTag, "stop foreground services")
        }

        filename = intent?.getStringExtra("name")
        var address = intent?.getStringExtra("address")

        // address 가 null 이어서 실패
        // 실패되는 정보에 대한 channel map 을 임의로 생성하여 callback 에 담아 보낸다
        if ( address == null ) {
            filename?.let { it1 -> sendCallback(it1, RESULT.PLAY_FAILED) }
            return START_STICKY
        }

        Log.d(onairTag, "start service filename: "+filename)
        Log.d(onairTag, "start service httpAddress: "+address)

        // address 가 valid 하지만 재생 실패되는 경우
        // 이 경우도 실패에 대한 callback 을 전달한다.
        try {
            address?.let {
                if ( !RadioPlayer.play(it) ) {
                    RadioPlayer.stop()
                    filename?.let { it1 -> sendCallback(it1, RESULT.PLAY_FAILED) }
                    return START_NOT_STICKY
                }
            }
        } catch ( e: Exception ) {
            Log.d(onairTag, "error: "+e.message)
        }

        // success callback
        filename?.let { it1 -> sendCallback(it1, RESULT.PLAY_SUCCESS) }

        return START_NOT_STICKY
    }

    private fun sendCallback(filename: String, result: RESULT) {
        OnAir.getInstance().notifyRadioServiceStatus(filename, result)
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(onairTag, "RadioService onCreate")

        registerDefaultNotificationChannel()
    }

    private fun registerDefaultNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(createDefaultNotificationChannel())
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createDefaultNotificationChannel() =
        NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
            description = CHANNEL_DESCRIPTION
            this.setShowBadge(true)
            this.lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }

    private fun createRadioNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID).apply {
            setContentTitle("Playing Radio")
            setContentText("라디오가 재생중입니다.")
            setSmallIcon(R.drawable.ic_radio_antenna)
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        }.build()

    override fun onDestroy() {
        super.onDestroy()
        Log.d(onairTag, "RadioService onDestroy")
        RadioPlayer.stop()
        filename?.let { it1 -> sendCallback(it1, RESULT.DESTROYED) }
    }
}