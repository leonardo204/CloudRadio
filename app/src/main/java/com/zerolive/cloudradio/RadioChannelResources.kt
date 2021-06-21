package com.zerolive.cloudradio

import android.annotation.SuppressLint
import android.content.Context
import android.os.*
import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.*
import java.net.URL
import java.net.URLConnection
import java.nio.charset.Charset


var resourceTag = "CR_Resource"

enum class RadioResource {
    OPEN_FAILED, DOWN_FAILED, SUCCESS
}

data class RadioCompletionMap(
    val defaultButtonText: String,    // button 에 표시할 기본 텍스트
    val title: String,                // pls 파일에서 읽어들인 title
    val id: Int,                      // channel 별 관리 목적의 id (arrayList 의 index 이기도 함)
    val filename: String,             // 실제 저장될 filename
    val fileaddress: String,          // 원본 file 주소
    val httpAddress: String?,           // 스트리밍 file 주소 (MediaPlayer 에 이걸 던진다)
    val mediaType: MEDIATYPE          // media type
)

enum class MEDIATYPE {
    UNKNOWN, RADIO, YOUTUBE_NORMAL, YOUTUBE_LIVE, YOUTUBE_PLAYLIST
}

enum class RadioThumbnails {
    DEFAULT {
        override fun getUrl(): String = "http://zerolive7.iptime.org:9093/api/public/dl/alnc5NXH/01_project/cloudradio/cloudradio_thumbnail.png"
    };
    abstract fun getUrl(): String
}

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
    AFN_THE_EAGLE {
        override fun getChannelTitle(): String = "AFNP_OSN_SC"
        override fun getChannelFilename(): String = "AFNP_OSN.pls"
        override fun getChannelAddress(): String = "http://playerservices.streamtheworld.com/pls/AFNP_OSN.pls"
        override fun getDefaultButtonText(): String = "AFN The Eagle"
    },
    AFN_THE_VOICE {
        override fun getChannelTitle(): String = "AFN_VCE_SC"
        override fun getChannelFilename(): String = "AFN_VCE.pls"
        override fun getChannelAddress(): String = "http://playerservices.streamtheworld.com/pls/AFN_VCE.pls"
        override fun getDefaultButtonText(): String = "AFN The Voice"
    },
    AFN_JOE_RADIO {
        override fun getChannelTitle(): String = "AFN_JOEP_SC"
        override fun getChannelFilename(): String = "AFN_JOEP.pls"
        override fun getChannelAddress(): String = "http://playerservices.streamtheworld.com/pls/AFN_JOEP.pls"
        override fun getDefaultButtonText(): String = "AFN JOE Radio"
    },
    AFN_LEGACY {
        override fun getChannelTitle(): String = "AFN_LGYP_SC"
        override fun getChannelFilename(): String = "AFN_LGYP.pls"
        override fun getChannelAddress(): String = "http://playerservices.streamtheworld.com/pls/AFN_LGYP.pls"
        override fun getDefaultButtonText(): String = "AFN Legacy"
    };
    abstract fun getChannelAddress(): String
    abstract fun getChannelFilename(): String
    abstract fun getChannelTitle(): String
    abstract fun getDefaultButtonText(): String
}

val res_handler: Handler = @SuppressLint("HandlerLeak")
object : Handler() {
    override fun handleMessage(msg: Message) {
        Log.d(resourceTag, "handler handleMessage: " + msg)

        val bundle = msg.data
        val title = bundle.getString("title")
        val url = bundle.getString("url")
        val type = bundle.getString("type")
        RadioChannelResources.makeChannelList(type, title, url)
        if (Program.bInitilized) Program.updateProgramButtons()
    }
}


@SuppressLint("StaticFieldLeak")
object RadioChannelResources: AsyncCallback {

    lateinit var mContext: Context
    lateinit var DEFAULT_FILE_PATH: String
    var bInitCompleted: Boolean = false
    var channelSize = 0

    var channelList = ArrayList<RadioCompletionMap>()

