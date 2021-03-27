package com.example.cloudradio

import android.content.Context
import android.os.AsyncTask
import android.os.Environment
import android.util.Log
import java.io.*
import java.net.URL
import java.net.URLConnection
import java.nio.charset.Charset

enum class RadioResource {
    OPEN_FAILED, DOWN_FAILED, SUCCESS
}

data class RadioCompletionMap (
    val title: String,                // pls 파일에서 읽어들인 title
    val id: Int,                      // channel 별 관리 목적의 id (arrayList 의 index 이기도 함)
    val filename: String,             // 실제 저장될 filename
    val fileaddress: String,          // 원본 file 주소
    val httpAddress: String           // 스트리밍 file 주소 (MediaPlayer 에 이걸 던진다)
)

/**
 * Radio Channel 의 Raw resource
 */
enum class RadioRawChannels {
    KBS_CLASSIC_FM {
        override fun getChannelTitle(): String = "KBS Classic FM"
        override fun getChannelFilename(): String  = "kbs_classic_fm.pls"
        override fun getChannelAddress(): String = "http://serpent0.duckdns.org:8088/kbsfm.pls"
    },
    KBS_COOL_FM {
        override fun getChannelTitle(): String = "KBS Cool FM"
        override fun getChannelFilename(): String  = "kbs_cool_fm.pls"
        override fun getChannelAddress(): String = "http://serpent0.duckdns.org:8088/kbs2fm.pls"
    },
    SBS_LOVE_FM {
        override fun getChannelTitle(): String = "SBS Love FM"
        override fun getChannelFilename(): String = "sbs_love_fm.pls"
        override fun getChannelAddress(): String = "http://serpent0.duckdns.org:8088/sbs2fm.pls"
    },
    SBS_POWER_FM {
        override fun getChannelTitle(): String = "SBS Power FM"
        override fun getChannelFilename(): String = "sbs_power_fm.pls"
        override fun getChannelAddress(): String = "http://serpent0.duckdns.org:8088/sbsfm.pls"
    },
    MBC_STANDARD_FM {
        override fun getChannelTitle(): String = "MBC 표준FM"
        override fun getChannelFilename(): String = "mbc_standard_fm.pls"
        override fun getChannelAddress(): String = "http://serpent0.duckdns.org:8088/mbcsfm.pls"
    },
    MBC_FM_FORU {
        override fun getChannelTitle(): String = "MBC FM4U"
        override fun getChannelFilename(): String = "mbc_fm_foru.pls"
        override fun getChannelAddress(): String = "http://serpent0.duckdns.org:8088/mbcfm.pls"
    },
    KBS_HAPPY_FM {
        override fun getChannelTitle(): String = "KBS HappyFM"
        override fun getChannelFilename(): String = "kbs_happy_fm.pls"
        override fun getChannelAddress(): String = "http://serpent0.duckdns.org:8088/kbs2radio.pls"
    },
    KBS_1_RADIO {
        override fun getChannelTitle(): String = "KBS 1Radio"
        override fun getChannelFilename(): String = "kbs_1_radio.pls"
        override fun getChannelAddress(): String = "http://serpent0.duckdns.org:8088/kbs1radio.pls"
    };
    abstract fun getChannelAddress(): String
    abstract fun getChannelFilename(): String
    abstract fun getChannelTitle(): String
}

interface AsyncCallback {
    fun onTaskDone(vararg string: String?)
}

class RadioChannelResources: AsyncCallback {

    private lateinit var mContext: Context
    lateinit var DEFAULT_FILE_PATH: String
    var bInitCompleted: Boolean = false

    companion object {
        var channelList = ArrayList<RadioCompletionMap>()

        private var instance: RadioChannelResources? = null

        fun getInstance(): RadioChannelResources =
                instance ?: synchronized(this) {
                    instance ?: RadioChannelResources().also {
                        instance = it
                    }
                }
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

    private fun removeChannelMapByfilename(filename: String) {
        for(i in channelList.indices) {
            if ( channelList.get(i).filename.equals(filename) ) {
                Log.d(onairTag, "remove channelMap: "+filename)
                channelList.removeAt(i)
                break
            }
        }
    }

    fun requestUpdateResource(filename: String) {
        Log.d(onairTag, "requestUpdateResource: " + filename)

        // remove channel array list
        removeChannelMapByfilename(filename)

        // remove exist file
        var fileobj = File(DEFAULT_FILE_PATH + filename )
        if ( fileobj.exists() ) {
            Log.d(onairTag, "remove file: "+fileobj)
            fileobj.delete()
        }
        if ( fileobj.exists() ) { Log.d(onairTag, "remove file is failed") }
        else { Log.d(onairTag, "remove file success") }


        // download again
        DownloadFileFromURL(this).execute(getRadioChannelHttpAddress(filename), filename)
    }


    fun initResources(context: Context) {
        mContext = context
        DEFAULT_FILE_PATH = mContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).toString() + "/"

        for(i in 0..RadioRawChannels.values().size-1 ) {
            Log.d(onairTag, "initResources( $i ) - " + DEFAULT_FILE_PATH + RadioRawChannels.values()[i].getChannelFilename() )
            var fileobj = File(DEFAULT_FILE_PATH + RadioRawChannels.values()[i].getChannelFilename() )
            if ( fileobj.exists() ) {
                Log.d(onairTag, "File exist")
                readChannelFile( fileobj )
            } else {
                Log.d(onairTag, "File don't exist")
                DownloadFileFromURL(this).execute(RadioRawChannels.values()[i].getChannelAddress(), RadioRawChannels.values()[i].getChannelFilename())
            }
        }
    }

