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
import androidx.fragment.app.Fragment
import java.util.HashMap

class Program : Fragment() {
    companion object {
        lateinit var mContext: Context
        var bInitialized: Boolean = false
        var btnList = HashMap<String, Button>()

        // program 버튼을 누른 순서대로 favList 에 filename 으로 저장
        // 먼저 들어간 program 이 1순위가 된다.
        var favList = ArrayList<String>()

        // radio buttons
        lateinit var btn_kbs_classic: Button
        lateinit var btn_kbs_cool: Button
        lateinit var btn_kbs_happy: Button
        lateinit var btn_kbs_1_radio: Button
        lateinit var btn_sbs_love: Button
        lateinit var btn_sbs_power: Button
        lateinit var btn_mbc_standard: Button
        lateinit var btn_mbc_fm_foru: Button
        lateinit var btn_save_setting: Button
        lateinit var btn_reset_setting: Button
        lateinit var btn_youtube_jazz_morning: Button

        private var instance: Program? = null

        fun getInstance(): Program =
            instance ?: synchronized(this) {
                instance ?: Program().also {
                    instance = it
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(onairTag, "Program onCreateView")

        if ( container != null ) {
            mContext = container.context
        }

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_program, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(onairTag, "Program onViewCreated")

        btn_kbs_classic = view.findViewById(R.id.btn_kbs_classic)
        btnList.put(RadioRawChannels.KBS_CLASSIC_FM.getChannelFilename(), btn_kbs_classic)
        btn_kbs_cool = view.findViewById(R.id.btn_kbs_cool)
        btnList.put(RadioRawChannels.KBS_COOL_FM.getChannelFilename(), btn_kbs_cool)
        btn_kbs_happy = view.findViewById(R.id.btn_kbs_happy)
        btnList.put(RadioRawChannels.KBS_HAPPY_FM.getChannelFilename(), btn_kbs_happy)
        btn_kbs_1_radio = view.findViewById(R.id.btn_kbs_1_radio)
        btnList.put(RadioRawChannels.KBS_1_RADIO.getChannelFilename(), btn_kbs_1_radio)
        btn_sbs_love = view.findViewById(R.id.btn_sbs_love)
        btnList.put(RadioRawChannels.SBS_LOVE_FM.getChannelFilename(), btn_sbs_love)
        btn_sbs_power = view.findViewById(R.id.btn_sbs_power)
        btnList.put(RadioRawChannels.SBS_POWER_FM.getChannelFilename(), btn_sbs_power)
        btn_mbc_fm_foru = view.findViewById(R.id.btn_mbc_fm_foru)
        btnList.put(RadioRawChannels.MBC_FM_FORU.getChannelFilename(), btn_mbc_fm_foru)
        btn_mbc_standard = view.findViewById(R.id.btn_mbc_s)
        btnList.put(RadioRawChannels.MBC_STANDARD_FM.getChannelFilename(), btn_mbc_standard)
        btn_youtube_jazz_morning = view.findViewById(R.id.btn_youtube_jazz_morning)
        btnList.put(RadioRawChannels.YOUTUBE_JAZZ_MORNING.getChannelFilename(), btn_youtube_jazz_morning)

        btn_save_setting = view.findViewById(R.id.btn_save_setting)
        btn_reset_setting = view.findViewById(R.id.btn_reset_setting)

        Log.d(onairTag, "btnList size: "+ btnList.size)

        if ( !bInitialized ) {
            Log.d(onairTag, "initial called")
            resetAllButtonText()
        } else {
            Log.d(onairTag, "use previous data")
        }

        btn_kbs_classic.setOnClickListener { onRadioButton(RadioRawChannels.KBS_CLASSIC_FM.getChannelFilename()) }
        btn_kbs_cool.setOnClickListener{ onRadioButton(RadioRawChannels.KBS_COOL_FM.getChannelFilename()) }
        btn_kbs_happy.setOnClickListener{ onRadioButton(RadioRawChannels.KBS_HAPPY_FM.getChannelFilename()) }
        btn_kbs_1_radio.setOnClickListener{ onRadioButton(RadioRawChannels.KBS_1_RADIO.getChannelFilename()) }
        btn_sbs_love.setOnClickListener{ onRadioButton(RadioRawChannels.SBS_LOVE_FM.getChannelFilename()) }
        btn_sbs_power.setOnClickListener{ onRadioButton(RadioRawChannels.SBS_POWER_FM.getChannelFilename()) }
        btn_mbc_fm_foru.setOnClickListener{ onRadioButton(RadioRawChannels.MBC_FM_FORU.getChannelFilename()) }
        btn_mbc_standard.setOnClickListener{ onRadioButton(RadioRawChannels.MBC_STANDARD_FM.getChannelFilename()) }
        btn_youtube_jazz_morning.setOnClickListener { onRadioButton(RadioRawChannels.YOUTUBE_JAZZ_MORNING.getChannelFilename()) }
        btn_save_setting.setOnClickListener { onRadioButton("SAVE") }
        btn_reset_setting.setOnClickListener { onRadioButton("RESET") }
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
        OnAir.getInstance().makePrograms(favList)
    }

    private fun resetAction() {
        resetAllButtonText()
        OnAir.getInstance().resetPrograms()
        favList.clear()
    }

    fun resetAllButtonText() {
        Log.d(onairTag, "resetButtons()")
        var iter = btnList.iterator()
        while( iter.hasNext() ) {
            var obj = iter.next()
            var message = getDefaultTextByFilename(obj.key)
            updateButtonText(obj.key, message, true, false)
        }
    }

    fun getDefaultTextByFilename(filename: String): String {
        when(filename) {
            RadioRawChannels.MBC_STANDARD_FM.getChannelFilename() -> return RADIO_BUTTON.MBC_STANDARD_DEFAULT.getMessage()
            RadioRawChannels.MBC_FM_FORU.getChannelFilename() -> return RADIO_BUTTON.MBC_FORU_DEFAULT.getMessage()
            RadioRawChannels.SBS_POWER_FM.getChannelFilename() -> return RADIO_BUTTON.SBS_POWER_DEFAULT.getMessage()
            RadioRawChannels.SBS_LOVE_FM.getChannelFilename() -> return RADIO_BUTTON.SBS_LOVE_DEFAULT.getMessage()
            RadioRawChannels.KBS_1_RADIO.getChannelFilename() -> return RADIO_BUTTON.KBS_1_RADIO_DEFAULT.getMessage()
            RadioRawChannels.KBS_HAPPY_FM.getChannelFilename() -> return RADIO_BUTTON.KBS_HAPPY_DEFAULT.getMessage()
            RadioRawChannels.KBS_COOL_FM.getChannelFilename() -> return  RADIO_BUTTON.KBS_COOL_DEFAULT.getMessage()
            RadioRawChannels.KBS_CLASSIC_FM.getChannelFilename() -> return RADIO_BUTTON.KBS_CLASSIC_DEFAULT.getMessage()
            RadioRawChannels.YOUTUBE_JAZZ_MORNING.getChannelFilename() -> return RADIO_BUTTON.YOUTUBE_JAZZ_MORNING_DEFAULT.getMessage()
            else -> return "Unknown"
        }
    }

    @SuppressLint("SetTextI18n")
    fun updateButtonText(filename: String, text: String, enable: Boolean, bFavorite: Boolean) {
        Log.d(onairTag, "updateButtonText $filename  $text  $enable")

        var iter = btnList.iterator()
        while( iter.hasNext() ) {
            var obj = iter.next()
            //Log.d(onairTag, "updateButtonText: "+obj.key )
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