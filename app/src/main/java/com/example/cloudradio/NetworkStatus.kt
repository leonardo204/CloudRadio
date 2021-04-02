package com.example.cloudradio

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

var netStatusTag = "CR_NetworkStatus"

object NetworkStatus {
    const val TYPE_WIFI = 1
    const val TYPE_MOBILE = 2
    const val TYPE_NOT_CONNECTED = 3

    //해당 context의 서비스를 사용하기위해서 context 객체를 받는다.
    @RequiresApi(Build.VERSION_CODES.M)
    fun getConnectivityStatus(context: Context): Int {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val activeNetwork = manager.activeNetwork ?: return TYPE_NOT_CONNECTED
        val actNw = manager.getNetworkCapabilities(activeNetwork) ?: return TYPE_NOT_CONNECTED
        var result = when{
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> TYPE_WIFI
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> TYPE_MOBILE
            else -> TYPE_NOT_CONNECTED
        }
        Log.d(netStatusTag, "network: " + getNetworkTypeString(result.toInt()) )
        return result
    }

    private fun getNetworkTypeString(type: Int): String {
        when(type) {
            1 -> return "WIFI"
            2 -> return "MOBILE"
            else -> return "Not Connected"
        }
    }
}
