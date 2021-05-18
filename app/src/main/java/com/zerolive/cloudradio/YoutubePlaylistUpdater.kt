package com.zerolive.cloudradio

import android.annotation.SuppressLint
import android.os.*
import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset


private const val plstTag = "CR_youtubePls"


data class YtbPlayListItem(
    val title: String,
    val videoId: String
)

val pls_handler: Handler = @SuppressLint("HandlerLeak")
object : Handler() {
    override fun handleMessage(msg: Message) {
        val bundle = msg.data
        val message = bundle.getString("message")
        val title = bundle.getString("title")
        val url = bundle.getString("url")
        val random = bundle.getString("random")
        val filename = bundle.getString("filename")
        val update = bundle.getString("update").toBoolean()

        message?.let {
            Log.d(plstTag, "ytbpls_handler call updatePlayLists: ${it}")
            YoutubePlaylistUpdater.updatePlayLists(it, filename, update)
        }

        title?.let {
            Log.d(plstTag, "ytbpls_handler title: ${it}, random: ${random}, url: ${url}")
            Program.addProgramButtons(it, url, random)
        }
    }
}

// TODO
// 4. 추가한 playlist 제거 필요 (ui 로 할 수 있게)
object YoutubePlaylistUpdater : AsyncCallback {

    var mPlayListId: String? = null
    var mDirPath: String? = null
    var mTitle: String? = null
    var mUrl: String? = null
    var mUpdate = false
    var mRandom = false
    var mRequestStatus = HashMap<String, Int>()
    var mListMap = HashMap<String, List<YtbPlayListItem>>()

