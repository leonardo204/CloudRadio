package com.example.cloudradio

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat.startForegroundService
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

var onairTag = "OnAir"

val handler: Handler = @SuppressLint("HandlerLeak")
object : Handler() {
    override fun handleMessage(msg: Message) {
        OnAir.getInstance().resetButtons()
    }
}

class OnAir : Fragment() {

    private var btnList = ArrayList<Button>()

    companion object {
        private var playingChannelName: RadioRawChannels? = null
        var bInitialized: Boolean = false

        var mFailedChannelMap: RadioCompletionMap? = null

        // request wether (fixed)
        val num_of_rows = 10
        val page_no = 1
        val data_type = "JSON"
        lateinit var mContext: Context

        lateinit var txt_timeView: TextView
        lateinit var txt_addrView: TextView
        lateinit var txt_skyView: TextView
        lateinit var txt_windView: TextView
        lateinit var txt_rainView: TextView
        lateinit var txt_fcstView: TextView

        lateinit var mBtn_weather_refresh: ImageButton

        lateinit var img_skyView: ImageView
        lateinit var img_rainView: ImageView
        lateinit var img_weatherView: ImageView

        lateinit var img_airStatus: ImageView
        lateinit var txt_pmValue: TextView
        lateinit var txt_pmGrade: TextView

        // radio buttons
        lateinit var btn_kbs_classic: Button
        lateinit var btn_kbs_cool: Button
        lateinit var btn_kbs_happy: Button
        lateinit var btn_kbs_1_radio: Button
        lateinit var btn_sbs_love: Button
        lateinit var btn_sbs_power: Button
        lateinit var btn_mbc_standard: Button
        lateinit var btn_mbc_fm_foru: Button

        var mAddressText: String = "N/A"

        private var instance: OnAir? = null

        fun getInstance(): OnAir =
                instance ?: synchronized(this) {
                    instance ?: OnAir().also {
                        instance = it
                    }
                }

        var mRainType: Int = 0
        var mSkyType: Int = 0
        var mTemperatureText: String = "N/A"
        var mWindText: String = "N/A"
        var mRainText: String = "N/A"
        var mFcstTimeText: String = "N/A"
        var mTimeText:String = "N/A"
        lateinit var mPMData: PMData
    }

    private fun onRefreshClick() {
        Log.d(onairTag, "information refresh")
        setCurrentTimeView()
        bInitialized = false
        txt_fcstView.setText("")
        MainActivity.getInstance().getGPSInfo()

        var text = "현재 지역의 날씨 정보를 업데이트합니다.\n잠시만 기다려주십시오."
        Toast.makeText(mContext, text, Toast.LENGTH_LONG).show()
    }

