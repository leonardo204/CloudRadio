package com.example.cloudradio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.*
import android.os.StrictMode.VmPolicy
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
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
        Log.d(moreTag, "handler handleMessage: " + msg)

        val bundle = msg.data
        val command = bundle.getString("command")
        val value = bundle.getString("value")

        if ( value.contains("cloudradio.apk") ) {
            when (command) {
                "success" -> {
                    More.buttonUpdate(More.btn_app_download, "앱 다운로드 완료", false)
                    More.reserveUpdate(
                        More.btn_app_download,
                        "앱 다운로드",
                        false,
                        3000,
                        "앱 다운로드 완료되었습니다.\n${value} 에서 앱을 설치해 주십시오.",
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
        }
    }
}


@SuppressLint("StaticFieldLeak")
object More : Fragment(), AsyncCallback {

    lateinit var DEFAULT_FILE_PATH: String

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


    var mContext: Context? = null
    var APP_VERSION_URL = "http://zerolive7.iptime.org:9093/api/public/dl/3I4X2Lnj/01_project/cloudradio/app_version.json"
    var APK_FILE_URL = "http://zerolive7.iptime.org:9093/api/public/dl/AyHsyPHc/01_project/cloudradio/cloudradio.apk"
    var CHANNEL_FILE_URL = "http://zerolive7.iptime.org:9093/api/public/dl/z0Qmcjjq/01_project/cloudradio/channels.json"

    private val REQUEST_WRITE_PERMISSION = 786

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_WRITE_PERMISSION && grantResults[0] == PackageManager.PERMISSION_GRANTED) Toast.makeText(
            mContext,
            "Permission granted",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_WRITE_PERMISSION
            )
        }
    }

    private fun canReadWriteExternal(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                ContextCompat.checkSelfPermission(
                    mContext!!,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
    }

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
        CRLog.d( "message: $message")
        Toast.makeText(mContext, message, Toast.LENGTH_LONG).show()
    }

    private fun getAppBigger(ver1: String, ver2: String): String? {
        CRLog.d( "getAppBigger: $ver1 $ver2")
        val str1 = ver1.substring(1)
        val str2 = ver2.substring(1)
        if (str1.substring(0, 3).toFloat() > str2.substring(0, 3).toFloat()) {
            CRLog.d( "1) " + str1.substring(0, 3) + " > " + str2.substring(0, 3))
            return ver1
        } else if (str2.substring(0, 3).toFloat() > str1.substring(0, 3).toFloat()) {
            CRLog.d( "2) " + str2.substring(0, 3) + " > " + str1.substring(0, 3))
            return ver2
        } else if (str1.substring(2).toFloat() > str2.substring(2).toFloat()) {
            CRLog.d( "3) " + str1.substring(2) + " > " + str2.substring(2))
            return ver1
        }
        CRLog.d( "$str1 < $str2")
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
                CRLog.d( "checkVersion error: " + e.message)
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
        CRLog.d( "getChannelVersion internal: ${sb}")
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
                CRLog.d( "checkVersion error: " + e.message)
            }
        }

        CRLog.d( "getChannelVersion download: ${content}")
        content?.let {
            val element = Json.parseToJsonElement(it)
            version2 = element.jsonObject["version"].toString().replace("\"", "")

            CRLog.d( "sys ch ver($version1)  down ch ver($version2)")

            if ( version1.toInt() - version2.toInt() < 0) {
                mChannelVersion = version2
            } else {
                mChannelVersion = version1
            }
        }

        if ( content == null ) {
            mChannelVersion = version1
        }

        CRLog.d( "getChannelVersion mChannelVersion:${mChannelVersion}")

        return mChannelVersion
    }

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        if (container != null) {
            mContext = container.context
        }

        DEFAULT_FILE_PATH = mContext!!.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).toString() + "/"

        val builder = VmPolicy.Builder()
        StrictMode.setVmPolicy(builder.build())

        if ( !canReadWriteExternal() ) {
            requestPermission()
        }

        var view: ViewGroup = inflater.inflate(R.layout.fragment_more, container, false) as ViewGroup

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
        CRLog.d( "device dpi => " + metrics?.density)
        txt_dpi.setText("해상도 DPI: ${metrics?.density?.let { getDPIText(it) }}")

        return view
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
        CRLog.d( "onButtonClick: $command")
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
                filename = filename + f_url[1]
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