    val mRadioCHResLock = Mutex()

    private fun getRadioChannelHttpAddress(filename: String): String? {
        for(i in RadioRawChannels.values().indices) {
            if ( RadioRawChannels.values()[i].getChannelFilename().equals(filename) ) {
                return RadioRawChannels.values()[i].getChannelAddress()
            }
        }
        return null
    }

    fun getTitleByFilename(filename: String): String {
        for(i in channelList.indices) {
            if ( channelList.get(i).filename.equals(filename) ) {
//                Log.d(
//                    resourceTag,
//
//                    "getTitleByFilename: ${filename} -> ${channelList.get(i).title}"
//                )
                return channelList.get(i).title
            }
        }
        return ""
    }

    fun getDefaultButtonTextByFilename(filename: String): String {
        for(i in channelList.indices) {
            if ( channelList.get(i).filename.equals(filename) ) {
                Log.d(
                    resourceTag,
                    
                    "getDefaultButtonTextByFilename: ${channelList.get(i).defaultButtonText}"
                )
                return channelList.get(i).defaultButtonText
            }
        }
        return ""
    }

    private fun removeChannelMapByfilename(filename: String): Int {
        for(i in channelList.indices) {
            if ( channelList.get(i).filename.equals(filename) ) {
                Log.d(resourceTag,  "remove channelMap: " + filename)
                removeChannelList(i)
                break
            }
        }
        return channelList.size
    }

    fun getDefaultTextByFilename(filename: String): String {

        for(i in channelList.indices) {
//            CRLog.d( " channelList.get(i).filename: ${channelList.get(i).filename} - $filename")
            if ( channelList.get(i).filename.equals(filename) ) {
                return channelList.get(i).defaultButtonText
            }
        }
        return "Unknown Channel"
    }

    fun getDefaultTextByTitle(title: String): String {

        for(i in channelList.indices) {
//            CRLog.d( " channelList.get(i).filename: ${channelList.get(i).filename} - $filename")
            if ( channelList.get(i).title.equals(title) ) {
                return channelList.get(i).defaultButtonText
            }
        }
        return "Unknown Channel"
    }

    fun getFilenameByTitle(title: String): String {

        for(i in channelList.indices) {
//            CRLog.d( " channelList.get(i).filename: ${channelList.get(i).filename} - $filename")
            if ( channelList.get(i).title.equals(title)  ) {
                return channelList.get(i).filename
            }
        }
        return "Unknown Filename"
    }

    fun getFilenameByDefaultText(defaultText: String): String {

        for(i in channelList.indices) {
//            CRLog.d( " channelList.get(i).filename: ${channelList.get(i).filename} - $filename")
            if ( channelList.get(i).defaultButtonText.equals(defaultText)  ) {
                return channelList.get(i).filename
            }
        }
        return "Unknown Filename"
    }

    fun getTitleByDefaultText(defaultText: String): String {

        for(i in channelList.indices) {
            if ( channelList.get(i).defaultButtonText.equals(defaultText) ) {
                return channelList.get(i).title
            }
        }
        return "Unknown Text"
    }

    fun requestUpdateResource(filename: String) {
        Log.d(resourceTag,  "requestUpdateResource: " + filename)

        // remove channel array list
        //Log.d(resourceTag,  "remove channelMap. reamins size: " + removeChannelMapByfilename(filename) )

        // remove exist file
        var fileobj = File(DEFAULT_FILE_PATH + filename)
        if ( fileobj.exists() ) {
            Log.d(resourceTag,  "remove file: " + fileobj)
            fileobj.delete()
        }
        if ( fileobj.exists() ) { Log.d(resourceTag,  "remove file is failed") }
        else { Log.d(resourceTag,  "remove file success") }

        // download again
        var httpAddress = getRadioChannelHttpAddress(filename)
        Log.d(resourceTag,  "download again: " + httpAddress)
        DownloadFileFromURL(this).execute(httpAddress, filename)
    }

