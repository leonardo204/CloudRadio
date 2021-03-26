package com.example.cloudradio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationPlayer {
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
        }
    }

    interface NOTIFICATION_ID {
        companion object {
            const val FOREGROUND_SERVICE = 101
        }
    }
}