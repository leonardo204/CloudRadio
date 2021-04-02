package com.example.cloudradio

import android.annotation.SuppressLint
import android.content.Context
import android.os.AsyncTask
import android.os.Environment
import android.util.Log
import java.io.*
import java.net.URL
import java.net.URLConnection
import java.nio.charset.Charset

var resourceTag = "CR_Resource"

enum class RadioResource {
    OPEN_FAILED, DOWN_FAILED, SUCCESS
}

data class RadioCompletionMap (
    val defaultButtonText: String,    // button 에 표시할 기본 텍스트
    val title: String,                // pls 파일에서 읽어들인 title
    val id: Int,                      // channel 별 관리 목적의 id (arrayList 의 index 이기도 함)
    val filename: String,             // 실제 저장될 filename
    val fileaddress: String,          // 원본 file 주소
    val httpAddress: String?           // 스트리밍 file 주소 (MediaPlayer 에 이걸 던진다)
)

/**
 * Radio Channel 의 Raw resource
 */
enum class RadioRawChannels {
    KBS_CLASSIC_FM {
        override fun getChannelTitle(): String = "KBS Classic FM"
        override fun getChannelFilename(): String  = "kbs_classic_fm.pls"
        override fun getChannelAddress(): String = "http://serpent0.duckdns.org:8088/kbsfm.pls"
        override fun getDefaultButtonText(): String = "KBS 클래식 FM"
    },
    KBS_COOL_FM {
        override fun getChannelTitle(): String = "KBS Cool FM"
        override fun getChannelFilename(): String  = "kbs_cool_fm.pls"
        override fun getChannelAddress(): String = "http://serpent0.duckdns.org:8088/kbs2fm.pls"
        override fun getDefaultButtonText(): String = "KBS 쿨 FM"
    },
    SBS_LOVE_FM {
        override fun getChannelTitle(): String = "SBS Love FM"
        override fun getChannelFilename(): String = "sbs_love_fm.pls"
        override fun getChannelAddress(): String = "http://serpent0.duckdns.org:8088/sbs2fm.pls"
        override fun getDefaultButtonText(): String = "SBS 러브 FM"
    },
    SBS_POWER_FM {
        override fun getChannelTitle(): String = "SBS Power FM"
        override fun getChannelFilename(): String = "sbs_power_fm.pls"
        override fun getChannelAddress(): String = "http://serpent0.duckdns.org:8088/sbsfm.pls"
        override fun getDefaultButtonText(): String = "SBS 파워 FM"
    },
    MBC_STANDARD_FM {
        override fun getChannelTitle(): String = "MBC 표준FM"
        override fun getChannelFilename(): String = "mbc_standard_fm.pls"
        override fun getChannelAddress(): String = "http://serpent0.duckdns.org:8088/mbcsfm.pls"
        override fun getDefaultButtonText(): String = "MBC 표준 FM"
    },
    MBC_FM_FORU {
        override fun getChannelTitle(): String = "MBC FM4U"
        override fun getChannelFilename(): String = "mbc_fm_foru.pls"
        override fun getChannelAddress(): String = "http://serpent0.duckdns.org:8088/mbcfm.pls"
        override fun getDefaultButtonText(): String = "MBC FM 포 유"
    },
    KBS_HAPPY_FM {
        override fun getChannelTitle(): String = "KBS HappyFM"
        override fun getChannelFilename(): String = "kbs_happy_fm.pls"
        override fun getChannelAddress(): String = "http://serpent0.duckdns.org:8088/kbs2radio.pls"
        override fun getDefaultButtonText(): String = "KBS 해피 FM"
    },
    KBS_1_RADIO {
        override fun getChannelTitle(): String = "KBS 1Radio"
        override fun getChannelFilename(): String = "kbs_1_radio.pls"
        override fun getChannelAddress(): String = "http://serpent0.duckdns.org:8088/kbs1radio.pls"
        override fun getDefaultButtonText(): String = "KBS 1 라디오"
    },
    YOUTUBE_JAZZ_MORNING {
        override fun getChannelTitle(): String = "BOSSA NOVA MORNING CAFE"
        override fun getChannelFilename(): String = "youtube_r80MlI__3Qo"
        override fun getChannelAddress(): String = "https://youtu.be/r80MlI__3Qo"
        override fun getDefaultButtonText(): String = "BOSSA NOVA MORNING CAFE"
    },
    YOUTUBE_IU_LILAC{
        override fun getChannelTitle(): String = "아이유 라일락"
        override fun getChannelFilename(): String = "youtube_Qio1G8GwKA4"
        override fun getChannelAddress(): String = "https://youtu.be/Qio1G8GwKA4"
        override fun getDefaultButtonText(): String = "아이유 라일락"
    },
    YOUTUBE_BB_ROLLIN {
        override fun getChannelTitle(): String = "브레이브걸스 롤린"
        override fun getChannelFilename(): String = "youtube_ZL47HB4uSlE"
        override fun getChannelAddress(): String = "https://youtu.be/ZL47HB4uSlE"
        override fun getDefaultButtonText(): String = "브레이브걸스 롤린"
    },
    YOUTUBE_MELONE_HIPHOP_TOP{
        override fun getChannelTitle(): String = "멜론 역대 힙합 1위 모음집"
        override fun getChannelFilename(): String = "youtube_pOQynBTIsZ0"
        override fun getChannelAddress(): String = "https://youtu.be/pOQynBTIsZ0"
        override fun getDefaultButtonText(): String = "멜론 역대 힙합 1위 모음집"
    },
    YOUTUBE_MELONE_2000 {
        override fun getChannelTitle(): String = "그때 그시절 멜론"
        override fun getChannelFilename(): String = "youtube_7CV67qnNOxk"
        override fun getChannelAddress(): String = "https://youtu.be/7CV67qnNOxk"
        override fun getDefaultButtonText(): String = "그때 그시절 멜론"
    },
    YOUTUBE_POP_EMOTION {
        override fun getChannelTitle(): String = "감성 팝송 모음"
        override fun getChannelFilename(): String = "youtube_OFvZO7ul41A"
        override fun getChannelAddress(): String = "https://youtu.be/OFvZO7ul41A"
        override fun getDefaultButtonText(): String = "새벽에 들으면 감성 터지는 팝송 모음"
    },
    YOUTUBE_SMOOTH_BLUES {
        override fun getChannelTitle(): String = "블루스 모음"
        override fun getChannelFilename(): String = "youtube_zdBGqWnpDRk"
        override fun getChannelAddress(): String = "https://youtu.be/zdBGqWnpDRk"
        override fun getDefaultButtonText(): String = "부드러운 블루스 모음"
    },
    YOUTUBE_PIANO_SPRING {
        override fun getChannelTitle(): String = "봄에 듣는 피아노"
        override fun getChannelFilename(): String = "youtube_i0gRgqy_dx8"
        override fun getChannelAddress(): String = "https://youtu.be/i0gRgqy_dx8"
        override fun getDefaultButtonText(): String = "봄에 듣는 피아노 모음집"
    };
    abstract fun getChannelAddress(): String
    abstract fun getChannelFilename(): String
    abstract fun getChannelTitle(): String
    abstract fun getDefaultButtonText(): String
}

