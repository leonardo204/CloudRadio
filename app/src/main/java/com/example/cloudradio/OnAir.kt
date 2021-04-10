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
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.HashMap
import kotlin.concurrent.timer


var onairTag = "CR_OnAir"

val handler: Handler = @SuppressLint("HandlerLeak")
object : Handler() {
    override fun handleMessage(msg: Message) {
        Log.d(onairTag, "handler handleMessage: " + msg)

        OnAir.stopRadioForegroundService()
        OnAir.resetAllButtonText(true)

        val bundle = msg.data
        val command = bundle.getString("command")

        when(command) {
            "RadioResource.SUCCESS" -> {
                OnAir.updateFavoriteList()
            }
        }
    }
}

enum class CLICK_TYPE {
    CLICK, CALLBACK
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

@SuppressLint("StaticFieldLeak")
object OnAir : Fragment() {

    var bInitialized: Boolean = false
    var onair_btnList = HashMap<String, Button>()

    // 현재 재생 중인 채널의 title
    var mCurrnetPlayFilename: String? = null

    // request wether (fixed)
    val num_of_rows = 10
    val page_no = 1
    val data_type = "JSON"
    var mContext: Context? = null

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

    var program_layout: LinearLayout? = null
    var youtubePlayer: YouTubePlayer? = null
    var youtubeState: PlayerConstants.PlayerState? = PlayerConstants.PlayerState.UNKNOWN
    var mVideoId: String? = null

    var mAddressText: String = "N/A"

    var mRainType: Int = 0
    var mSkyType: Int = 0
    var mTemperatureText: String = "N/A"
    var mWindText: String = "N/A"
    var mRainText: String = "N/A"
    var mFcstTimeText: String = "N/A"
    var mTimeText:String = "N/A"
    var mPMData: PMData? = null

    var DEFAULT_FILE_PATH: String? = null
    var FAVORITE_CHANNEL_JSON = "savedFavoriteChannels.json"

    lateinit var txt_loading: TextView

    // fragment 가 다시 시작될 때 button list hashmap 에서 button 들 복구해준다.
    fun restoreButtons() {
        program_layout?.removeAllViews()

        val iter = onair_btnList.iterator()
        while( iter.hasNext() ) {
            val obj = iter.next()
            val filename = obj.key
            val btn = obj.value

            Log.d(onairTag, "restoreButton: ${filename}")
            val parent = btn.parent as ViewGroup
            parent.removeView(btn)
            Log.d(onairTag, "parent: $parent - $program_layout")
            btn.setOnClickListener { onRadioButton(filename, CLICK_TYPE.CLICK) }
            program_layout?.addView(btn)
            updateButtonText( filename, Program.getDefaultTextByFilename(filename),true )
        }
    }

    // read favorite list from file
    fun updateFavoriteList() {
        Log.d(onairTag, "updateFavoriteList")

        var parent = txt_loading.parent as ViewGroup?
        parent?.removeView(txt_loading)

        var fileObj = File(DEFAULT_FILE_PATH+ FAVORITE_CHANNEL_JSON)
        if ( !fileObj.exists() && !fileObj.canRead() ) {
            Log.d(onairTag, "Can't load ${DEFAULT_FILE_PATH+FAVORITE_CHANNEL_JSON}")
            Toast.makeText(mContext, "즐겨찾기가 없습니다.", Toast.LENGTH_LONG).show()
            return
        }

        var ins = fileObj.inputStream()
        var content = ins.readBytes().toString(Charset.defaultCharset())
        var list = ArrayList<String>()

        var ele = Json.parseToJsonElement(content)
        Log.d(onairTag, "updateFavoriteList size: ${ele.jsonArray.size}")

        for(i in 0..ele.jsonArray.size-1) {
            var filename = ele.jsonArray[i].jsonObject["filename"].toString().replace("\"","")
            Log.d(onairTag, "updateFavoriteList: $filename")
            list.add(filename)
        }

        makePrograms(list)
        Program.updatePrograms(list)
        Toast.makeText(mContext, "즐겨찾기 로딩이 성공적으로 완료되었습니다.", Toast.LENGTH_LONG).show()
    }

