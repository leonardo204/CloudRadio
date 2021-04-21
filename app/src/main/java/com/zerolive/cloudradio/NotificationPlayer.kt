package com.zerolive.cloudradio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.zerolive.cloudradio.R
import com.fasterxml.jackson.databind.util.ClassUtil.getPackageName


object NotificationPlayer {
    const val CHANNEL_ID = "CloudRadioService" // 임의의 채널 ID

    fun createNotification(context: Context, filename: String, manager: NotificationManager, playPause: Boolean): Notification {

        // 알림 클릭시 OnAir 이동됨
        val notificationIntent = Intent(context, OnAir::class.java)
        notificationIntent.action = Constants.ACTION.MAIN_ACTION
        notificationIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP //or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            notificationIntent,
            FLAG_UPDATE_CURRENT
        )

        // 각 버튼들에 관한 Intent
        val playIntent = Intent(context, RadioService::class.java)
        playIntent.action = Constants.ACTION.PLAY_ACTION
        val playPendingIntent = PendingIntent.getService(context, 0, playIntent, 0)

        val pauseIntent = Intent(context, RadioService::class.java)
        pauseIntent.action = Constants.ACTION.PAUSE_ACTION
        val pausePendingIntent = PendingIntent.getService(context, 0, pauseIntent, 0)

        // custom view
        val remoteViews = RemoteViews(getPackageName(OnAir::class.java),
            R.layout.notificatoin_player
        )
        remoteViews.setImageViewResource(R.id.img_albumart, R.drawable.ic_radio_antenna)

        // true -> playing
        if ( playPause ) {
            remoteViews.setTextViewText(
                R.id.txt_title, "재생중: " + RadioChannelResources.getDefaultButtonTextByFilename(
                    filename
                )
            )
            remoteViews.setImageViewResource(R.id.btn_play_pause, R.drawable.pause)
            remoteViews.setOnClickPendingIntent(R.id.btn_play_pause, pausePendingIntent)
        } else {
            remoteViews.setTextViewText(
                R.id.txt_title, "일시정지: " + RadioChannelResources.getDefaultButtonTextByFilename(
                    filename
                )
            )
            remoteViews.setImageViewResource(R.id.btn_play_pause, R.drawable.play)
            remoteViews.setOnClickPendingIntent(R.id.btn_play_pause, playPendingIntent)
        }



        // 알림
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("CloudRadio")
            .setContentText("라디오가 재생중입니다.")
            .setSmallIcon(R.drawable.ic_radio_antenna)
            .setOngoing(true) // true 일경우 알림 리스트에서 클릭하거나 좌우로 드래그해도 사라지지 않음
            .setContentIntent(pendingIntent)
            .setContent(remoteViews)
            .setNotificationSilent()
            .build()

        // Oreo 부터는 Notification Channel을 만들어야 함
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "CloudRadio", // 채널표시명
                NotificationManager.IMPORTANCE_DEFAULT
            )

            serviceChannel.description = "Internet Radio Service"
            serviceChannel.enableVibration(true)
            serviceChannel.vibrationPattern = longArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0)

            manager.createNotificationChannel(serviceChannel)
        }

        return notification
    }
}

class Constants {
    interface ACTION {
        companion object {
            const val MAIN_ACTION = "action.main"
            const val PREV_ACTION = "action.prev"
            const val PLAY_ACTION = "action.play"
            const val PAUSE_ACTION = "action.pause"
            const val NEXT_ACTION = "action.next"
            const val CLOSE_ACTION = "action.close"
            const val STARTFOREGROUND_ACTION = "action.startforeground"
            const val STOPFOREGROUND_ACTION = "action.stopforeground"
            const val NOTI_UPDATE_PLAY = "action.noti.play"
            const val NOTI_UPDATE_PAUSE = "action.noti.pause"
        }
    }
}