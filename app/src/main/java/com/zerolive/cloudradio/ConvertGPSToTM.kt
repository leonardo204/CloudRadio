package com.zerolive.cloudradio

import android.os.AsyncTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

data class TMXY (val tmX: Double, val tmY: Double)

object ConvertGPSToTM {
    val mConsumerKey = "d715e9f18db7431f892e"
    val mConsumerSecret = "6daba12f23b24b82a4b9"
    var mAccessToken: String? = null
    var tmxy: TMXY? = null

    private fun handleTokenResult(response: String) {
        val element = Json.parseToJsonElement(response)
        val result = Json.parseToJsonElement(element.jsonObject["result"].toString())
        val accessToken = result.jsonObject["accessToken"].toString().replace("\"", "")
        accessToken?.let {
            CRLog.d("handleTokenResult: ${it}")
            mAccessToken = it
        }
    }

    private fun handleCoordinateResult(response: String): TMXY? {
        val element = Json.parseToJsonElement(response)
        val result = Json.parseToJsonElement(element.jsonObject["result"].toString())
        val errMsg = element.jsonObject["errMsg"].toString().replace("\"", "")
        CRLog.d("handleCoordinateResult. errMsg=${errMsg}")
        if ( errMsg.equals("Success") ) {
            val posX = result.jsonObject["posX"].toString().replace("\"", "")
            val posY = result.jsonObject["posY"].toString().replace("\"", "")

            CRLog.d("handleCoordinateResult.  x:${posX} y:${posY}")

            return TMXY(posX.toDouble(), posY.toDouble())
        } else {
            return null
        }
    }

    fun convert(posX: Double, posY: Double) {
        CoroutineScope(IO).launch {
            val tokenResult = getKostatToken()
            tokenResult?.let { handleTokenResult(it) }
            mAccessToken?.let {
                val tmxyResult = getTMCoordination(posX, posY, it)
                tmxy = tmxyResult?.let { it1 -> handleCoordinateResult(it1) }

                tmxy?.let {
                    // 정확한 tmxy 좌표를 얻었으면 이걸로 미세먼지 측정소 정보를 다시 얻어보자
                    CRLog.d("convert(GPS. ${posX}, ${posY} ) -> (TM. ${it.tmX}, ${it.tmY} )")
                    AirStatus.requestTMCoordination(GeoInfomation.umdName)
                }
            }
        }
    }
    fun getTMCoordination(posX: Double, posY: Double, accessToken: String): String? {
        var apiurl = "https://sgisapi.kostat.go.kr/OpenAPI3/transformation/transcoord.json"
        apiurl += "?accessToken=${accessToken}"
        apiurl += "&src=4326"
        apiurl += "&dst=5181"
        apiurl += "&posX=${posX}"
        apiurl += "&posY=${posY}"

        CRLog.d("getTMCoordination url: ${apiurl}")
        val response = StringBuffer()

        try {
            val url = URL(apiurl)
            val con: HttpURLConnection = url.openConnection() as HttpURLConnection
            con.setRequestMethod("GET")
            val br = BufferedReader(InputStreamReader(con.getInputStream(), "UTF-8"))
            var inputLine: String?
            while (br.readLine().also { inputLine = it } != null) {
                response.append(inputLine)
            }
            br.close()
            CRLog.d("length: ${response.length}")
        } catch (e: Exception) {
            CRLog.d("getTMCoordination Error: " + e.message)
        }

        return response.toString()
    }

    fun getKostatToken(): String? {
        var apiurl = "https://sgisapi.kostat.go.kr/OpenAPI3/auth/authentication.json"
        apiurl += "?consumer_key=${mConsumerKey}"
        apiurl += "&consumer_secret=${mConsumerSecret}"

        CRLog.d("GetKostatToken url: ${apiurl}")
        val response = StringBuffer()

        try {
            val url = URL(apiurl)
            val con: HttpURLConnection = url.openConnection() as HttpURLConnection
            con.setRequestMethod("GET")
            val br = BufferedReader(InputStreamReader(con.getInputStream(), "UTF-8"))
            var inputLine: String?
            while (br.readLine().also { inputLine = it } != null) {
                response.append(inputLine)
            }
            br.close()
            CRLog.d("length: ${response.length}")
        } catch (e: Exception) {
            CRLog.d("GetKostatToken Error: " + e.message)
        }

        return response.toString()
    }
}