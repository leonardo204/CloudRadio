package com.zerolive.cloudradio

import android.annotation.SuppressLint
import android.icu.text.DecimalFormat
import android.icu.text.NumberFormat
import android.os.Build
import androidx.annotation.RequiresApi
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit

var weatherTag = "CR_WeatherStatus"

// response
data class WEATHER(
    val response: WEATHER_RESPONSE
)
data class WEATHER_RESPONSE(
    val header: WEATHER_HEADER,
    val body: WEATHER_BODY
)
data class WEATHER_HEADER(
    val resultCode: Int,
    val resultMsg: String
)
data class WEATHER_BODY(
    val dataType: String,
    val items: WEATHER_ITEMS
)
data class WEATHER_ITEMS(
    val item: List<WEATHER_ITEM>
)
data class WEATHER_ITEM(
    val baseDate: Int,
    val baseTime: Int,
    val category: String,
    val fcstDate: String,
    val fcstTime: Int,
    val fcstValue: String
)

interface WeatherInterface {
    @GET("getVilageFcst?serviceKey=ZZSvyzoRPHWzl9Uj650WLGx37OJ%2FQA0VdvtKq4SD8K6au7LhEI4X1l2jx4J4iB05XOq9H%2BQGU%2FmvNTSkC22Fqg%3D%3D")
    fun GetWeather(
        @Query("dataType", encoded = true) data_type: String,
        @Query("numOfRows") num_of_rows: Int,
        @Query("pageNo") page_no: Int,
        @Query("base_date", encoded = true) base_date: String,
        @Query("base_time", encoded = true) base_time: String,
        @Query("nx", encoded = true) nx: String,
        @Query("ny", encoded = true) ny: String
    ): Call<WEATHER>
}

val weatherClient = OkHttpClient.Builder()
//    .addInterceptor(FixEncodingInterceptor())
//    .addInterceptor(httpLoggingInterceptor())         // http request inspector debuggin
//    .followRedirects(false)
//    .followSslRedirects(false)
    .connectTimeout(3000, TimeUnit.MILLISECONDS)
    .readTimeout(3000, TimeUnit.MILLISECONDS)
    .build()

// 20210705 API 2.0 ?????? ?????? ??????
private val retrofit = Retrofit.Builder()
    .baseUrl("http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/") // ????????? / ????????? ???????????? ???
    .addConverterFactory(GsonConverterFactory.create()) // converter ??????
    .client(weatherClient)
    .build() // retrofit ?????? ??????

object ApiObject {
    val retrofitService: WeatherInterface by lazy {
        retrofit.create(WeatherInterface::class.java)
    }
}


// location result
internal class LatXLngY {
    var lat = 0.0
    var lng = 0.0
    var x = 0.0
    var y = 0.0
}

object WeatherStatus {

    var TO_GRID = 0
    var TO_GPS = 1

    private fun getLastBaseTime(calBase: Calendar):Calendar {
        val t = calBase.get(Calendar.HOUR_OF_DAY)
        if (t < 2)
        {
            calBase.add(Calendar.DATE, -1)
            calBase.set(Calendar.HOUR_OF_DAY, 23)
        }
        else
        {
            calBase.set(Calendar.HOUR_OF_DAY, t - (t + 1) % 3)
        }
        return calBase
    }

    private fun getSnowAmount(value: Double): String {
        if ( value < 0.1f ) return "??????"
        else if(value >= 0.1f && value < 1.0f) return "1cm??????";
        else if(value >= 1.0f && value < 5.0f) return "1~4cm";
        else if(value >= 5.0f && value < 10.0f) return "5~9cm";
        else if(value >= 10.0f && value < 20.0f) return "10~19cm";
        else return "20cm??????";
    }

