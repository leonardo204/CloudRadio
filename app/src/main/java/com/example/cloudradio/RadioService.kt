package com.example.cloudradio

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.os.AsyncTask
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import java.io.*
import java.net.URL
import java.net.URLConnection
import java.nio.charset.Charset



class RadioService : Service() {

    companion object {
        private const val NOTIFICATION_DOWNLOAD_ID = 1
        private const val NOTIFICATION_COMPLETE_ID = 2
        private const val CHANNEL_ID = "RadioService"
        private const val CHANNEL_NAME = "default"
        private const val CHANNEL_DESCRIPTION = "This is default notification channel"
    }

    private var channels: RadioCompletionMap? = null

    private val notificationManager @SuppressLint("ServiceCast")
    get() = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    private fun getChannels(name: String): RadioCompletionMap? {
        for(i in RadioChannelResources.channelList.indices) {
            if ( RadioChannelResources.channelList.get(i).filename.equals(name) ) {
                return RadioChannelResources.channelList.get(i)
            }
        }
        return null
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(onairTag, "RadioService onStartCommand")
        startForeground(NOTIFICATION_DOWNLOAD_ID, createRadioNotification())

        var name = intent?.getStringExtra("name")
        var address = intent?.getStringExtra("address")
        channels = name?.let { getChannels(it) }

        if ( address == null ) {
            for(i in RadioRawChannels.values().indices) {
                if ( name.equals( RadioRawChannels.values()[i].getChannelFilename() ) ) {
                    var map = RadioCompletionMap(RadioRawChannels.values()[i].getChannelTitle(), 999, RadioRawChannels.values()[i].getChannelFilename(), RadioRawChannels.values()[i].getChannelAddress(), "N/A")
                    OnAir.getInstance().notifyRadioServiceDestroyed(map, false)
                    stopSelf()
                    break
                }
            }
        }

        Log.d(onairTag, "start service: "+name)
        Log.d(onairTag, "start service: "+address)
        try {
            address?.let {
                if ( !RadioPlayer.play(it) ) {
                    RadioPlayer.stop()
                    channels?.let { OnAir.getInstance().notifyRadioServiceDestroyed(it, false) }
                }
            }
        } catch ( e: Exception ) {
            Log.d(onairTag, "error: "+e.message)
        }

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
        channels?.let { OnAir.getInstance().notifyRadioServiceDestroyed(it, true) }
    }
}