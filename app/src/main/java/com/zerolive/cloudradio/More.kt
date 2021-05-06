package com.zerolive.cloudradio

import android.annotation.SuppressLint
import android.content.Context
import android.os.*
import android.os.StrictMode.VmPolicy
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.zerolive.cloudradio.R
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.*
import java.lang.Thread.sleep
import java.net.URL
import java.net.URLConnection
import java.nio.charset.Charset
import java.util.*
import kotlin.concurrent.timer


var moreTag = "CR_more"


val more_handler: Handler = @SuppressLint("HandlerLeak")
object : Handler() {
    @SuppressLint("SetTextI18n")
    override fun handleMessage(msg: Message) {
        val bundle = msg.data
        val command = bundle.getString("command")
        val value = bundle.getString("value")

        Log.d(moreTag, "more_handler handleMessage: ${command} - ${value}")


        if ( value.contains("cloudradio.apk") ) {
            when (command) {
                "success" -> {
                    More.buttonUpdate(More.btn_app_download, "앱 다운로드 완료", false)
                    More.reserveUpdate(
                        More.btn_app_download,
                        "앱 다운로드",
                        false,
                        3000,
                        "앱 다운로드 완료되었습니다.\n다운로드 폴더에서 앱을 설치해 주십시오.",
                        null
                    )
                }
                "failed" -> {
                    More.buttonUpdate(More.btn_app_download, "앱 다운로드 실패!!", false)
                    More.reserveUpdate(
                        More.btn_app_download,
                        "앱 다운로드",
                        false,
                        3000,
                        "앱 다운로드 실패했습니다.\n잠시후 다시 시도하여 주십시오.",
                        null
                    )
                }
            }
        } else if ( value.contains("channels.json") ) {
            when (command) {
                "success" -> {
                    More.buttonUpdate(More.btn_channel_update, "채널 업데이트 완료", false)
                    More.reserveUpdate(
                        More.btn_channel_update,
                        "채널 업데이트",
                        false,
                        3000,
                        "채널 업데이트 완료되었습니다.",
                        null
                    )
                    More.txt_version.setText(More.mAppVersion + " (" + More.getChannelVersion() + ")")
                    More.mContext?.let {
                        Program.resetProgramButtons()
                        RadioChannelResources.channelList.clear()
                        RadioChannelResources.channelSize = 0
                        RadioChannelResources.initResources(it)
                    }
                    //Program.resetAction()
                }
                "failed" -> {
                    More.buttonUpdate(More.btn_channel_update, "채널 업데이트 실패!!", false)
                    More.reserveUpdate(
                        More.btn_channel_update,
                        "채널 업데이트",
                        false,
                        3000,
                        "채널 업데이트 실패했습니다.\n잠시후 다시 시도하여 주십시오.",
                        null
                    )
                }
            }
        } else if ( value.contains("app_version.json") ) {
            when(command) {
                "success" -> {
                    More.buttonUpdate(More.btn_version_check, "업데이트 확인 완료", false)
                    More.reserveUpdate(
                        More.btn_version_check,
                        "업데이트 확인",
                        true,
                        3000,
                        "업데이트 확인 완료했습니다.",
                        value
                    )
                }
                "failed" -> {
                    More.buttonUpdate(More.btn_version_check, "업데이트 확인 실패!!", false)
                    More.reserveUpdate(
                        More.btn_version_check,
                        "업데이트 확인",
                        true,
                        3000,
                        "업데이트 확인에 실패했습니다.\n잠시후 다시 시도하여 주십시오.",
                        null
                    )
                }
            }
        } else if ( value.contains("Timer") ) {
            Log.d(moreTag, "timer button update")
            when(command) {
                "updateTimer" -> {
                    More.btn_timer_start.setText("${More.mTimeMin} 분  ${More.mTimeSecond} 초")
                    More.btn_timer_start.isEnabled = false
                }
                "finishTimer" -> {
                    More.btn_timer_start.setText("시작")
                    More.btn_timer_start.isEnabled = true
                    OnAir.requestStopRadioService()
                }
            }
        }
    }
}

object SeekBarHandler : SeekBar.OnSeekBarChangeListener {
    var mProgress: Int = 0

