package com.example.cloudradio

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE

@SuppressLint("StaticFieldLeak")
object RadioNotification {

    var mContext: Context? = null
    const val NOTIFICATION_ID = 1

    fun init(context: Context) {
        mContext = context
    }

    fun updateNotification(filename: String, playPause: Boolean) {
        mContext?.let {
            val manager = it.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(NOTIFICATION_ID)
            val notification = NotificationPlayer.createNotification(
                it,
                filename,
                manager,
                playPause
            )
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    fun createNotification(filename: String, playPause: Boolean): Notification? {
        mContext?.let {
            val manager = it.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(NOTIFICATION_ID)
            val notification = NotificationPlayer.createNotification(
                it,
                filename,
                manager,
                playPause
            )
            return notification
        }
        return null
    }

    fun destroy() {
        mContext = null
    }
}