    private fun loadFavoriteList() {
        makePrograms(Program.favList)
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
            onair_btnList.put(filename, btn)
            btn.setOnClickListener { onRadioButton(filename, CLICK_TYPE.CLICK) }
            program_layout?.addView(btn)
            updateButtonText(
                filename,
                Program.getDefaultTextByFilename(filename),
                true
            )
        }
    }

    fun resetPrograms() {
        onair_btnList.clear()
        stopRadioForegroundService()
        program_layout?.removeAllViews()
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

        var iter = onair_btnList.iterator()
        while( iter.hasNext() ) {
            var obj = iter.next()
            //Log.d(onairTag, "updateButtonText key(${obj.key}) - filename(${filename})" )
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
        var iter = onair_btnList.iterator()
        while( iter.hasNext() ) {
            var obj = iter.next()
            Log.d(onairTag, "filename: " + obj.key)
            var message = Program.getDefaultTextByFilename(obj.key)
            updateButtonText(obj.key, message, enable)
        }
    }

    @JvmName("setYoutubeState1")
    fun setYoutubeState(state: PlayerConstants.PlayerState) {
        Log.d(onairTag, "setYoutubeState $state")
        youtubeState = state
        when(state) {
            PlayerConstants.PlayerState.UNKNOWN -> {
                Log.d(onairTag, "state UNKNOWN")
            }
            PlayerConstants.PlayerState.ENDED -> {
                Log.d(onairTag, "state ENDED")

                if ( mVideoId != null ) {
                    Log.d(onairTag, "Auto re-play")
                    youtubePlayer?.loadVideo(mVideoId!!, 0.0f)
                }
            }
            PlayerConstants.PlayerState.PLAYING -> {
                Log.d(onairTag, "state PLAYING")
                mCurrnetPlayFilename?.let { updateButtonText(it, RADIO_BUTTON.PLAYING_MESSAGE.getMessage(), true) }
            }
            PlayerConstants.PlayerState.BUFFERING -> {
                Log.d(onairTag, "state BUFFERING")
            }
            PlayerConstants.PlayerState.PAUSED -> {
                Log.d(onairTag, "state PAUSED")
            }
            PlayerConstants.PlayerState.UNSTARTED -> {
                Log.d(onairTag, "state UNSTARTED")
            }
            PlayerConstants.PlayerState.VIDEO_CUED -> {
                Log.d(onairTag, "state VIDEO_CUED")
            }
            else -> { Log.d(onairTag, "state unknown")}
        }
    }

    // - YoutubeView 는 fragment 시작 시 동적 생성해서 가지고 있도록 (static)
    // - YoutubeView 에서 YoutubeHandler 를 addlistener 붙이고 onReady 를 받으면 player 를 얻을 수 있다
    // - player 를 얻어서 RadioService 에서 videoId 에 따라서 cueVideo 를 불러서 넣어주고 play 해주면 된다
    // - view 의 UI 중에 재생 컨트롤은 풀어서 일시정지 가능하도록..
    // - 나중에 view 를 floating 으로 띄우는 방법도 생각해보자.
    private fun createYoutubeView(filename: String, videoId: String) {
        Log.d( onairTag,"createYoutubeView prev(${mCurrnetPlayFilename}) - cur($filename)  $videoId" )

        resetAllButtonText(true)
        stopRadioForegroundService()

        mCurrnetPlayFilename = filename

        // 이전 유튜브가 재생 중인 경우 우선 유튜브 재생을 중지
        if ( mCurrnetPlayFilename != null && !filename.equals(mCurrnetPlayFilename) ) {
            Log.d(onairTag, "createYoutubeView  ----------------  prev stop!!!!!!!!!!")

            playStopYoutube("", null, false)
            return
        }

        if ( youtubeState == PlayerConstants.PlayerState.PLAYING ) {
            Log.d(onairTag, "createYoutubeView  ----------------  stop!!!!!!!!!!")

            playStopYoutube(filename, videoId, false)

            mVideoId = null

            updateButtonText(
                    filename,
                    Program.getDefaultTextByFilename(filename),
                    true
            )
        } else {
            Log.d(onairTag, "createYoutubeView  ----------------- play!!!!!!!!!!")

            playStopYoutube(filename, videoId, true)

            mVideoId = videoId

            updateButtonText(filename, RADIO_BUTTON.PLAYING_MESSAGE.getMessage(), true)
        }
    }

    // false 인 경우 vid 는 null 로 들어옴
    fun playStopYoutube(filename: String, videoId: String?, play: Boolean) {
        Log.d(onairTag, "youtubeView: ${MainActivity.youtubeView}")

        if ( play ) {
            val parent = MainActivity.youtubeView!!.parent as ViewGroup?
            parent?.removeView(MainActivity.youtubeView)
            program_layout?.addView(MainActivity.youtubeView)
            startRadioForegroundService("youtube", filename, videoId!!)
        } else {
            val parent = MainActivity.youtubeView!!.parent as ViewGroup?
            parent?.removeView(MainActivity.youtubeView)
            program_layout?.removeView(MainActivity.youtubeView)
            stopRadioForegroundService()
        }
        Log.d(onairTag, "playStopYoutube: $play")
    }

    private var mLastClickTime: Long = 0
    private var mMinClickAllowTime: Long = 1500

    private fun checkClickInvalid(clicktype: CLICK_TYPE): Boolean {
        if ( clicktype == CLICK_TYPE.CALLBACK ) return false
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
    fun onRadioButton(filename: String, clicktype: CLICK_TYPE) {
        Log.d(onairTag, "onRadioButton!  $filename")

        if (checkClickInvalid(clicktype)) {
            Log.d(onairTag, "ignore click event")
            return
        }

        updateButtonText(filename, "잠시만 기다려주십시오", false)

        // 현재 요청한 채널이 이전 채널과 다르면 우선 버튼 텍스트 초기화
        if (!filename.equals(mCurrnetPlayFilename)) {
            resetAllButtonText(true)
            updateButtonText(filename, "잠시만 기다려주십시오", false)
        }

        // 재생 중인 경우 callback 을 받아서 처리한다.
        //  1. 서비스 중지
        //  2. 현재 요청 채널이름 저장
        //  3. callback 받고
        //  4. callback 의 채널과 현재 요청 채널 비교
        //     - 같으면 => 그냥 있음
        //     - 다르면 => 요청한 채널로 서비스 시작 (이거 1회 더 불러주면 됨)
        if (RadioPlayer.isPlaying()) {
            // 중지 후 처리 시작
            Log.d(onairTag, "stop previous service")
            stopRadioForegroundService()

            // 요청한 채널 저장
            Log.d(onairTag, "mCurrnetPlayFilename: " + filename)
            mCurrnetPlayFilename = filename
        }
        // youtube 실행 중인 경우 youtube 중지
        // onDestroy callback 불려짐
        else if ( youtubeState == PlayerConstants.PlayerState.PLAYING ) {
            playStopYoutube(filename, null, false)

            // 요청한 채널 저장
            Log.d(onairTag, "mCurrnetPlayFilename: " + filename)
            mCurrnetPlayFilename = filename
        } else {
            // youtube play
            if ( filename.contains("youtube") ) {
                var videoId = filename.substring(filename.indexOf("youtube_") + 8)
                Log.d(onairTag, "videoId: " + videoId)
                createYoutubeView(filename, videoId)
                return
            }
            // radio play
            else {
                startRadioForegroundService("radio", filename, null)
            }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(onairTag, "onCreate")
    }

    //
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(onairTag, "OnAir onCreateView")

        if ( container != null ) {
            mContext = container.context
        }

        DEFAULT_FILE_PATH = mContext?.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).toString() + "/"

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

        program_layout = view.findViewById(R.id.layout_radio_linear)

        txt_loading = view.findViewById(R.id.txt_loading)

        Log.d(onairTag, "init data")
        bInitialized = true

        // 시간은 최초 1회만 ... 혹은 refresh 인 경우
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setCurrentTimeView()
        } else {
            val sdf = SimpleDateFormat("dd/M/yyyy hh:mm:ss")
            val currentDate = sdf.format(Date())
            txt_timeView.setText(currentDate)
        }

        loadFavoriteList()

        resetAllButtonText(true)

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

    fun resetAll() {
        Log.d(onairTag, "resetAll")
        bInitialized = false
        mCurrnetPlayFilename = null
        mContext = null

        program_layout = null
        youtubePlayer = null
        youtubeState = PlayerConstants.PlayerState.UNKNOWN
        mVideoId = null

        mAddressText = "N/A"

        mRainType = 0
        mSkyType = 0
        mTemperatureText = "N/A"
        mWindText = "N/A"
        mRainText = "N/A"
        mFcstTimeText = "N/A"
        mTimeText = "N/A"
        mPMData = null
    }


    /*
        1 -> return "맑음"
        3 -> return "구름 많음"
        4 -> return "흐림"
     */
    fun setSkyStatusImage(skyType: Int) {
        mSkyType = skyType
        Log.d(onairTag, "mSkyType code: " + skyType)

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
        Log.d(onairTag, "rainType code: " + rainType)

        when(rainType) {
            0 -> img_rainView.setImageResource(R.drawable.ic_humidity)
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

    @SuppressLint("SetTextI18n")
    fun updateAirStatus(data: PMData) {
        mPMData = data
        var grade: String = "1"
        var gradeName: String

        if ( data.pm10Grade1h.equals("알 수 없음") ) {
            grade = "99"
        } else if ( data.pm10Grade1h!!.toInt() > grade.toInt() ) {
            grade = data.pm10Grade1h
        }

        if ( data.pm25Grade1h.equals("알 수 없음") ) {
            grade = "99"
        } else if ( data.pm25Grade1h!!.toInt() > grade.toInt() ) {
            grade = data.pm25Grade1h
        }
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
            else -> {
                img_airStatus.setImageResource(R.drawable.red_circle)
                Log.d(onairTag, "Can't know pm grade")
                gradeName = "알수없음"
            }
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
                mContext?.let { startForegroundService(it, intent) }
            }
            else {
                var intent = Intent(mContext, RadioService::class.java)
                intent.setAction(Constants.ACTION.STARTFOREGROUND_ACTION)
                mContext?.let { it.startService(intent) }
            }
        }
    }

    fun stopRadioForegroundService() {
        mContext?.let {
            var intent = Intent(mContext, RadioService::class.java)
            intent.setAction(Constants.ACTION.STOPFOREGROUND_ACTION)
            mContext?.let { it.stopService(intent) }
        }
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
                Log.d(onairTag, "mCurrnetPlayFilename: $filename")
                mCurrnetPlayFilename = filename
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
                        it, Program.getDefaultTextByFilename(
                            mCurrnetPlayFilename!!
                        ), true
                    )
                }

