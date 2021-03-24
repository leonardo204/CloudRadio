package com.example.cloudradio

import android.app.Dialog
import android.content.Context
import android.view.Window
import android.widget.Button
import android.widget.TextView

class NoticePopupActivity(context : Context) {
    private val dlg = Dialog(context)   //부모 액티비티의 context 가 들어감
    private lateinit var txtPopup: TextView
    private lateinit var btnOK : Button
    private lateinit var listener : NoticePopupActivity

    fun start(message : String) {
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)   //타이틀바 제거
        dlg.setContentView(R.layout.notice_popup)               //다이얼로그에 사용할 xml 파일을 불러옴
        dlg.setCancelable(false)                        //다이얼로그의 바깥 화면을 눌렀을 때 다이얼로그가 닫히지 않도록 함

        txtPopup = dlg.findViewById(R.id.txt_popup)
        txtPopup.setText( message )

        btnOK = dlg.findViewById(R.id.btn_ok)
        btnOK.setOnClickListener {
            dlg.dismiss()
            listener.onOKClicked("확인을 눌렀습니다")
        }

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
        fun onOKClicked(content : String)
    }
}