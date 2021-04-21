package com.zerolive.cloudradio

import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader

object FileIO {
    fun fileRead(context: Context, fileUri: Uri): String {
        val sb = StringBuilder()
        val inputStream = context.contentResolver.openInputStream(fileUri)
        inputStream?.let {
            val reader = BufferedReader(InputStreamReader(inputStream))
            val readLines = reader.readLines()
            readLines.forEach {
                sb.append(it)
            }

            it.close()
        }
        return sb.toString()
    }
}