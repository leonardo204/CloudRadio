package com.example.cloudradio

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.icu.text.DecimalFormat
import android.icu.text.NumberFormat
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*


var onairTag = "OnAir"

// request location code
/*
http://www.kma.go.kr/DFSROOT/POINT/DATA/top.json.txt
각 지역 시,도의 코드를 제공합니다.

http://www.kma.go.kr/DFSROOT/POINT/DATA/mdl.11.json.txt
mdl.11.json에서 11은 위에서 알아댄 시,도 코드입니다. 해당 코드를 입력하면 해당 시,도의 시군구 목록이 나타납니다.

http://www.kma.go.kr/DFSROOT/POINT/DATA/leaf.11545.json.txt
leaf.11545,json에서 11545는 위의 과정에서 알아낸 시군구 코드입니다. 이 코드를 통해 자신이 원하는 자료를 알아내실 수 있습니다.
 */

var location_mid_code = 0

data class LOCATIONLEAF (
    val code: Int,
    val value: String,
    val x: String,
    val y: String
)

data class LOCATIONINFO(
    val code: Int,
    val value: String
)

interface LocationLeafInterface {
    @GET("{filename}")
    fun getLocation(
        @Path("filename") filename: String
    ): Call<List<LOCATIONLEAF>>
}

interface LocationMidInterface {
    @GET("{filename}")
    fun getLocation(
        @Path("filename") filename: String
    ): Call<List<LOCATIONINFO>>
}

interface LocationTopInterface {
    @GET("top.json.txt")
    fun getLocation(): Call<List<LOCATIONINFO>>
}

internal class FixEncodingInterceptor : Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val response = chain.proceed(chain.request())
        val oldMediaType: MediaType = MediaType.parse(response.header("Content-Type"))!!
        // update only charset in mediatype
        val newMediaType: MediaType = MediaType.parse(
            oldMediaType.type().toString() + "/" + oldMediaType.subtype() + "; charset=utf-8"
        )!!
        // update body
        val newResponseBody: ResponseBody =
            ResponseBody.create(newMediaType, response.body()?.bytes())
        return response.newBuilder()
            .removeHeader("Content-Type")
            .addHeader("Content-Type", newMediaType.toString())
            .body(newResponseBody)
            .build()
    }
}

val client = OkHttpClient.Builder()
    .addInterceptor(FixEncodingInterceptor())
    .build()

private val locationRetrofit = Retrofit.Builder()
    .baseUrl("http://www.kma.go.kr/DFSROOT/POINT/DATA/")
    .addConverterFactory(GsonConverterFactory.create())
    .client(client)
    .build() // retrofit 객체 생성

object locationTopObj {
    val retrofitService: LocationTopInterface by lazy {
        locationRetrofit.create(LocationTopInterface::class.java)
    }
}

object locationMidObj {
    val retrofitService: LocationMidInterface by lazy {
        locationRetrofit.create(LocationMidInterface::class.java)
    }
}

object locationLeafObj {
    val retrofitService: LocationLeafInterface by lazy {
        locationRetrofit.create(LocationLeafInterface::class.java)
    }
}

// request wether
val num_of_rows = 10
val page_no = 1
val data_type = "JSON"
var base_time = 1700
var base_date = 20210316
var nx = "60"
var ny = "125"

// response
data class WEATHER(
    val response: RESPONSE
)
data class RESPONSE(
    val header: HEADER,
    val body: BODY
)
data class HEADER(
    val resultCode: Int,
    val resultMsg: String
)
data class BODY(
    val dataType: String,
    val items: ITEMS
)
data class ITEMS(
    val item: List<ITEM>
)
data class ITEM(
    val baseDate: Int,
    val baseTime: Int,
    val category: String,
    val fcstDate: String,
    val fcstTime: String,
    val fcstValue: String
)