    private fun getWindDirectionString(value: Int): String {
        var data: Int = ((value + 22.5 * 0.5) / 22.5).toInt()
        when(data) {
            0 -> return "??????"
            1 -> return "?????????"
            2 -> return "??????"
            3 -> return "?????????"
            4 -> return "???"
            5 -> return "?????????"
            6 -> return "??????"
            7 -> return "?????????"
            8 -> return "???"
            9 -> return "?????????"
            10 -> return "??????"
            11 -> return "?????????"
            12 -> return "???"
            13 -> return "?????????"
            14 -> return "??????"
            15 -> return "?????????"
            16 -> return "???"
            else -> return "??? ??? ??????"
        }
    }

    private fun getRainAmount(value: Double): String {
        if ( value < 0.1f ) return "??????"
        else if(value >= 0.1f && value < 1.0f) return "1mm??????";
        else if(value >= 1.0f && value < 5.0f) return "1~4mm";
        else if(value >= 5.0f && value < 10.0f) return "5~9mm";
        else if(value >= 10.0f && value < 20.0f) return "10~19mm";
        else if(value >= 20.0f && value < 40.0f) return "20~39mm";
        else if(value >= 40.0f && value < 70.0f) return "40~69mm";
        else return "70mm??????";
    }

    private fun getSkyType(type: Int): String {
        when( type ) {
            1 -> return "??????"
            3 -> return "?????? ??????"
            4 -> return "??????"
            else -> return "??? ??? ??????"
        }
    }

    private fun getRainType(type: Int): String {
        when( type ) {
            0 -> return "??????"
            1 -> return "???"
            2 -> return "???/???(????????????)"
            3 -> return "???"
            4 -> return "?????????"
            5 -> return "?????????"
            6 -> return "?????????/?????????"
            7 -> return "?????????"
            else -> return "??????"
        }
    }

    private fun updateRainProperty(humidity: String, rainPercent: String) {
        CRLog.d(

            "updateRainProperty  humidity: " + humidity + ", rain percent: " + rainPercent
        )
        var text: String = "??????  "+humidity +" %\n"
        text += "????????????  "+rainPercent +" %\n"
        OnAir.txt_rainView.setText(text)
        OnAir.mRainText = text
    }

    private fun updateWindProperty(windSpeed: Double, windDirection: String) {
        CRLog.d("updateWindProperty  direction: " + windDirection + ", speed: " + windSpeed)
        var text:String = "??????  "+windDirection +"\n"
        text += "??????  "+ windSpeed + " m/s"
        OnAir.txt_windView.setText(text)
        OnAir.mWindText = text
    }

    private fun updateTemperature(cur: String, high: String, low: String) {
        CRLog.d("updateTemperature cur: " + cur + ", high: " + high + ", low: " + low)
        var text:String = "??????  "+cur +"???\n"
        if ( high.equals("N/A") == false) text += "??? ?????? " + high +"???\n"
        if ( low.equals("N/A") == false) text += "??? ?????? " + low +"???\n"
        OnAir.txt_skyView.setText(text)
        OnAir.mTemperatureText = text
    }

    private fun updateFcstTimeView(basetime: String, fcstTime: String) {
        val new_basetime = basetime.substring(0, 2) + ":" + basetime.subSequence(2, basetime.length)
        val new_fcstTime = fcstTime.substring(0, 2) + ":" + fcstTime.subSequence(2, fcstTime.length)
        val updateText = "?????? ( " + new_basetime + " ~ " + new_fcstTime+ " )"

        CRLog.d("updateFcstTimeView: " + updateText)
        OnAir.txt_fcstView.setText(updateText)
        OnAir.mFcstTimeText = updateText
    }


