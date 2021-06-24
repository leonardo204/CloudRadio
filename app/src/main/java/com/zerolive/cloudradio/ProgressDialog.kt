package com.zerolive.cloudradio

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Window

class ProgressDialog(context: Context) {
    private val dlg = Dialog(context)
    fun init() {
        // 다이얼 로그 제목을 안보이게...
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dlg.setContentView(R.layout.dialog_progress)
        dlg.setCancelable(false)
        dlg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    fun show() {
        CRLog.d("ProgressDialog show")
        dlg.show()
    }

    fun hide() {
        CRLog.d("ProgressDialog hide")
        dlg.hide()
        dlg.dismiss()
    }

    fun isShowing(): Boolean {
        CRLog.d("ProgressDialog showing: ${dlg.isShowing}")
        return dlg.isShowing
    }
}