interface WeatherInterface {
    @GET("getVilageFcst?serviceKey=ZZSvyzoRPHWzl9Uj650WLGx37OJ%2FQA0VdvtKq4SD8K6au7LhEI4X1l2jx4J4iB05XOq9H%2BQGU%2FmvNTSkC22Fqg%3D%3D")
    fun GetWeather(
        @Query("dataType") data_type: String,
        @Query("numOfRows") num_of_rows: Int,
        @Query("pageNo") page_no: Int,
        @Query("base_date") base_date: Int,
        @Query("base_time") base_time: Int,
        @Query("nx") nx: String,
        @Query("ny") ny: String
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


// location result
internal class LatXLngY {
    var lat = 0.0
    var lng = 0.0
    var x = 0.0
    var y = 0.0
}

class OnAir : Fragment(), LocationListener {

    private lateinit var mContext: Context
    private var mLatitude: Double = 0.0  // 위도 (가로...)
    private var mLongitude: Double = 0.0 // 경도 (세로...)

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        if (container != null) {
            mContext = container.context
        }

        // location
        getLocation()

        return inflater.inflate(R.layout.fragment_onair, container, false)
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

    private fun parseItems(items: ITEMS) {
        Log.d(onairTag, items.item.toString())
        for(i in 0..(num_of_rows-1) ) {
            Log.d(onairTag, "  --  $i -- ")
            Log.d(onairTag, "* category: " + items.item[i].category)
            Log.d(onairTag, "* baseDate: " + items.item[i].baseDate)
            Log.d(onairTag, "* baseTime: " + items.item[i].baseTime)
            Log.d(onairTag, "* fcstDate: " + items.item[i].fcstDate)
            Log.d(onairTag, "* fcstTime: " + items.item[i].fcstTime)
            Log.d(onairTag, "* fcstValue: " + items.item[i].fcstValue)
        }
        Log.d(onairTag, "")
    }

    private fun getLocationLeafDetail(code: Int): Boolean {
        val filename = "leaf." + code + ".json.txt"
        var locationLeafCall = locationLeafObj.retrofitService.getLocation(filename)
        locationLeafCall.enqueue((object: retrofit2.Callback<List<LOCATIONLEAF>> {
            override fun onResponse(
                call: Call<List<LOCATIONLEAF>>,
                response: Response<List<LOCATIONLEAF>>
            ) {
                if (response.isSuccessful && response.body() != null) {
                    Log.d(onairTag, "LEAF: " + response.body())
                    Log.d(onairTag, "LEAF[0] code: " + response.body()!![0].code)
                    Log.d(onairTag, "LEAF[0] value: " + response.body()!![0].value)
                    Log.d(onairTag, "LEAF[0] x: " + response.body()!![0].x)
                    Log.d(onairTag, "LEAF[0] y: " + response.body()!![0].y)
                }
            }

            override fun onFailure(call: Call<List<LOCATIONLEAF>>, t: Throwable) {
                Log.d(onairTag, "locationMidCall fail : " + t.message)
            }

        }))
        return false
    }

    private fun getLocationMidDetail(code: Int) :Boolean {
        val filename = "mdl." + code + ".json.txt"
        var locationMidCall = locationMidObj.retrofitService.getLocation(filename)
        locationMidCall.enqueue((object: retrofit2.Callback<List<LOCATIONINFO>> {
            override fun onResponse(
                call: Call<List<LOCATIONINFO>>,
                response: Response<List<LOCATIONINFO>>
            ) {
                if (response.isSuccessful && response.body() != null) {
                    Log.d(onairTag, "MID: " + response.body())
                    Log.d(onairTag, "MID[0] code: " + response.body()!![0].code)
                    Log.d(onairTag, "MID[0] value: " + response.body()!![0].value)

                    getLocationLeafDetail( response.body()!![0].code )
                }
            }

            override fun onFailure(call: Call<List<LOCATIONINFO>>, t: Throwable) {
                Log.d(onairTag, "locationMidCall fail : " + t.message)
            }

        }))
        return false;
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestWeather() {

        // get gps and x, y location
        Log.d(onairTag, "Check GPS. Latitude: " + mLatitude + " , Longitude: " + mLongitude)
        val CurGPS = convertGRID_GPS(TO_GRID, Math.abs(mLatitude), Math.abs(mLongitude))
        Log.d(onairTag, "Current Location.  x: " + CurGPS.x + ", y: " + CurGPS.y)
        nx = CurGPS.x.toInt().toString()
        ny = CurGPS.y.toInt().toString()

        // get location detail
        var locationTopCall = locationTopObj.retrofitService.getLocation()
        locationTopCall.enqueue(object : retrofit2.Callback<List<LOCATIONINFO>> {
            override fun onResponse(
                call: Call<List<LOCATIONINFO>>,
                response: Response<List<LOCATIONINFO>>
            ) {
                if (response.isSuccessful && response.body() != null) {
                    Log.d(onairTag, "TOP: " + response.body())
                    Log.d(onairTag, "TOP[0] code: " + response.body()!![0].code)
                    Log.d(onairTag, "TOP[0] value: " + response.body()!![0].value)

                    getLocationMidDetail( response.body()!![0].code )
                }
            }

            override fun onFailure(call: Call<List<LOCATIONINFO>>, t: Throwable) {
                Log.d(onairTag, "locationTopCall fail : " + t.message)
            }
        })

        // time
        val current = LocalDateTime.now()
        val formatter1 = DateTimeFormatter.ofPattern("yyyyMMdd")
        val currdate1 = current.format(formatter1)
        val f: NumberFormat = DecimalFormat("00")

        var curtime = getLastBaseTime(Calendar.getInstance())
        var curtime2 = f.format(curtime.get(Calendar.HOUR_OF_DAY)) + f.format(curtime.get(Calendar.MINUTE))

        base_date = currdate1.toInt()
        base_time = curtime2.toInt()

        Log.d(onairTag, "- request weather - ")
        Log.d(onairTag, "data_type: $data_type")
        Log.d(onairTag, "num_of_rows: $num_of_rows")
        Log.d(onairTag, "page_no: $page_no")
        Log.d(onairTag, "date: " + base_date + ", time: " + base_time)
        Log.d(onairTag, "nx: " + nx + ", ny: " + ny)

        val call = ApiObject.retrofitService.GetWeather(
            data_type,
            num_of_rows,
            page_no,
            base_date,
            base_time,
            nx,
            ny
        )

        call.enqueue(object : retrofit2.Callback<WEATHER> {
            override fun onResponse(call: Call<WEATHER>, response: Response<WEATHER>) {
                if (response.isSuccessful) {
                    var bodyStr = response.body()
                    Log.d(onairTag, bodyStr!!.toString())
                    if (bodyStr!!.response.body != null) {
                        parseItems(bodyStr!!.response.body.items)
                    }
                }
            }

            override fun onFailure(call: Call<WEATHER>, t: Throwable) {
                Log.d(onairTag, "api fail : " + t.message)
            }
        })
    }

    private lateinit var locationManager: LocationManager
    private val locationPermissionCode = 2

    private fun getLocation() {
        locationManager = getActivity()?.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if ((ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(
                mContext as Activity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                locationPermissionCode
            )
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 5f, this)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onLocationChanged(location: Location) {
        Log.d(
            onairTag,
            "Get GPS. Latitude: " + location.latitude + " , Longitude: " + location.longitude
        )
        mLatitude = location.latitude
        mLongitude = location.longitude

        // call weather after get location
        requestWeather()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == locationPermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(mContext, "Permission Granted", Toast.LENGTH_SHORT).show()
            }
            else {
                Toast.makeText(mContext, "Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        TODO("Not yet implemented")
    }

    override fun onProviderEnabled(provider: String?) {
        TODO("Not yet implemented")
    }

    override fun onProviderDisabled(provider: String?) {
        TODO("Not yet implemented")
    }

    var TO_GRID = 0
    var TO_GPS = 1

    private fun convertGRID_GPS(mode: Int, lat_X: Double, lng_Y: Double):LatXLngY {
        val RE = 6371.00877 // 지구 반경(km)
        val GRID = 5.0 // 격자 간격(km)
        val SLAT1 = 30.0 // 투영 위도1(degree)
        val SLAT2 = 60.0 // 투영 위도2(degree)
        val OLON = 126.0 // 기준점 경도(degree)
        val OLAT = 38.0 // 기준점 위도(degree)
        val XO = 43.0 // 기준점 X좌표(GRID)
        val YO = 136.0 // 기1준점 Y좌표(GRID)
        //
        // LCC DFS 좌표변환 ( code : "TO_GRID"(위경도->좌표, lat_X:위도, lng_Y:경도), "TO_GPS"(좌표->위경도, lat_X:x, lng_Y:y) )
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