    /*
     * POP	????????????	 %
     * PTY	????????????	????????? (??????(0), ???(1), ???/???(????????????)(2), ???(3), ?????????(4), ?????????(5), ?????????/?????????(6), ?????????(7))
     * R06	6?????? ?????????	?????? (1 mm)
     * REH	??????	 %
     * S06	6?????? ?????????	??????(1 cm)
     * SKY	????????????	????????? (??????(1), ????????????(3), ??????(4))
     * T3H	3?????? ??????	 ???
     * TMN	?????? ????????????	 ???
     * TMX	??? ????????????	 ???
     * UUU	??????(????????????)	 m/s
     * VVV	??????(????????????)	 m/s
     * VEC  ??????      deg 10bit
     */
    private fun parseItems(items: WEATHER_ITEMS) {
        CRLog.d(items.item.toString())
        CRLog.d("- ??????: " + items.item[0].baseDate)
        CRLog.d("- ????????????: " + items.item[0].baseTime)
        CRLog.d("- ????????????: " + items.item[0].fcstTime)

        var basetime: String
        var fcsttime: String

        if ( items.item[0].baseTime < 1000 ) basetime = "0"+items.item[0].baseTime.toString()
        else basetime = items.item[0].baseTime.toString()

        if ( items.item[0].fcstTime < 1000 ) fcsttime = "0"+items.item[0].fcstTime.toString()
        else fcsttime = items.item[0].fcstTime.toString()

        updateFcstTimeView(basetime, fcsttime)

        var currentTemperature: String = "N/A"
        var higherTempearture: String = "N/A"
        var lowerTempearture: String = "N/A"

        var windDirection: String = "N/A"
        var windSpeed: Double = 0.0

        var humidity: String = "N/A"
        var rainPercent: String = "N/A"

        var fcstTime = items.item[0].fcstTime

        for (i in items.item.indices) {
            CRLog.d("category: " + items.item[i].category)
            when( items.item[i].category ) {
                "POP" -> {
                    if (fcstTime.equals(items.item[i].fcstTime)) {
                        CRLog.d("- ????????????(%): " + items.item[i].fcstValue)
                        rainPercent = items.item[i].fcstValue
                    }
                }
                "PTY" -> {
                    CRLog.d("- ????????????(code): " + getRainType(items.item[i].fcstValue.toInt()))
                    OnAir.setRainStatusImage(items.item[i].fcstValue.toInt())
                }
                "R06" -> {
                    CRLog.d("- 6?????? ?????????(mm): " + getRainAmount(items.item[i].fcstValue.toDouble()))
                }
                "PCP" -> {
                    CRLog.d("- 1?????? ?????????(mm): ${items.item[i].fcstValue}")
                }
                "REH" -> {
                    CRLog.d("- ??????(%): " + items.item[i].fcstValue)
                    humidity = items.item[i].fcstValue
                }
                "S06" -> {
                    CRLog.d("- 6?????? ?????????(mm): " + getSnowAmount(items.item[i].fcstValue.toDouble()))
                }
                "SKY" -> {
                    CRLog.d("- ????????????(code): " + getSkyType(items.item[i].fcstValue.toInt()))
                    OnAir.setSkyStatusImage(items.item[i].fcstValue.toInt())
                }
                "T3H" -> {
                    CRLog.d("- 3?????? ??????(???): " + items.item[i].fcstValue)
                    currentTemperature = items.item[i].fcstValue;
                }
                "TMN" -> {
                    CRLog.d("-  ?????? ??????(???): " + items.item[i].fcstValue)
                    lowerTempearture = items.item[i].fcstValue
                }
                "TMX" -> {
                    CRLog.d("- ??? ?????? ??????(???): " + items.item[i].fcstValue)
                    higherTempearture = items.item[i].fcstValue
                }
                // version 2.0
                "TMP" -> {
                    CRLog.d("- 1?????? ??????(???): ${items.item[i].fcstValue}")
                    currentTemperature = items.item[i].fcstValue;
                }
                "UUU" -> {
                    CRLog.d("- ??????(????????????)(m/s): " + items.item[i].fcstValue)
                    if (windSpeed < items.item[i].fcstValue.toDouble()) windSpeed =
                        items.item[i].fcstValue.toDouble()
                }
                "VVV" -> {
                    CRLog.d("- ??????(????????????)(m/s): " + items.item[i].fcstValue)
                    if (windSpeed < items.item[i].fcstValue.toDouble()) windSpeed =
                        items.item[i].fcstValue.toDouble()
                }
                "VEC" -> {
                    CRLog.d("- ??????: " + getWindDirectionString(items.item[i].fcstValue.toInt()))
                    windDirection = getWindDirectionString(items.item[i].fcstValue.toInt())
                }
                "WSD" -> {
                    CRLog.d("- ??????(m/s): " + items.item[i].fcstValue)
                    if (windSpeed < items.item[i].fcstValue.toDouble()) windSpeed =
                        items.item[i].fcstValue.toDouble()
                }
                else -> CRLog.d("Invalid category: " + items.item[i].category)
            }
        }
        CRLog.d("")

        updateTemperature(currentTemperature, higherTempearture, lowerTempearture)

        updateWindProperty(windSpeed, windDirection)

        updateRainProperty(humidity, rainPercent)
    }

