package com.zerolive.cloudradio

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView


class NoticePopupActivity(context: Context) {
    private val dlg = Dialog(context)   //부모 액티비티의 context 가 들어감
    private lateinit var txtPopup: TextView
    private lateinit var btnOK : Button
    private lateinit var listener : NoticePopupActivity

    fun init() {
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)   //타이틀바 제거
        dlg.setContentView(R.layout.notice_popup)               //다이얼로그에 사용할 xml 파일을 불러옴
        dlg.setCancelable(false)                        //다이얼로그의 바깥 화면을 눌렀을 때 다이얼로그가 닫히지 않도록 함

        txtPopup = dlg.findViewById(R.id.txt_popup)
        btnOK = dlg.findViewById(R.id.btn_ok)
        btnOK.setOnClickListener { onClickHandler() }
    }

    private fun onClickHandler() {
        listener.onOKClicked("확인을 눌렀습니다")
    }

    fun setMessage(message: String) {
        txtPopup.setText(message)
    }

    fun setBtnEnable(enable: Boolean) {
        btnOK.isEnabled = enable
        if ( !enable ) {
            btnOK.setBackgroundColor(Color.DKGRAY)
        }
    }

    fun show() {
        val lp = WindowManager.LayoutParams()
        lp.copyFrom(dlg.window?.attributes)
        lp.width = 800
        lp.height = 800

        dlg.window?.attributes = lp
        dlg.show()
    }

    fun setOnOKClickedListener(listener: (String) -> Unit) {
        this.listener = object: NoticePopupActivity {
            override fun onOKClicked(content: String) {
                listener(content)
            }
        }
    }

    interface NoticePopupActivity {
        fun onOKClicked(content: String)
    }
}