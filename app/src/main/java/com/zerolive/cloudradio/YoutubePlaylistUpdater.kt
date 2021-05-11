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

        message?.let {
            Log.d(plstTag, "ytbpls_handler message: ${it}")
            YoutubePlaylistUpdater.updatePlayLists(it)
        }

        title?.let {
            Log.d(plstTag, "ytbpls_handler title: ${it}")
            Program.addProgramButtons(it)
        }
    }
}

// TODO
// 1. title - video id 별로 json 파일 저장 ( { "playlistId":"xxx", "type":"custom", "items": [ {"title":"yyy", "videoId":"ssss"} ] } )
// 2. program list 추가
// 3. 재생 시 순차/랜덤 재생
// 4. 추가한 playlist 제거 필요 (ui 로 할 수 있게)
object YoutubePlaylistUpdater : AsyncCallback {

    var mPlayListId: String? = null
    var mDirPath: String? = null
    var mTitle: String? = null

    fun checkUrl(path: String, title: String, url: String) {
        mPlayListId = null
        mTitle  = null
        mDirPath = null

        CRLog.d("checkUrl: ${url}")
        if ( url.startsWith("https://youtube.com/playlist?list=") || url.startsWith("https://www.youtube.com/playlist?list=") ) {
            mPlayListId = url.substring(url.indexOf("playlist?list=") + 14)
            MainActivity.getInstance().makeToast("${mPlayListId}")
        } else {
            MainActivity.getInstance().makeToast("입력한 URL이 올바르지 않습니다. 확인해 주세요.")
        }

        mPlayListId?.let {
            CRLog.d("mPlayListId: ${it}")
            val ret = GetPlayLists(this).execute(it)

            mDirPath = path
            mTitle = "ytbpls_" + title.replace(" ", "_")
        }
    }

    private fun parseResult(jsonStr: String): List<YtbPlayListItem> {
        val element = Json.parseToJsonElement(jsonStr)
        val items = Json.parseToJsonElement(element.jsonObject["items"].toString())

        var ytbPlsLists: List<YtbPlayListItem> = listOf()

        for(i in items.jsonArray.indices) {
            val snippet = Json.parseToJsonElement(items.jsonArray[i].jsonObject["snippet"].toString())
            val title = snippet.jsonObject["title"].toString()
            val resId = Json.parseToJsonElement(snippet.jsonObject["resourceId"].toString())
            val videoId = resId.jsonObject["videoId"].toString()
            Log.d(plstTag, "[${i}] title: ${title}  - videoId: ${videoId}")
            val item = YtbPlayListItem(title.replace("\"", ""), videoId.replace("\"", ""))
            ytbPlsLists += item
        }

        return ytbPlsLists
    }

    private fun writePlayList(list: List<YtbPlayListItem>) {
        val gson = GsonBuilder().create()
        val listType: TypeToken<List<YtbPlayListItem>> = object: TypeToken<List<YtbPlayListItem>>() {}

        val arr = gson.toJson(list, listType.type)
        CRLog.d("writePlayList json: ${arr}")

        WritePlaylistFile().execute(mDirPath + mTitle + ".json", arr.toString())
    }

    fun updatePlayLists(message: String) {
        // pasring
        var list: List<YtbPlayListItem> = parseResult(message)

        // write json file
        list?.let { writePlayList(it) }

        // make program list
        mTitle?.let {
            val msg = pls_handler.obtainMessage()
            val bundle = Bundle()
            bundle.putString("title", it)
            msg.data = bundle
            pls_handler.sendMessage(msg)
        }
    }

    override fun onTaskDone(vararg str: String?) {
        Log.d(plstTag, "result: ${str[0]}")

        MainActivity.getInstance().runOnUiThread( Runnable {
            str[0]?.let {
                val msg = pls_handler.obtainMessage()
                val bundle = Bundle()
                bundle.putString("message", it)
                msg.data = bundle
                pls_handler.sendMessage(msg)
            }
        })
    }

    @SuppressLint("StaticFieldLeak")
    internal class WritePlaylistFile() : AsyncTask<String?, String?, String?>() {

        override fun doInBackground(vararg param: String?): String? {
            CRLog.d("WritePlaylistFile.doInBackground")
            var filename = param[0]
            var data = param[1]

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

class GetPlayLists(context: YoutubePlaylistUpdater) : AsyncTask<String, String, String>() {
    val callback: AsyncCallback = context
    val API_KEY = "AIzaSyC-8Ut8ITfm9KKHE-8-5pre5CzeStgUC-w"

    override fun doInBackground(vararg param: String?): String? {
        val id = param[0]

        var apiurl = "https://www.googleapis.com/youtube/v3/playlistItems"
        apiurl += "?key=${API_KEY}"
        apiurl += "&part=snippet"
        apiurl += "&playlistId=${id}"
        apiurl += "&maxResults=50"
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

        callback.onTaskDone(response.toString())
        return "success";
    }
}