    fun getCurrentDatetime(): String {
        var curdate: String
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            val current: LocalDateTime = LocalDateTime.now()
            val formatter1 = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            curdate = current.format(formatter1).toString()
        } else {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss")
            curdate = sdf.format(Date())
        }
        return curdate
    }

    @SuppressLint("SimpleDateFormat")
    @RequiresApi(Build.VERSION_CODES.O)
    fun requestWeather(lat: Double, lng: Double) {
        // get gps and x, y location
        CRLog.d("Check GPS. Latitude: " + lat + " , Longitude: " + lng)

        // gps -> tm
        ConvertGPSToTM.convert(lng, lat)

        val CurGPS = convertGRID_GPS(TO_GRID, Math.abs(lat), Math.abs(lng))
        CRLog.d("Current Location.  x: " + CurGPS.x + ", y: " + CurGPS.y)
        val nx = CurGPS.x.toInt().toString()
        val ny = CurGPS.y.toInt().toString()

        // time
        val curtime = getLastBaseTime(Calendar.getInstance())
        val curtime2:String
        val currdate1:String

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        {
            val current: LocalDateTime = LocalDateTime.now()
            val formatter1 = DateTimeFormatter.ofPattern("yyyyMMdd")
            currdate1 = current.format(formatter1)
            val f: NumberFormat = DecimalFormat("00")
            curtime2 = f.format(curtime.get(Calendar.HOUR_OF_DAY)) + f.format(curtime.get(Calendar.MINUTE))

        } else {
            var sdf = SimpleDateFormat("yyyyMMdd")
            currdate1 = sdf.format(Date())
            sdf = SimpleDateFormat("hh")
            val hh:String
            val mm:String
            if ( sdf.format(Date()).toInt() < 10 ) hh = "0"+sdf.format(Date())
            else hh = sdf.format(Date())
            sdf = SimpleDateFormat("mm")
            if ( sdf.format(Date()).toInt() < 10 ) mm = "0"+sdf.format(Date())
            else mm = sdf.format(Date())
            curtime2 = hh + mm
        }

        val base_date = currdate1
        val base_time = curtime2

        CRLog.d("- request weather - ")
        CRLog.d("data_type: ${OnAir.data_type}")
        CRLog.d("num_of_rows: ${OnAir.num_of_rows}")
        CRLog.d("page_no: ${OnAir.page_no}")
        CRLog.d("date: " + base_date + ", time: " + base_time)
        CRLog.d("nx: " + nx + ", ny: " + ny)

        val call = ApiObject.retrofitService.GetWeather(
            OnAir.data_type,
            OnAir.num_of_rows,
            OnAir.page_no,
            base_date,
            base_time,
            nx,
            ny
        )

        CRLog.d("URL: " + call.request().url().toString())

        call.enqueue(object : retrofit2.Callback<WEATHER> {
            override fun onResponse(call: Call<WEATHER>, response: Response<WEATHER>) {
                if (response.isSuccessful) {
                    var bodyStr = response.body()
                    CRLog.d(bodyStr!!.toString())
                    if ( bodyStr!!.response != null ) {
                        if (bodyStr!!.response.body != null) {
                            parseItems(bodyStr!!.response.body.items)
                        }
                    } else {
                        CRLog.d("ERROR: request WEATHER was failed!!!!")
                        MainActivity.getInstance().makeToast("?????? ?????? ??????????????? ?????????????????????.")
                        MainActivity.getInstance().removeLoading()
                    }
                }
            }

            override fun onFailure(call: Call<WEATHER>, t: Throwable) {
                CRLog.d("api fail : " + t.message)
                MainActivity.getInstance().makeToast("?????? ?????? ??????????????? ?????????????????????.")
                MainActivity.getInstance().removeLoading()
            }
        })
    }



    private fun convertGRID_GPS(mode: Int, lat_X: Double, lng_Y: Double): LatXLngY {
        val RE = 6371.00877 // ?????? ??????(km)
        val GRID = 5.0 // ?????? ??????(km)
        val SLAT1 = 30.0 // ?????? ??????1(degree)
        val SLAT2 = 60.0 // ?????? ??????2(degree)
        val OLON = 126.0 // ????????? ??????(degree)
        val OLAT = 38.0 // ????????? ??????(degree)
        val XO = 43.0 // ????????? X??????(GRID)
        val YO = 136.0 // ???1?????? Y??????(GRID)
        //
        // LCC DFS ???????????? ( code : "TO_GRID"(?????????->??????, lat_X:??????, lng_Y:??????), "TO_GPS"(??????->?????????, lat_X:x, lng_Y:y) )
        //
        val DEGRAD = Math.PI / 180.0
        val RADDEG = 180.0 / Math.PI
        val re = RE / GRID
        val slat1 = SLAT1 * DEGRAD
        val slat2 = SLAT2 * DEGRAD
        val olon = OLON * DEGRAD
        val olat = OLAT * DEGRAD
        var sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5)
        sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn)
        var sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5)
        sf = Math.pow(sf, sn) * Math.cos(slat1) / sn
        var ro = Math.tan(Math.PI * 0.25 + olat * 0.5)
        ro = re * sf / Math.pow(ro, sn)
        val rs = LatXLngY()
        if (mode == TO_GRID)
        {
            rs.lat = lat_X
            rs.lng = lng_Y
            var ra = Math.tan(Math.PI * 0.25 + (lat_X) * DEGRAD * 0.5)
            ra = re * sf / Math.pow(ra, sn)
            var theta = lng_Y * DEGRAD - olon
            if (theta > Math.PI) theta -= 2.0 * Math.PI
            if (theta < -Math.PI) theta += 2.0 * Math.PI
            theta *= sn
            rs.x = Math.floor(ra * Math.sin(theta) + XO + 0.5)
            rs.y = Math.floor(ro - ra * Math.cos(theta) + YO + 0.5)
        }
        else
        {
            rs.x = lat_X
            rs.y = lng_Y
            val xn = lat_X - XO
            val yn = ro - lng_Y + YO
            var ra = Math.sqrt(xn * xn + yn * yn)
            if (sn < 0.0)
            {
                ra = -ra
            }
            var alat = Math.pow((re * sf / ra), (1.0 / sn))
            alat = 2.0 * Math.atan(alat) - Math.PI * 0.5
            var theta = 0.0
            if (Math.abs(xn) <= 0.0)
            {
                theta = 0.0
            }
            else
            {
                if (Math.abs(yn) <= 0.0)
                {
                    theta = Math.PI * 0.5
                    if (xn < 0.0)
                    {
                        theta = -theta
                    }
                }
                else
                    theta = Math.atan2(xn, yn)
            }
            val alon = theta / sn + olon
            rs.lat = alat * RADDEG
            rs.lng = alon * RADDEG
        }
        return rs
    }
}