                // 요청된 채널은 실패, disable 처리 -> updateResource callback 으로 초기화됨
                resetAllButtonText(false)
                updateButtonText(filename, RADIO_BUTTON.FAILED_MESSAGE.getMessage(), false)
                mCurrnetPlayFilename = filename
            }
            RESULT.DESTROYED -> {
                // 중지된 filename 과 요청된 filename 이 같으면 button text 만 update
                if (filename.equals(mCurrnetPlayFilename)) {
                    resetAllButtonText(true)
                    updateButtonText(filename, RADIO_BUTTON.STOPPED_MESSAGE.getMessage(), true)
                }
                // 서로 filename 이 다르면 요청된 filename 서비스 시작
                else if (mCurrnetPlayFilename != null) {
                    Log.d(onairTag, "call for mCurrentPlayFilename: ${mCurrnetPlayFilename}")
                    mCurrnetPlayFilename?.let { onRadioButton(it, CLICK_TYPE.CALLBACK) }
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
                    var bundle = Bundle()
                    bundle.putString("command", "RadioResource.SUCCESS")
                    msg.data = bundle
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
                    var bundle = Bundle()
                    bundle.putString("command", "RadioResource.FAILED")
                    msg.data = bundle
                    handler.sendMessage(msg)
                    cancel()
                }
            }
        }
    }
}