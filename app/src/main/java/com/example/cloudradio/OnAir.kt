package com.example.cloudradio

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import kotlin.concurrent.timer

var onairTag = "OnAir"

val handler: Handler = @SuppressLint("HandlerLeak")
object : Handler() {
    override fun handleMessage(msg: Message) {
        Log.d(onairTag, "handler handleMessage: "+msg)
        OnAir.getInstance().resetAllButtonText()
        OnAir.getInstance().stopRadioForegroundService()
    }
}

enum class RADIO_BUTTON {
    FAILED_MESSAGE {
        override fun getMessage() = "재생 실패 잠시 후 다시 시도하여 주십시오."
    },
    STOPPED_MESSAGE {
        override fun getMessage(): String = "정지 ( 터치하여 재생 시작 )"
    },
    PLAYING_MESSAGE {
        override fun getMessage(): String = "재생중 ( 터치하여 정지 )"
    },
    KBS_CLASSIC_DEFAULT {
        override fun getMessage(): String = "KBS 클래식 FM"
    },
    KBS_COOL_DEFAULT {
        override fun getMessage(): String = "KBS COOL FM"
    },
    KBS_HAPPY_DEFAULT {
        override fun getMessage(): String = "KBS HAPPY FM"
    },
    KBS_1_RADIO_DEFAULT {
        override fun getMessage(): String = "KBS 1 라디오"
    },
    SBS_LOVE_DEFAULT {
        override fun getMessage(): String = "SBS LOVE FM"
    },
    SBS_POWER_DEFAULT {
        override fun getMessage(): String = "SBS POWER FM"
    },
    MBC_FORU_DEFAULT {
        override fun getMessage(): String = "MBC FM For U"
    },
    MBC_STANDARD_DEFAULT {
        override fun getMessage(): String = "MBC 표준 FM"
    };
    abstract fun getMessage(): String
}

class OnAir : Fragment() {

    companion object {
        var bInitialized: Boolean = false
        var btnList = HashMap<String, Button>()

        var mFailedChannelMap: RadioCompletionMap? = null

        // 현재 재생 중인 채널의 title
        var mCurrnetPlayFilename: String? = null

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
        txt_fcstView.setText("")
        MainActivity.getInstance().getGPSInfo()

        var text = "현재 지역의 날씨 정보를 업데이트합니다.\n잠시만 기다려주십시오."
        Toast.makeText(mContext, text, Toast.LENGTH_LONG).show()
    }

    @SuppressLint("SetTextI18n")
    fun updateButtonText(filename: String, text: String, enable: Boolean) {
        Log.d(onairTag, "updateButtonText $filename  $text  $enable")

        var iter = btnList.iterator()
        while( iter.hasNext() ) {
            var obj = iter.next()
            //Log.d(onairTag, "updateButtonText: "+obj.key )
            if ( obj.key.equals(filename) ) {
                //Log.d(onairTag, "updateButtonText ok")
                var button = obj.value
                button.setText(text)

                when(text) {
                    RADIO_BUTTON.PLAYING_MESSAGE.getMessage() -> button.setBackgroundColor(Color.CYAN)
                    RADIO_BUTTON.STOPPED_MESSAGE.getMessage() -> button.setBackgroundColor(Color.YELLOW)
                    RADIO_BUTTON.FAILED_MESSAGE.getMessage() -> button.setBackgroundColor(Color.RED)
                    else -> button.setBackgroundColor(Color.WHITE)
                }

                button.isEnabled = enable
                break
            }
        }
    }

    fun getDefaultTextByFilename(filename: String): String {
        when(filename) {
            RadioRawChannels.MBC_STANDARD_FM.getChannelFilename() -> return "MBC 표준 FM"
            RadioRawChannels.MBC_FM_FORU.getChannelFilename() -> return "MBC FM For U"
            RadioRawChannels.SBS_POWER_FM.getChannelFilename() -> return "SBS POWER FM"
            RadioRawChannels.SBS_LOVE_FM.getChannelFilename() -> return "SBS LOVE FM"
            RadioRawChannels.KBS_1_RADIO.getChannelFilename() -> return "KBS 1 라디오"
            RadioRawChannels.KBS_HAPPY_FM.getChannelFilename() -> return "KBS HAPPY FM"
            RadioRawChannels.KBS_COOL_FM.getChannelFilename() -> return  "KBS COOL FM"
            RadioRawChannels.KBS_CLASSIC_FM.getChannelFilename() -> return "KBS 클래식 FM"
            else -> return "Unknown"
        }
    }