    // 움직이는 중 불림
    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
//        Log.d(moreTag,"onProgressChanged progress: ${progress}")
        mProgress = progress
        drawTimeText()
    }

    // 움직이기 시작할 때 불림
    override fun onStartTrackingTouch(seekBar: SeekBar?) {
        Log.d(moreTag,"onStartTrackingTouch progress: $mProgress")
    }

    // 움직임을 멈췄을 때 불림
    override fun onStopTrackingTouch(seekBar: SeekBar?) {
//        Log.d(moreTag,"onStartTrackingTouch progress: ${mProgress}")
        drawTimeText()
    }

    private fun drawTimeText() {
        if ( mProgress == 0 ) {
            More.txt_selected_time.setText("(아래를 움직여서 시간 설정)")
            More.btn_timer_start.isEnabled = false
        } else {
            More.txt_selected_time.setText("$mProgress 분")
            More.mInitialTimeValue = mProgress
            More.btn_timer_start.isEnabled = true
        }
    }
}


@SuppressLint("StaticFieldLeak")
object More : Fragment(), AsyncCallback {

    lateinit var DEFAULT_FILE_PATH: String
    lateinit var DOWNLOAD_PATH: String

    lateinit var mAppVersion: String
    lateinit var mChannelVersion: String

    // app update
    lateinit var btn_app_download: Button
    lateinit var btn_version_check: Button
    lateinit var txt_version : TextView
    lateinit var txt_version_result: TextView

    // channel update
    lateinit var btn_channel_update: Button

    // dpi
    lateinit var txt_dpi: TextView

    // timer
    lateinit var seekbar: SeekBar
    lateinit var txt_selected_time: TextView
    lateinit var btn_timer_start: Button
    lateinit var btn_timer_cancel: Button
    lateinit var btn_timer_minus: ImageButton
    lateinit var btn_timer_plus: ImageButton
    var mTimertask: Timer? = null
    var mInitialTimeValue: Int = 0
    var mTimeSecond: Int = 0
    var mTimeMin: Int = 0

    var bInitialized = false

    var mContext: Context? = null
    var APP_VERSION_URL = "http://zerolive7.iptime.org:9093/api/public/dl/3I4X2Lnj/01_project/cloudradio/app_version.json"
    var APK_FILE_URL = "http://zerolive7.iptime.org:9093/api/public/dl/AyHsyPHc/01_project/cloudradio/cloudradio.apk"
    var CHANNEL_FILE_URL = "http://zerolive7.iptime.org:9093/api/public/dl/z0Qmcjjq/01_project/cloudradio/channels.json"

    fun install(path: String) {
        MainActivity.getInstance().installApp(path)
    }

    fun buttonUpdate(button: Button, text: String, enable: Boolean) {
        Log.d(moreTag,"buttonUpdate: $text ($enable)")

        button.setText(text)
        button.isEnabled = enable
    }

    fun reserveUpdate(
        button: Button,
        buttonText: String,
        enable: Boolean,
        time: Long,
        toastMessage: String,
        verFilePath: String?
    ) {
        verFilePath?.let { checkVersion(it) }
        sleep(time)
        buttonUpdate(button, buttonText, enable)
        makeToastMessage(toastMessage)
    }

    fun makeToastMessage(message: String) {
        CRLog.d("message: $message")
        Toast.makeText(mContext, message, Toast.LENGTH_LONG).show()
    }

    private fun getAppBigger(ver1: String, ver2: String): String? {
        CRLog.d("getAppBigger: $ver1 $ver2")
        val str1 = ver1.substring(1)
        val str2 = ver2.substring(1)
        if (str1.substring(0, 3).toFloat() > str2.substring(0, 3).toFloat()) {
            CRLog.d("1) " + str1.substring(0, 3) + " > " + str2.substring(0, 3))
            return ver1
        } else if (str2.substring(0, 3).toFloat() > str1.substring(0, 3).toFloat()) {
            CRLog.d("2) " + str2.substring(0, 3) + " > " + str1.substring(0, 3))
            return ver2
        } else if (str1.substring(2).toFloat() > str2.substring(2).toFloat()) {
            CRLog.d("3) " + str1.substring(2) + " > " + str2.substring(2))
            return ver1
        }
        CRLog.d("$str1 < $str2")
        return ver2
    }