@SuppressLint("StaticFieldLeak")
object RadioChannelResources: AsyncCallback {

    private lateinit var mContext: Context
    lateinit var DEFAULT_FILE_PATH: String
    var bInitCompleted: Boolean = false

    var channelList = ArrayList<RadioCompletionMap>()

    private fun getRadioChannelHttpAddress(filename: String): String? {
        for(i in RadioRawChannels.values().indices) {
            if ( RadioRawChannels.values()[i].getChannelFilename().equals(filename) ) {
                return RadioRawChannels.values()[i].getChannelAddress()
            }
        }
        return null
    }

    fun getDefaultButtonTextByFilename(filename: String): String {
        for(i in channelList.indices) {
            if ( channelList.get(i).filename.equals(filename) ) {
                Log.d(resourceTag, "getDefaultButtonTextByFilename: ${channelList.get(i).defaultButtonText}")
                return channelList.get(i).defaultButtonText
            }
        }
        return ""
    }

    private fun removeChannelMapByfilename(filename: String): Int {
        for(i in channelList.indices) {
            if ( channelList.get(i).filename.equals(filename) ) {
                Log.d(resourceTag, "remove channelMap: "+filename)
                removeChannelList(i)
                break
            }
        }
        return channelList.size
    }

    fun requestUpdateResource(filename: String) {
        Log.d(resourceTag, "requestUpdateResource: " + filename)

        // remove channel array list
        //Log.d(resourceTag, "remove channelMap. reamins size: " + removeChannelMapByfilename(filename) )

        // remove exist file
        var fileobj = File(DEFAULT_FILE_PATH + filename )
        if ( fileobj.exists() ) {
            Log.d(resourceTag, "remove file: "+fileobj)
            fileobj.delete()
        }
        if ( fileobj.exists() ) { Log.d(resourceTag, "remove file is failed") }
        else { Log.d(resourceTag, "remove file success") }

        // download again
        var httpAddress = getRadioChannelHttpAddress(filename)
        Log.d(resourceTag, "download again: "+httpAddress)
        DownloadFileFromURL(this).execute(httpAddress, filename)
    }


