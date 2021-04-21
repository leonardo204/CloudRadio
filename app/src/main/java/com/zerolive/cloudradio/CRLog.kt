package com.zerolive.cloudradio

import android.util.Log

object CRLog {
    val tag = "CR_Log"

    fun d(msg: String) {
        var log = buildLogMsg(msg);
        Log.d(tag, log);
    }
    private fun buildLogMsg(message: String): String? {
        val ste = Thread.currentThread().stackTrace[4]
        val sb = StringBuilder()
        sb.append("[")
        sb.append(ste.fileName)
//        sb.append(" > ")
//        sb.append(ste.methodName)
        sb.append(" > #")
        sb.append(ste.lineNumber)
        sb.append("] ")
        sb.append(message)
        return sb.toString()
    }
}