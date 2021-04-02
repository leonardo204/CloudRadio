package com.example.cloudradio

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.*
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat.startForegroundService
import androidx.fragment.app.Fragment
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.concurrent.timer


var onairTag = "CR_OnAir"

val handler: Handler = @SuppressLint("HandlerLeak")
object : Handler() {
    override fun handleMessage(msg: Message) {
        Log.d(onairTag, "handler handleMessage: " + msg)
        OnAir.getInstance().resetAllButtonText(true)
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
    };
    abstract fun getMessage(): String
}

class OnAir : Fragment() {

    companion object {
        var bInitialized: Boolean = false
        var btnList = HashMap<String, Button>()

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

        lateinit var layout: LinearLayout
        var youtubeView: com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView? = null

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

    // filename 들을 담은 array list 가 전달됨
    // array list 로부터 filename 에 해당하는 버튼을 동적 생성
    fun makePrograms(favList: ArrayList<String>) {
        resetPrograms()

        var iter = favList.iterator()
        while( iter.hasNext() ) {
            var filename = iter.next()
            Log.d(onairTag, "makePrograms:" + filename)
            var btn = Button(mContext)
            btnList.put(filename, btn)
            btn.setOnClickListener { onRadioButton(filename) }
            layout.addView(btn)
            updateButtonText(
                filename,
                Program.getInstance().getDefaultTextByFilename(filename),
                true
            )
        }
    }

    fun resetPrograms() {
        btnList.clear()
        stopRadioForegroundService()
        layout.removeAllViews()
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

                when(text) {
                    RADIO_BUTTON.PLAYING_MESSAGE.getMessage() -> {
                        button.setBackgroundColor(Color.CYAN)
                        button.setText(RadioChannelResources.getDefaultButtonTextByFilename(filename) + " : " + text)
                    }
                    RADIO_BUTTON.STOPPED_MESSAGE.getMessage() -> {
                        button.setBackgroundColor(Color.YELLOW)
                        button.setText(RadioChannelResources.getDefaultButtonTextByFilename(filename) + " : " + text)
                    }
                    RADIO_BUTTON.FAILED_MESSAGE.getMessage() -> {
                        button.setBackgroundColor(Color.RED)
                        button.setText(RadioChannelResources.getDefaultButtonTextByFilename(filename) + " : " + text)
                    }
                    else -> {
                        button.setBackgroundColor(Color.WHITE)
                        button.setText(text)
                    }
                }

                button.isEnabled = enable
                break
            }
        }
    }

    fun resetAllButtonText(enable: Boolean) {
        Log.d(onairTag, "resetAllButtonText()")
        var iter = btnList.iterator()
        while( iter.hasNext() ) {
            var obj = iter.next()
            Log.d(onairTag, "filename: " + obj.key)
            var message = Program.getInstance().getDefaultTextByFilename(obj.key)
            updateButtonText(obj.key, message, enable)
        }
    }

    private fun createYoutubeView(filename: String, videoId: String) {
        Log.d(
            onairTag,
            "createYoutubeView prev(${mCurrnetPlayFilename}) - cur($filename)  $videoId"
        )

        resetAllButtonText(true)
        stopRadioForegroundService()

        // 이전 유튜브가 재생 중인 경우 우선 유튜브 재생을 중지
        if ( !filename.equals(mCurrnetPlayFilename) ) {
            Log.d(onairTag, "createYoutubeView  ----------------  stop!!!!!!!!!!")

            playStopYoutube("", null, false)
        }
        mCurrnetPlayFilename = filename

        if ( YoutubeHandler.isPlay ) {
            Log.d(onairTag, "createYoutubeView  ----------------  stop!!!!!!!!!!")

            playStopYoutube(filename, videoId, false)

            updateButtonText(
                filename,
                Program.getInstance().getDefaultTextByFilename(filename),
                true
            )
        } else {
            Log.d(onairTag, "createYoutubeView  ----------------- play!!!!!!!!!!")

            playStopYoutube(filename, videoId, true)

            updateButtonText(filename, RADIO_BUTTON.PLAYING_MESSAGE.getMessage(), true)
        }
    }

    // false 인 경우 vid 는 null 로 들어옴
    private fun playStopYoutube(filename: String, videoId: String?, play: Boolean) {
        if ( play ) {
            youtubeView = YouTubePlayerView(mContext)

            // ui
            var uiController = youtubeView?.getPlayerUiController()
            uiController?.showCurrentTime(false)
            uiController?.showFullscreenButton(false)
            uiController?.showPlayPauseButton(false)
            uiController?.showSeekBar(false)
            uiController?.showSeekBar(false)
            uiController?.showVideoTitle(false)
            uiController?.showDuration(false)
            uiController?.showUi(false)
            uiController?.showYouTubeButton(false)

            layout.addView(youtubeView)
            startRadioForegroundService("youtube", filename, videoId!!)
        } else {
            YoutubeHandler.isPlay = false
            layout.removeView(youtubeView)
            youtubeView = null
            stopRadioForegroundService()
        }
    }

    private var mLastClickTime: Long = 0
    private var mMinClickAllowTime: Long = 1500

    private fun checkClickInvalid(): Boolean {
        if (SystemClock.elapsedRealtime() - mLastClickTime < mMinClickAllowTime){
            return true
        }
        mLastClickTime = SystemClock.elapsedRealtime();
        return false
    }

    // 라디오 버튼 누르면 모두 여기서 처리
    // 재생 중이면,
    //   - 어떤 채널에서 재생 중인지 확인 후 동일 채널이면 중지
    //   - 동일 채널이 아닌 경우엔, 해당 채널 스탑 후 내 채널 재생 시작
    // 재생 중이 아니라면
    //   - 내 채널 재생 시작
    fun onRadioButton(filename: String) {
        Log.d(onairTag, "onRadioButton!  $filename")

        if ( checkClickInvalid() ) {
            Log.d(onairTag, "ignore click event")
            return
        }

        // youtube 요청인 경우
        if ( filename.contains("youtube") ) {
            var videoId = filename.substring(filename.indexOf("youtube_") + 8)
            Log.d(onairTag, "videoId: " + videoId)
            createYoutubeView(filename, videoId)
            return
        }
        // radio 요청을 하였는데, youtube 실행 중인 경우 youtube 중지
        else if ( YoutubeHandler.isPlay ) {
            playStopYoutube(filename, null, false)
        }

        // 현재 요청한 채널이 이전 채널과 다르면 우선 버튼 텍스트 초기화
        if ( !filename.equals(mCurrnetPlayFilename) ) {
            resetAllButtonText(true)
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
            Log.d(onairTag, "save request service name: " + filename)
            mCurrnetPlayFilename = filename
        } else {
            startRadioForegroundService("radio", filename, null)
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

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(onairTag, "OnAir onCreateView")

        if ( container != null ) {
            mContext = container.context
        }

        var view: ViewGroup = inflater.inflate(R.layout.fragment_onair, container, false) as ViewGroup

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

        mBtn_weather_refresh = view.findViewById(R.id.btn_weatherRefresh)
        mBtn_weather_refresh.setOnClickListener { onRefreshClick() }

        layout = view.findViewById(R.id.layout_radio_linear)

        if ( !bInitialized ) {
            // 시간은 최초 1회만 ... 혹은 refresh 인 경우
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                setCurrentTimeView()
            } else {
                val sdf = SimpleDateFormat("dd/M/yyyy hh:mm:ss")
                val currentDate = sdf.format(Date())
                txt_timeView.setText(currentDate)
            }
            bInitialized = true
        } else {
            if ( mPMData != null ) {
                txt_addrView.setText(mAddressText)
                txt_rainView.setText(mRainText)
                txt_windView.setText(mWindText)
                txt_skyView.setText(mTemperatureText)
                txt_fcstView.setText(mFcstTimeText)
                txt_timeView.setText(mTimeText)

                setSkyStatusImage(mSkyType)
                setRainStatusImage(mRainType)
                updateAirStatus(mPMData)
            } else {
                onRefreshClick()
            }
        }

        makePrograms(Program.favList)

        Log.d(onairTag, "mCurrnetPlayFilename: " + mCurrnetPlayFilename)
        resetAllButtonText(true)
        mCurrnetPlayFilename?.let { updateButtonText(
            it,
            RADIO_BUTTON.PLAYING_MESSAGE.getMessage(),
            true
        ) }

        return view
    }

    override fun onStart() {
        super.onStart()
        Log.d(onairTag, "onStart")
    }

    override fun onStop() {
        super.onStop()
        Log.d(onairTag, "onStop")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(onairTag, "onDestroyView")
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
        Log.d(onairTag, "worst grade: " + AirStatus.getGradeString(grade))
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
        // radio
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
    private fun startRadioForegroundService(serviceName: String, filename: String, videoId: String?) {
        var address:String? = null
        if ( serviceName.equals("radio") ) {
            address = getRadioChannelHttpAddress(filename)
        }

        Intent(mContext, RadioService::class.java).run {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
                var intent = Intent(mContext, RadioService::class.java)
                intent.putExtra("serviceName", serviceName)
                intent.putExtra("videoId", videoId)
                intent.putExtra("name", filename)
                intent.putExtra("address", address)
                intent.setAction(Constants.ACTION.STARTFOREGROUND_ACTION)
                startForegroundService(mContext, intent)
            }
            else {
                var intent = Intent(mContext, RadioService::class.java)
                intent.setAction(Constants.ACTION.STARTFOREGROUND_ACTION)
                mContext.startService(intent)
            }
        }
    }

    fun stopRadioForegroundService() {
        var intent = Intent(mContext, RadioService::class.java)
        intent.setAction(Constants.ACTION.STOPFOREGROUND_ACTION)
        mContext.stopService(intent)
    }

    // callback
    //  4. callback 의 채널과 현재 요청 채널 비교
    //     - 같으면 => 그냥 있음
    //     - 다르면 => 요청한 채널로 서비스 시작 (이거 1회 더 불러주면 됨)
    fun notifyRadioServiceStatus(filename: String, result: RESULT) {
        Log.d(
            onairTag,
            "notifyRadioServiceStatus: " + result + ", filename: " + filename + " - mCurrnetPlayFilename: $mCurrnetPlayFilename"
        )

        when(result) {
            RESULT.PLAY_SUCCESS -> {
                resetAllButtonText(true)
                updateButtonText(filename, RADIO_BUTTON.PLAYING_MESSAGE.getMessage(), true)
            }
            RESULT.PLAY_FAILED -> {

                if (!filename.contains("youtube")) {
                    RadioChannelResources.requestUpdateResource(filename)
                } else {
                    Log.d(onairTag, "timer ~")
                    var msg = handler.obtainMessage()
                    timer(initialDelay = 3000, period = 10000) {
                        handler.sendMessage(msg)
                        cancel()
                    }
                }
                // 우선 이전 채널에 대해서 초기화 (없으면 null 체크하여 실행 x)
                mCurrnetPlayFilename?.let {
                    updateButtonText(
                        it, Program.getInstance().getDefaultTextByFilename(
                            mCurrnetPlayFilename!!
                        ), true
                    )
                }

                // 요청된 채널은 실패, disable 처리 -> updateResource callback 으로 초기화됨
                resetAllButtonText(false)
                updateButtonText(filename, RADIO_BUTTON.FAILED_MESSAGE.getMessage(), false)
            }
            RESULT.DESTROYED -> {
                // 중지된 filename 과 요청된 filename 이 같으면 button text 만 update
                if (filename.equals(mCurrnetPlayFilename)) {
                    resetAllButtonText(true)
                    updateButtonText(filename, RADIO_BUTTON.STOPPED_MESSAGE.getMessage(), true)
                }
                // 서로 filename 이 다르면 요청된 filename 서비스 시작
                else if (mCurrnetPlayFilename != null) {
                    if (!mCurrnetPlayFilename!!.contains("youtube")) mCurrnetPlayFilename?.let {
                        onRadioButton(
                            it
                        )
                    }
                }
            }
        }
    }

    // success 는 모두 성공 시에만 callback 이 불림
    fun notifyRadioResourceUpdate(filename: String?, result: RadioResource) {
        Log.d(onairTag, "resource update result: " + result + ", filename: " + filename)
        when(result) {
            RadioResource.SUCCESS -> {
                var msg = handler.obtainMessage()
                timer(initialDelay = 3000, period = 10000) {
                    handler.sendMessage(msg)
                    cancel()
                }
            }
            RadioResource.OPEN_FAILED, RadioResource.DOWN_FAILED -> {
                if (!bInitialized) {
                    Log.d(onairTag, "Ignore failed (not initialized)")
                    return
                }

                Log.d(onairTag, "timer ~")
                var msg = handler.obtainMessage()
                timer(initialDelay = 3000, period = 10000) {
                    handler.sendMessage(msg)
                    cancel()
                }
            }
        }
    }
}