package com.zerolive.cloudradio

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

object HeadSetConnectReceiver : BroadcastReceiver()
{
    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.getAction();
        CRLog.d("onReceive action: ${action}")

        when(action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> doStart()
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> doStop()
            Intent.ACTION_HEADSET_PLUG -> {
                val state = intent?.getIntExtra("state", -1)
                when(state) {
                    0 -> {
                        CRLog.d("headset plug out")
                        doStop()
                    }
                    1 -> {
                        CRLog.d("headset plug in")
                        doStart()
                    }
                }
            }
        }
    }

    private fun doStart() {
        OnAir.requestStartRadioService()
    }

    private fun doStop() {
        OnAir.requestStopRadioService()
    }
}