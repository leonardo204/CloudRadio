package com.zerolive.cloudradio

import android.annotation.SuppressLint
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.*
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService
import androidx.fragment.app.Fragment
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.concurrent.timer


var onairTag = "CR_OnAir"

var onair_handler: Handler = Handler(object : Handler.Callback {
    override fun handleMessage(msg: Message?): Boolean {
        Log.d(onairTag, "handler handleMessage: " + msg?.data)

        //OnAir.stopRadioForegroundService()
        OnAir.resetAllButtonText(true)

        val bundle = msg?.data
        val command = bundle?.getString("command")

        when(command) {
            "RadioResource.FAILED" -> OnAir.updateFavoriteList()
            "RadioResource.SUCCESS" -> {
                OnAir.updateFavoriteList()
//                YoutubeLiveUpdater.update()
                //YoutubePlaylistUpdater.update()
                return true
            }
        }
        return false
    }
})

enum class CLICK_TYPE {
    CLICK, CALLBACK
}

enum class RADIO_BUTTON {
    FAILED_MESSAGE {
        override fun getMessage() = "?????? ?????? ?????? ??? ?????? ???????????? ????????????."
    },
    STOPPED_MESSAGE {
        override fun getMessage(): String = "?????? ( ???????????? ?????? ?????? )"
    },
    PAUSED_MESSAGE {
        override fun getMessage(): String = "???????????? ( ???????????? ?????? ?????? )"
    },
    PLAYING_MESSAGE {
        override fun getMessage(): String = "????????? ( ???????????? ?????? )"
    };
    abstract fun getMessage(): String
}

enum class RADIO_STATUS {
    PLAYING, STOPPED, PAUSED, UNSTARTED
}

data class YTBPLSITEM(
    var title: String,
    var videoId: String,
    var thumbnail: String
)

@SuppressLint("StaticFieldLeak")
object OnAir : Fragment() {

    var bInitialized: Boolean = false
    var bUpdateReady = false
    var onair_btnList = HashMap<String, Button>()

    // ?????? ?????? ?????? ????????? filename
    var mCurrentPlayFilename: String? = null

    // ?????? ?????? ?????? ????????? ????????? ??????
    var mCurrnetPlayTitle: String? = null

    var mYoutubePlaylistPlaybackFailed = false

    // request wether (fixed)
    val num_of_rows = 10
    val page_no = 1
    val data_type = "JSON"
    var mContext: Context? = null

    var mCurrentSecond: Long = 0
    var mDuration: Long = 0
    var mThumbnail: Bitmap? = null
    var mAlbumUrl: String? = null
    var bThumbnailChanged = false
    var mAlbumUri = arrayListOf<Uri>()
    var mAlbumRealPath: String? = null

    lateinit var weather_view: ConstraintLayout

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

    lateinit var img_airStatus: ImageButton
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

    var mView: ViewGroup? = null

    var mCurPlsItems = ArrayList<YTBPLSITEM>()
    var mCurPlsIdx = 0

    // youtube background play ??? ?????? hidden
    var hiddenCount = 0

    var updateFavCount = 0

    // init resource ???????????? ???????????? ??????
    fun updateFavoriteList() {
        updateFavCount++
        CRLog.d("updateFavoriteList ${updateFavCount} / ${RadioChannelResources.resourceInitCount}")

        val fileObj = File(DEFAULT_FILE_PATH + FAVORITE_CHANNEL_JSON)
        if ( fileObj.exists() && fileObj.canRead() ) {
            val ins = fileObj.inputStream()
            val content = ins.readBytes().toString(Charset.defaultCharset())
            val list = ArrayList<String>()

            val ele = Json.parseToJsonElement(content)
            CRLog.d("updateFavoriteList size: ${ele.jsonArray.size}")

            for(i in 0..ele.jsonArray.size-1) {
                val title = ele.jsonArray[i].jsonObject["title"].toString().replace("\"", "")
                CRLog.d("updateFavoriteList: $title")
                if ( list.contains(title) ) {
                    CRLog.d(" > skip duplication: ${title}")
                    continue
                }
                list.add(title)
            }

            val realList = updateOnAirPrograms(list)
            Program.updatePrograms(realList)

            MainActivity.mLastYtbPlsStatus?.let {
                if ( it.state == PlayerConstants.PlayerState.PLAYING ) {
                    updateOnAirButtonText(it.filename, RADIO_BUTTON.PLAYING_MESSAGE.getMessage(), true)
                    mCurrentPlayFilename = it.filename
                    mVideoId = it.videoId
                    playStopYoutube(it.filename, it.videoId, true)
                } else if ( it.state == PlayerConstants.PlayerState.PAUSED ) {
                    updateOnAirButtonText(it.filename, RADIO_BUTTON.PAUSED_MESSAGE.getMessage(), true)
                }
            }
        }

        if ( updateFavCount == RadioChannelResources.resourceInitCount ) {
            // ?????? ?????? ????????? ??????????????? ????????? ?????? AFN ???????????? ?????????
            AFNRadioResource.init()

            bInitialized = true
            if ( !fileObj.exists() && !fileObj.canRead() ) {
                MainActivity.getInstance().makeToast("??????????????? ????????????.")
            } else {
                MainActivity.getInstance().makeToast("???????????? ????????? ??????????????? ?????????????????????.")
            }

            // ???????????? ???????????? ???????????? ????????? ????????????.
            if ( /* AirStatus.bWeatherLoaded ||*/ 1==1 || MainActivity.bSkipWaitingGPS ) {
                MainActivity.getInstance().removeLoading()
            }
        }
    }

    private fun loadFavoriteList() {
        updateOnAirPrograms(Program.mCurFavList)
    }

    // title ?????? ?????? array list ??? ?????????
    // array list ????????? title ??? ???????????? ????????? ?????? ??????
    fun updateOnAirPrograms(favList: ArrayList<String>): ArrayList<String> {
        resetPrograms()

        val list = ArrayList<String>()
        favList.sort()

        val iter = favList.iterator()
        while( iter.hasNext() ) {
            val title = iter.next()
            CRLog.d("updateOnAirPrograms:" + title)
            if ( RadioChannelResources.getDefaultTextByTitle(title).equals("Unknown Channel")
                || onair_btnList.containsKey(title) ) {
                CRLog.d(" > skip unknown or duplication channels.")
                continue
            }
            list.add(title)
            val btn = Button(mContext)
            onair_btnList.put(title, btn)
            btn.setOnClickListener { onRadioButton(title, CLICK_TYPE.CLICK) }
            program_layout?.addView(btn)

            val prefix = "ytbpls_"
            val defaultText = RadioChannelResources.getDefaultTextByTitle(title).replace(prefix, "")

            updateOnAirButtonText(
                RadioChannelResources.getFilenameByTitle(title),
                defaultText,
                true
            )
        }

        // init ????????? ?????? ??????/??????/???????????? ???????????? ?????????, ????????? ???????????????
        mCurrentPlayFilename?.let {
            val tt = RadioChannelResources.getTitleByFilename(it)
            CRLog.d("updateOnAirPrograms current:" + tt)

            if ( onair_btnList.containsKey(tt) ) {
                if ( isPlayingRadioService() ) {
                    updateOnAirButtonText(it, RADIO_BUTTON.PLAYING_MESSAGE.getMessage(),true)
                } else {
                    if (mYoutubeState == PlayerConstants.PlayerState.PAUSED) {
                        updateOnAirButtonText(it,RADIO_BUTTON.PAUSED_MESSAGE.getMessage(),true)
                    } else {
                        updateOnAirButtonText(it,RADIO_BUTTON.STOPPED_MESSAGE.getMessage(),true)
                    }
                }
            }
            /*
              ?????? ????????? ?????????????????? ????????? ??????.
             */
            else {
                requestStopCheckRadioService()
            }
        }

        return list
    }