    private fun checkVersion(filepath: String) {
        val fileobj = File(filepath)
        var content: String? = null

        if (fileobj.exists() && fileobj.canRead()) {
            try {
                var ins: InputStream = fileobj.inputStream()
                content = ins.readBytes().toString(Charset.defaultCharset())
            } catch (e: Exception) {
                CRLog.d("checkVersion error: " + e.message)
            }
        }

        content?.let {
            val element = Json.parseToJsonElement(it)
            val nAppVer = element.jsonObject["version_cloudradio"].toString().replace("\"", "")
            val nChVer = element.jsonObject["version_channel"].toString().replace("\"", "")

            CRLog.d(

                "get version ( $mAppVersion vs $nAppVer  -  $mChannelVersion vs $nChVer )"
            )

            var needAppUpdate: Boolean = false
            var needChannelUpdate: Boolean = false
            if ( !mAppVersion.equals(nAppVer) ) {
                var bigger = getAppBigger(mAppVersion, nAppVer)
                if ( nAppVer.equals(bigger) ) {
                    needAppUpdate = true
                }
            }

            if ( !mChannelVersion.equals(nChVer) ) {
                if ( (mChannelVersion.toInt() - nChVer.toInt()) < 0 ) {
                    needChannelUpdate = true
                }
            }

            if ( needAppUpdate && needChannelUpdate ) {
                txt_version_result.setText("앱/채널 업데이트 가능")
                btn_channel_update.isEnabled = true
                btn_app_download.isEnabled = true
            } else if ( needAppUpdate ) {
                txt_version_result.setText("앱 업데이트 가능")
                btn_app_download.isEnabled = true
            } else if ( needChannelUpdate ) {
                txt_version_result.setText("채널 업데이트 가능")
                btn_channel_update.isEnabled = true
            } else {
                txt_version_result.setText("최신 버전 사용중입니다.")
            }
        }
    }

    fun getChannelVersion(): String {
        // 내부 버전
        val inputStream = mContext!!.assets.open("channels.json")
        val sb = StringBuilder()
        inputStream?.let {
            val reader = BufferedReader(InputStreamReader(inputStream))
            val readLines = reader.readLines()
            readLines.forEach {
                sb.append(it)
            }
            it.close()
        }
        CRLog.d("getChannelVersion internal: ${sb}")
        val element = Json.parseToJsonElement(sb.toString())
        val version1 = element.jsonObject["version"].toString().replace("\"", "")
        val version2: String

        // 다운 버전
        val file = File(DEFAULT_FILE_PATH + "/" + "channels.json")
        var content: String? = null

        if ( file.exists() && file.canRead() ) {
            try {
                var ins: InputStream = file.inputStream()
                content = ins.readBytes().toString(Charset.defaultCharset())
            } catch (e: Exception) {
                CRLog.d("checkVersion error: " + e.message)
            }
        }

        CRLog.d("getChannelVersion download: ${content}")
        content?.let {
            val element = Json.parseToJsonElement(it)
            version2 = element.jsonObject["version"].toString().replace("\"", "")

            CRLog.d("sys ch ver($version1)  down ch ver($version2)")

            if ( version1.toInt() - version2.toInt() < 0) {
                mChannelVersion = version2
            } else {
                mChannelVersion = version1
            }
        }

        if ( content == null ) {
            mChannelVersion = version1
        }

        CRLog.d("getChannelVersion mChannelVersion:$mChannelVersion")

        return mChannelVersion
    }

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        CRLog.d("onCreateView ${bInitialized}")

        val view: ViewGroup = inflater.inflate(R.layout.fragment_more, container, false) as ViewGroup

        if (container != null) {
            mContext = container.context
        }

        DEFAULT_FILE_PATH = mContext!!.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).toString() + "/"
        DOWNLOAD_PATH = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString() + "/"

        val builder = VmPolicy.Builder()
        StrictMode.setVmPolicy(builder.build())

