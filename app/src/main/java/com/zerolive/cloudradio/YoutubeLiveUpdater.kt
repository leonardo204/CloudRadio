package com.zerolive.cloudradio

import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset


object YoutubeLiveUpdater : AsyncCallback{

    val updaterTag = "CR_Updater"
    var mUpdateCount = 0

    fun update() {
        Log.d(updaterTag,  "Update")

        var element: JsonElement? = RadioChannelResources.getResourceElement()

        // version check end
        element?.let {
            var data = Json.parseToJsonElement(element.jsonObject["data"].toString())
            Log.d(resourceTag, "channelSize ${RadioChannelResources.channelSize} -> ${RadioChannelResources.channelSize +data.jsonArray.size}")
            RadioChannelResources.channelSize += data.jsonArray.size
            for (i in data.jsonArray.indices) {
                Log.d(updaterTag,  "-    [$i]   -")
                Log.d(updaterTag,  "${data.jsonArray[i]}")

                val live = data.jsonArray[i].jsonObject["live"].toString().replace("\"", "").toBoolean()
                val title = data.jsonArray[i].jsonObject["title"].toString().replace("\"", "")
                val address = data.jsonArray[i].jsonObject["fileaddress"].toString().replace("\"", "")

                // live 인 경우엔 redirection url 을 얻어서 설정해야 함
                if ( live ) {
                    mUpdateCount++
                    CheckLiveAddress(this).execute(title, address)
                    continue
                }
            }
        }
        MainActivity.getInstance().makeToast("유튜브 라이브 채널 업데이트 요청 완료")
    }



    override fun onTaskDone(vararg arg: String?) {
        val title = arg[0]
        var bUpdate = false
        mUpdateCount--
        title?.let {
            val finalUrl = arg[1]
            Log.d(updaterTag, "Update Live Channel for ${title}")
            for(i in RadioChannelResources.channelList.indices) {
                if ( title == RadioChannelResources.channelList.get(i).title ) {
                    if ( finalUrl != RadioChannelResources.channelList.get(i).httpAddress ) {
                        Log.d(updaterTag, "Update Live Channel Address of title(${title}) as ${RadioChannelResources.channelList.get(i).httpAddress} -> ${finalUrl}")
                        var videoId = finalUrl!!.substring( finalUrl!!.indexOf("watch?v=")+8)
                        var filename = "youtube_" + videoId
                        val map = RadioCompletionMap(
                            RadioChannelResources.channelList.get(i).defaultButtonText,
                            RadioChannelResources.channelList.get(i).title,
                            RadioChannelResources.channelList.get(i).id,
                            filename,
                            RadioChannelResources.channelList.get(i).fileaddress,
                            finalUrl,
                            MEDIATYPE.YOUTUBE_LIVE
                        )
                        synchronized(RadioChannelResources.mContext) {
                            RadioChannelResources.channelList.removeAt(i)
                            RadioChannelResources.channelList.add(i, map)
                        }
                        bUpdate = true
                    }
                }
            }
        }
        // Update 된 내용으로 favorite list file 을 재작성
        if ( bUpdate ) {
            Program.saveFavList()
        }

        if ( mUpdateCount == 0 ) {
            Log.d(updaterTag, "Youtube Live update is completed")

            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({
                MainActivity.getInstance().makeToast("유튜브 라이브 채널 업데이트가 완료되었습니다.")
            }, 0)
        }
    }

    internal class CheckLiveAddress(context: YoutubeLiveUpdater) : AsyncTask<String, String, String>() {
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
                Log.d(updaterTag,  "CheckLiveAddress.  title(${title}) url check: $finalUrl")
            } catch (e: Exception) {
                Log.d(updaterTag,  "CheckLiveAddress Exception(title:${title}): "+e.message)
                callback.onTaskDone(null)
                return null
            }

            callback.onTaskDone(title, finalUrl)
            return null
        }
    }
}