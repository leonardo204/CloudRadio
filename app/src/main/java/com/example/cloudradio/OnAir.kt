package com.example.cloudradio

import android.content.Context
import android.icu.text.DecimalFormat
import android.icu.text.NumberFormat
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Logger
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*


var onairTag = "OnAir"






class OnAir : Fragment() {

    private var bInitialized: Boolean = false
    private lateinit var mContext: Context

    companion object {

        // request wether (fixed)
        val num_of_rows = 10
        val page_no = 1
        val data_type = "JSON"


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

        var mRainType: Int = 0
        var mSkyType: Int = 0
        var mTemperatureText: String = "N/A"
        var mWindText: String = "N/A"
        var mRainText: String = "N/A"
        var mFcstTimeText: String = "N/A"
    }

    private fun onRefreshClick() {
        Log.d(onairTag, "information refresh")
        setCurrentTimeView()
        bInitialized = true
        txt_fcstView.setText("")
        MainActivity.getInstance().getGPSInfo()

        var text = "현재 지역의 날씨 정보를 업데이트합니다.\n잠시만 기다려주십시오."
        Toast.makeText(mContext, text, Toast.LENGTH_LONG).show()
    }

    @Suppress("DEPRECATION")
    private fun onClickPlay() {
        Log.d(onairTag, "onClickPlay: ")
        val url: Uri = Uri.parse("android.resource://" + mContext.getPackageName().toString() + "/" + R.raw.kbs_classic_fm )
        var fileText = FileIO.fileRead(mContext, url)
        Log.d(onairTag, "fileText: "+fileText)

        var urlText = "https://1fm.gscdn.kbs.co.kr/1fm_192_1.m3u8?Expires=1616669502&Policy=eyJTdGF0ZW1lbnQiOlt7IlJlc291cmNlIjoiaHR0cHM6Ly8xZm0uZ3NjZG4ua2JzLmNvLmtyLzFmbV8xOTJfMS5tM3U4IiwiQ29uZGl0aW9uIjp7IkRhdGVMZXNzVGhhbiI6eyJBV1M6RXBvY2hUaW1lIjoxNjE2NjY5NTAyfX19XX0_&Signature=YErRYtA6MoVFSv8fJNvO7hIFeToA6jJP9nRSR2haXmE0N9hRePfdbRaORW1d6ntAT8PwlR70z2OPNffbXJq1HJsTnOnCHWSN7SMEloh0YftRbww5heRg3DpPIbeHGW-t9jW4-8vyPCjh4UB5ejajP7000sVFcKdTL2-DckYEToqnMPXGSBQ5A3IVZYpazkgBQeZny1IbXjU9SPp3C7XkC6MY-mVvT2IK7VQW7j9RXqqgpmq1RZDZYcOdJjxy2vKlVKebgC58qI~fqSApU298rZmdcBzjK1UCLmk2Nzy5ohCVCX6nRfFRlsEV7nUrvM8ykbDFehQBGF9TIGTxrbdnYQ__&Key-Pair-Id=APKAICDSGT3Y7IXGJ3TA"
        val mediaPlayer: MediaPlayer? = MediaPlayer().apply {
            setAudioStreamType(AudioManager.STREAM_MUSIC)
            setDataSource(urlText)
            prepare() // 오랜 시간이 걸릴 수도 있습니다! (buffering 혹은 기타 등등)
            start()
        }

    }

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
        if (str.equals("am")) {
            result += "오전"
        }
        else {
            result += "오후"
        }

        formatter = DateTimeFormatter.ofPattern("hh")
        str = current.format(formatter)
        result = result + " " + str + "시"

        formatter = DateTimeFormatter.ofPattern("mm")
        str = current.format(formatter)
        result = result + " " + str + "분"


        txt_timeView.setText(result)

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

        img_weatherView = view.findViewById(R.id.image_empty_weather)
        img_skyView = view.findViewById(R.id.img_sky)
        img_rainView = view.findViewById(R.id.img_humidity)

        setCurrentTimeView()

        if ( bInitialized != true ) {
            Log.d(onairTag, "load data")
            bInitialized = true
        } else {
            Log.d(onairTag, "use previous data")

            txt_addrView.setText(mAddressText)
            txt_rainView.setText(mRainText)
            txt_windView.setText(mWindText)
            txt_skyView.setText(mTemperatureText)
            txt_fcstView.setText(mFcstTimeText)

            setSkyStatusImage(mSkyType)
            setRainStatusImage(mRainType)
        }

        mBtn_weather_refresh = view.findViewById(R.id.btn_weatherRefresh)
        mBtn_weather_refresh.setOnClickListener { onRefreshClick() }


        var btn_play: Button = view.findViewById(R.id.btn_play)
        btn_play.setOnClickListener { onClickPlay() }
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

    fun updateAddressView() {
        Log.d(onairTag, "current_address : " + mAddressText)
        txt_addrView.setText(mAddressText)
    }

}