//        CRLog.d("canReadWriteExternal(): ${canReadWriteExternal()}")
//        if ( !canReadWriteExternal() ) {
//            requestPermission()
//        }


        // version check
        var res = mContext?.resources
        txt_version = view.findViewById(R.id.txt_app_version)
        mAppVersion = res?.getString(R.string.version_cloudradio).toString()
        txt_version.setText(mAppVersion + " (" + getChannelVersion() + ")")

        txt_version_result = view.findViewById(R.id.txt_app_version_result)
        txt_version_result.setText("")

        btn_version_check = view.findViewById(R.id.btn_app_version_check)
        btn_version_check.setOnClickListener { onButtonClick("VERSION CHECK") }

        // app update
        btn_app_download = view.findViewById(R.id.btn_app_download)
        btn_app_download.setOnClickListener { onButtonClick("APP UPDATE") }
        btn_app_download.isEnabled = false

        // channel update
        btn_channel_update = view.findViewById(R.id.btn_channel_update)
        btn_channel_update.setOnClickListener { onButtonClick("CHANNEL UPDATE") }
        btn_channel_update.isEnabled = false

        /*
            0.75 - ldpi
            1.0 - mdpi
            1.5 - hdpi
            2.0 - xhdpi
            3.0 - xxhdpi
            4.0 - xxxhdpi
         */
        txt_dpi = view.findViewById(R.id.txt_dpi)
        val metrics = mContext?.resources?.displayMetrics
        CRLog.d("device dpi => " + metrics?.density)
        txt_dpi.setText("해상도 DPI: ${metrics?.density?.let { getDPIText(it) }}")

        // timer
        seekbar = view.findViewById(R.id.seekbar_timer)
        seekbar.setOnSeekBarChangeListener(SeekBarHandler)
        txt_selected_time = view.findViewById(R.id.txt_selected_time)
        btn_timer_start = view.findViewById(R.id.btn_start_timer)
        btn_timer_start.setOnClickListener { onTimerStart() }
        btn_timer_start.isEnabled = false
        btn_timer_cancel = view.findViewById(R.id.btn_timer_cancel)
        btn_timer_cancel.setOnClickListener { onTimerCancel() }
        btn_timer_minus = view.findViewById(R.id.btn_timer_minus)
        btn_timer_minus.setOnClickListener { onTimerPlusMinus(-1) }
        btn_timer_plus = view.findViewById(R.id.btn_timer_plus)
        btn_timer_plus.setOnClickListener { onTimerPlusMinus(1) }

        bInitialized = true

        return view
    }

    private fun onTimerPlusMinus(degree: Int) {
        Log.d(moreTag, "onTimerPlusMinus: ${degree}")

        if (( mInitialTimeValue == 0 && degree == -1 )
            || ( mInitialTimeValue == 120 && degree == 1 )) {
            Log.d(moreTag, "Ignore plus/minus curProgress: ${mInitialTimeValue}")
            return
        }

        seekbar.let {
            btn_timer_start.isEnabled = true
            mInitialTimeValue += degree
            it.progress = mInitialTimeValue
            if ( mInitialTimeValue == 0 ) {
                txt_selected_time.setText("(아래를 움직여서 시간 설정)")
            } else {
                txt_selected_time.setText("${mInitialTimeValue} 분")
            }
        }
    }

    private fun onTimerStart() {
        Log.d(moreTag,"onTimerStart: $mInitialTimeValue")
        if ( mInitialTimeValue <= 0 || mInitialTimeValue > 120 ) {
            Log.d(moreTag,"ignore TimeValue: $mInitialTimeValue")
            return
        }

        mTimeMin = mInitialTimeValue -1
        mTimeSecond = 60
        var count = 0

        mTimertask = timer(period = 1000) {
            val msg = more_handler.obtainMessage()
            val bundle = Bundle()
            bundle.putString("value", "Timervalue")

            if ( mTimeMin == 0 && mTimeSecond == 0 ) {
                Log.d(moreTag,"timer over")
                mTimertask!!.cancel()
                bundle.putString("command", "finishTimer")

            } else {
                if (count < 60) {
                    count++
                    mTimeSecond--
                } else {
                    count = 0
                    mTimeSecond = 60
                    mTimeMin--
                }
                bundle.putString("command", "updateTimer")
            }

            msg.data = bundle
            more_handler.sendMessage(msg)
        }
    }

    private fun onTimerCancel() {
        Log.d(moreTag,"onTimerCancel: $mInitialTimeValue")
        mTimertask?.cancel()
        btn_timer_start.setText("시작")
        btn_timer_start.isEnabled = false

    }

    private fun getDPIText(density: Float): String {
        when {
            density <= 0.75f -> {
                return "ldpi"
            }
            density <= 1.0f -> {
                return "mdpi"
            }
            density <= 1.5f -> {
                return "hdpi"
            }
            density <= 2.0f -> {
                return "xhdpi"
            }
            density <= 3.0f -> {
                return "xxhdpi"
            }
            density <= 4.0f -> {
                return "xxxhdpi"
            }
            else -> return "unknown"
        }
    }


    private fun onButtonClick(command: String) {
        CRLog.d("onButtonClick: $command")
        txt_version_result.setText("")

        when( command ) {
            "VERSION CHECK" -> {
                buttonUpdate(btn_version_check, "업데이트 확인 중입니다.", false)
                versionCheck()
            }
            "CHANNEL UPDATE" -> {
                buttonUpdate(btn_channel_update, "업데이트 중", false)
                DownloadApplication(this).execute(CHANNEL_FILE_URL, "channels.json")
            }
            "APP UPDATE" -> {
                buttonUpdate(btn_app_download, "다운로드 중", false)
                DownloadApplication(this).execute(APK_FILE_URL, "cloudradio.apk")
            }
        }
    }

    private fun versionCheck() {
        DownloadApplication(this).execute(APP_VERSION_URL, "app_version.json")
    }

    // command - success or failed
    // value - filename (success  시에는 path 가 됨)
    override fun onTaskDone(vararg string: String?) {
        Log.d(moreTag, "download result: ${string[0]}")

        var msg = more_handler.obtainMessage()
        var bundle = Bundle()
        bundle.putString("command", string[0])
        bundle.putString("value", string[1])

        msg.data = bundle
        more_handler.sendMessage(msg)
    }


    /**
     * Background Async Task to download file
     */
    @SuppressLint("StaticFieldLeak")
    internal class DownloadApplication(context: More) :
        AsyncTask<String?, String?, String?>() {

        val callback: AsyncCallback = context
        var filename: String = DEFAULT_FILE_PATH
        var bCompleted: Boolean = false

        /**
         * Before starting background thread Show Progress Bar Dialog
         */
        override fun onPreExecute() {
            super.onPreExecute()
        }

        /**
         * Downloading file in background thread
         */
        override fun doInBackground(vararg f_url: String?): String? {
            Log.d(moreTag, "DownloadApplication.doInBackground")
            var count: Int
            try {
                val url = URL(f_url[0])
                Log.d(moreTag, "down from: " + url)
                val connection: URLConnection = url.openConnection()
                connection.connect()

                // this will be useful so that you can show a tipical 0-100%
                // progress bar
                val lenghtOfFile: Int = connection.getContentLength()

                // download the file
                val urlInput: InputStream = url.openStream()
                val input: InputStream = BufferedInputStream(urlInput)


                // Output stream
                if ( f_url[1]!!.contains(".apk") ) {
                    filename = DOWNLOAD_PATH + f_url[1]
                } else {
                    filename = filename + f_url[1]
                }
                Log.d(moreTag, "filename:" + filename)

                // file remove if it exist
                var fileobj = File(filename)
                if ( fileobj.exists() ) {
                    Log.d(moreTag, "remove previous version")
                    fileobj.delete()
                }

                val output: OutputStream = FileOutputStream(filename)
                val data = ByteArray(1024)
                var total: Long = 0
                while (input.read(data).also { count = it } != -1) {
                    total += count.toLong()
                    // publishing the progress....
                    // After this onProgressUpdate will be called
                    publishProgress("" + (total * 100 / lenghtOfFile).toInt())

                    // writing data to file
                    output.write(data, 0, count)
                }

                // flushing output
                output.flush()

                // closing streams
                output.close()
                input.close()
                bCompleted = true
            } catch (e: IOException) {
                Log.d(moreTag, "Download Error: " + e.message)
            } catch (e: Exception) {
                Log.d(moreTag, "Download Error: " + e.message)
            } finally {

            }
            Log.d(moreTag, "DownloadFileFromURL.doInBackground end")

            if (bCompleted) {
                callback.onTaskDone("success", filename)
            }
            else {
                Log.d(moreTag, "Download failed")
                callback.onTaskDone("failed", f_url[0])
            }

            return null
        }

        /**
         * Updating progress bar
         */
        override fun onProgressUpdate(vararg values: String?) {
        }

        /**
         * After completing background task Dismiss the progress dialog
         */
        override fun onPostExecute(file_url: String?) {
        }
    }
}