    fun getResourceElement(): JsonElement? {
        var element: JsonElement? = null

        // system 내장
        val inputStream = mContext.assets.open("channels.json")
        val sb = StringBuilder()
        inputStream?.let {
            val reader = BufferedReader(InputStreamReader(inputStream))
            val readLines = reader.readLines()
            readLines.forEach {
                sb.append(it)
            }
            it.close()
        }
        Log.d(resourceTag,  "${sb}")
        var ele1 = Json.parseToJsonElement(sb.toString())
        val version1 = ele1.jsonObject["version"].toString().replace("\"", "")

        // downloaded file 있는지 체크
        val file = File(DEFAULT_FILE_PATH + "/" + "channels.json")
        var content: String? = null
        val version2: String

        if ( file.exists() && file.canRead() ) {
            try {
                var ins: InputStream = file.inputStream()
                content = ins.readBytes().toString(Charset.defaultCharset())
            } catch (e: Exception) {
                Log.d(resourceTag,  "checkVersion error: " + e.message)
            }

            content?.let {
                var ele2 = Json.parseToJsonElement(it)
                version2 = ele2.jsonObject["version"].toString().replace("\"", "")

                Log.d(resourceTag,  "sys ch ver($version1)  down ch ver($version2)")

                if ( version1.toInt() - version2.toInt() < 0) {
                    Log.d(resourceTag,  "use download channels.json")
                    element = Json.parseToJsonElement(content)
                } else {
                    Log.d(resourceTag,  "use internal channels.json 1")
                    element = Json.parseToJsonElement(sb.toString())
                }
            }
        } else {
            Log.d(resourceTag,  "use internal channels.json 2")
            element = Json.parseToJsonElement(sb.toString())
        }
        return element
    }

    private fun addFromDataFile() {
        Log.d(resourceTag,  "addFromDataFile")
        var element: JsonElement? = getResourceElement()

        // version check end
        element?.let {
            var data = Json.parseToJsonElement(element.jsonObject["data"].toString())
            synchronized(mContext) {
                CRLog.d( "channelSize ${channelSize} -> ${channelSize+data.jsonArray.size}")
                channelSize += data.jsonArray.size
            }
            for (i in data.jsonArray.indices) {
                Log.d(resourceTag,  "-    [$i]   -")
                Log.d(resourceTag,  "${data.jsonArray[i]}")

                val live = data.jsonArray[i].jsonObject["live"].toString().replace("\"", "").toBoolean()
                val title = data.jsonArray[i].jsonObject["title"].toString().replace("\"", "")
                val address = data.jsonArray[i].jsonObject["fileaddress"].toString().replace("\"", "")

                // live 인 경우엔 redirection url 을 얻어서 설정해야 함
                if ( live ) {
                    ParseUrl(this).execute(title, address)
                    continue
                }

                // live 가 아닌 경우에만 아래 값들이 유효
                val videoId = data.jsonArray[i].jsonObject["videoId"].toString().replace("\"", "")
                val filename = "youtube_" + videoId

                val map = RadioCompletionMap(
                    title,
                    title,
                    channelList.size,
                    filename,
                    address,
                    address,
                    MEDIATYPE.YOUTUBE_NORMAL
                )
                addChannelList(map)
            }
        }
    }

    fun clearResources() {
        Log.d(resourceTag,  "clearResources")
        synchronized(this) {
            channelSize = 0
            channelList.clear()
        }
    }

    private fun addYtbPlaylist(): Int {
        var num = 0
        val fileobj = File(DEFAULT_FILE_PATH + "ytbpls.json")

        if ( fileobj.exists() && fileobj.canRead() ) {
            val ins: InputStream = fileobj.inputStream()
            val content = ins.readBytes().toString(Charset.defaultCharset())
            val items = Json.parseToJsonElement(content)
            num = items.jsonArray.size
            for(i in items.jsonArray.indices) {
                val title = Json.parseToJsonElement(items.jsonArray[i].jsonObject["title"].toString())
                    .toString()
                    .replace("\"","")
                    .replace("\\","")
                val map = RadioCompletionMap(title, title, channelList.size, title, title, null, MEDIATYPE.YOUTUBE_PLAYLIST)
                addChannelList(map)
            }
        }

        CRLog.d("addYtbPlaylist : ${num}")

        return num
    }