    fun setFailedChannel(channel: RadioCompletionMap?) {
        Log.d(onairTag, "setFailedChannel: " + channel?.filename)
        mFailedChannelMap = channel

        if ( bInitialized ) {
            var msg = handler.obtainMessage()
            handler.sendMessage(msg)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun resetButtonByObj(button: Button) {
        for (i in btnList.indices) {
            if ( btnList.get(i) == button ) continue
            when ( btnList.get(i) ) {
                btn_kbs_classic -> btn_kbs_classic.setText("KBS 클래식 FM")
                btn_kbs_cool -> btn_kbs_cool.setText("KBS COOL FM")
                btn_kbs_happy -> btn_kbs_happy.setText("KBS HAPPY FM")
                btn_kbs_1_radio -> btn_kbs_1_radio.setText("KBS 1 라디오")
                btn_sbs_love -> btn_sbs_love.setText("SBS LOVE FM")
                btn_sbs_power -> btn_sbs_power.setText("SBS POWER FM")
                btn_mbc_fm_foru -> btn_mbc_fm_foru.setText("MBC FM For U")
                btn_mbc_standard -> btn_mbc_standard.setText("MBC 표준 FM")
                else -> Log.d(onairTag, "skip resetButton")
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun resetButtonByChannels(channel: RadioRawChannels?) {
        val played = RadioPlayer.isPlaying()
        when (channel?.getChannelTitle()) {
            "KBS Classic FM" -> {
                if (played) btn_kbs_classic.setText("Playing ( Touch to stop )")
                else btn_kbs_classic.setText("Stopped ( Touch to play )")
            }
            "KBS Cool FM" -> {
                if (played) btn_kbs_cool.setText("Playing ( Touch to stop )")
                else btn_kbs_cool.setText("Stopped ( Touch to play )")
            }
            "SBS Love FM" -> {
                if (played) btn_sbs_love.setText("Playing ( Touch to stop )")
                else btn_sbs_love.setText("Stopped ( Touch to play )")
            }
            "SBS Power FM" -> {
                if (played) btn_sbs_power.setText("Playing ( Touch to stop )")
                else btn_sbs_power.setText("Stopped ( Touch to play )")
            }
            "MBC 표준FM" -> {
                if (played) btn_mbc_standard.setText("Playing ( Touch to stop )")
                else btn_mbc_standard.setText("Stopped ( Touch to play )")
            }
            "MBC FM4U" -> {
                if (played) btn_mbc_fm_foru.setText("Playing ( Touch to stop )")
                else btn_mbc_fm_foru.setText("Stopped ( Touch to play )")
            }
            "KBS HappyFM" -> {
                if (played) btn_kbs_happy.setText("Playing ( Touch to stop )")
                else btn_kbs_happy.setText("Stopped ( Touch to play )")
            }
            "KBS 1Radio" -> {
                if (played) btn_kbs_1_radio.setText("Playing ( Touch to stop )")
                else btn_kbs_1_radio.setText("Stopped ( Touch to play )")
            }
            else -> Log.d(onairTag, "skip resetButton")
        }
    }

    @SuppressLint("SetTextI18n")
    fun requestResetButtons() {
        Log.d(onairTag, "requestResetButtons()")

        if (!bInitialized) {
            Log.d(onairTag, "Ignore requestResetButtons()")
            return
        }
        instance?.resetButtons()
    }

    fun resetButtons() {
        Log.d(onairTag, "resetButtons()")

        btn_kbs_classic.isEnabled = true
        btn_kbs_classic.setText("KBS 클래식 FM")

        btn_kbs_cool.isEnabled = true
        btn_kbs_cool.setText("KBS COOL FM")

        btn_kbs_happy.isEnabled = true
        btn_kbs_happy.setText("KBS HAPPY FM")

        btn_kbs_1_radio.isEnabled = true
        btn_kbs_1_radio.setText("KBS 1 라디오")

        btn_sbs_love.isEnabled = true
        btn_sbs_love.setText("SBS LOVE FM")

        btn_sbs_power.isEnabled = true
        btn_sbs_power.setText("SBS POWER FM")

        btn_mbc_fm_foru.isEnabled = true
        btn_mbc_fm_foru.setText("MBC FM For U")

        btn_mbc_standard.isEnabled = true
        btn_mbc_standard.setText("MBC 표준 FM")
    }

    @SuppressLint("SetTextI18n")
    private fun onKbsClassic() {
        if ( RadioPlayer.isPlaying() ) {
            Log.d(onairTag, "onKbsClassic stop")

            playingChannelName = RadioRawChannels.KBS_CLASSIC_FM
            stopRadioForegroundService()

            btn_kbs_classic.setText("Stopped ( Touch to play )")
        } else {
            Log.d(onairTag, "onKbsClassic play")

            startRadioForegroundService(RadioRawChannels.KBS_CLASSIC_FM)

            btn_kbs_classic.setText("Playing ( Touch to stop )")
        }
        resetButtonByObj(btn_kbs_classic)
    }

    @SuppressLint("SetTextI18n")
    private fun onMbcSFM() {
        if ( RadioPlayer.isPlaying() ) {
            Log.d(onairTag, "onMbcSFM stop")

            playingChannelName = RadioRawChannels.MBC_STANDARD_FM
            stopRadioForegroundService()

            btn_mbc_standard.setText("Stopped ( Touch to play )")
        } else {
            Log.d(onairTag, "onMbcSFM play")

            startRadioForegroundService(RadioRawChannels.MBC_STANDARD_FM)

            btn_mbc_standard.setText("Playing ( Touch to stop )")
        }
        resetButtonByObj(btn_mbc_standard)
    }

    @SuppressLint("SetTextI18n")
    private fun onMbcFmForU() {
        if ( RadioPlayer.isPlaying() ) {
            Log.d(onairTag, "onMbcFmForU stop")

            playingChannelName = RadioRawChannels.MBC_FM_FORU
            stopRadioForegroundService()

            btn_mbc_fm_foru.setText("Stopped ( Touch to play )")
        } else {
            Log.d(onairTag, "onMbcFmForU play")

            startRadioForegroundService(RadioRawChannels.MBC_FM_FORU)

            btn_mbc_fm_foru.setText("Playing ( Touch to stop )")
        }
        resetButtonByObj(btn_mbc_fm_foru)
    }

    @SuppressLint("SetTextI18n")
    private fun onSbsPower() {
        if ( RadioPlayer.isPlaying() ) {
            Log.d(onairTag, "onSbsPower stop")

            playingChannelName = RadioRawChannels.SBS_POWER_FM
            stopRadioForegroundService()

            btn_sbs_power.setText("Stopped ( Touch to play )")
        } else {
            Log.d(onairTag, "onSbsPower play")

            startRadioForegroundService(RadioRawChannels.SBS_POWER_FM)

            btn_sbs_power.setText("Playing ( Touch to stop )")
        }
        resetButtonByObj(btn_sbs_power)
    }

    @SuppressLint("SetTextI18n")
    private fun onSbsLove() {
        if ( RadioPlayer.isPlaying() ) {
            Log.d(onairTag, "onSbsLove stop")

            playingChannelName = RadioRawChannels.SBS_LOVE_FM
            stopRadioForegroundService()

            btn_sbs_love.setText("Stopped ( Touch to play )")
        } else {
            Log.d(onairTag, "onSbsLove play")

            startRadioForegroundService(RadioRawChannels.SBS_LOVE_FM)

            btn_sbs_love.setText("Playing ( Touch to stop )")
        }
        resetButtonByObj(btn_sbs_love)
    }

    @SuppressLint("SetTextI18n")
    private fun onKbs1Radio() {
        if ( RadioPlayer.isPlaying() ) {
            Log.d(onairTag, "onKbs1Radio stop")

            playingChannelName = RadioRawChannels.KBS_1_RADIO
            stopRadioForegroundService()

            btn_kbs_1_radio.setText("Stopped ( Touch to play )")
        } else {
            Log.d(onairTag, "onKbs1Radio play")

            startRadioForegroundService(RadioRawChannels.KBS_1_RADIO)

            btn_kbs_1_radio.setText("Playing ( Touch to stop )")
        }
        resetButtonByObj(btn_kbs_1_radio)
    }

    @SuppressLint("SetTextI18n")
    private fun onKbsHappy() {
        if ( RadioPlayer.isPlaying() ) {
            Log.d(onairTag, "onKbsHappy stop")

            playingChannelName = RadioRawChannels.KBS_HAPPY_FM
            stopRadioForegroundService()

            btn_kbs_happy.setText("Stopped ( Touch to play )")
        } else {
            Log.d(onairTag, "onKbsHappy play")

            startRadioForegroundService(RadioRawChannels.KBS_HAPPY_FM)

            btn_kbs_happy.setText("Playing ( Touch to stop )")
        }
        resetButtonByObj(btn_kbs_happy)
    }

    @SuppressLint("SetTextI18n")
    private fun onKbsCool() {
        if ( RadioPlayer.isPlaying() ) {
            Log.d(onairTag, "onKbsCool stop")

            playingChannelName = RadioRawChannels.KBS_COOL_FM
            stopRadioForegroundService()

            btn_kbs_cool.setText("Stopped ( Touch to play )")
        } else {
            Log.d(onairTag, "onKbsCool play")

            startRadioForegroundService(RadioRawChannels.KBS_COOL_FM)

            btn_kbs_cool.setText("Playing ( Touch to stop )")
        }
        resetButtonByObj(btn_kbs_cool)
    }


    private fun getRadioChannelFileName(channels: RadioRawChannels): String {
        return channels.getChannelFilename()
    }

    private fun getRadioChannelHttpAddress(channels: RadioRawChannels): String? {
        var idx = 0
        for(i in RadioChannelResources.channelList.indices) {
            if ( RadioChannelResources.channelList.get(i).title.equals(channels.getChannelTitle()) ) {
                idx =  i
                return  RadioChannelResources.channelList.get(idx).httpAddress
            }
        }
        return null
    }

    private fun setDisableButton(map: RadioCompletionMap?, message: String) {
        val FAILED_MESSAGE = message
        when(map?.title) {
            "KBS Classic FM" -> {
                btn_kbs_classic.setText(FAILED_MESSAGE)
                btn_kbs_classic.isEnabled = false
            }
            "KBS Cool FM" -> {
                btn_kbs_cool.setText(FAILED_MESSAGE)
                btn_kbs_cool.isEnabled = false
            }
            "SBS Love FM" -> {
                btn_sbs_love.setText(FAILED_MESSAGE)
                btn_sbs_love.isEnabled = false
            }
            "SBS Power FM" -> {
                btn_sbs_power.setText(FAILED_MESSAGE)
                btn_sbs_power.isEnabled = false
            }
            "MBC 표준FM" -> {
                btn_mbc_standard.setText(FAILED_MESSAGE)
                btn_mbc_standard.isEnabled = false
            }
            "MBC FM4U" -> {
                btn_mbc_fm_foru.setText(FAILED_MESSAGE)
                btn_mbc_fm_foru.isEnabled = false
            }
            "KBS HappyFM" -> {
                btn_kbs_happy.setText(FAILED_MESSAGE)
                btn_kbs_happy.isEnabled = false
            }
            "KBS 1Radio" -> {
                btn_kbs_1_radio.setText(FAILED_MESSAGE)
                btn_kbs_1_radio.isEnabled = false
            }
            else -> Log.d(onairTag, "There is not exist any services to start")
        }
    }

    fun notifyRadioServiceDestroyed(map: RadioCompletionMap, result: Boolean) {
        Log.d(onairTag, "notifyRadioServiceDestroyed: " + result)

        if ( !result ) {
            RadioChannelResources.getInstance().requestUpdateResource(map)
            val FAILED_MESSAGE = "재생 실패 잠시 후 다시 시도하여 주십시오."
            setDisableButton(map, FAILED_MESSAGE)
        } else {

            Log.d(onairTag, "Ready to " + playingChannelName?.getChannelTitle())
            if (map.title.equals(playingChannelName?.getChannelTitle())) {
                Log.d(onairTag, "Ignore same channel")
            } else {
                when (playingChannelName?.getChannelTitle()) {
                    "KBS Classic FM" -> onKbsClassic()
                    "KBS Cool FM" -> onKbsCool()
                    "SBS Love FM" -> onSbsLove()
                    "SBS Power FM" -> onSbsPower()
                    "MBC 표준FM" -> onMbcSFM()
                    "MBC FM4U" -> onMbcFmForU()
                    "KBS HappyFM" -> onKbsHappy()
                    "KBS 1Radio" -> onKbs1Radio()
                    else -> Log.d(onairTag, "There is not exist any services to start")
                }
            }
        }
    }

    /*
     Service
    */
    fun startRadioForegroundService(channels: RadioRawChannels) {
        var name = getRadioChannelFileName(channels)
        var address:String? = getRadioChannelHttpAddress(channels)

        Intent(mContext, RadioService::class.java).run {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
                var intent: Intent = Intent(mContext, RadioService::class.java)
                intent.putExtra("name", name)
                intent.putExtra("address", address)
                startForegroundService(mContext, intent)
            }
            else {
                requireActivity().startService(Intent(mContext, RadioService::class.java))
            }
        }
    }

    fun stopRadioForegroundService() {
        Intent(mContext, RadioService::class.java).run {
            requireActivity().stopService(Intent(mContext, RadioService::class.java))
        }
    }

    @SuppressLint("NewApi")
    private fun setCurrentTimeView() {
        var result: String
        val current: LocalDateTime = LocalDateTime.now()
        var formatter = DateTimeFormatter.ofPattern("yyyy")
        var str = current.format(formatter)
        result = str + "년"

        formatter = DateTimeFormatter.ofPattern("MM")
        str = current.format(formatter)
        result = result + " " + str + "월"

        formatter = DateTimeFormatter.ofPattern("dd")
        str = current.format(formatter)
        result = result + " " + str + "일"

        result += "  "
        formatter = DateTimeFormatter.ofPattern("a")
        str = current.format(formatter)
        Log.d(onairTag, "오전/오후: " + str)
        result += str

        formatter = DateTimeFormatter.ofPattern("hh")
        str = current.format(formatter)
        result = result + " " + str + "시"

        formatter = DateTimeFormatter.ofPattern("mm")
        str = current.format(formatter)
        result = result + " " + str + "분"

        txt_timeView.setText(result)
        mTimeText = result

        Log.d(onairTag, "setCurrentTimeView(): " + result)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(onairTag, "onViewCreated: " + bInitialized)

        txt_timeView = view.findViewById(R.id.text_time)
        txt_addrView = view.findViewById(R.id.text_address)
        txt_skyView = view.findViewById(R.id.text_sky)
        txt_rainView = view.findViewById(R.id.text_rain)
        txt_fcstView = view.findViewById(R.id.text_fcstTime)
        txt_windView = view.findViewById(R.id.text_wind)
        txt_pmGrade = view.findViewById(R.id.txt_pmGrade)
        txt_pmValue = view.findViewById(R.id.txt_pmValue)

        img_weatherView = view.findViewById(R.id.image_empty_weather)
        img_skyView = view.findViewById(R.id.img_sky)
        img_rainView = view.findViewById(R.id.img_humidity)
        img_airStatus = view.findViewById(R.id.image_airStatus)

        btn_kbs_classic = view.findViewById(R.id.btn_kbs_classic)
        btnList.add(btn_kbs_classic)
        btn_kbs_cool = view.findViewById(R.id.btn_kbs_cool)
        btnList.add(btn_kbs_cool)
        btn_kbs_happy = view.findViewById(R.id.btn_kbs_happy)
        btnList.add(btn_kbs_happy)
        btn_kbs_1_radio = view.findViewById(R.id.btn_kbs_1_radio)
        btnList.add(btn_kbs_1_radio)
        btn_sbs_love = view.findViewById(R.id.btn_sbs_love)
        btnList.add(btn_sbs_love)
        btn_sbs_power = view.findViewById(R.id.btn_sbs_power)
        btnList.add(btn_sbs_power)
        btn_mbc_fm_foru = view.findViewById(R.id.btn_mbc_fm_foru)
        btnList.add(btn_mbc_fm_foru)
        btn_mbc_standard = view.findViewById(R.id.btn_mbc_s)
        btnList.add(btn_mbc_standard)

        if ( !bInitialized ) {
            Log.d(onairTag, "load data")
            bInitialized = true

            // 시간은 최초 1회만 ... 혹은 refresh 인 경우
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setCurrentTimeView()
            } else {
                val sdf = SimpleDateFormat("dd/M/yyyy hh:mm:ss")
                val currentDate = sdf.format(Date())
                txt_timeView.setText(currentDate)
            }

            // 최초 실행 될 때는 service 를 한 번 멈춰준다.
            stopRadioForegroundService()

            resetButtons()

            Log.d(onairTag, "request retry for " + mFailedChannelMap?.title)
            RadioChannelResources.getInstance().requestUpdateResource(mFailedChannelMap)
            val FAILED_MESSAGE = "주소 로딩 실패. 잠시만 기다려주십시오."
            setDisableButton(mFailedChannelMap, FAILED_MESSAGE)

        } else {
            Log.d(onairTag, "use previous data")

            txt_addrView.setText(mAddressText)
            txt_rainView.setText(mRainText)
            txt_windView.setText(mWindText)
            txt_skyView.setText(mTemperatureText)
            txt_fcstView.setText(mFcstTimeText)
            txt_timeView.setText(mTimeText)

            setSkyStatusImage(mSkyType)
            setRainStatusImage(mRainType)
            updateAirStatus(mPMData)

            resetButtonByChannels(playingChannelName)
        }

        mBtn_weather_refresh = view.findViewById(R.id.btn_weatherRefresh)
        mBtn_weather_refresh.setOnClickListener { onRefreshClick() }


        btn_kbs_classic.setOnClickListener { onKbsClassic() }
        btn_kbs_cool.setOnClickListener{ onKbsCool() }
        btn_kbs_happy.setOnClickListener{ onKbsHappy() }
        btn_kbs_1_radio.setOnClickListener{ onKbs1Radio() }
        btn_sbs_love.setOnClickListener{ onSbsLove() }
        btn_sbs_power.setOnClickListener{ onSbsPower() }
        btn_mbc_fm_foru.setOnClickListener{ onMbcFmForU() }
        btn_mbc_standard.setOnClickListener{ onMbcSFM() }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        Log.d(onairTag, "onCreateView")

        if ( container != null ) {
            mContext = container.context
        }

        return inflater.inflate(R.layout.fragment_onair, container, false)
    }


    /*
        1 -> return "맑음"
        3 -> return "구름 많음"
        4 -> return "흐림"
     */
    fun setSkyStatusImage(skyType: Int) {
        mSkyType = skyType
        Log.d(onairTag, "rainType code is invalid: " + skyType)

        when (skyType) {
            1 -> img_skyView.setImageResource(R.drawable.ic_sunny)
            3 -> img_skyView.setImageResource(R.drawable.ic_cloudy)
            4 -> img_skyView.setImageResource(R.drawable.ic_clouds)
            else -> Log.d(onairTag, "skyType code is invalid: " + skyType)
        }
    }

    /*
       (없음(0), 비(1), 비/눈(진눈개비)(2), 눈(3), 소나기(4), 빗방울(5), 빗방울/눈날림(6), 눈날림(7))
     */
    fun setRainStatusImage(rainType: Int){
        mRainType = rainType
        Log.d(onairTag, "rainType code is invalid: " + rainType)

        when(rainType) {
            1 -> img_rainView.setImageResource(R.drawable.ic_rain1)
            2 -> img_rainView.setImageResource(R.drawable.ic_rain2)
            3 -> img_rainView.setImageResource(R.drawable.ic_rain3)
            4 -> img_rainView.setImageResource(R.drawable.ic_rain4)
            5 -> img_rainView.setImageResource(R.drawable.ic_rain5)
            6 -> img_rainView.setImageResource(R.drawable.ic_rain6)
            7 -> img_rainView.setImageResource(R.drawable.ic_rain7)
            else -> Log.d(onairTag, "rainType code is invalid: " + rainType)
        }
    }

    fun updateAddressView(status: Boolean) {
        if ( status ) {
            Log.d(onairTag, "current_address : " + mAddressText)
            txt_addrView.setText(mAddressText)
        } else {
            Log.d(onairTag, "Address info receiving is failed")
            txt_addrView.setText("알 수 없음")
        }
    }

    fun updateAirStatus(data: PMData) {
        mPMData = data
        var grade: String = "1"
        var gradeName: String
        if ( data.pm10Grade1h!!.toInt() > grade.toInt() ) grade = data.pm10Grade1h
        if ( data.pm25Grade1h!!.toInt() > grade.toInt() ) grade = data.pm25Grade1h
        Log.d(onairTag, "worst grade: " + AirStatus.getInstance().getGradeString(grade))
        when(grade) {
            "1" -> {
                img_airStatus.setImageResource(R.drawable.skyblue_circle); gradeName = "좋음"
            }
            "2" -> {
                img_airStatus.setImageResource(R.drawable.green_circle); gradeName = "보통"
            }
            "3" -> {
                img_airStatus.setImageResource(R.drawable.orange_circle); gradeName = "나쁨"
            }
            "4" -> {
                img_airStatus.setImageResource(R.drawable.red_circle); gradeName = "매우나쁨"
            }
            else -> { Log.d(onairTag, "Can't know pm grade"); gradeName = "알수없음" }
        }
        txt_pmGrade.setText(gradeName)
        txt_pmValue.setText("초미세먼지 (" + data.pm25Value + ") / 미세먼지 (" + data.pm10Value + ")")
    }

}