package com.example.cloudradio

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
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

class OnAir : Fragment() {

    private var bInitialized: Boolean = false

    companion object {

        // request wether (fixed)
        val num_of_rows = 10
        val page_no = 1
        val data_type = "JSON"
        lateinit var mContext: Context


        var mAddressText: String = "N/A"

        private var instance: OnAir? = null

        fun getInstance(): OnAir =
                instance ?: synchronized(this) {
                    instance ?: OnAir().also {
                        instance = it
                    }
                }

        lateinit var mBtn_weather_refresh: ImageButton

        lateinit var txt_timeView: TextView
        lateinit var txt_addrView: TextView
        lateinit var txt_skyView: TextView
        lateinit var txt_windView: TextView
        lateinit var txt_rainView: TextView
        lateinit var txt_fcstView: TextView

        lateinit var img_skyView: ImageView
        lateinit var img_rainView: ImageView
        lateinit var img_weatherView: ImageView

        lateinit var img_airStatus: ImageView
        lateinit var txt_pmValue: TextView
        lateinit var txt_pmGrade: TextView

        lateinit var btn_play: Button

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

    private fun onClickPlayStop() {
        if ( RadioPlayer.isPlaying() ) {
            Log.d(onairTag, "stop")
            stopRadioForegroundService()
            btn_play.setText("Stopped ( Touch to play )")
        } else {
            Log.d(onairTag, "play")
            startRadioForegroundService()
            btn_play.setText("Playing ( Touch to stop )")
        }
    }

    /*
     Service
    */
    fun startRadioForegroundService() {
        Intent(mContext, RadioService::class.java).run {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) startForegroundService(mContext, Intent(mContext, RadioService::class.java))
            else requireActivity().startService(Intent(mContext, RadioService::class.java))
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
        Log.d(onairTag, "오전/오후: "+str)
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
        Log.d(onairTag, "onViewCreated")

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

        btn_play = view.findViewById(R.id.btn_play)

        if ( bInitialized != true ) {
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
        }

        mBtn_weather_refresh = view.findViewById(R.id.btn_weatherRefresh)
        mBtn_weather_refresh.setOnClickListener { onRefreshClick() }


        var btn_play: Button = view.findViewById(R.id.btn_play)
        btn_play.setOnClickListener { onClickPlayStop() }
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
        if ( data.pm10Grade1h.toInt() > grade.toInt() ) grade = data.pm10Grade1h
        if ( data.pm25Grade1h.toInt() > grade.toInt() ) grade = data.pm25Grade1h
        Log.d(onairTag, "worst grade: "+AirStatus.getInstance().getGradeString(grade))
        when(grade) {
            "1" -> { img_airStatus.setImageResource(R.drawable.skyblue_circle); gradeName = "좋음" }
            "2" -> { img_airStatus.setImageResource(R.drawable.green_circle); gradeName = "보통" }
            "3" -> { img_airStatus.setImageResource(R.drawable.orange_circle); gradeName = "나쁨" }
            "4" -> { img_airStatus.setImageResource(R.drawable.red_circle); gradeName = "매우나쁨" }
            else -> { Log.d(onairTag, "Can't know pm grade"); gradeName = "알수없음" }
        }
        txt_pmGrade.setText(gradeName)
        txt_pmValue.setText("초미세먼지 ("+data.pm25Value+") / 미세먼지 ("+data.pm10Value+")")
    }

}