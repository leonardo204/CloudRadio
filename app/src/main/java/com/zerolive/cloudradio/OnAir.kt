package com.zerolive.cloudradio

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.*
import android.text.TextUtils
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
import java.io.InputStream
import java.nio.charset.Charset
import java.security.cert.CRL
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.concurrent.timer


var onairTag = "CR_OnAir"

val handler: Handler = @SuppressLint("HandlerLeak")
object : Handler() {
    override fun handleMessage(msg: Message) {
        Log.d(onairTag, "handler handleMessage: " + msg)

        //OnAir.stopRadioForegroundService()
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
    PAUSED_MESSAGE {
        override fun getMessage(): String = "일시정지 ( 터치하여 재생 시작 )"
    },
    PLAYING_MESSAGE {
        override fun getMessage(): String = "재생중 ( 터치하여 정지 )"
    };
    abstract fun getMessage(): String
}

enum class RADIO_STATUS {
    PLAYING, STOPPED, PAUSED, UNSTARTED
}

data class YTBPLSITEM (
    var title: String,
    var videoId: String
)

@SuppressLint("StaticFieldLeak")
object OnAir : Fragment() {

    var bInitialized: Boolean = false
    var bUpdateReady = false
    var onair_btnList = HashMap<String, Button>()

    // 현재 재생 중인 채널의 filename
    var mCurrnetPlayFilename: String? = null

    // 현재 재생 중인 채널의 컨텐츠 제목
    var mCurrnetPlayTitle: String? = null

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
    var youtube_layout: LinearLayout? = null

    var youtubePlayer: YouTubePlayer? = null
    var mYoutubeState: PlayerConstants.PlayerState? = PlayerConstants.PlayerState.UNKNOWN
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

    var mRadioStatus: RADIO_STATUS = RADIO_STATUS.UNSTARTED

    lateinit var txt_loading: TextView

    var mView: ViewGroup? = null

    var mCurPlsItems = ArrayList<YTBPLSITEM>()
    var mCurPlsIdx = 0

    // init resource 시점에만 단독으로 부름
    fun updateFavoriteList() {
        val parent = txt_loading.parent as ViewGroup?
        parent?.removeView(txt_loading)

        val fileObj = File(DEFAULT_FILE_PATH + FAVORITE_CHANNEL_JSON)
        if ( !fileObj.exists() && !fileObj.canRead() ) {
            CRLog.d("Can't load ${DEFAULT_FILE_PATH + FAVORITE_CHANNEL_JSON}")
            Toast.makeText(mContext, "즐겨찾기가 없습니다.", Toast.LENGTH_LONG).show()
            return
        }

        val ins = fileObj.inputStream()
        val content = ins.readBytes().toString(Charset.defaultCharset())
        val list = ArrayList<String>()

        val ele = Json.parseToJsonElement(content)
        CRLog.d("updateFavoriteList size: ${ele.jsonArray.size}")

        for(i in 0..ele.jsonArray.size-1) {
            val title = ele.jsonArray[i].jsonObject["title"].toString().replace("\"","")
            CRLog.d("updateFavoriteList: $title")
            if ( list.contains(title) ) {
                CRLog.d(" > skip duplication: ${title}")
                continue
            }
            list.add(title)
        }

        var realList = updateOnAirPrograms(list)
        Program.updatePrograms(realList)
        Toast.makeText(mContext, "즐겨찾기 로딩이 성공적으로 완료되었습니다.", Toast.LENGTH_LONG).show()

        bInitialized = true
    }

    private fun loadFavoriteList() {
        updateOnAirPrograms(Program.mCurFavList)
    }

    // title 들을 담은 array list 가 전달됨
    // array list 로부터 title 에 해당하는 버튼을 동적 생성
    fun updateOnAirPrograms(favList: ArrayList<String>): ArrayList<String> {
        resetPrograms()

        var list = ArrayList<String>()

        var iter = favList.iterator()
        while( iter.hasNext() ) {
            var title = iter.next()
            CRLog.d("updateOnAirPrograms:" + title)
            if ( RadioChannelResources.getDefaultTextByTitle(title).equals("Unknown Channel") ) {
                CRLog.d(" > skip unknown channels.")
                continue
            }
            list.add(title)
            var btn = Button(mContext)
            onair_btnList.put(title, btn)
            btn.setOnClickListener { onRadioButton(title, CLICK_TYPE.CLICK) }
            program_layout?.addView(btn)
            updateOnAirButtonText(
                RadioChannelResources.getFilenameByTitle(title),
                RadioChannelResources.getDefaultTextByTitle(title),
                true
            )
        }
        return list
    }