    fun resetAllButtonText() {
        Log.d(onairTag, "resetButtons()")
        var iter = btnList.iterator()
        while( iter.hasNext() ) {
            var obj = iter.next()
            var message = getDefaultTextByFilename(obj.key)
            updateButtonText(obj.key, message, true)
        }
    }

    // 라디오 버튼 누르면 모두 여기서 처리
    // 재생 중이면,
    //   - 어떤 채널에서 재생 중인지 확인 후 동일 채널이면 중지
    //   - 동일 채널이 아닌 경우엔, 해당 채널 스탑 후 내 채널 재생 시작
    // 재생 중이 아니라면
    //   - 내 채널 재생 시작
    fun onRadioButton(filename: String) {
        // 현재 요청한 채널이 이전 채널과 다르면 우선 버튼 텍스트 초기화
        if ( !filename.equals(mCurrnetPlayFilename) ) {
            resetAllButtonText()
        }
        // 재생 중인 경우 callback 을 받아서 처리한다.
        //  1. 서비스 중지
        //  2. 현재 요청 채널이름 저장
        //  3. callback 받고
        //  4. callback 의 채널과 현재 요청 채널 비교
        //     - 같으면 => 그냥 있음
        //     - 다르면 => 요청한 채널로 서비스 시작 (이거 1회 더 불러주면 됨)
        if ( RadioPlayer.isPlaying() ) {
            // 중지 후 처리 시작
            Log.d(onairTag, "stop previous service")
            stopRadioForegroundService()

            // 요청한 채널 저장
            Log.d(onairTag, "save request service name: "+filename)
            mCurrnetPlayFilename = filename
        } else {
            startRadioForegroundService(filename)
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

        Log.d(onairTag, "btnList size: "+btnList.size)


        if ( !bInitialized ) {
            Log.d(onairTag, "initial called")
            bInitialized = true

            // 시간은 최초 1회만 ... 혹은 refresh 인 경우
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setCurrentTimeView()
            } else {
                val sdf = SimpleDateFormat("dd/M/yyyy hh:mm:ss")
                val currentDate = sdf.format(Date())
                txt_timeView.setText(currentDate)
            }

            resetAllButtonText()

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

            Log.d(onairTag, "mCurrnetPlayFilename: "+mCurrnetPlayFilename)

            resetAllButtonText()
            mCurrnetPlayFilename?.let { updateButtonText(it, RADIO_BUTTON.PLAYING_MESSAGE.getMessage(), true) }
        }

        mBtn_weather_refresh = view.findViewById(R.id.btn_weatherRefresh)
        mBtn_weather_refresh.setOnClickListener { onRefreshClick() }


        btn_kbs_classic.setOnClickListener { onRadioButton(RadioRawChannels.KBS_CLASSIC_FM.getChannelFilename()) }
        btn_kbs_cool.setOnClickListener{ onRadioButton(RadioRawChannels.KBS_COOL_FM.getChannelFilename()) }
        btn_kbs_happy.setOnClickListener{ onRadioButton(RadioRawChannels.KBS_HAPPY_FM.getChannelFilename()) }
        btn_kbs_1_radio.setOnClickListener{ onRadioButton(RadioRawChannels.KBS_1_RADIO.getChannelFilename()) }
        btn_sbs_love.setOnClickListener{ onRadioButton(RadioRawChannels.SBS_LOVE_FM.getChannelFilename()) }
        btn_sbs_power.setOnClickListener{ onRadioButton(RadioRawChannels.SBS_POWER_FM.getChannelFilename()) }
        btn_mbc_fm_foru.setOnClickListener{ onRadioButton(RadioRawChannels.MBC_FM_FORU.getChannelFilename()) }
        btn_mbc_standard.setOnClickListener{ onRadioButton(RadioRawChannels.MBC_STANDARD_FM.getChannelFilename()) }
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


    private fun getRadioChannelHttpAddress(filename: String): String? {
        var idx = 0
        for(i in RadioChannelResources.channelList.indices) {
            if ( RadioChannelResources.channelList.get(i).filename.equals(filename) ) {
                idx =  i
                return  RadioChannelResources.channelList.get(idx).httpAddress
            }
        }
        return null
    }

    /**
     * Service
     */
    fun startRadioForegroundService(filename: String) {
        var address:String? = getRadioChannelHttpAddress(filename)

        Intent(mContext, RadioService::class.java).run {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
                var intent = Intent(mContext, RadioService::class.java)
                intent.putExtra("name", filename)
                intent.putExtra("address", address)
                intent.setAction(Constants.ACTION.STARTFOREGROUND_ACTION)
                startForegroundService(mContext, intent)
            }
            else {
                var activity = activity
                var intent = Intent(mContext, RadioService::class.java)
                intent.setAction(Constants.ACTION.STARTFOREGROUND_ACTION)
                activity?.startService(intent)
            }
        }
    }