    fun initResources(context: Context) {
        mContext = context
        DEFAULT_FILE_PATH = mContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).toString() + "/"

        // for youtube playlist
        val num = addYtbPlaylist()
        synchronized(mContext) {
            CRLog.d("channelSize ${channelSize} -> ${channelSize+num}")
            channelSize += num
        }

        // for youtube
        addFromDataFile()

        synchronized(mContext) {
            CRLog.d("channelSize ${channelSize} -> ${channelSize+RadioRawChannels.values().size}")
            channelSize += RadioRawChannels.values().size
        }

        // for radio
        for(i in RadioRawChannels.values().indices) {
            Log.d(
                resourceTag,
                
                "add radio channels ( $i ) - " + DEFAULT_FILE_PATH + RadioRawChannels.values()[i].getChannelFilename()
            )

            var fileobj = File(DEFAULT_FILE_PATH + RadioRawChannels.values()[i].getChannelFilename())
            if (fileobj.exists()) {
                Log.d(resourceTag,  "File exist")
                readChannelFile(fileobj)
            } else {
                Log.d(resourceTag,  "File don't exist")

                // 파일이 없더라도 일단 빈 상태로 채널 맵을 채워둔다
                // 다운로드 완료 되어 파일 읽는데 성공하면, 이것 지우고 새것으로 대체됨
                var map =  RadioCompletionMap(
                    RadioRawChannels.values()[i].getDefaultButtonText(),
                    RadioRawChannels.values()[i].getChannelTitle(),
                    channelList.size,
                    RadioRawChannels.values()[i].getChannelFilename(),
                    RadioRawChannels.values()[i].getChannelAddress(),
                    null,
                    MEDIATYPE.RADIO
                )
                addChannelList(map)

                DownloadFileFromURL(this).execute(
                    RadioRawChannels.values()[i].getChannelAddress(),
                    RadioRawChannels.values()[i].getChannelFilename()
                )
            }
        }