    fun resetPrograms() {
        CRLog.d("resetPrograms")
        onair_btnList.clear()
//        if ( mRadioStatus == RADIO_STATUS.PLAYING || mRadioStatus == RADIO_STATUS.PAUSED) {
//            mCurrnetPlayFilename?.let { stopRadioForegroundService(it) }
//        }
        if ( !bInitialized ) requestStopRadioService()
        program_layout?.removeAllViews()
    }

    private fun onRefreshClick() {
        CRLog.d("information refresh")
        setCurrentTimeView()
        txt_fcstView.setText("")
        MainActivity.getInstance().getGPSInfo()

        var text = "현재 지역의 날씨 정보를 업데이트합니다.\n잠시만 기다려주십시오."
        Toast.makeText(mContext, text, Toast.LENGTH_LONG).show()
    }

    @SuppressLint("SetTextI18n")
    fun updateOnAirButtonText(filename: String, text: String, enable: Boolean) {
        //CRLog.d( "updateOnAirButtonText $filename  $text  $enable")

        val iter = onair_btnList.iterator()
        while( iter.hasNext() ) {
            val obj = iter.next()
            val title = obj.key
            //CRLog.d( "updateButtonText key(${obj.key}) - filename(${filename})" )
            if ( RadioChannelResources.getFilenameByTitle(title).equals(filename) ) {
                //CRLog.d( "updateButtonText ok")
                val button = obj.value

                when(text) {
                    RADIO_BUTTON.PLAYING_MESSAGE.getMessage() -> {
                        button.setBackgroundColor(Color.CYAN)
                        if ( mCurPlsItems.size == 0 ) {
                            button.setText(
                                RadioChannelResources.getDefaultButtonTextByFilename(
                                    filename
                                ) + " : " + text
                            )
                        } else {
                            button.ellipsize = TextUtils.TruncateAt.MARQUEE
                            button.marqueeRepeatLimit = -1
                            button.setSingleLine()
                            button.isSelected = true
                            button.setText(
                                mCurPlsItems.get(mCurPlsIdx).title + " : " + text
                            )
                        }
                    }
                    RADIO_BUTTON.STOPPED_MESSAGE.getMessage() -> {
                        button.setBackgroundColor(Color.YELLOW)
                        button.setText(RadioChannelResources.getDefaultButtonTextByFilename(filename) + " : " + text)
                    }
                    RADIO_BUTTON.FAILED_MESSAGE.getMessage() -> {
                        button.setBackgroundColor(Color.RED)
                        button.setText(RadioChannelResources.getDefaultButtonTextByFilename(filename) + " : " + text)
                    }
                    RADIO_BUTTON.PAUSED_MESSAGE.getMessage() -> {
                        button.setBackgroundColor(Color.MAGENTA)
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
        CRLog.d("resetAllButtonText()")
        val iter = onair_btnList.iterator()
        while( iter.hasNext() ) {
            val obj = iter.next()
            val title = obj.key
            val filename = RadioChannelResources.getFilenameByTitle(title)
            //CRLog.d( "filename: " + obj.key)
            val message = RadioChannelResources.getDefaultTextByTitle(title)
            updateOnAirButtonText(filename, message, enable)
        }
    }

    // youtube view 에서 직접 controll 하는 경우에 notification bar 업데이트 해줌
    @JvmName("setYoutubeState1")
    fun setYoutubeState(state: PlayerConstants.PlayerState) {
        CRLog.d( "setYoutubeState $state")
        setYoutubeStateManual(state)
        //youtubeState = state
        when(state) {
//            PlayerConstants.PlayerState.PLAYING -> {
//                CRLog.d( "state PLAYING")
//                RadioNotification.updateNotification(mCurrnetPlayFilename!!, true)
//                mCurrnetPlayFilename?.let { updateButtonText(it, RADIO_BUTTON.PLAYING_MESSAGE.getMessage(), true) }
//            }
//            PlayerConstants.PlayerState.PAUSED -> {
//                CRLog.d( "state PAUSED")
//                RadioNotification.updateNotification(mCurrnetPlayFilename!!, false)
//                mCurrnetPlayFilename?.let { updateButtonText(it, RADIO_BUTTON.PAUSED_MESSAGE.getMessage(), true) }
//            }
//            PlayerConstants.PlayerState.VIDEO_CUED -> {
//                CRLog.d("state VIDEO_CUED")
//                youtubePlayer?.play()
//            }
//            PlayerConstants.PlayerState.ENDED -> {
//                CRLog.d("state ENDED")
//
//                if ( mCurPlsItems.size > 0 ) {
//                    CRLog.d("Auto re-play for pls")
//                    mCurPlsIdx++
//                    if ( mCurPlsIdx == mCurPlsItems.size ) {
//                        mCurPlsIdx = 0
//                    }
//                    mVideoId = mCurPlsItems.get(mCurPlsIdx).videoId
//                    CRLog.d("Next Playing... [${mCurPlsIdx}] videoId: ${mVideoId}    - title: ${mCurPlsItems.get(mCurPlsIdx).title}  ")
//                    MainActivity.getInstance().makeToast("다음 재생: ${mCurPlsItems.get(mCurPlsIdx).title}")
//                    youtubePlayer?.loadVideo(mVideoId!!, 0.0f)
//                } else if ( mVideoId != null ) {
//                    CRLog.d("Auto re-play")
//                    youtubePlayer?.loadVideo(mVideoId!!
        //
        //
        //
        //
        //
        //
        //
        //                    , 0.0f)
//                }
//            }
        }
    }

    @JvmName("setYoutubeStateManual")
    fun setYoutubeStateManual(state: PlayerConstants.PlayerState) {
        CRLog.d("setYoutubeStateManual $state")
        mYoutubeState = state

        mCurrnetPlayFilename?.let {
            CRLog.d("check content type ${it}")

            if ( it.contains("youtube") || it.contains("ytbpls") ) {
                CRLog.d("youtube content OK")
            } else {
                CRLog.d("Ignore non-youtube content")
                return
            }
        }

        when(state) {
            PlayerConstants.PlayerState.UNKNOWN -> {
                CRLog.d("state UNKNOWN")
            }
            PlayerConstants.PlayerState.ENDED -> {
                CRLog.d("state ENDED")

                if ( mCurPlsItems.size > 0 ) {
                    CRLog.d("Auto re-play for pls")
                    mCurPlsIdx++
                    if ( mCurPlsIdx == mCurPlsItems.size ) {
                        mCurPlsIdx = 0
                    }
                    mVideoId = mCurPlsItems.get(mCurPlsIdx).videoId
                    MainActivity.getInstance().makeToast("다음 재생: ${mCurPlsItems.get(mCurPlsIdx).title}")
                    youtubePlayer?.loadVideo(mVideoId!!, 0.0f)
                } else if ( mVideoId != null ) {
                    CRLog.d("Auto re-play")
                    youtubePlayer?.loadVideo(mVideoId!!, 0.0f)
                }
            }
            PlayerConstants.PlayerState.PLAYING -> {
                CRLog.d("state PLAYING")
                mCurrnetPlayFilename?.let { updateOnAirButtonText(it, RADIO_BUTTON.PLAYING_MESSAGE.getMessage(), true) }
            }
            PlayerConstants.PlayerState.BUFFERING -> {
                CRLog.d("state BUFFERING")
            }
            PlayerConstants.PlayerState.PAUSED -> {
                CRLog.d("state PAUSED")
                mCurrnetPlayFilename?.let { updateOnAirButtonText(it, RADIO_BUTTON.PAUSED_MESSAGE.getMessage(), true) }
            }
            PlayerConstants.PlayerState.UNSTARTED -> {
                CRLog.d("state UNSTARTED")
            }
            PlayerConstants.PlayerState.VIDEO_CUED -> {
                CRLog.d("state VIDEO_CUED")
            }
            else -> {
                CRLog.d("state unknown")
            }
        }
    }

    // - YoutubeView 는 fragment 시작 시 동적 생성해서 가지고 있도록 (static)
    // - YoutubeView 에서 YoutubeHandler 를 addlistener 붙이고 onReady 를 받으면 player 를 얻을 수 있다
    // - player 를 얻어서 RadioService 에서 videoId 에 따라서 cueVideo 를 불러서 넣어주고 play 해주면 된다
    // - view 의 UI 중에 재생 컨트롤은 풀어서 일시정지 가능하도록..
    // - 나중에 view 를 floating 으로 띄우는 방법도 생각해보자.
    private fun createYoutubeView(filename: String, videoId: String) {
        CRLog.d("createYoutubeView prev($mCurrnetPlayFilename) - cur($filename)  $videoId")

        resetAllButtonText(true)
        //stopRadioForegroundService()

        mCurrnetPlayFilename = filename

        // 이전 유튜브가 재생 중인 경우 우선 유튜브 재생을 중지
        if ( mCurrnetPlayFilename != null && !filename.equals(mCurrnetPlayFilename) ) {
            CRLog.d("createYoutubeView  ----------------  prev stop!!!!!!!!!!")

            playStopYoutube("", null, false)
            return
        }

        if ( mYoutubeState == PlayerConstants.PlayerState.PLAYING ) {
            CRLog.d("createYoutubeView  ----------------  stop!!!!!!!!!!")

            playStopYoutube(filename, videoId, false)

            mVideoId = null

            updateOnAirButtonText(
                    filename,
                    RadioChannelResources.getDefaultTextByFilename(filename),
                    true
            )
        } else {
            CRLog.d("createYoutubeView  ----------------- play!!!!!!!!!!")

            playStopYoutube(filename, videoId, true)

            mVideoId = videoId

            updateOnAirButtonText(filename, RADIO_BUTTON.PLAYING_MESSAGE.getMessage(), true)
        }
    }

    // false 인 경우 vid 는 null 로 들어옴
    fun playStopYoutube(filename: String, videoId: String?, play: Boolean) {
        CRLog.d("playStopYoutube: filename=${filename} - videoId=${videoId} - play=${play}")

        if ( play ) {
            val parent = MainActivity.youtubeView!!.parent as ViewGroup?
            parent?.removeView(MainActivity.youtubeView)
            youtube_layout?.addView(MainActivity.youtubeView)
            startRadioForegroundService("youtube", filename, videoId!!)
        } else {
            val parent = MainActivity.youtubeView!!.parent as ViewGroup?
            parent?.removeView(MainActivity.youtubeView)
            youtube_layout?.removeView(MainActivity.youtubeView)
            stopRadioForegroundService(filename)
        }
        CRLog.d("playStopYoutube: $play")
    }

    private var mLastClickTime: Long = 0
    private var mMinClickAllowTime: Long = 1500

    private fun checkClickInvalid(clicktype: CLICK_TYPE): Boolean {
        if ( clicktype == CLICK_TYPE.CALLBACK) return false
        if (SystemClock.elapsedRealtime() - mLastClickTime < mMinClickAllowTime){
            return true
        }
        mLastClickTime = SystemClock.elapsedRealtime();
        return false
    }

    fun requestStartRadioService() {
        Log.d(onairTag, "requestStartRadioService for ${mCurrnetPlayFilename}")
        mCurrnetPlayFilename?.let {
            if ( it.contains("youtube") && mYoutubeState != PlayerConstants.PlayerState.PLAYING && mVideoId != null) {
                Log.d(onairTag, "start youtube")
                playStopYoutube(it, mVideoId, true)
            } else if ( it.contains("ytbpls_") && mYoutubeState != PlayerConstants.PlayerState.PLAYING && mVideoId != null ) {
                Log.d(onairTag, "start youtube playlist")
                playStopYoutube(it, mVideoId, true)
            } else if ( !it.contains("youtube") && !RadioPlayer.isPlaying() ) {
                Log.d(onairTag, "start radio")
                startRadioForegroundService("radio", it, null)
            }
        }
    }

    fun requestStopRadioService() {
        Log.d(onairTag, "requestStopRadioService")
        if (RadioPlayer.isPlaying()) {
            Log.d(onairTag, "stop radio")
            mCurrnetPlayFilename?.let { stopRadioForegroundService(it) }
        }
        else if (mYoutubeState == PlayerConstants.PlayerState.PLAYING) {
            Log.d(onairTag, "stop youtube")
            mCurrnetPlayFilename?.let { playStopYoutube(it, null, false) }
        } else {
            Log.d(onairTag, "stop nothing")
        }
    }

    // 라디오 버튼 누르면 모두 여기서 처리
    // 재생 중이면,
    //   - 어떤 채널에서 재생 중인지 확인 후 동일 채널이면 중지
    //   - 동일 채널이 아닌 경우엔, 해당 채널 스탑 후 내 채널 재생 시작
    // 재생 중이 아니라면
    //   - 내 채널 재생 시작
    fun onRadioButton(title: String, clicktype: CLICK_TYPE) {
        CRLog.d("onRadioButton!  $title")
        val filename = RadioChannelResources.getFilenameByTitle(title)

        if (checkClickInvalid(clicktype)) {
            CRLog.d("ignore click event")
            return
        }

        updateOnAirButtonText(filename, "잠시만 기다려주십시오", false)

        // 현재 요청한 채널이 이전 채널과 다르면 우선 버튼 텍스트 초기화
        if (!filename.equals(mCurrnetPlayFilename)) {
            resetAllButtonText(true)
            updateOnAirButtonText(filename, "잠시만 기다려주십시오", false)
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
            CRLog.d("stop previous service")
            mCurrnetPlayFilename?.let { stopRadioForegroundService(it) }

            // 요청한 채널 저장
            CRLog.d("mCurrnetPlayFilename: " + filename)
            mCurrnetPlayFilename = filename
        }
        // youtube playlist 재생 중인 경우에는 stop 대신 그냥 pause 를 함
        else if (filename.startsWith("ytbpls_") && mYoutubeState == PlayerConstants.PlayerState.PLAYING && filename.equals(mCurrnetPlayFilename) ) {
            youtubePlayer?.pause()
            mRadioStatus = RADIO_STATUS.PAUSED
            setYoutubeStateManual(PlayerConstants.PlayerState.PAUSED)
        }
        // youtube pause 상태인 경우 + 같은 파일 요청이면 pause 파일 resume
        else if ( mYoutubeState == PlayerConstants.PlayerState.PAUSED && filename.equals(
                mCurrnetPlayFilename
            ) ) {
            CRLog.d("play resume: $mCurrnetPlayFilename")
            youtubePlayer?.play()
            updateOnAirButtonText(mCurrnetPlayFilename!!, RADIO_BUTTON.PLAYING_MESSAGE.getMessage(), true)
            RadioNotification.updateNotification(mCurrnetPlayFilename!!, true)
        }
        // youtube 실행/PAUSE 중 + 다른 파일 요청인 경우 youtube 중지
        // onDestroy callback 불려짐
        else if (mYoutubeState == PlayerConstants.PlayerState.PAUSED || mYoutubeState == PlayerConstants.PlayerState.PLAYING) {

            // stop 시 이전 채널로 요청해야 함
            playStopYoutube(mCurrnetPlayFilename!!, null, false)

            // 요청한 채널 저장
            CRLog.d("mCurrnetPlayFilename: " + filename)
            mCurrnetPlayFilename = filename

            mCurPlsItems.clear()
            mCurPlsIdx = 0
        }
        // play radio / youtube
        else {
            mCurPlsItems.clear()
            mCurPlsIdx = 0

            // youtube play
            if ( filename.contains("youtube") ) {
                val videoId = filename.substring(filename.indexOf("youtube_") + 8)
                CRLog.d("youtube videoId: " + videoId)
                createYoutubeView(filename, videoId)
                return
            }
            // youtube playlist
            else if ( filename.startsWith("ytbpls_") ) {
                val videoId = getVideoId(filename)
                CRLog.d("ytbpls videoId: " + videoId)
                videoId?.let { createYoutubeView(filename, it) }
                return
            }
            // radio play
            else {
                startRadioForegroundService("radio", filename, null)
            }
        }
    }

    private fun checkDoRandom(title: String): Boolean {
        CRLog.d("checkDoRandom: ${title}")
        val fileobj = File(DEFAULT_FILE_PATH + "ytbpls.json")
        if ( fileobj.exists() && fileobj.canRead() ) {
            val ins: InputStream = fileobj.inputStream()
            val content = ins.readBytes().toString(Charset.defaultCharset())
            val items = Json.parseToJsonElement(content)
            for(i in items.jsonArray.indices) {
                val tt = Json.parseToJsonElement(items.jsonArray[i].jsonObject["title"].toString())
                val rr = Json.parseToJsonElement(items.jsonArray[i].jsonObject["random"].toString())
                if ( title.equals(tt.toString().replace("\"",""))) {
                    CRLog.d("checkDoRandom result: ${rr.toString().replace("\"","").toBoolean()}")
                    return rr.toString().replace("\"","").toBoolean()
                }
            }
        }
        CRLog.d("checkDoRandom result: false")
        return false
    }

    private fun getVideoId(filename: String): String? {
        var videoId: String? = null

        CRLog.d("getVideoId from file: ${filename}")

        val fileobj = File(DEFAULT_FILE_PATH + filename +".json")

        if ( fileobj.exists() && fileobj.canRead() ) {
            mCurPlsItems.clear()

            val ins: InputStream = fileobj.inputStream()
            val content = ins.readBytes().toString(Charset.defaultCharset())
            val items = Json.parseToJsonElement(content)
            for(i in items.jsonArray.indices) {
                val title = Json.parseToJsonElement(items.jsonArray[i].jsonObject["title"].toString())
                val vid = Json.parseToJsonElement(items.jsonArray[i].jsonObject["videoId"].toString())
                val map = YTBPLSITEM(title.toString().replace("\"",""), vid.toString().replace("\"",""))
                mCurPlsItems.add(map)
            }
        }

        if ( mCurPlsItems.size > 0 ) {
            if ( checkDoRandom(filename) ) {
                Collections.shuffle(mCurPlsItems)
            }
            for(i in mCurPlsItems.indices) {
                CRLog.d("[${i}] videoId: ${mCurPlsItems.get(i).videoId}    - title: ${mCurPlsItems.get(i).title}  ")
            }
            videoId = mCurPlsItems.get(0).videoId
        }

        if ( videoId == null ) {
            updateOnAirButtonText(
                filename,
                RADIO_BUTTON.STOPPED_MESSAGE.getMessage(),
                true
            )
            MainActivity.getInstance().makeToast("비디오 목록을 가져올 수 없습니다. 앱을 다시 시작하여 주십시오.")
        }

        return videoId
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
        CRLog.d("오전/오후: " + str)
        result += str

        formatter = DateTimeFormatter.ofPattern("hh")
        str = current.format(formatter)
        result = result + " " + str + "시"

        formatter = DateTimeFormatter.ofPattern("mm")
        str = current.format(formatter)
        result = result + " " + str + "분"

        txt_timeView.setText(result)
        mTimeText = result

        CRLog.d("setCurrentTimeView(): " + result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CRLog.d("onCreate")
    }

    //
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        CRLog.d("OnAir onCreateView ${bInitialized}")

        val view: ViewGroup = inflater.inflate(R.layout.fragment_onair, container, false) as ViewGroup
        mView = view

        mView?.let {
            txt_timeView = it.findViewById(R.id.text_time)
            txt_addrView = it.findViewById(R.id.text_address)
            txt_skyView = it.findViewById(R.id.text_sky)
            txt_rainView = it.findViewById(R.id.text_rain)
            txt_fcstView = it.findViewById(R.id.text_fcstTime)
            txt_windView = it.findViewById(R.id.text_wind)
            txt_pmGrade = it.findViewById(R.id.txt_pmGrade)
            txt_pmValue = it.findViewById(R.id.txt_pmValue)
            txt_loading = it.findViewById(R.id.txt_loading)

            img_weatherView = it.findViewById(R.id.image_empty_weather)
            img_skyView = it.findViewById(R.id.img_sky)
            img_rainView = it.findViewById(R.id.img_humidity)
            img_airStatus = it.findViewById(R.id.image_airStatus)

            mBtn_weather_refresh = it.findViewById(R.id.btn_weatherRefresh)

            program_layout = it.findViewById(R.id.layout_radio_linear)
            youtube_layout = it.findViewById(R.id.layout_youtube_view)
        }

        if ( container != null ) {
            mContext = container.context
        }

        init()
        loadFavoriteList()

        return view
    }

    override fun onStart() {
        super.onStart()
        CRLog.d("onStart")
        if ( bInitialized ) {
            YoutubeLiveUpdater.update()
            YoutubePlaylistUpdater.update()
        }
        bInitialized = true
    }

    private fun init() {
        CRLog.d("init data")

        mContext?.let {
            DEFAULT_FILE_PATH =
                it.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).toString() + "/"
        }

        mBtn_weather_refresh.setOnClickListener { onRefreshClick() }

        // 시간은 최초 1회만 ... 혹은 refresh 인 경우
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            setCurrentTimeView()
        } else {
            val sdf = SimpleDateFormat("dd/M/yyyy hh:mm:ss")
            val currentDate = sdf.format(Date())
            txt_timeView.setText(currentDate)
        }
        resetAllButtonText(true)

    }

    override fun onStop() {
        super.onStop()
        CRLog.d("onStop")
        bUpdateReady = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        CRLog.d("onDestroyView")
    }

    fun resetAll() {
        CRLog.d("resetAll")
        bInitialized = false
        mCurrnetPlayFilename = null
        mContext = null

        program_layout = null
        youtube_layout = null
        youtubePlayer = null
        mYoutubeState = PlayerConstants.PlayerState.UNKNOWN
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
        CRLog.d("mSkyType code: " + skyType)

        when (skyType) {
            1 -> img_skyView.setImageResource(R.drawable.ic_sunny)
            3 -> img_skyView.setImageResource(R.drawable.ic_cloudy)
            4 -> img_skyView.setImageResource(R.drawable.ic_clouds)
            else -> CRLog.d("skyType code is invalid: " + skyType)
        }
    }

    /*
       (없음(0), 비(1), 비/눈(진눈개비)(2), 눈(3), 소나기(4), 빗방울(5), 빗방울/눈날림(6), 눈날림(7))
     */
    fun setRainStatusImage(rainType: Int){
        mRainType = rainType
        CRLog.d("rainType code: " + rainType)

        when(rainType) {
            0 -> img_rainView.setImageResource(R.drawable.ic_humidity)
            1 -> img_rainView.setImageResource(R.drawable.ic_rain1)
            2 -> img_rainView.setImageResource(R.drawable.ic_rain2)
            3 -> img_rainView.setImageResource(R.drawable.ic_rain3)
            4 -> img_rainView.setImageResource(R.drawable.ic_rain4)
            5 -> img_rainView.setImageResource(R.drawable.ic_rain5)
            6 -> img_rainView.setImageResource(R.drawable.ic_rain6)
            7 -> img_rainView.setImageResource(R.drawable.ic_rain7)
            else -> CRLog.d("rainType code is invalid: " + rainType)
        }
    }

    fun updateAddressView(status: Boolean) {
        if ( status ) {
            CRLog.d("current_address : " + mAddressText)
            txt_addrView.setText(mAddressText)
        } else {
            CRLog.d("Address info receiving is failed")
            txt_addrView.setText("알 수 없음")
        }
    }

    @SuppressLint("SetTextI18n")
    fun updateAirStatus(data: PMData) {
        mPMData = data
        var grade: String = "1"
        var gradeName: String

        CRLog.d("updateAirStatus: ${data}")

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

        CRLog.d("worst grade: " + AirStatus.getGradeString(grade))
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
                CRLog.d("Can't know pm grade")
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
        CRLog.d("startRadioForegroundService ${serviceName} ${filename} ${videoId}")
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
                intent.putExtra("serviceName", serviceName)
                intent.putExtra("videoId", videoId)
                intent.putExtra("name", filename)
                intent.putExtra("address", address)
                intent.setAction(Constants.ACTION.STARTFOREGROUND_ACTION)
                mContext?.let { it.startService(intent) }
            }
        }
    }