    fun initResources(context: Context) {
        mContext = context
        DEFAULT_FILE_PATH = mContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).toString() + "/"

        for(i in RadioRawChannels.values().indices) {
            Log.d(resourceTag, "initResources( $i ) - " + DEFAULT_FILE_PATH + RadioRawChannels.values()[i].getChannelFilename() )

            if ( RadioRawChannels.values()[i].getChannelFilename().contains("youtube") ) {
                Log.d(resourceTag, "for youtube")
                setYoutubeChannels(RadioRawChannels.values()[i])
            } else {
                Log.d(resourceTag, "for radio")
                var fileobj = File(DEFAULT_FILE_PATH + RadioRawChannels.values()[i].getChannelFilename())
                if (fileobj.exists()) {
                    Log.d(resourceTag, "File exist")
                    readChannelFile(fileobj)
                } else {
                    Log.d(resourceTag, "File don't exist")
                    // 파일이 없더라도 일단 빈 상태로 채널 맵을 채워둔다
                    // 다운로드 완료 되어 파일 읽는데 성공하면, 이것 지우고 새것으로 대체됨
                    var map =  RadioCompletionMap(
                                RadioRawChannels.values()[i].getDefaultButtonText(),
                                RadioRawChannels.values()[i].getChannelTitle(),
                                channelList.size,
                                RadioRawChannels.values()[i].getChannelFilename(),
                                RadioRawChannels.values()[i].getChannelAddress(),
                                null )
                    addChannelList(map)

                    DownloadFileFromURL(this).execute(
                        RadioRawChannels.values()[i].getChannelAddress(),
                        RadioRawChannels.values()[i].getChannelFilename()
                    )
                }
            }
        }
        Log.d(resourceTag, "initResources: "+ channelList.size)
    }

    private fun addChannelList(map: RadioCompletionMap) {
        Log.d(resourceTag, "addChannelList filename ${map.filename}")
        channelList.add(map)
    }

    private fun removeChannelList(idx: Int) {
        Log.d(resourceTag, "removeChannelList filename ${channelList.get(idx).filename}")
        channelList.removeAt(idx)
    }

    private fun setYoutubeChannels(channels: RadioRawChannels) {
        var map = RadioCompletionMap(channels.getDefaultButtonText(), channels.getChannelTitle(), channelList.size,
            channels.getChannelFilename(), channels.getChannelAddress(),  channels.getChannelAddress() )
        addChannelList(map)
        Log.d(resourceTag, "channel resource add ok - filename(${map.filename}) - channelList.size: "+channelList.size)
    }

    private fun setChannelsFromPlsFile(content: String) {
        var httpAddress = content.substring(content.indexOf("File1=")+6, content.indexOf("Title1=") - 1)
        var title = content.substring(content.indexOf("Title1=")+7, content.indexOf("Length1=") - 1)
        Log.d(resourceTag, "title($title) httpAddress($httpAddress)")

        // check and remove duplication from channelList
        for(i in channelList.indices) {
            if ( title.equals( channelList.get(i).title ) ) {
                removeChannelList(i)
                break
            }
        }

        for(i in RadioRawChannels.values().indices) {
            if ( title.equals( RadioRawChannels.values()[i].getChannelTitle()) ) {
                var map = RadioCompletionMap(RadioRawChannels.values()[i].getDefaultButtonText(), title, channelList.size,
                        RadioRawChannels.values()[i].getChannelFilename(),
                        RadioRawChannels.values()[i].getChannelAddress(),  httpAddress )
                addChannelList(map)
                Log.d(resourceTag, "channel resource add ok - filename(${map.filename}) - channelList.size: "+channelList.size)
                break
            }
        }

        if ( channelList.size == RadioRawChannels.values().size ) {
            Log.d(resourceTag, "setChannelsFromFile completed")
            bInitCompleted = true
            OnAir.getInstance().notifyRadioResourceUpdate(null, RadioResource.SUCCESS)
        }
    }

    override fun onTaskDone(vararg arg: String?) {
        Log.d(resourceTag, "callback Type: " + arg[0])
        when(arg[0])
        {
            "Download" -> OpenFileFromPath(this).execute(arg[1])
            "fileopen" -> setChannelsFromPlsFile(arg[1]!!)
            "failed" -> {
                when( arg[1] ) {
                    "openFile" -> sendCallback(arg[1], arg[2])                // filename
                    "downFile" -> sendCallback(arg[1], arg[2], arg[3])        // fileaddress , filename
                    else -> Log.d(resourceTag, "ignore failed action")
                }
            }
            else -> Log.d(resourceTag, "do nothing type: " + arg[0])
        }
    }

    private fun sendCallback(vararg arg: String?) {
        Log.d(resourceTag, "doFailedAction: " + arg[0])
        when( arg[0] ) {
            "openFile" -> {
                Log.d(resourceTag, "open failed filename: "+ arg[1])
                arg[1]?.let { OnAir.getInstance().notifyRadioResourceUpdate(it, RadioResource.OPEN_FAILED) }
            }
            "downFile" -> {
                Log.d(resourceTag, "down failed addr: "+ arg[1]+ ", filename: "+ arg[2])
                Log.d(resourceTag, "reset Button Text")
                arg[2]?.let { OnAir.getInstance().notifyRadioResourceUpdate(it, RadioResource.DOWN_FAILED) }
            }
            else -> Log.d(resourceTag, "ignore failed action")
        }
    }

    private fun readChannelFile(fileobj: File) {
        if ( fileobj.canRead() ) {
            var ins: InputStream = fileobj.inputStream()
            var content = ins.readBytes().toString(Charset.defaultCharset())
            setChannelsFromPlsFile( content )
        } else {
            Log.d(resourceTag, "Can't read file: "+fileobj.toString())
        }
    }

    internal class OpenFileFromPath(context: RadioChannelResources): AsyncTask<String, String, String>() {
        val callback: AsyncCallback = context
        var bCompleted: Boolean = false

        override fun doInBackground(vararg params: String?): String? {
            val fileobj = File(params[0])
            var content: String? = null
            Log.d(resourceTag, "OpenFileFromPath:" + params[0])

            if (fileobj.exists() && fileobj.canRead()) {

                try {
                    var ins: InputStream = fileobj.inputStream()
                    content = ins.readBytes().toString(Charset.defaultCharset())
                    bCompleted = true
                } catch (e: Exception) {
                    Log.d(resourceTag, "OpenFileFromPath error: "+e.message)
                }
            }

            if (bCompleted) callback.onTaskDone("fileopen", content)
            else {
                Log.d(resourceTag, "fileopen failed")
                callback.onTaskDone("failed", "openFile", params[0])
            }

            return null
        }

    }


    /**
     * Background Async Task to download file
     */
    @SuppressLint("StaticFieldLeak")
    internal class DownloadFileFromURL(context: RadioChannelResources) :
            AsyncTask<String?, String?, String?>() {

        val callback: AsyncCallback = context
        var filename: String = DEFAULT_FILE_PATH
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
            Log.d(resourceTag, "DownloadFileFromURL.doInBackground")
            var count: Int
            try {
                val url = URL(f_url[0])
                Log.d(resourceTag, "down from: "+url)
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
                Log.d(resourceTag, "filename:" + filename)
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
            Log.d(resourceTag, "DownloadFileFromURL.doInBackground end")

            if (bCompleted) callback.onTaskDone("Download", filename)
            else {
                Log.d(resourceTag, "Download failed")
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
            Log.d(resourceTag, "download finished: " + file_url)
        }
    }
}