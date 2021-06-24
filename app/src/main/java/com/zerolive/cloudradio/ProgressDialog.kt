package com.zerolive.cloudradio

import android.app.Dialog
import android.content.Context
import android.view.Window

class ProgressDialog(context: Context) : Dialog(context) {
    init {
        // 다이얼 로그 제목을 안보이게...
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_progress)
    }
}