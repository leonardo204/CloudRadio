package com.example.cloudradio

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.media.session.PlaybackState.ACTION_PAUSE
import android.media.session.PlaybackState.ACTION_STOP
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat


class RadioService : Service() {

    companion object {
        private const val NOTIFICATION_DOWNLOAD_ID = 1
        private const val NOTIFICATION_COMPLETE_ID = 2
        private const val CHANNEL_ID = "RadioService"
        private const val CHANNEL_NAME = "default"
        private const val CHANNEL_DESCRIPTION = "This is default notification channel"
    }

    private val notificationManager @SuppressLint("ServiceCast")
    get() = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(onairTag, "RadioService onStartCommand")
        startForeground(NOTIFICATION_DOWNLOAD_ID, createRadioNotification())

        RadioPlayer.play(RadioPlayer.KBS_CLASSIC_FM)

        return START_STICKY
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
    }

}