    fun stopRadioForegroundService() {
        var activity = activity
        Intent(mContext, RadioService::class.java).run {
            var intent = Intent(mContext, RadioService::class.java)
            intent.setAction(Constants.ACTION.STOPFOREGROUND_ACTION)
            activity?.stopService(intent)
        }
    }

    // callback
    //  4. callback 의 채널과 현재 요청 채널 비교
    //     - 같으면 => 그냥 있음
    //     - 다르면 => 요청한 채널로 서비스 시작 (이거 1회 더 불러주면 됨)
    fun notifyRadioServiceStatus(filename: String, result: RESULT) {
        Log.d(onairTag, "notifyRadioServiceStatus: " + result + ", filename: "+filename)

        when(result) {
            RESULT.PLAY_SUCCESS -> {
                resetAllButtonText()
                updateButtonText(filename, RADIO_BUTTON.PLAYING_MESSAGE.getMessage(), true)
            }
            RESULT.PLAY_FAILED -> {
                RadioChannelResources.getInstance().requestUpdateResource(filename)
                // 우선 이전 채널에 대해서 초기화 (없으면 null 체크하여 실행 x)
                mCurrnetPlayFilename?.let { updateButtonText(it, getDefaultTextByFilename(mCurrnetPlayFilename!!), true) }

                // 요청된 채널은 실패, disable 처리 -> updateResource callback 으로 초기화됨
                updateButtonText(filename, RADIO_BUTTON.FAILED_MESSAGE.getMessage(), false)
            }
            RESULT.DESTROYED -> {
                // 중지된 filename 과 요청된 filename 이 같으면 button text 만 update
                if ( mCurrnetPlayFilename.equals(filename) ) {
                    resetAllButtonText()
                    updateButtonText(filename, RADIO_BUTTON.STOPPED_MESSAGE.getMessage(), true)
                }
                // 서로 filename 이 다르면 요청된 filename 서비스 시작
                else {
                    mCurrnetPlayFilename?.let { onRadioButton(it) }
                }
            }
        }
    }

    // success 는 모두 성공 시에만 callback 이 불림
    fun notifyRadioResourceUpdate(filename: String?, result: RadioResource) {
        Log.d(onairTag, "resource update result: "+result + ", filename: "+filename)
        when(result) {
            RadioResource.SUCCESS -> {}
            RadioResource.OPEN_FAILED, RadioResource.DOWN_FAILED -> {
                if (!bInitialized) {
                    Log.d(onairTag, "Ignore failed (not initialized)")
                    return
                }

                Log.d(onairTag, "timer ~")
                var msg = handler.obtainMessage()
                val timer = timer(initialDelay = 3000, period = 10000) {
                    handler.sendMessage(msg)
                    cancel()
                }
            }
        }
    }
}