    fun resetPrograms() {
        CRLog.d("resetPrograms")
        onair_btnList.clear()
//        if ( mRadioStatus == RADIO_STATUS.PLAYING || mRadioStatus == RADIO_STATUS.PAUSED) {
//            mCurrnetPlayFilename?.let { stopRadioForegroundService(it) }
//        }
        if ( !bInitialized ) requestStopPauseRadioService()
        program_layout?.removeAllViews()
    }

    private fun onRefreshClick() {
        CRLog.d("information refresh")
        setCurrentTimeView()
        txt_fcstView.setText("")
        MainActivity.getInstance().getGPSInfo()

        var text = "?????? ????????? ?????? ????????? ?????????????????????.\n????????? ?????????????????????."
        MainActivity.getInstance().makeToast(text)
    }

    @SuppressLint("SetTextI18n")
    fun updateOnAirButtonText(filename: String, text: String, enable: Boolean) {
        //CRLog.d( "updateOnAirButtonText $filename  $text  $enable")

        val prefix = "ytbpls_"
        var defaultText = RadioChannelResources.getDefaultTextByFilename(filename).replace(prefix, "")

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
                        if (mCurPlsItems.size == 0) {
                            button.setText( defaultText + " : " + text )
                        } else {
                            button.ellipsize = TextUtils.TruncateAt.MARQUEE
                            button.marqueeRepeatLimit = -1
                            button.setSingleLine()
                            button.isSelected = true
                            button.setText( mCurPlsItems.get(mCurPlsIdx).title + " : " + text )
                        }
                    }
                    RADIO_BUTTON.STOPPED_MESSAGE.getMessage() -> {
                        button.setBackgroundColor(Color.YELLOW)
                        button.setText(defaultText + " : " + text)
                    }
                    RADIO_BUTTON.FAILED_MESSAGE.getMessage() -> {
                        button.setBackgroundColor(Color.RED)
                        button.setText(defaultText + " : " + text)
                    }
                    RADIO_BUTTON.PAUSED_MESSAGE.getMessage() -> {
                        button.setBackgroundColor(Color.MAGENTA)
                        button.setText(defaultText + " : " + text)
                    }
                    else -> {
                        defaultText = text
                        if ( defaultText.startsWith(prefix) ) {
                            defaultText = defaultText.substring(defaultText.indexOf(prefix) + prefix.length)
                        }

                        button.setBackgroundColor(Color.WHITE)
                        button.setText(defaultText)
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

    // youtube view ?????? ?????? controll ?????? ????????? notification bar ???????????? ??????
    @JvmName("setYoutubeState1")
    fun setYoutubeState(state: PlayerConstants.PlayerState) {
        CRLog.d("setYoutubeState $state")
        if ( mYoutubeState == PlayerConstants.PlayerState.UNSTARTED && state ==  PlayerConstants.PlayerState.PAUSED ) {
            CRLog.d("Ignore late pause event")
        } else {
            setYoutubeStateManual(state)
        }
    }

    @JvmName("setYoutubeStateManual")
    fun setYoutubeStateManual(state: PlayerConstants.PlayerState) {
        CRLog.d("setYoutubeStateManual $state")
        mYoutubeState = state

        mCurrentPlayFilename?.let {
            CRLog.d("check content type ${it}")
            val type = RadioChannelResources.getMediaType(it)

            if ( type == MEDIATYPE.YOUTUBE_PLAYLIST || type == MEDIATYPE.YOUTUBE_NORMAL || type == MEDIATYPE.YOUTUBE_LIVE ) {
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

                if (mCurPlsItems.size > 0) {
                    CRLog.d("Auto re-play for pls")
                    mCurPlsIdx++
                    if (mCurPlsIdx == mCurPlsItems.size) {
                        mCurPlsIdx = 0
                    }
                    mVideoId = mCurPlsItems.get(mCurPlsIdx).videoId
//                    MainActivity.getInstance()
//                        .makeToast("?????? ??????: ${mCurPlsItems.get(mCurPlsIdx).title}")
                    GetBitmapFromUrl().execute(mCurPlsItems.get(mCurPlsIdx).thumbnail)
                    youtubePlayer?.loadVideo(mVideoId!!, 0.0f)
                } else if (mVideoId != null) {
                    CRLog.d("Auto re-play")
                    youtubePlayer?.loadVideo(mVideoId!!, 0.0f)
                }
            }
            PlayerConstants.PlayerState.PLAYING -> {
                CRLog.d("state PLAYING")
                mCurrentPlayFilename?.let {
                    updateOnAirButtonText(it,RADIO_BUTTON.PLAYING_MESSAGE.getMessage(),true)
//                    setMetadata()

                    val state = PlaybackStateCompat.Builder()
                        .setActions(MainActivity.getInstance().getFullActions())
                        .setState(
                            PlaybackStateCompat.STATE_PLAYING,
                            mCurrentSecond,
                            1.0f,
                            SystemClock.elapsedRealtime()
                        ).build()
                    CRLog.d("setPlaybackState: ${state.state}")
                    MainActivity.mMediaSession?.setPlaybackState(state)
                }
                weather_view.visibility = View.GONE
                mVideoId?.let { GetYoutubeThumbnails().execute(it) }
            }
            PlayerConstants.PlayerState.BUFFERING -> {
                CRLog.d("state BUFFERING")
            }
            PlayerConstants.PlayerState.PAUSED -> {
                CRLog.d("state PAUSED")
                mCurrentPlayFilename?.let {
                    updateOnAirButtonText(
                        it,
                        RADIO_BUTTON.PAUSED_MESSAGE.getMessage(),
                        true
                    )
//                    setMetadata()

                    val state = PlaybackStateCompat.Builder()
                        .setActions(MainActivity.getInstance().getFullActions())
                        .setState(
                            PlaybackStateCompat.STATE_PAUSED,
                            mCurrentSecond,
                            1.0f,
                            SystemClock.elapsedRealtime()
                        ).build()
                    CRLog.d("setPlaybackState: ${state.state}")
                    MainActivity.mMediaSession?.setPlaybackState(state)
                }
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

    // - YoutubeView ??? fragment ?????? ??? ?????? ???????????? ????????? ????????? (static)
    // - YoutubeView ?????? YoutubeHandler ??? addlistener ????????? onReady ??? ????????? player ??? ?????? ??? ??????
    // - player ??? ????????? RadioService ?????? videoId ??? ????????? cueVideo ??? ????????? ???????????? play ????????? ??????
    // - view ??? UI ?????? ?????? ???????????? ????????? ???????????? ???????????????..
    // - ????????? view ??? floating ?????? ????????? ????????? ???????????????.
    private fun createYoutubeView(filename: String, videoId: String) {
        CRLog.d("createYoutubeView prev($mCurrentPlayFilename) - cur($filename)  $videoId")

        resetAllButtonText(true)
        //stopRadioForegroundService()

        mCurrentPlayFilename = filename

        // ?????? ???????????? ?????? ?????? ?????? ?????? ????????? ????????? ??????
        if ( mCurrentPlayFilename != null && !filename.equals(mCurrentPlayFilename) ) {
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

    // false ??? ?????? vid ??? null ??? ?????????
    fun playStopYoutube(filename: String, videoId: String?, play: Boolean) {
        CRLog.d("playStopYoutube: filename=${filename} - videoId=${videoId} - play=${play}")
        val type = RadioChannelResources.getMediaType(filename)

        if ( play ) {
            if ( type == MEDIATYPE.YOUTUBE_PLAYLIST ) {
                MainActivity.uiController?.showCustomAction1(true)
                MainActivity.uiController?.showCustomAction2(true)
            } else {
                MainActivity.uiController?.showCustomAction1(false)
                MainActivity.uiController?.showCustomAction2(false)
            }
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

    fun setMetadata() {
        if ( getThumbnail() == null ) {
            CRLog.d("Ignore setMetadata until settting thumbnail")
            return
        }

        // duration ??? ?????? ???????????? ?????? ?????? skip
        mCurrentPlayFilename?.let {
            val type = RadioChannelResources.getMediaType(it)
            if ( type == MEDIATYPE.RADIO || type == MEDIATYPE.YOUTUBE_LIVE ) {
                CRLog.d("duration don't care")
            } else {
                if ( mDuration <= 0 ) {
                    CRLog.d("Ignore setMetadata until setting duration (cur: ${mDuration})")
                    return
                }
            }
        }

        CRLog.d("setMetadata!")

        val lastIdx = mAlbumUri.size - 1
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, getTitle())
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, getArtist())
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDuration())
            .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, getThumbnail())
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, getThumbnail())
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, getThumbnail())

        if ( lastIdx >= 0 ) {
            metadata.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, mAlbumUri.get(lastIdx).toString())
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, mAlbumUri.get(lastIdx).toString())
                .putString(MediaMetadataCompat.METADATA_KEY_ART_URI, mAlbumUri.get(lastIdx).toString())
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_URI, mAlbumUri.get(lastIdx).toString())
        }