    private fun setChannelsFromFile(content: String) {
        var httpAddress = content.substring(content.indexOf("File1=")+6, content.indexOf("Title1=") - 1)
        var title = content.substring(content.indexOf("Title1=")+7, content.indexOf("Length1=") - 1)
        Log.d(onairTag, "title($title) httpAddress($httpAddress)")

        for(i in 0..RadioRawChannels.values().size-1) {
            if ( title.equals( RadioRawChannels.values()[i].getChannelTitle()) ) {
                var map = RadioCompletionMap(title, channelList.size,
                        RadioRawChannels.values()[i].getChannelFilename(),
                        RadioRawChannels.values()[i].getChannelAddress(),  httpAddress )
                Log.d(onairTag, "channel resource add ok - title($title) - channelList.size: "+channelList.size)
                channelList.add(map)
                break
            }
        }

        if ( channelList.size == RadioRawChannels.values().size ) {
            Log.d(onairTag, "initResources are completed")
            bInitCompleted = true
            OnAir.getInstance().notifyRadioResourceUpdate(null, RadioResource.SUCCESS)
        }
    }

    override fun onTaskDone(vararg arg: String?) {
        Log.d(onairTag, "callback Type: " + arg[0])
        when(arg[0])
        {
            "Download" -> OpenFileFromPath(this).execute(arg[1])
            "fileopen" -> setChannelsFromFile(arg[1]!!)
            "failed" -> {
                when( arg[1] ) {
                    "openFile" -> sendCallback(arg[1], arg[2])                // filename
                    "downFile" -> sendCallback(arg[1], arg[2], arg[3])        // fileaddress , filename
                    else -> Log.d(onairTag, "ignore failed action")
                }
            }
            else -> Log.d(onairTag, "do nothing type: " + arg[0])
        }
    }

    private fun sendCallback(vararg arg: String?) {
        Log.d(onairTag, "doFailedAction: " + arg[0])
        when( arg[0] ) {
            "openFile" -> {
                Log.d(onairTag, "open failed filename: "+ arg[1])
                arg[1]?.let { OnAir.getInstance().notifyRadioResourceUpdate(it, RadioResource.OPEN_FAILED) }
            }
            "downFile" -> {
                Log.d(onairTag, "down failed addr: "+ arg[1]+ ", filename: "+ arg[2])
                Log.d(onairTag, "reset Button Text")
                arg[2]?.let { OnAir.getInstance().notifyRadioResourceUpdate(it, RadioResource.DOWN_FAILED) }
            }
            else -> Log.d(onairTag, "ignore failed action")
        }
    }

    private fun readChannelFile(fileobj: File) {
        if ( fileobj.canRead() ) {
            var ins: InputStream = fileobj.inputStream()
            var content = ins.readBytes().toString(Charset.defaultCharset())
            setChannelsFromFile( content )
        } else {
            Log.d(onairTag, "Can't read file: "+fileobj.toString())
        }
    }

    internal class OpenFileFromPath(context: RadioChannelResources): AsyncTask<String, String, String>() {
        val callback: AsyncCallback = context
        var bCompleted: Boolean = false

        override fun doInBackground(vararg params: String?): String? {
            val fileobj = File(params[0])
            var content: String? = null
            Log.d(onairTag, "OpenFileFromPath:" + params[0])

            if (fileobj.exists() && fileobj.canRead()) {

                try {
                    var ins: InputStream = fileobj.inputStream()
                    content = ins.readBytes().toString(Charset.defaultCharset())
                    bCompleted = true
                } catch (e: Exception) {
                    Log.d(onairTag, "OpenFileFromPath error: "+e.message)
                }
            }

            if (bCompleted) callback.onTaskDone("fileopen", content)
            else {
                Log.d(onairTag, "fileopen failed")
                callback.onTaskDone("failed", "openFile", params[0])
            }

            return null
        }

    }


    /**
     * Background Async Task to download file
     */
    internal class DownloadFileFromURL(context: RadioChannelResources) :
            AsyncTask<String?, String?, String?>() {

        val callback: AsyncCallback = context
        var filename: String = RadioChannelResources.getInstance().DEFAULT_FILE_PATH
        var bCompleted: Boolean = false

        /**
         * Before starting background thread Show Progress Bar Dialog
         */
        override fun onPreExecute() {
            super.onPreExecute()
//            JColorChooser.showDialog(progress_bar_type)
        }

        /**
         * Downloading file in background thread
         */
        override fun doInBackground(vararg f_url: String?): String? {
            Log.d(onairTag, "DownloadFileFromURL.doInBackground")
            var count: Int
            try {
                val url = URL(f_url[0])
                Log.d(onairTag, "down from: "+url)
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
                Log.d(onairTag, "filename:" + filename)
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
                Log.e("Download Error: ", e.message)
            } catch (e: Exception) {
                Log.e("Download Error: ", e.message)
            } finally {

            }
            Log.d(onairTag, "DownloadFileFromURL.doInBackground end")

            if (bCompleted) callback.onTaskDone("Download", filename)
            else {
                Log.d(onairTag, "Download failed")
                callback.onTaskDone( "failed", "downFile", f_url[0], f_url[1])
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
            Log.d(onairTag, "download finished: " + file_url)
        }
    }
}