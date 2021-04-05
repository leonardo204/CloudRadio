package com.example.cloudradio

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import java.util.HashMap

var programTag = "CR_Program"

@SuppressLint("StaticFieldLeak")
object Program : Fragment() {

    lateinit var mContext: Context
    var program_btnList = HashMap<String, Button>()

    // program 버튼을 누른 순서대로 favList 에 filename 으로 저장
    // 먼저 들어간 program 이 1순위가 된다.
    var favList = ArrayList<String>()

    lateinit var btn_save_setting: Button
    lateinit var btn_reset_setting: Button

    lateinit var layout_programs: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(programTag, "Program onCreateView")

        if ( container != null ) {
            mContext = container.context
        }

        var view: ViewGroup = inflater.inflate(R.layout.fragment_program, container, false) as ViewGroup

        layout_programs = view.findViewById(R.id.layout_grid_programs)
        btn_save_setting = view.findViewById(R.id.btn_save_setting)
        btn_reset_setting = view.findViewById(R.id.btn_reset_setting)
        btn_save_setting.setOnClickListener { onRadioButton("SAVE") }
        btn_reset_setting.setOnClickListener { onRadioButton("RESET") }

        initProgramButtons()

        // Inflate the layout for this fragment
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(programTag, "Program onViewCreated")

    }

    private fun initProgramButtons() {
        Log.d(programTag, "channels: ${RadioChannelResources.channelList.size}")
        for(i in RadioChannelResources.channelList.indices) {
            var button = Button(mContext)
            var filename = RadioChannelResources.channelList.get(i).filename
            button.setText( RadioChannelResources.channelList.get(i).defaultButtonText )
            button.setOnClickListener { onRadioButton(filename) }
            program_btnList.put(filename, button)
//            var layoutParams: GridLayout.LayoutParams =  GridLayout.LayoutParams()
//            layoutParams.setGravity(Gravity.FILL_HORIZONTAL)
//            layout_grid_programs.addView(button, layoutParams)
            layout_programs.addView(button)
        }
        resetAllButtonText()
        Log.d(programTag, "initProgramButtons() btnList: "+ program_btnList.size)
    }

    // 라디오 버튼 누르면 모두 여기서 처리
    // 선호채널 상태
    //   - 선호채널 리스트 제거
    //   - 색상 표시 리셋
    // 선호채널 상태가 아니라면
    //   - 선호채널 리스트 등록
    //   - 선호채널 색상 표시
    fun onRadioButton(filename: String) {
        when(filename) {
            "SAVE" -> saveAction()
            "RESET" -> resetAction()
            else -> {
                if ( favList.contains(filename) ) {
                    favList.remove(filename)
                    updateButtonText(filename, getDefaultTextByFilename(filename), true, false)
                } else {
                    favList.add(filename)
                    updateButtonText(filename, "즐겨찾기 추가, "+getDefaultTextByFilename(filename), true, true)
                }
            }
        }
    }

    private fun saveAction() {
        OnAir.makePrograms(favList)
    }

    private fun resetAction() {
        resetAllButtonText()
        OnAir.resetPrograms()
        favList.clear()
    }

    fun resetAllButtonText() {
        Log.d(programTag, "resetButtons()")
        var iter = program_btnList.iterator()
        while( iter.hasNext() ) {
            var obj = iter.next()
            var message = getDefaultTextByFilename(obj.key)
            updateButtonText(obj.key, message, true, false)
        }
    }

    fun getDefaultTextByFilename(filename: String): String {

        for(i in RadioChannelResources.channelList.indices) {
//            Log.d(programTag, " RadioChannelResources.channelList.get(i).filename: ${RadioChannelResources.channelList.get(i).filename} - $filename")
            if ( RadioChannelResources.channelList.get(i).filename.equals(filename) ) {
                return RadioChannelResources.channelList.get(i).defaultButtonText
            }
        }
        return "Unknown Channel"
    }

    @SuppressLint("SetTextI18n")
    fun updateButtonText(filename: String, text: String, enable: Boolean, bFavorite: Boolean) {
        Log.d(programTag, "updateButtonText $filename  $text  $enable")

        var iter = program_btnList.iterator()
        while( iter.hasNext() ) {
            var obj = iter.next()
            //Log.d(programTag, "updateButtonText: "+obj.key )
            if ( obj.key.equals(filename) ) {
                var button = obj.value
                if (bFavorite) {
                    button.setBackgroundColor(Color.CYAN)
                } else {
                    button.setBackgroundColor(Color.WHITE)
                }

                button.isEnabled = enable
                button.setText(text)
                break
            }
        }
    }
}