    // title, url 중 하나라도 저장된 내용이 있으면 false
    fun checkValidation(path: String, title: String, url: String): Boolean {
        CRLog.d("checkValidation: ${title} - ${url}")

        val fileobj = File(path + "ytbpls.json")
        if ( fileobj.exists() && fileobj.canRead() ) {
            val ins: InputStream = fileobj.inputStream()
            val content = ins.readBytes().toString(Charset.defaultCharset())
            val items = Json.parseToJsonElement(content)
            for(i in items.jsonArray.indices) {
                val jTitle = Json.parseToJsonElement(items.jsonArray[i].jsonObject["title"].toString())
                val jUrl = Json.parseToJsonElement(items.jsonArray[i].jsonObject["url"].toString())
                CRLog.d("checkValidation [$i] in:${title} - read:${jTitle.toString().replace("\"","").replace("ytbpls_","")}")
                CRLog.d("checkValidation [$i] in:${url} - read:${jUrl.toString().replace("\"","")}")
                CRLog.d(" ")

                if ( title.equals(jTitle.toString().replace("\"","").replace("ytbpls_","")) || url.equals(jUrl.toString().replace("\"","")) ) {
                    return false
                }
            }
        }
        return true
    }

    fun checkUrl(path: String, title: String, url: String, random: Boolean) {
        mPlayListId = null
        mTitle  = null
        mDirPath = null

        CRLog.d("checkUrl. title: ${title} - random: ${random} - url: ${url}")

        if ( !checkValidation(path, title, url) ) {
            CRLog.d("checkValidation false (duplication)")
            MainActivity.getInstance().makeToast("동일한 이름 혹은 주소의 재생 목록이 있습니다. 확인해 주세요.")
            return
        }

        CRLog.d("checkUrl: ${url}")
        if ( url.startsWith("https://youtube.com/playlist?list=") || url.startsWith("https://www.youtube.com/playlist?list=") ) {
            mPlayListId = url.substring(url.indexOf("playlist?list=") + 14)
            MainActivity.getInstance().makeToast("${mPlayListId}")
        } else {
            MainActivity.getInstance().makeToast("입력한 URL이 올바르지 않습니다. 확인해 주세요.")
        }

        // 처음 playlist 요청하는 시점
        // 1) 사용자가 직접 playlist 를 추가했음
        mPlayListId?.let {
            CRLog.d("mPlayListId: ${it}")
            mDirPath = path
            mUrl = url
            mRandom = random
            mTitle = "ytbpls_" + title.replace(" ", "_")
            mRequestStatus.put(mTitle!!, 1)
            CRLog.d("[req. ${mRequestStatus.get(mTitle)}] update title: ${mTitle}  -  playlistId: ${mPlayListId}")
            GetPlayLists(this, mTitle, false).execute(it)
        }
    }

    // 처음 playlist 요청하는 시점
    // 2) playlist update 를 요청함
    fun update() {
        if ( mDirPath == null ) {
            CRLog.d("path isn't set.")
            mDirPath = Program.DEFAULT_FILE_PATH
        }
        CRLog.d("path: ${mDirPath}")

        val fileobj = File(mDirPath + "ytbpls.json")
        if ( fileobj.exists() && fileobj.canRead() ) {
            mUpdate = true

            val ins: InputStream = fileobj.inputStream()
            val content = ins.readBytes().toString(Charset.defaultCharset())
            val items = Json.parseToJsonElement(content)
            for(i in items.jsonArray.indices) {
                val jTitle = Json.parseToJsonElement(items.jsonArray[i].jsonObject["title"].toString())
                val jUrl = Json.parseToJsonElement(items.jsonArray[i].jsonObject["url"].toString())
                mPlayListId = jUrl.toString().replace("\"","").substring(jUrl.toString().replace("\"","").indexOf("playlist?list=") + 14)
                mTitle = jTitle.toString().replace(" ", "_").replace("\"","")
                if ( mRequestStatus.containsKey(mTitle!!) ) {
                    mRequestStatus.put(mTitle!!, mRequestStatus.get(mTitle!!)!!+1)
                } else {
                    mRequestStatus.put(mTitle!!, 1)
                }
                CRLog.d("[req. ${mRequestStatus.get(mTitle)}] update title: ${mTitle}  -  playlistId: ${mPlayListId}")
                GetPlayLists(this, mTitle, true).execute(mPlayListId)
            }
        }

        MainActivity.getInstance().makeToast("유튜브 재생목록 업데이트 요청 완료")
    }

    private fun parseResult(filename: String, jsonStr: String): List<YtbPlayListItem> {
        val element = Json.parseToJsonElement(jsonStr)
        val items = Json.parseToJsonElement(element.jsonObject["items"].toString())
        var pageToken: String? = null
        pageToken = Json.parseToJsonElement(element.jsonObject["nextPageToken"].toString()).toString().replace("\"","")
        Log.d(plstTag, "pageToken: ${pageToken}")

        if ( mRequestStatus.containsKey(filename) ) {
            mRequestStatus.put(filename, mRequestStatus.get(filename)!!.toInt()-1)
        }
        if ( pageToken != null && !pageToken.equals("null") ) {
            if ( mRequestStatus.containsKey(filename) ) {
                mRequestStatus.put(filename, mRequestStatus.get(filename)!!.toInt()+1)
            }
            CRLog.d("[req. ${mRequestStatus.get(mTitle)}] update title: ${mTitle}  -  playlistId: ${mPlayListId}")
            GetPlayLists(this, filename, false).execute(mPlayListId, pageToken)
        }

        var ytbPlsLists: List<YtbPlayListItem> = listOf()

        for(i in items.jsonArray.indices) {
            val snippet = Json.parseToJsonElement(items.jsonArray[i].jsonObject["snippet"].toString())
            val title = snippet.jsonObject["title"].toString()
            val resId = Json.parseToJsonElement(snippet.jsonObject["resourceId"].toString())
            val videoId = resId.jsonObject["videoId"].toString()
            Log.d(plstTag, "[${i}] title: ${title}  - videoId: ${videoId}")
            if ( title.toString().replace("\"","").equals("Deleted video") ) {
                Log.d(plstTag, " > skip Deleted video!")
                continue
            }
            val item = YtbPlayListItem(title.replace("\"", ""), videoId.replace("\"", ""))
            ytbPlsLists += item
        }

        return ytbPlsLists
    }

    private fun getYtbPlsListFromFile(list: List<YtbPlayListItem>, update: Boolean): List<YtbPlayListItem> {
        var l = list

        // add extra from json file
        val fileobj = File(mDirPath + mTitle + ".json")
        if ( fileobj.exists() && fileobj.canRead() ) {
            if ( update ) {
                Log.d(plstTag, "remove ${mTitle}.json for update")
                fileobj.delete()
            } else {
                val ins: InputStream = fileobj.inputStream()
                val content = ins.readBytes().toString(Charset.defaultCharset())
                Log.d(plstTag, " > read content:${content}")
                val items = Json.parseToJsonElement(content)
                for (i in items.jsonArray.indices) {
                    val title =
                        Json.parseToJsonElement(items.jsonArray[i].jsonObject["title"].toString())
                    val videoId =
                        Json.parseToJsonElement(items.jsonArray[i].jsonObject["videoId"].toString())

                    // check duplication
                    var dupl = false
                    for (k in l.indices) {
                        if (l.get(k).videoId.equals(videoId.toString().replace("\"", ""))) {
                            dupl = true
                            break;
                        }
                    }

                    if (dupl) {
                        Log.d(
                            plstTag,
                            "skip update! reason. duplication: v(${videoId}) -  ${title}"
                        )
                    } else {
                        val item = YtbPlayListItem(
                            title.toString().replace("\"", ""),
                            videoId.toString().replace("\"", "")
                        )
                        l += item
                    }
                }
            }
        }

        return l
    }


    // prev 에 없는 list 의 것만 newList 에 넣어서 mListMap 에 합침
    private fun addWithCheckDupl(filename: String, list: List<YtbPlayListItem>) {
        var prev = mListMap.get(filename)!!
        var newList: List<YtbPlayListItem> = listOf()
        newList += prev

        for(i in list.indices) {
            val vid = list.get(i).videoId
            val title = list.get(i).title
            var bDupl = false
            for(k in prev.indices) {
                if ( prev.get(k).videoId.equals(vid) || prev.get(k).title.equals(title) ) {
                    bDupl = true
                    break
                }
            }
            if ( bDupl ) {
//                Log.d(plstTag, " > skip duplication: videoId: ${vid}    - title: ${title}")
            } else {
                newList += YtbPlayListItem(title, vid)
            }
        }

        mListMap.put(filename, newList)
    }

    private fun addYtbPlsList(filename: String, list: List<YtbPlayListItem>, update: Boolean) {

        if ( mListMap.containsKey(filename) ) {
            var l = mListMap.get(filename)!! + list
            addWithCheckDupl(filename, l)
        } else {
            mListMap.put(filename, list)
        }

        // add extra from json file
        val fileobj = File(mDirPath + filename + ".json")
        if ( fileobj.exists() && fileobj.canRead() && update ) {
            Log.d(plstTag, "remove ${filename}.json for update")
            fileobj.delete()
        }
    }

    // 각 재생목록에 대한 video id 들을 작성
    // ex) ytbpls_케이팝.json
    private fun writePlayList(list: List<YtbPlayListItem>, filename: String) {
        val gson = GsonBuilder().disableHtmlEscaping().create()
        val listType: TypeToken<List<YtbPlayListItem>> = object: TypeToken<List<YtbPlayListItem>>() {}

        val arr = gson.toJson(list, listType.type)
        CRLog.d("writePlayList filename(${filename+".json"}) -  json: ${arr}")

        if ( filename.equals("N/A") ) {
            WritePlaylistFile().execute(
                mDirPath + mTitle + ".json",
                arr.toString().replace("\\", "")
            )
        } else {
            WritePlaylistFile().execute(
                mDirPath + filename+ ".json",
                arr.toString().replace("\\", "")
            )
        }
    }

    fun updatePlayLists(message: String, filename: String, update: Boolean) {
        Log.d(plstTag, "updatePlsyLists ${filename} u(${update})")

        // pasring
        val list1: List<YtbPlayListItem> = parseResult(filename, message)

        // add reserved list
        addYtbPlsList(filename, list1, update)

        if ( mRequestStatus.containsKey(filename) && mRequestStatus.get(filename)!! < 1 ) {
            // write json file
            Log.d(plstTag, "writePlayList num: ${mListMap.size}")
            writePlayList(mListMap.get(filename)!!, filename)
        }

        // make program list
        if ( !mUpdate && mUrl != null ) {
            mTitle?.let {
                val msg = pls_handler.obtainMessage()
                val bundle = Bundle()
                bundle.putString("title", it)
                bundle.putString("url", mUrl!!)
                bundle.putString("random", mRandom.toString())
                msg.data = bundle
                pls_handler.sendMessage(msg)
            }
        }

        mUpdate = false
    }

    override fun onTaskDone(vararg str: String?) {
        var title: String? = null
        var update = false
        if ( str.size > 1 ) {
            title = str[1]
            update = str[2].toBoolean()
        }
        Log.d(plstTag, "result: u(${update}) >  ${title} - ${str[0]}")

        MainActivity.getInstance().runOnUiThread( Runnable {
            str[0]?.let {
                val msg = pls_handler.obtainMessage()
                val bundle = Bundle()
                bundle.putString("message", it)
                if ( title != null ) {
                    bundle.putString("filename", title)
                } else {
                    bundle.putString("filename", "N/A")
                }
                bundle.putString("update", update.toString())
                msg.data = bundle
                pls_handler.sendMessage(msg)
            }
        })
    }

    @SuppressLint("StaticFieldLeak")
    internal class WritePlaylistFile() : AsyncTask<String?, String?, String?>() {

        override fun doInBackground(vararg param: String?): String? {
            val filename = param[0]
            val data = param[1]

            CRLog.d("WritePlaylistFile filename: ${filename}")
            CRLog.d("WritePlaylistFile data: ${data}")

            try {
                var fileObj = File(filename)
                if ( fileObj.exists() ) {
                    CRLog.d("remove previous ${filename}")
                    fileObj.delete()
                }

                if (data != null) {
                    CRLog.d("write ${filename}")
                    fileObj.writeText(data, Charset.defaultCharset())
                }
            } catch (e: IOException) {
                CRLog.d("WritePlaylistFile Error: " + e.message)
            } catch (e: Exception) {
                CRLog.d("WritePlaylistFile Error: " + e.message)
            } finally {

            }
            CRLog.d("WritePlaylistFile.doInBackground end")

            return null
        }
    }
}

class GetPlayLists(context: YoutubePlaylistUpdater, title: String?, update: Boolean) : AsyncTask<String, String, String>() {
    val callback: AsyncCallback = context
    val API_KEY = "AIzaSyC-8Ut8ITfm9KKHE-8-5pre5CzeStgUC-w"
    var mToken: String? = null
    var mTitle: String? = title
    var mUpdate = update

    override fun doInBackground(vararg param: String?): String? {
        val id = param[0]
        if ( param.size > 1 ) {
            param[1]?.let { mToken = it }
        }

        var apiurl = "https://www.googleapis.com/youtube/v3/playlistItems"
        apiurl += "?key=${API_KEY}"
        apiurl += "&part=snippet"
        apiurl += "&playlistId=${id}"
        apiurl += "&maxResults=50"
        mToken?.let { apiurl += "&pageToken=${mToken}"}

        Log.d(plstTag, "Req url(pls): ${apiurl}")

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
            Log.d(plstTag, "length: ${response.length}")
            callback.onTaskDone(response.toString(), mTitle, mUpdate.toString())

        } catch (e: Exception) {
            Log.d(plstTag,"GetPlayLists Error: " + e.message)
        }

        return "success";
    }
}