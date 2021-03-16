package com.example.cloudradio

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment

import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

var onairTag = "OnAir"

val num_of_rows = 10
val page_no = 1
val data_type = "JSON"
var base_time = 1700
var base_date = 20210316
val nx = "60"
val ny = "125"

data class WEATHER (
    val response : RESPONSE
)
data class RESPONSE (
    val header : HEADER,
    val body : BODY
)
data class HEADER(
    val resultCode : Int,
    val resultMsg : String
)
data class BODY(
    val dataType : String,
    val items : ITEMS
)
data class ITEMS(
    val item : List<ITEM>
)
data class ITEM(
    val baseDate : Int,
    val baseTime : Int,
    val category : String,
    val fcstDate : String,
    val fcstTime : String,
    val fcstValue : String
)

interface WeatherInterface {
    @GET("getVilageFcst?serviceKey=ZZSvyzoRPHWzl9Uj650WLGx37OJ%2FQA0VdvtKq4SD8K6au7LhEI4X1l2jx4J4iB05XOq9H%2BQGU%2FmvNTSkC22Fqg%3D%3D")
    fun GetWeather(
        @Query("dataType") data_type : String,
        @Query("numOfRows") num_of_rows : Int,
        @Query("pageNo") page_no : Int,
        @Query("base_date") base_date : Int,
        @Query("base_time") base_time : Int,
        @Query("nx") nx : String,
        @Query("ny") ny : String
    ): Call<WEATHER>
}


private val retrofit = Retrofit.Builder()
    .baseUrl("http://apis.data.go.kr/1360000/VilageFcstInfoService/") // 마지막 / 반드시 들어가야 함
    .addConverterFactory(GsonConverterFactory.create()) // converter 지정
    .build() // retrofit 객체 생성

object ApiObject {
    val retrofitService: WeatherInterface by lazy {
        retrofit.create(WeatherInterface::class.java)
    }
}

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

fun parseItems(items: ITEMS) {
    Log.d(onairTag, items.item.toString())
    for(i in 0..(num_of_rows-1) ) {
        Log.d(onairTag, "  --  $i -- ")
        Log.d(onairTag, "* category: "+ items.item[i].category)
        Log.d(onairTag, "* baseDate: "+ items.item[i].baseDate)
        Log.d(onairTag, "* baseTime: "+ items.item[i].baseTime)
        Log.d(onairTag, "* fcstDate: "+ items.item[i].fcstDate)
        Log.d(onairTag, "* fcstTime: "+ items.item[i].fcstTime)
        Log.d(onairTag, "* fcstValue: "+ items.item[i].fcstValue)
    }
    Log.d(onairTag,"")
}

class OnAir : Fragment() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment

        val current = LocalDateTime.now()
        val formatter1 = DateTimeFormatter.ofPattern("yyyyMMdd")
        val currdate1 = current.format(formatter1)

        var curtime = getLastBaseTime(Calendar.getInstance())
        var curtime2 = curtime.get(Calendar.HOUR_OF_DAY).toString() + curtime.get(Calendar.MINUTE).toString()

        base_date = currdate1.toInt()
        base_time = curtime2.toInt()

        Log.d(onairTag, "date: $base_date")
        Log.d(onairTag,"time: $base_time")

        val call = ApiObject.retrofitService.GetWeather(data_type, num_of_rows, page_no, base_date, base_time, nx, ny)
        call.enqueue(object : retrofit2.Callback<WEATHER>{
            override fun onResponse(call: Call<WEATHER>, response: Response<WEATHER>) {
                if (response.isSuccessful){
                    var bodyStr = response.body()
                    Log.d(onairTag, bodyStr!!.toString())
                    if ( bodyStr!!.response.body != null ) {
                        Log.d(onairTag, "* 0th category: "+ bodyStr!!.response.body.items.item[0].category)
                        parseItems(bodyStr!!.response.body.items)
                    }
                }
            }
            override fun onFailure(call: Call<WEATHER>, t: Throwable) {
                Log.d(onairTag, "api fail : "+ t.message)
            }
        })

        return inflater.inflate(R.layout.fragment_onair, container, false)
    }
}