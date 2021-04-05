package com.example.cloudradio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import java.io.*
import java.lang.Thread.sleep
import java.net.URL
import java.net.URLConnection
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.timer


var moreTag = "CR_more"

val more_handler: Handler = @SuppressLint("HandlerLeak")
object : Handler() {
    override fun handleMessage(msg: Message) {
        Log.d(moreTag, "handler handleMessage: " + msg)

        var bundle = msg.data
        var command = bundle.getString("command")
        var value = bundle.getString("value")

        when(command) {
            "success" -> {
                More.buttonUpdate("앱 다운로드 완료", false)
                More.reserveUpdate("앱 다운로드", true, 3000, "앱 다운로드 완료되었습니다.\n${value} 에서 앱을 설치해 주십시오.")
            }
            "failed" -> {
                More.buttonUpdate("앱 다운로드 실패!!", false)
                More.reserveUpdate("앱 다운로드", true, 3000, "앱 다운로드 실패했습니다.\n잠시후 다시 시도하여 주십시오.")
            }
        }
    }
}

@SuppressLint("StaticFieldLeak")
object More : Fragment(), AsyncCallback {

    var bNeedReset: Boolean = false

    lateinit var btn_app_download: Button
    lateinit var DEFAULT_FILE_PATH: String
    lateinit var txt_version_buildtime : TextView

    var mContext: Context? = null
    var FILE_URL = "http://zerolive7.iptime.org:9093/api/public/dl/AyHsyPHc/01_project/cloudradio/cloudradio.apk"


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

    fun buttonUpdate(text: String, enable: Boolean) {
        Log.d(moreTag, "buttonUpdate: $text ($enable)")

        btn_app_download.setText(text)
        btn_app_download.isEnabled = enable
    }

    fun reserveUpdate(buttonText: String, enable: Boolean, time: Long, toastMessage: String) {
        sleep(time)
        buttonUpdate(buttonText, enable)
        makeToastMessage(toastMessage)
    }

    fun makeToastMessage(message: String) {
        Log.d(moreTag, "message: $message")
        Toast.makeText(mContext, message, Toast.LENGTH_LONG).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        if (container != null) {
            mContext = container.context
        }

        val builder = VmPolicy.Builder()
        StrictMode.setVmPolicy(builder.build())

        if ( !canReadWriteExternal() ) {
            requestPermission()
        }

        var view: ViewGroup = inflater.inflate(R.layout.fragment_more, container, false) as ViewGroup

        var res = mContext?.resources
        txt_version_buildtime = view.findViewById(R.id.txt_version_buildtime)
        txt_version_buildtime.setText(res?.getString(R.string.version_cloudradio))

        btn_app_download = view.findViewById(R.id.btn_app_download)
        btn_app_download.setOnClickListener { onButtonClick("APP DOWNLOAD") }
        DEFAULT_FILE_PATH = mContext!!.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).toString() + "/"

        return view
    }


    private fun onButtonClick(command: String) {
        Log.d(moreTag, "onButtonClick: $command")
        bNeedReset = false
        when( command ) {
            "APP DOWNLOAD" -> {
                buttonUpdate("앱 다운로드 중입니다.", false)
                DownloadApplication(this).execute(FILE_URL, "cloudradio.apk")
            }
        }
    }

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
                Log.e(moreTag, "Download Error: " + e.message)
            } catch (e: Exception) {
                Log.e(moreTag, "Download Error: " + e.message)
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