    fun stopRadioForegroundService(filename: String) {
        CRLog.d("stopRadioForegroundService $mContext ${filename}")

        mContext?.let {
            Intent(it, RadioService::class.java).run {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
                    var intent = Intent(it, RadioService::class.java)
                    intent.putExtra("name", filename)
                    intent.setAction(Constants.ACTION.STOPFOREGROUND_ACTION)
                    startForegroundService(it, intent)
                } else {
                    var intent = Intent(it, RadioService::class.java)
                    intent.putExtra("name", filename)
                    intent.setAction(Constants.ACTION.STOPFOREGROUND_ACTION)
                    it.startService(intent)
                }
            }
        }
    }

    // callback
    //  4. callback 의 채널과 현재 요청 채널 비교
    //     - 같으면 => 그냥 있음
    //     - 다르면 => 요청한 채널로 서비스 시작 (이거 1회 더 불러주면 됨)
    fun notifyRadioServiceStatus(filename: String, result: RESULT) {
        CRLog.d(
            "notifyRadioServiceStatus: " + result + ", filename: " + filename + " - mCurrnetPlayFilename: $mCurrnetPlayFilename"
        )

        when(result) {
            RESULT.PLAY_SUCCESS -> {
                resetAllButtonText(true)
                updateOnAirButtonText(filename, RADIO_BUTTON.PLAYING_MESSAGE.getMessage(), true)
                CRLog.d("mCurrnetPlayFilename: $filename")
                mCurrnetPlayFilename = filename
            }
            RESULT.PLAY_FAILED -> {

                if (!filename.contains("youtube")) {
                    RadioChannelResources.requestUpdateResource(filename)
                } else {
                    CRLog.d("timer ~")
                    var msg = handler.obtainMessage()
                    timer(initialDelay = 3000, period = 10000) {
                        handler.sendMessage(msg)
                        cancel()
                    }
                }
                // 우선 이전 채널에 대해서 초기화 (없으면 null 체크하여 실행 x)
                mCurrnetPlayFilename?.let {
                    updateOnAirButtonText(
                        it, RadioChannelResources.getDefaultTextByFilename(
                            mCurrnetPlayFilename!!
                        ), true
                    )
                }

                // 요청된 채널은 실패, disable 처리 -> updateResource callback 으로 초기화됨
                resetAllButtonText(false)
                updateOnAirButtonText(filename, RADIO_BUTTON.FAILED_MESSAGE.getMessage(), false)
                mCurrnetPlayFilename = filename
            }
            RESULT.DESTROYED -> {
                // 중지된 filename 과 요청된 filename 이 같으면 button text 만 update
                if (filename.equals(mCurrnetPlayFilename)) {
                    resetAllButtonText(true)
                    updateOnAirButtonText(filename, RADIO_BUTTON.STOPPED_MESSAGE.getMessage(), true)
                    setYoutubeStateManual(PlayerConstants.PlayerState.UNSTARTED)
                }
                // 서로 filename 이 다르면 요청된 filename 서비스 시작
                else if (mCurrnetPlayFilename != null) {
                    CRLog.d("start to service for $mCurrnetPlayFilename")
                    mCurrnetPlayFilename?.let {
                        if ( it.contains("youtube") ) {
                            var videoId = it.substring(it.indexOf("youtube_") + 8)
                            CRLog.d("videoId: " + videoId)
                            createYoutubeView(it, videoId)
                        }
                        // radio play
                        else {
                            startRadioForegroundService("radio", it, null)
                        }
                    }
                }
            }
        }
    }

    // success 는 모두 성공 시에만 callback 이 불림
    fun notifyRadioResourceUpdate(filename: String?, result: RadioResource) {
        CRLog.d("resource update result: " + result + ", filename: " + filename)
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
                    CRLog.d("Ignore failed (not initialized)")
                    return
                }

                CRLog.d("timer ~")
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