        Log.d(resourceTag,  "initResources(cur/total): ${channelList.size} / $channelSize" )
    }

    private fun addChannelList(map: RadioCompletionMap) {
        synchronized(mContext) {
            channelList.add(map)
        }

        Log.d(resourceTag,  "addChannelList filename ${map.filename} channelList size: ${channelList.size} / channelSize: ${channelSize}")

        if ( channelList.size == channelSize ) {
            initResourceComplete()

            // 모든 채널 리소스 업데이트가 완료된 이후 AFN 주소들만 바꿔줌
            AFNRadioResource.init()
        }
    }

    private fun initResourceComplete() {
        Log.d(resourceTag,  "Resource init completed")
        bInitCompleted = true
        OnAir.notifyRadioResourceUpdate(null, RadioResource.SUCCESS)
    }

    private fun removeChannelList(idx: Int) {
        Log.d(resourceTag,  "removeChannelList filename ${channelList.get(idx).filename} size: ${channelList.size}")
        channelList.removeAt(idx)
    }

    // 1. 우선 File1 의 것을 담고
    // 2. File1 안에서 https 가 있으면 그걸 찾고
    // 3. http 만 있으면 그것으로 address 를 설정한다.
    private fun parseAddress(content: String): String {
        var httpAddress: String
        if ( content.contains("File2" ) ) {
            httpAddress = content.substring(
                content.indexOf("File1=") + 6,
                content.indexOf("File2=") - 1
            )
        }
        else {
            httpAddress = content.substring(
                content.indexOf("File1=") + 6,
                content.indexOf("Title1=") - 1
            )
        }

        if ( httpAddress.contains("http://") && httpAddress.contains("https://") ) {
            // https://... http://....
            if ( httpAddress.indexOf("http://") > httpAddress.indexOf("https://") ) {
                httpAddress = httpAddress.substring( 0, httpAddress.indexOf("http://") )
            }
            // http://... https://....
            else {
                httpAddress = httpAddress.substring( httpAddress.indexOf("https://") )
            }
        }

        return httpAddress
    }

    private fun setChannelsFromPlsFile(content: String) {
        var httpAddress: String
        var title: String

        // address
        httpAddress = parseAddress(content)

        // title
        if ( content.contains("Title2") ) {
            title = content.substring(
                content.indexOf("Title1=") + 7,
                content.indexOf("Title2=") - 1
            )
        } else {
            title = content.substring(
                content.indexOf("Title1=") + 7,
                content.indexOf("Length1=") - 1
            )
        }
        // 혹시 모를 앞 공백 제거
        title = title.trimMargin()
        httpAddress = httpAddress.trimMargin()
        Log.d(resourceTag,  "title($title) httpAddress($httpAddress)")

        // check and remove duplication from channelList
        for(i in channelList.indices) {
            if (title == channelList.get(i).title) {
                removeChannelList(i)
                break
            }
        }

        for(i in RadioRawChannels.values().indices) {
            //Log.d(resourceTag,  "add resource compare for ($title)(len:${title.length}) - (${RadioRawChannels.values()[i].getChannelTitle()})(len:${RadioRawChannels.values()[i].getChannelTitle().length}): ${title.compareTo(RadioRawChannels.values()[i].getChannelTitle())} ")
            if (title.compareTo(RadioRawChannels.values()[i].getChannelTitle()) == 0) {
                var map = RadioCompletionMap(
                    RadioRawChannels.values()[i].getDefaultButtonText(), title, channelList.size,
                    RadioRawChannels.values()[i].getChannelFilename(),
                    RadioRawChannels.values()[i].getChannelAddress(), httpAddress, MEDIATYPE.RADIO
                )
                addChannelList(map)
                Log.d(resourceTag, "channel resource add ok - filename(${map.filename}) - channelList.size: " + channelList.size )
                break
            }
        }

        Log.d(resourceTag,  "setChannelsFromPlsFile  cur/size: ${channelList.size} / $channelSize")
    }

    private fun removeDuplication(title: String) {
        for(i in channelList.indices) {
            if ( channelList.get(i).title.equals(title) ) {
                Log.d(resourceTag,  "removeDuplication: ${title}")
                channelList.removeAt(i)
                break
            }
        }
    }

    fun getMediaType(filename: String): MEDIATYPE {
        for(i in channelList.indices) {
            if ( channelList.get(i).filename.equals(filename) ) {
//                CRLog.d("mediatype: ${channelList.get(i).mediaType}")
                return channelList.get(i).mediaType
            }
        }
        CRLog.d("mediatype: ${MEDIATYPE.UNKNOWN}")
        return MEDIATYPE.UNKNOWN
    }

    fun makeChannelList(argType: String, title: String, url: String) {
        Log.d(resourceTag,  "makeChannelList ${title} : ${url}")
        val videoId = url.substring( url.indexOf("watch?v=")+8)
        val filename = "youtube_" + videoId

        // title 이 겹치는 channel 이 있는 경우 조사하여 중복 제거
        removeDuplication(title)

        var type: MEDIATYPE
        if ( argType.equals( MEDIATYPE.YOUTUBE_LIVE.toString() ) ) {
            type = MEDIATYPE.YOUTUBE_LIVE
        } else {
            type = MEDIATYPE.YOUTUBE_NORMAL
        }
        Log.d(resourceTag, "media type: ${type}")

        val map = RadioCompletionMap(title, title, channelList.size, filename, url, url, type)
        addChannelList(map)
        // 즐겨 찾기에 대해 live channel url 업데이트가 있을 수 있으니 확인 후
        // 업데이트가 있어서 filename 이 서로 다를 경우, 여기서 favorite list 를 저장해준다.
        checkFavListUpdate(filename, title)
    }

    private fun checkFavListUpdate(newFilename: String, newTitle: String) {
        val fileObj = File(OnAir.DEFAULT_FILE_PATH + OnAir.FAVORITE_CHANNEL_JSON)

        if ( !fileObj.exists() && !fileObj.canRead() ) {
            Log.d(resourceTag,  "checkFavListUpdate: Can't load ${OnAir.DEFAULT_FILE_PATH + OnAir.FAVORITE_CHANNEL_JSON}")
            return
        }

        val ins = fileObj.inputStream()
        val content = ins.readBytes().toString(Charset.defaultCharset())

        val ele = Json.parseToJsonElement(content)
        Log.d(resourceTag,  "checkFavListUpdate size: ${ele.jsonArray.size}")

        for(i in 0..ele.jsonArray.size-1) {
            val filename = ele.jsonArray[i].jsonObject["filename"].toString().replace("\"","")
            val title = ele.jsonArray[i].jsonObject["title"].toString().replace("\"","")

            if (newTitle.equals(title)) {
                Log.d(resourceTag,  "OLD> title: ${title}  -  filename: ${filename}")
                Log.d(resourceTag,  "NEW> title: ${newTitle}  -  filename: ${newFilename}")

                if ( !newFilename.equals(filename) ) {
                    // update 해줘야 함
                    val newContent = content.replace(filename, newFilename);
                    Log.d(resourceTag,  "old json: ${content}")
                    Log.d(resourceTag,  "new json: ${newContent}")
                    Program.WriteFile()
                        .execute(Program.DEFAULT_FILE_PATH + Program.FAVORITE_CHANNEL_JSON, newContent)
                    break
                }
            }
        }
    }

    override fun onTaskDone(vararg arg: String?) {
        Log.d(resourceTag,  "callback Type: " + arg[0])
        when(arg[0])
        {
            "Download" -> OpenFileFromPath(this).execute(arg[1])
            "fileopen" -> {
                setChannelsFromPlsFile(arg[1]!!)
                OnAir.notifyRadioResourceUpdate(null, RadioResource.SUCCESS)
            }
            "ParseUrl" -> {
                val msg = res_handler.obtainMessage()
                val bundle = Bundle()
                val title = arg[1]
                val url = arg[2]
                val type = arg[3]

                if ( title != null && url != null ) {

                    bundle.putString("title", title)
                    bundle.putString("url", url)
                    bundle.putString("type", type)

                    msg.data = bundle
                    res_handler.sendMessage(msg)
                } else {
                    synchronized(mContext) {
                        CRLog.d( "channelSize ${channelSize} -> ${channelSize-1}")
                        channelSize--
                    }
                    Log.d(resourceTag,  "ParseUrl failed: title=${title} url=${url} cur/channelSize: ${channelList.size}/${channelSize}")
                    if ( channelList.size == channelSize ) {
                        initResourceComplete()
                    }
                }
            }
            "failed" -> {
                when (arg[1]) {
                    "openFile" -> sendCallback(arg[1], arg[2])                // filename
                    "downFile" -> sendCallback(
                        arg[1],
                        arg[2],
                        arg[3]
                    )        // fileaddress , filename
                    "ParseUrl" -> {
                        synchronized(mContext) {
                            CRLog.d( "channelSize ${channelSize} -> ${channelSize-1}")
                            channelSize--
                        }
                        Log.d(resourceTag,  "ParseUrl Failed to get Channel url: ${arg[2]} cur/channelSize: ${channelList.size}/${channelSize}")
                        if ( channelList.size == channelSize ) {
                            initResourceComplete()
                        }
                    }
                    else -> Log.d(resourceTag,  "ignore failed action")
                }
            }
            else -> Log.d(resourceTag,  "do nothing type: " + arg[0])
        }
    }

    private fun sendCallback(vararg arg: String?) {
        Log.d(resourceTag,  "doFailedAction: " + arg[0])

        if ( !OnAir.bInitialized) {
            Log.d(resourceTag,  "Ignore callback. reason: Not initialized OnAir fragment")
            return
        }

        when( arg[0] ) {
            "openFile" -> {
                Log.d(resourceTag,  "open failed filename: " + arg[1])
                arg[1]?.let { OnAir.notifyRadioResourceUpdate(it, RadioResource.OPEN_FAILED) }
            }
            "downFile" -> {
                Log.d(resourceTag,  "down failed addr: " + arg[1] + ", filename: " + arg[2])
                Log.d(resourceTag,  "reset Button Text")
                arg[2]?.let { OnAir.notifyRadioResourceUpdate(it, RadioResource.DOWN_FAILED) }
            }
            else -> Log.d(resourceTag,  "ignore failed action")
        }
    }

    private fun readChannelFile(fileobj: File) {
        if ( fileobj.canRead() ) {
            var ins: InputStream = fileobj.inputStream()
            var content = ins.readBytes().toString(Charset.defaultCharset())
            setChannelsFromPlsFile(content)
        } else {
            Log.d(resourceTag,  "Can't read file: " + fileobj.toString())
        }
    }

    internal class OpenFileFromPath(context: RadioChannelResources): AsyncTask<String, String, String>() {
        val callback: AsyncCallback = context
        var bCompleted: Boolean = false

        override fun doInBackground(vararg params: String?): String? {
            val fileobj = File(params[0])
            var content: String? = null
            Log.d(resourceTag,  "OpenFileFromPath:" + params[0])

            if (fileobj.exists() && fileobj.canRead()) {

                try {
                    var ins: InputStream = fileobj.inputStream()
                    content = ins.readBytes().toString(Charset.defaultCharset())
                    bCompleted = true
                } catch (e: Exception) {
                    Log.d(resourceTag,  "OpenFileFromPath error: " + e.message)
                }
            }

            if (bCompleted) callback.onTaskDone("fileopen", content)
            else {
                Log.d(resourceTag,  "fileopen failed")
                callback.onTaskDone("failed", "openFile", params[0])
            }

            return null
        }
    }

    internal class ParseUrl(context: RadioChannelResources) : AsyncTask<String, String, String>() {
        val callback: AsyncCallback = context
        override fun doInBackground(vararg param: String?): String? {
            var title = param[0]
            var url = param[1]
            var finalUrl: String? = null
            try {
                //Connect to the website
                val document: Document = Jsoup.connect(url)
                    .timeout(3000)
                    .get()

                //Get the logo source of the website
                val link = document.select("link[rel=canonical]").first()
                finalUrl = link.attr("abs:href")
                Log.d(resourceTag,  "ParseUrl.  title(${title}) url check: $finalUrl")
            } catch (e: Exception) {
                Log.d(resourceTag,  "ParseUrl Exception: "+e.message)
                callback.onTaskDone("failed", "ParseUrl", title)
                return null
            }

            callback.onTaskDone("ParseUrl", title, finalUrl, MEDIATYPE.YOUTUBE_LIVE.toString())

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
            Log.d(resourceTag,  "DownloadFileFromURL.doInBackground")
            var count: Int
            try {
                val url = URL(f_url[0])
                Log.d(resourceTag,  "down from: " + url)
                val connection: URLConnection = url.openConnection()
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.connect()

                // this will be useful so that you can show a tipical 0-100%
                // progress bar
                val lenghtOfFile: Int = connection.getContentLength()

                // download the file
                val urlInput: InputStream = url.openStream()
                val input: InputStream = BufferedInputStream(urlInput)


                // Output stream
                filename = filename + f_url[1]
                Log.d(resourceTag,  "filename:" + filename)
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
            Log.d(resourceTag,  "DownloadFileFromURL.doInBackground end")

            if (bCompleted) callback.onTaskDone("Download", filename)
            else {
                Log.d(resourceTag,  "Download failed")
                callback.onTaskDone("failed", "downFile", f_url[0], f_url[1])
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
            Log.d(resourceTag,  "download finished: " + file_url)
        }
    }
}