        MainActivity.mMediaSession?.setMetadata(metadata.build())
        MainActivity.mMediaSession?.isActive = true
    }

    fun getTitle(): String {
        var ret = "??? ??? ??????"
        mCurrentPlayFilename?.let {
            val type = RadioChannelResources.getMediaType(it)

            if ( type == MEDIATYPE.YOUTUBE_PLAYLIST ) {
                ret = "[${mCurPlsIdx+1}/${mCurPlsItems.size}] " + mCurPlsItems.get(mCurPlsIdx).title
            } else {
                ret = RadioChannelResources.getDefaultTextByFilename(it)
            }
        }
        CRLog.d("getTitle() ${ret}")
        return ret
    }

    fun getArtist() : String {
        var ret = "??? ??? ??????"
        mCurrentPlayFilename?.let {
            ret = RadioChannelResources.getDefaultTextByFilename(it)
        }
        CRLog.d("getArtist() ${ret}")
        return ret
    }

    fun getDuration() : Long {
        CRLog.d("getDuration() for ${mCurrentPlayFilename}")
        mCurrentPlayFilename?.let {
            val type = RadioChannelResources.getMediaType(it)
            if ( type == MEDIATYPE.RADIO || type == MEDIATYPE.YOUTUBE_LIVE ) {
                CRLog.d("infinite duration")
                mDuration = -1
            }
        }
        CRLog.d("getDuration() ${mDuration}")
        return mDuration
    }

    fun getThumbnail() : Bitmap? {
        CRLog.d("getThumbnail(): ${mThumbnail}")
        return mThumbnail
    }

    private fun setThumbnail(bitmap: Bitmap, uri: Uri?) {
        mThumbnail = bitmap
        uri?.let { mAlbumUri.add(it) }
        val lastIdx = mAlbumUri.size - 1
        CRLog.d("setThumbnail(${bThumbnailChanged}): URL=${mThumbnail}  /  URI=${mAlbumUri.get(lastIdx)}")
        if ( bThumbnailChanged ) {
            setMetadata()
            bThumbnailChanged = false
        }
    }

    fun getCurrentSecond() : Long {
        return mCurrentSecond
    }
    fun setCurrentSecond(sec: Float) {
        mCurrentSecond = (sec*1000).toLong()
    }

    fun parseVideoInfo(info: String) {
        val element = Json.parseToJsonElement(info)
        val items = Json.parseToJsonElement(element.jsonObject["items"].toString())
//        for( i in items.jsonArray.indices ) {
            val i = 0
            val snippet = Json.parseToJsonElement(items.jsonArray[i].jsonObject["snippet"].toString())
            val thumbnails = Json.parseToJsonElement(snippet.jsonObject["thumbnails"].toString())
            var thumbId: kotlinx.serialization.json.JsonElement
            if ( thumbnails.jsonObject["standard"] != null ) {
                thumbId = Json.parseToJsonElement(thumbnails.jsonObject["standard"].toString())
            } else if ( thumbnails.jsonObject["high"] != null ) {
                thumbId = Json.parseToJsonElement(thumbnails.jsonObject["high"].toString())
            } else {
                thumbId = Json.parseToJsonElement(thumbnails.jsonObject["default"].toString())
            }
            val thumbnail = thumbId.jsonObject["url"].toString().replace("\"", "")
            GetBitmapFromUrl().execute(thumbnail)
//        }
    }

    //Make sure to call this function on a worker thread, else it will block main thread
    fun saveImageInQ(inContext: Context, bitmap: Bitmap): String? {
        val filename = "IMG_${System.currentTimeMillis()}.jpg"
        var fos: OutputStream? = null
        var imageUri: Uri? = null
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        //use application context to get contentResolver
        val contentResolver = inContext.contentResolver

        contentResolver.also { resolver ->
            imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            fos = imageUri?.let { resolver.openOutputStream(it) }
        }

        fos?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }

        contentValues.clear()
        contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
        imageUri?.let { contentResolver.update(it, contentValues, null, null) }

        return imageUri.toString()
    }

    fun getImageUri(inContext: Context, inImage: Bitmap): Uri? {
        val path = saveImageInQ(inContext, inImage)
        if ( path != null ) {
            return Uri.parse(path!!)
        } else {
            return null
        }
    }

    fun getRealPathFromURI(context: Context, uri: Uri?): String? {

        // DocumentProvider
        if (DocumentsContract.isDocumentUri(context, uri)) {

            // ExternalStorageProvider
            if (isExternalStorageDocument(uri!!)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split: Array<String?> = docId.split(":".toRegex()).toTypedArray()
                val type = split[0]
                return if ("primary".equals(type, ignoreCase = true)) {
                    (Environment.getExternalStorageDirectory().toString() + "/"
                            + split[1])
                } else {
                    val SDcardpath = getRemovableSDCardPath(context)?.split("/Android".toRegex())!!.toTypedArray()[0]
                    SDcardpath + "/" + split[1]
                }
            } else if (isDownloadsDocument(uri!!)) {
                val id = DocumentsContract.getDocumentId(uri)
                val contentUri = ContentUris.withAppendedId(
                    Uri.parse("content://downloads/public_downloads"),
                    java.lang.Long.valueOf(id))
                return getDataColumn(context, contentUri, null, null)
            } else if (isMediaDocument(uri!!)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split: Array<String?> = docId.split(":".toRegex()).toTypedArray()
                val type = split[0]
                var contentUri: Uri? = null
                if ("image" == type) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                } else if ("video" == type) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else if ("audio" == type) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }
                val selection = "_id=?"
                val selectionArgs = arrayOf(split[1])
                return getDataColumn(context, contentUri, selection,
                    selectionArgs)
            }
        } else if (uri != null) {
            if ("content".equals(uri.getScheme(), ignoreCase = true)) {
                // Return the remote address
                return if (isGooglePhotosUri(uri)) uri.getLastPathSegment() else getDataColumn(context, uri, null, null)
            } else if ("file".equals(uri.getScheme(), ignoreCase = true)) {
                return uri.getPath()
            }
        }
        return null
    }

    fun getRemovableSDCardPath(context: Context): String? {
        val storages = ContextCompat.getExternalFilesDirs(context, null)
        return if (storages.size > 1 && storages[0] != null && storages[1] != null) storages[1].toString() else ""
    }

    fun getDataColumn(
        context: Context, uri: Uri?,
        selection: String?, selectionArgs: Array<String?>?
    ): String? {
        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(column)
        try {
            cursor = context.contentResolver.query(
                uri!!, projection,
                selection, selectionArgs, null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val index: Int = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(index)
            }
        } finally {
            if (cursor != null) cursor.close()
        }
        return null
    }

    fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri
            .authority
    }

    fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri
            .authority
    }

    fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri
            .authority
    }

    fun isGooglePhotosUri(uri: Uri): Boolean {
        return "com.google.android.apps.photos.content" == uri
            .authority
    }

    internal class GetBitmapFromUrl() : AsyncTask<String?, String?, String?>() {
        override fun doInBackground(vararg params: String?): String? {
            val url = params[0]
            Log.d(onairTag, "1) GetBitmapFromUrl. recv_url:${url}  cur_url:${mAlbumUrl}  uri: ${mAlbumUri}")

            if ( url != mAlbumUrl && url != null ) {

                // remove preivous album thumbnail file
                if ( mAlbumUri.size > 0 ) {
                    removeThumbnails()
                }

                mAlbumUrl = url
                val bit: Bitmap? = getBitmapFromURL(url)
                if ( bit != null ) {
                    bThumbnailChanged = true
                    val uri: Uri? = mContext?.let{ getImageUri(it, bit) }
                    if ( uri != null ) {
                        mAlbumRealPath = mContext?.let { getRealPathFromURI(it, uri) }
                        Log.d(onairTag, "GetBitmapFromUrl: url=${url}")
                        Log.d(onairTag, "GetBitmapFromUrl: uri=${uri}")
                        Log.d(onairTag, "GetBitmapFromUrl: path=${mAlbumRealPath}")
                        setThumbnail(bit, uri)
                    } else {
                        Log.d(onairTag, "GetBitmapFromUrl FAILED!!!")
                    }
                } else {
                    Log.d(onairTag, "GetBitmapFromUrl FAILED!!!")
                }
            } else {
                Log.d(onairTag, "Ignore!!! GetBitmapFromUrl. recv:${url}  cur:${mAlbumUrl}")
            }
            return null
        }

        private fun getBitmapFromURL(src: String?): Bitmap? {
            return try {
                val url = URL(src)
                val connection: HttpURLConnection = url.openConnection() as HttpURLConnection
                connection.setDoInput(true)
                connection.connect()
                val input: InputStream = connection.getInputStream()
                BitmapFactory.decodeStream(input)
            } catch (e: IOException) {
                // Log exception
                null
            }
        }
    }

    fun IsMediaPlaying() : Boolean {
        var isPlayed = false
        mCurrentPlayFilename?.let {
            val mediatype = RadioChannelResources.getMediaType(it)
            when(mediatype) {
                    MEDIATYPE.YOUTUBE_NORMAL,
                    MEDIATYPE.YOUTUBE_LIVE,
                    MEDIATYPE.YOUTUBE_PLAYLIST -> {
                        if ( mYoutubeState == PlayerConstants.PlayerState.PLAYING ) isPlayed = true
                    }
                MEDIATYPE.RADIO -> {
                    if ( RadioPlayer.isPlaying() ) isPlayed = true
                }
            }
        }
        CRLog.d("IsMediaPlaying(): ${isPlayed}")
        return isPlayed
    }

    fun requestPlayNext() {
        mCurrentPlayFilename?.let {
            val mediaType = RadioChannelResources.getMediaType(it)
            if ( mediaType == MEDIATYPE.YOUTUBE_LIVE ||
                mediaType == MEDIATYPE.YOUTUBE_NORMAL ||
                mediaType == MEDIATYPE.UNKNOWN || RadioPlayer.isPlaying() ) {
                CRLog.d("Ignore forward")
                return
            }
            mCurPlsIdx++
            if (mCurPlsIdx == mCurPlsItems.size) {
                mCurPlsIdx = 0
            }
            mVideoId = mCurPlsItems.get(mCurPlsIdx).videoId
//            MainActivity.getInstance().makeToast("?????? ??????: ${mCurPlsItems.get(mCurPlsIdx).title}")
            GetBitmapFromUrl().execute(mCurPlsItems.get(mCurPlsIdx).thumbnail)
            youtubePlayer?.loadVideo(mVideoId!!, 0.0f)
        }
    }

    fun requestPlayPrevious() {
        mCurrentPlayFilename?.let {
            val mediaType = RadioChannelResources.getMediaType(it)
            if ( mediaType == MEDIATYPE.YOUTUBE_LIVE ||
                mediaType == MEDIATYPE.YOUTUBE_NORMAL ||
                mediaType == MEDIATYPE.UNKNOWN || RadioPlayer.isPlaying() ) {
                    CRLog.d("Ignore rewind")
                return
            }
            mCurPlsIdx--
            if (mCurPlsIdx < 0) {
                mCurPlsIdx = 0
            }
            mVideoId = mCurPlsItems.get(mCurPlsIdx).videoId
//            MainActivity.getInstance().makeToast("?????? ??????: ${mCurPlsItems.get(mCurPlsIdx).title}")
            GetBitmapFromUrl().execute(mCurPlsItems.get(mCurPlsIdx).thumbnail)
            youtubePlayer?.loadVideo(mVideoId!!, 0.0f)
        }
    }

    fun isPlayingRadioService() : Boolean {
        mCurrentPlayFilename?.let {
            val type = RadioChannelResources.getMediaType(it)

            if ( type == MEDIATYPE.RADIO && type != MEDIATYPE.UNKNOWN) {
                if (RadioPlayer.isPlaying()) {
                    CRLog.d("isPlayingRadioService() true:  radio mode")
                    return true
                }
            } else {
                if (mYoutubeState == PlayerConstants.PlayerState.PLAYING && mVideoId != null) {
                    CRLog.d("isPlayingRadioService() true: youtube mode")
                    return true
                }
            }
        }
        CRLog.d("isPlayingRadioService() false")

        return false
    }

    fun requestStartRadioService() {
        Log.d(onairTag, "requestStartRadioService for ${mCurrentPlayFilename}")
        mCurrentPlayFilename?.let {
            val type = RadioChannelResources.getMediaType(it)
            Log.d(onairTag, "type: ${type}")

            if ( type == MEDIATYPE.YOUTUBE_LIVE && mYoutubeState != PlayerConstants.PlayerState.PLAYING && mVideoId != null) {
                Log.d(onairTag, "start youtube")
                playStopYoutube(it, mVideoId, true)
            } else if ( (type == MEDIATYPE.YOUTUBE_NORMAL || type == MEDIATYPE.YOUTUBE_PLAYLIST) && mYoutubeState != PlayerConstants.PlayerState.PLAYING && mVideoId != null ) {
                Log.d(onairTag, "start youtube playlist state: ${mYoutubeState}")
                if ( mYoutubeState == PlayerConstants.PlayerState.PAUSED ) {
                    youtubePlayer?.play()
                } else {
                    playStopYoutube(it, mVideoId, true)
                }
            } else if ( type == MEDIATYPE.RADIO && !RadioPlayer.isPlaying() ) {
                Log.d(onairTag, "start radio")
                startRadioForegroundService("radio", it, null)
            } else {
                Log.d(onairTag, "start nothing")
            }
        }
    }

    fun requestDestroyRadioService() {
        Log.d(onairTag, "requestDestroyRadioService: ${mCurrentPlayFilename}")
        if (RadioPlayer.isPlaying()) {
            Log.d(onairTag, "stop radio")
            weather_view.visibility = View.VISIBLE
            mCurrentPlayFilename?.let { stopRadioForegroundService(it) }
        }
        else {
            mCurrentPlayFilename?.let {
                if ( mYoutubeState != PlayerConstants.PlayerState.UNKNOWN ) {
                    Log.d(onairTag, "stop youtube")
                    weather_view.visibility = View.VISIBLE
                    if ( FullScreenHelper.mFullScreen ) MainActivity.getInstance().exitFullScreen()
                    mCurrentPlayFilename?.let { playStopYoutube(it, null, false) }
                }
            }
        }
    }

    fun requestStopCheckRadioService() {
        Log.d(onairTag, "requestStopCheckRadioService: ${mCurrentPlayFilename}")
        if ( isPlayingRadioService() ) {
            mCurrentPlayFilename?.let {
                val type = RadioChannelResources.getMediaType(it)
                val tt = RadioChannelResources.getTitleByFilename(it)
                when (type) {
                    MEDIATYPE.RADIO -> {
                        if ( onair_btnList.containsKey(tt) ) {
                            Log.d(onairTag, "keep radio status")
                        } else {
                            Log.d(onairTag, "stop radio")
                            weather_view.visibility = View.VISIBLE
                            mCurrentPlayFilename?.let { stopRadioForegroundService(it) }
                        }
                    }
                    MEDIATYPE.YOUTUBE_LIVE,
                    MEDIATYPE.YOUTUBE_PLAYLIST,
                    MEDIATYPE.YOUTUBE_NORMAL -> {
                        if (onair_btnList.containsKey(tt)) {
                            Log.d(onairTag, "keep youtube")
                        } else {
                            Log.d(onairTag, "stop youtube")
                            weather_view.visibility = View.VISIBLE
                            if ( FullScreenHelper.mFullScreen ) MainActivity.getInstance().exitFullScreen()
                            mCurrentPlayFilename?.let { playStopYoutube(it, null, false) }
                        }
                    }
                    else -> {
                        Log.d(onairTag, "unkonwn type: ${type}")
                    }
                }
            }
        }
    }

    fun requestStopPauseRadioService() {
        CRLog.d("requestStopPauseRadioService: ${mCurrentPlayFilename}")
        if ( isPlayingRadioService() ) {
            mCurrentPlayFilename?.let {
                val type = RadioChannelResources.getMediaType(it)
                val title = RadioChannelResources.getTitleByFilename(it)
                when (type) {
                    MEDIATYPE.RADIO -> {
                        CRLog.d( "stop radio")
                        weather_view.visibility = View.VISIBLE
                        mCurrentPlayFilename?.let { stopRadioForegroundService(it) }
                    }
                    MEDIATYPE.YOUTUBE_LIVE,
                    MEDIATYPE.YOUTUBE_PLAYLIST,
                    MEDIATYPE.YOUTUBE_NORMAL -> {
                        if (onair_btnList.containsKey(title)) {
                            CRLog.d( "pause youtube")
                            youtubePlayer?.pause()
                        } else {
                            CRLog.d( "stop youtube")
                            weather_view.visibility = View.VISIBLE
                            if ( FullScreenHelper.mFullScreen ) MainActivity.getInstance().exitFullScreen()
                            mCurrentPlayFilename?.let { playStopYoutube(it, null, false) }
                        }
                    }
                    else -> {
                        CRLog.d( "unkonwn type: ${type}")
                    }
                }
            }
        }
    }

    // ????????? ?????? ????????? ?????? ????????? ??????
    // ?????? ?????????,
    //   - ?????? ???????????? ?????? ????????? ?????? ??? ?????? ???????????? ??????
    //   - ?????? ????????? ?????? ?????????, ?????? ?????? ?????? ??? ??? ?????? ?????? ??????
    // ?????? ?????? ????????????
    //   - ??? ?????? ?????? ??????
    fun onRadioButton(title: String, clicktype: CLICK_TYPE) {
        CRLog.d("onRadioButton!  $title")
        val filename = RadioChannelResources.getFilenameByTitle(title)

        if (checkClickInvalid(clicktype)) {
            CRLog.d("ignore click event")
            return
        }

        updateOnAirButtonText(filename, "????????? ?????????????????????", false)

        // ?????? ????????? ????????? ?????? ????????? ????????? ?????? ?????? ????????? ?????????
        if (!filename.equals(mCurrentPlayFilename)) {
            resetAllButtonText(true)
            updateOnAirButtonText(filename, "????????? ?????????????????????", false)
        }

        val req_type = RadioChannelResources.getMediaType(filename)
        var cur_type: MEDIATYPE? = null
        mCurrentPlayFilename?.let { cur_type = RadioChannelResources.getMediaType(it) }

        // ?????? ?????? ?????? callback ??? ????????? ????????????.
        //  1. ????????? ??????
        //  2. ?????? ?????? ???????????? ??????
        //  3. callback ??????
        //  4. callback ??? ????????? ?????? ?????? ?????? ??????
        //     - ????????? => ?????? ??????
        //     - ????????? => ????????? ????????? ????????? ?????? (?????? 1??? ??? ???????????? ???)
        if (RadioPlayer.isPlaying()) {
            CRLog.d("[1] stop previous service")

            clearYoutubePlayListItem()

            // ?????? ??? ?????? ??????
            mCurrentPlayFilename?.let { stopRadioForegroundService(it) }

            // ????????? ?????? ??????
            CRLog.d("mCurrnetPlayFilename: " + filename)
            mCurrentPlayFilename = filename
        }
        // youtube playlist/normal ?????? ?????? ???????????? stop ?????? ?????? pause ??? ???
        else if ( mYoutubeState == PlayerConstants.PlayerState.PLAYING
            && filename.equals( mCurrentPlayFilename ) ) {
            CRLog.d("[2] JUST do pause for youtube playlist or normal contents")

            youtubePlayer?.pause()
            mRadioStatus = RADIO_STATUS.PAUSED
            setYoutubeStateManual(PlayerConstants.PlayerState.PAUSED)
        }
        // youtube pause ????????? ?????? + ?????? ?????? ???????????? pause ?????? resume
        else if ( mYoutubeState == PlayerConstants.PlayerState.PAUSED
            && filename.equals( mCurrentPlayFilename ) ) {
            CRLog.d("[3] resume youtube: $mCurrentPlayFilename")
            youtubePlayer?.play()
            updateOnAirButtonText(mCurrentPlayFilename!!,RADIO_BUTTON.PLAYING_MESSAGE.getMessage(),true)
            RadioNotification.updateNotification(mCurrentPlayFilename!!, true)
        }
        // youtube ??????/PAUSE ??? + ?????? ?????? ????????? ?????? youtube ??????
        // onDestroy callback ?????????
        else if ( ( cur_type == MEDIATYPE.YOUTUBE_LIVE || cur_type == MEDIATYPE.YOUTUBE_NORMAL
            || cur_type == MEDIATYPE.YOUTUBE_PLAYLIST )
            && mYoutubeState != PlayerConstants.PlayerState.UNKNOWN ) {
            CRLog.d("[4] stop youtube: $mCurrentPlayFilename state: ${mYoutubeState}")

            // stop ??? ?????? ????????? ???????????? ???
            mCurrentPlayFilename?.let { playStopYoutube(it, null, false) }

            // ????????? ?????? ??????
            CRLog.d("mCurrnetPlayFilename: " + filename)
            mCurrentPlayFilename = filename

            clearYoutubePlayListItem()
        }
        // play radio / youtube
        // ????????? ?????? ?????? ?????? ????????? ?????? ?????? ???????????? ???????????? ??????
        else {
            CRLog.d("[5] play channel: ${filename}")

            clearYoutubePlayListItem()

            // youtube play
            if ( req_type == MEDIATYPE.YOUTUBE_LIVE || req_type == MEDIATYPE.YOUTUBE_NORMAL ) {
                val videoId = filename.substring(filename.indexOf("youtube_") + 8)
                CRLog.d("youtube videoId: " + videoId)
                createYoutubeView(filename, videoId)
                return
            }
            // youtube playlist
            else if ( req_type == MEDIATYPE.YOUTUBE_PLAYLIST ) {
                val videoId = getVideoId(filename)
                CRLog.d("ytbpls videoId: " + videoId)
                videoId?.let { createYoutubeView(filename, it) }
                mContext?.let {
                    if ( mCurPlsItems.size > 0 ) {
                        GetBitmapFromUrl().execute(mCurPlsItems.get(mCurPlsIdx).thumbnail)
                    }
                }
                return
            }
            // radio play
            else {
                startRadioForegroundService("radio", filename, null)
            }
        }
    }

    private fun clearYoutubePlayListItem() {
        CRLog.d("clearYoutubePlayListItem()")
        mCurPlsItems.clear()
        mCurPlsIdx = 0
        mThumbnail = null
        mDuration = 0
        mAlbumUrl = null
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
                if ( title.equals(tt.toString().replace("\"", ""))) {
                    CRLog.d("checkDoRandom result: ${rr.toString().replace("\"", "").toBoolean()}")
                    return rr.toString().replace("\"", "").toBoolean()
                }
            }
        }
        CRLog.d("checkDoRandom result: false")
        return false
    }

    private fun getVideoId(filename: String): String? {
        var videoId: String? = null

        CRLog.d("getVideoId from file: ${filename}")

        val fileobj = File(DEFAULT_FILE_PATH + filename)
        val title = RadioChannelResources.getTitleByFilename(filename)

        if ( fileobj.exists() && fileobj.canRead() ) {
            clearYoutubePlayListItem()

            val ins: InputStream = fileobj.inputStream()
            val content = ins.readBytes().toString(Charset.defaultCharset())
            val items = Json.parseToJsonElement(content)
            for(i in items.jsonArray.indices) {
                val tt = Json.parseToJsonElement(items.jsonArray[i].jsonObject["title"].toString()).toString().replace(
                    "\"",
                    ""
                )
                val vid = Json.parseToJsonElement(items.jsonArray[i].jsonObject["videoId"].toString()).toString().replace(
                    "\"",
                    ""
                )
                val thumbnail = Json.parseToJsonElement(items.jsonArray[i].jsonObject["thumbnail"].toString()).toString().replace(
                    "\"",
                    ""
                )
                val map = YTBPLSITEM(tt, vid, thumbnail)
                mCurPlsItems.add(map)
            }
        }

        if ( mCurPlsItems.size > 0 ) {
            if ( checkDoRandom(title) ) {
                Collections.shuffle(mCurPlsItems)
            }
//            for(i in mCurPlsItems.indices) {
//                CRLog.d(
//                    "[${i}] videoId: ${mCurPlsItems.get(i).videoId}    - title: ${
//                        mCurPlsItems.get(
//                            i
//                        ).title
//                    }   - thumbnail: ${mCurPlsItems.get(i).thumbnail} "
//                )
//            }
            videoId = mCurPlsItems.get(0).videoId
        }

        if ( videoId == null ) {
//            updateOnAirButtonText(
//                filename,
//                RADIO_BUTTON.STOPPED_MESSAGE.getMessage(),
//                true
//            )
            updateOnAirButtonText(filename, RADIO_BUTTON.FAILED_MESSAGE.getMessage(), false)

            MainActivity.getInstance().makeToast("????????? ????????? ????????? ??? ????????????. ????????? ???????????? ?????????. ????????? ????????? ????????????.")
            YoutubePlaylistUpdater.update()
            mYoutubePlaylistPlaybackFailed = true
        }

        return videoId
    }

    @SuppressLint("NewApi")
    private fun setCurrentTimeView() {
        var result: String
        val current: LocalDateTime = LocalDateTime.now()
        var formatter = DateTimeFormatter.ofPattern("yyyy")
        var str = current.format(formatter)
        result = str + "???"

        formatter = DateTimeFormatter.ofPattern("MM")
        str = current.format(formatter)
        result = result + " " + str + "???"

        formatter = DateTimeFormatter.ofPattern("dd")
        str = current.format(formatter)
        result = result + " " + str + "???"

        result += "  "
        formatter = DateTimeFormatter.ofPattern("a")
        str = current.format(formatter)
        CRLog.d("??????/??????: " + str)
        result += str

        formatter = DateTimeFormatter.ofPattern("hh")
        str = current.format(formatter)
        result = result + " " + str + "???"

        formatter = DateTimeFormatter.ofPattern("mm")
        str = current.format(formatter)
        result = result + " " + str + "???"

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
            weather_view = it.findViewById(R.id.layout_weather_const)
            txt_timeView = it.findViewById(R.id.text_time)
            txt_addrView = it.findViewById(R.id.text_address)
            txt_skyView = it.findViewById(R.id.text_sky)
            txt_rainView = it.findViewById(R.id.text_rain)
            txt_fcstView = it.findViewById(R.id.text_fcstTime)
            txt_windView = it.findViewById(R.id.text_wind)
            txt_pmGrade = it.findViewById(R.id.txt_pmGrade)
            txt_pmValue = it.findViewById(R.id.txt_pmValue)

            img_weatherView = it.findViewById(R.id.image_empty_weather)
            img_skyView = it.findViewById(R.id.img_sky)
            img_rainView = it.findViewById(R.id.img_humidity)
            img_airStatus = it.findViewById(R.id.image_airStatus)
            img_airStatus.setOnClickListener { onClickHidden() }

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
        CRLog.d("onStart fullScreen(${FullScreenHelper.mFullScreen})")
        if ( bInitialized ) {
            YoutubeLiveUpdater.update()
            YoutubePlaylistUpdater.update()
            if ( FullScreenHelper.mFullScreen ) {
                MainActivity.getInstance().setFullScreen()
            }
        }
        bInitialized = true
    }

    private fun onClickHidden() {
        CRLog.d("onClickHidden ${hiddenCount++}")
        if ( More.getLockPlay() == false ) {
            if (hiddenCount == 30) {
                CRLog.d("Enable hidden")
                MainActivity.getInstance().makeToast("Awesome enabled")
                More.txt_main_version.setText(MainActivity.mainVersionString + " ( Awesome! )")

                val item = More.loadSettings()
                item?.let {
                    it.lockplay = true.toString()
                    More.saveSettings(it)
                }
            }
        }
    }

    private fun init() {
        CRLog.d("init data")

        mContext?.let {
            DEFAULT_FILE_PATH =
                it.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).toString() + "/"
        }

        mBtn_weather_refresh.setOnClickListener { onRefreshClick() }

        // ????????? ?????? 1?????? ... ?????? refresh ??? ??????
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

        requestDestroyRadioService()

        removeThumbnails()

        MainActivity.getInstance().systmeDestroy()
    }

    private const val DELETE_PERMISSION_REQUEST = 0x1033

    private fun removeThumbnails() {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )
        var displayName: String? = null
        var contentUri: Uri? = null
        mContext?.contentResolver?.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val displayNameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                displayName = cursor.getString(displayNameColumn)
                if ( !displayName?.contains("CRIMG-")!!) {
                    continue
                }
                contentUri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )
            }
        }

        // ?????? Uri??? MediaStore?????? ??????
        contentUri?.let{
            try {
                mContext?.contentResolver?.delete(it, null, null)
                mAlbumUri.remove(it)
                Log.d(
                    onairTag,
                    "Removed $displayName from MediaStore: $it  ( remains uri num: ${mAlbumUri.size} )"
                )
            } catch (e: RecoverableSecurityException) {
                val intentSender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    e.userAction.actionIntent.intentSender
                } else {
                    TODO("VERSION.SDK_INT < O")
                }
                intentSender?.let {
                    startIntentSenderForResult(
                        it,
                        DELETE_PERMISSION_REQUEST,
                        null,
                        0,
                        0,
                        0,
                        null
                    )
                }
            }
        }
    }

    fun resetAll() {
        CRLog.d("resetAll")
        bInitialized = false
        mCurrentPlayFilename = null
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
        1 -> return "??????"
        3 -> return "?????? ??????"
        4 -> return "??????"
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
       (??????(0), ???(1), ???/???(????????????)(2), ???(3), ?????????(4), ?????????(5), ?????????/?????????(6), ?????????(7))
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
            txt_addrView.setText("??? ??? ??????")
        }
    }

    @SuppressLint("SetTextI18n")
    fun updateAirStatus(data: PMData) {
        mPMData = data
        var grade: String = "1"
        var gradeName: String

        CRLog.d("updateAirStatus: ${data}")

        if ( data.pm10Grade1h.equals("??? ??? ??????") ) {
            grade = "99"
        } else if ( data.pm10Grade1h!!.toInt() > grade.toInt() ) {
            grade = data.pm10Grade1h
        }

        if ( data.pm25Grade1h.equals("??? ??? ??????") ) {
            grade = "99"
        } else if ( data.pm25Grade1h!!.toInt() > grade.toInt() ) {
            grade = data.pm25Grade1h
        }

        CRLog.d("worst grade: " + AirStatus.getGradeString(grade))
        when(grade) {
            "1" -> {
                img_airStatus.setImageResource(R.drawable.skyblue_circle); gradeName = "??????"
            }
            "2" -> {
                img_airStatus.setImageResource(R.drawable.green_circle); gradeName = "??????"
            }
            "3" -> {
                img_airStatus.setImageResource(R.drawable.orange_circle); gradeName = "??????"
            }
            "4" -> {
                img_airStatus.setImageResource(R.drawable.red_circle); gradeName = "????????????"
            }
            else -> {
                img_airStatus.setImageResource(R.drawable.red_circle)
                CRLog.d("Can't know pm grade")
                gradeName = "????????????"
            }
        }
        txt_pmGrade.setText(gradeName)
        txt_pmValue.setText("??????????????? (" + data.pm25Value + ") / ???????????? (" + data.pm10Value + ")")
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
            weather_view.visibility = View.VISIBLE
            address = getRadioChannelHttpAddress(filename)
            CRLog.d("address: ${address}")
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
            weather_view.visibility = View.VISIBLE

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
    //  4. callback ??? ????????? ?????? ?????? ?????? ??????
    //     - ????????? => ?????? ??????
    //     - ????????? => ????????? ????????? ????????? ?????? (?????? 1??? ??? ???????????? ???)
    fun notifyRadioServiceStatus(filename: String, result: RESULT) {
        CRLog.d(
            "notifyRadioServiceStatus: " + result + ", filename: " + filename + " - mCurrnetPlayFilename: $mCurrentPlayFilename"
        )

        when(result) {
            RESULT.PLAY_SUCCESS -> {
                resetAllButtonText(true)
                updateOnAirButtonText(filename, RADIO_BUTTON.PLAYING_MESSAGE.getMessage(), true)
                CRLog.d("mCurrnetPlayFilename: $filename")
                mCurrentPlayFilename = filename

                // radio playing success ??? ?????? metadata ???????????? ?????????.
                // youtube??? YoutubeHandler ??? onDuration callback ?????? ???
                val type = RadioChannelResources.getMediaType(filename)
                CRLog.d("type: ${type}")
                if (type == MEDIATYPE.RADIO) {
                    // 2021-05.28 ?????? ???????????? ?????? ?????? ???????????? ??????????????? ???
                    GetBitmapFromUrl().execute(RadioThumbnails.DEFAULT.getUrl())

                    setMetadata()

                    val state = PlaybackStateCompat.Builder()
                        .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f, SystemClock.elapsedRealtime())
                        .setActions(MainActivity.getInstance().getFullActions())
                        .build()
                    CRLog.d("setPlaybackState: ${state.state}")
                    MainActivity.mMediaSession?.setPlaybackState(state)
                }
            }
            RESULT.PLAY_FAILED -> {

                if (!filename.contains("youtube")) {
                    RadioChannelResources.requestUpdateResource(filename)
                } else {
                    CRLog.d("timer ~")
                    var msg = onair_handler.obtainMessage()
                    timer(initialDelay = 3000, period = 10000) {
                        onair_handler.sendMessage(msg)
                        cancel()
                    }
                }
                // ?????? ?????? ????????? ????????? ????????? (????????? null ???????????? ?????? x)
                mCurrentPlayFilename?.let {
                    updateOnAirButtonText(
                        it, RadioChannelResources.getDefaultTextByFilename(
                            mCurrentPlayFilename!!
                        ), true
                    )
                }

                // ????????? ????????? ??????, disable ?????? -> updateResource callback ?????? ????????????
                resetAllButtonText(false)
                updateOnAirButtonText(filename, RADIO_BUTTON.FAILED_MESSAGE.getMessage(), false)
                mCurrentPlayFilename = filename
            }
            RESULT.DESTROYED -> {
                // ????????? filename ??? ????????? filename ??? ????????? button text ??? update
                if (filename.equals(mCurrentPlayFilename)) {
                    resetAllButtonText(true)
                    updateOnAirButtonText(filename, RADIO_BUTTON.STOPPED_MESSAGE.getMessage(), true)
                    setYoutubeStateManual(PlayerConstants.PlayerState.UNSTARTED)
                }
                // ?????? filename ??? ????????? ????????? filename ????????? ??????
                else if (mCurrentPlayFilename != null) {
                    CRLog.d("start to service for $mCurrentPlayFilename")
                    mCurrentPlayFilename?.let {
                        val type = RadioChannelResources.getMediaType(it)

                        if ( type == MEDIATYPE.YOUTUBE_LIVE || type == MEDIATYPE.YOUTUBE_NORMAL ) {
                            val videoId = it.substring(it.indexOf("youtube_") + 8)
                            CRLog.d("videoId: " + videoId)
                            createYoutubeView(it, videoId)
                        }
                        // youtube playlist
                        else if ( type == MEDIATYPE.YOUTUBE_PLAYLIST ) {
                            val videoId = getVideoId(it)
                            CRLog.d("ytbpls videoId: " + videoId)
                            if ( mCurPlsItems.size > 0 ) {
                                GetBitmapFromUrl().execute(mCurPlsItems.get(mCurPlsIdx).thumbnail)
                            }
                            videoId?.let { createYoutubeView(mCurrentPlayFilename!!, it) }
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

    // success ??? ?????? ?????? ????????? callback ??? ??????
    fun notifyRadioResourceUpdate(filename: String?, result: RadioResource) {
        CRLog.d("resource update result: " + result + ", filename: " + filename)
        when(result) {
            RadioResource.SUCCESS -> {
                val msg = onair_handler.obtainMessage()
                timer(initialDelay = 3000, period = 10000) {
                    val bundle = Bundle()
                    bundle.putString("command", "RadioResource.SUCCESS")
                    msg.data = bundle
                    onair_handler.sendMessage(msg)
                    cancel()
                }
            }
            RadioResource.OPEN_FAILED, RadioResource.DOWN_FAILED -> {
                if (!bInitialized) {
                    CRLog.d("Ignore failed (not initialized)")
                    return
                }

                CRLog.d("timer ~")
                val msg = onair_handler.obtainMessage()
                timer(initialDelay = 3000, period = 10000) {
                    val bundle = Bundle()
                    bundle.putString("command", "RadioResource.FAILED")
                    msg.data = bundle
                    onair_handler.sendMessage(msg)
                    cancel()
                }
            }
        }
    }
}


class GetYoutubeThumbnails() : AsyncTask<String, String, String>() {
    val API_KEY = "AIzaSyC-8Ut8ITfm9KKHE-8-5pre5CzeStgUC-w"

    override fun doInBackground(vararg param: String?): String? {
        val videoId = param[0]

        var apiurl = "https://www.googleapis.com/youtube/v3/videos"
        apiurl += "?key=${API_KEY}"
        apiurl += "&part=snippet"
        apiurl += "&id=${videoId}"
        apiurl += "&maxResults=50"

        Log.d(onairTag, "GetYoutubeThumbnails(). Req url: ${apiurl}")

        try {
            val url = URL(apiurl)
            val con: HttpURLConnection = url.openConnection() as HttpURLConnection
            con.setRequestMethod("GET")
            val br = BufferedReader(InputStreamReader(con.getInputStream(), "UTF-8"))
            var inputLine: String?
            val response = StringBuffer()
            while (br.readLine().also { inputLine = it } != null) {
                response.append(inputLine)
            }
            br.close()
            Log.d(onairTag, "length: ${response.length}")
            Log.d(onairTag, "result: ${response}")
            OnAir.parseVideoInfo( response.toString() )

        } catch (e: Exception) {
            Log.d(onairTag,"GetPlayLists Error: " + e.message)
        }

        return "success";
    }
}