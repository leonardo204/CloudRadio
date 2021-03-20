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
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Logger
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
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


// 2021-03-20 google geocoding api 로 변경함
https://maps.googleapis.com/maps/api/geocode/json?latlng=37.566535,126.977969&language=ko&key=AIzaSyC-8Ut8ITfm9KKHE-8-5pre5CzeStgUC-w
 */

data class GEO_RESPONSE(
    val plus_code: GEO_PLUSCODE,
    val results: List<GEO_RESULT>,
    val status: String               // INVALID_REQUEST, OK
)

data class GEO_PLUSCODE(
    val compound_code: String,
    val global_code: String
)

data class GEO_RESULT(
    val address_components: List<GEO_ADDRESS>,
    val formatted_address: String,
    val geometry: GEO_GEOMETRY,
    val place_id: String,
    val plus_code: GEO_PLUSCODE,
    val types: List<String>
)

data class GEO_ADDRESS(
    val long_name: String,
    val short_name: String,
    val types: List<String>
)

// [ "postal_code" ]
// [ "country", "political" ]
// [ administrative_area_level_1, political ]
// [ "political", "sublocality", "sublocality_level_1" ]
// [ "political", "sublocality", "sublocality_level_2" ]
// [ "premise" ]

data class GEO_GEOMETRY(
    val location: GEO_LOCATION,
    val location_type: String,
    val viewport: GEO_VIEWPORT
)

data class GEO_LOCATION(
    val lat: String,
    val lng: String
)

data class GEO_VIEWPORT(
    val northeast: GEO_LOCATION,
    val southwest: GEO_LOCATION
)

interface GeocodingAPIInterface {
    @GET("json?language=ko&key=AIzaSyC-8Ut8ITfm9KKHE-8-5pre5CzeStgUC-w")
    fun getGeoInfo(
        @Query("latlng") latlng: String
    ): Call<GEO_RESPONSE>
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

private fun httpLoggingInterceptor(): HttpLoggingInterceptor? {
    val interceptor = HttpLoggingInterceptor(object : Logger {
        override fun log(message: String) {
            Log.e(onairTag, message + "")
        }
    })
    return interceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
}

val client = OkHttpClient.Builder()
    .addInterceptor(FixEncodingInterceptor())
    //.addInterceptor(httpLoggingInterceptor())         // http request inspector debugging
    .build()

private val geoRetrofit = Retrofit.Builder()
    .baseUrl("https://maps.googleapis.com/maps/api/geocode/")
    .addConverterFactory(GsonConverterFactory.create())
    .client(client)
    .build() // retrofit 객체 생성

object geoObj {
    val retrofitService: GeocodingAPIInterface by lazy {
        geoRetrofit.create(GeocodingAPIInterface::class.java)
    }
}

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
        @Query("base_date") base_date: String,
        @Query("base_time") base_time: String,
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

class OnAir : Fragment(), LocationListener, ActivityCompat.OnRequestPermissionsResultCallback {

    // request wether (fixed)
    val num_of_rows = 10
    val page_no = 1
    val data_type = "JSON"

    private lateinit var mContext: Context
    private var mLatitude: Double = 0.0  // 위도 (가로...)
    private var mLongitude: Double = 0.0 // 경도 (세로...)

    private lateinit var timeTextView: TextView
    private lateinit var weatherTextView: TextView
    private lateinit var addrTextView: TextView
    var notLoadedMessage: String = "PLEASE WAIT..."

    lateinit var mDateText: String
    lateinit var mAddressText: String
    lateinit var mWeatherText: String


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(onairTag, "onViewCreated")

        timeTextView = view.findViewById(R.id.timeTextView)
        addrTextView = view.findViewById(R.id.addrTextView)
        weatherTextView = view.findViewById(R.id.weatherTextView)

        timeTextView.setText(notLoadedMessage)
        addrTextView.setText(notLoadedMessage)
        weatherTextView.setText(notLoadedMessage)

    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(onairTag, "onCreateView")

        if (container != null) {
            mContext = container.context
        }

        // get current gps info
        checkPermissions()

        return inflater.inflate(R.layout.fragment_onair, container, false)
    }

    private fun makeToast(message: String) {
        Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show()
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

    private fun getSnowAmount(value: Double): String {
        if ( value < 0.1f ) return "없음"
        else if(value >= 0.1f && value < 1.0f) return "1cm미만";
        else if(value >= 1.0f && value < 5.0f) return "1~4cm";
        else if(value >= 5.0f && value < 10.0f) return "5~9cm";
        else if(value >= 10.0f && value < 20.0f) return "10~19cm";
        else return "20cm이상";
    }

    private fun getWindDirectionString(value: Int): String {
        var data: Int = ((value + 22.5 * 0.5) / 22.5).toInt()
        when(data) {
            0 -> return "북풍"
            1 -> return "북북동"
            2 -> return "북동"
            3 -> return "동북동"
            4 -> return "동"
            5 -> return "동남동"
            6 -> return "남동"
            7 -> return "남남동"
            8 -> return "남"
            9 -> return "남남서"
            10 -> return "남서"
            11 -> return "서남서"
            12 -> return "서"
            13 -> return "서북서"
            14 -> return "북서"
            15 -> return "북북서"
            16 -> return "북"
            else -> return "알 수 없음"
        }
    }

    private fun getRainAmount(value: Double): String {
        if ( value < 0.1f ) return "없음"
        else if(value >= 0.1f && value < 1.0f) return "1mm미만";
        else if(value >= 1.0f && value < 5.0f) return "1~4mm";
        else if(value >= 5.0f && value < 10.0f) return "5~9mm";
        else if(value >= 10.0f && value < 20.0f) return "10~19mm";
        else if(value >= 20.0f && value < 40.0f) return "20~39mm";
        else if(value >= 40.0f && value < 70.0f) return "40~69mm";
        else return "70mm이상";
    }

    private fun getSkyType(type: Int): String {
        when( type ) {
            1 -> return "맑음"
            3 -> return "구름 많음"
            4 -> return "흐림"
            else -> return "알 수 없음"
        }
    }

    private fun getRainType(type: Int): String {
        when( type ) {
            0 -> return "없음"
            1 -> return "비"
            2 -> return "비/눈(진눈개비)"
            3 -> return "눈"
            4 -> return "소나기"
            5 -> return "빗방울"
            6 -> return "빗방울/눈날림"
            7 -> return "눈날림"
            else -> return "없음"
        }
    }

    /*
     * POP	강수확률	 %
     * PTY	강수형태	코드값 (없음(0), 비(1), 비/눈(진눈개비)(2), 눈(3), 소나기(4), 빗방울(5), 빗방울/눈날림(6), 눈날림(7))
     * R06	6시간 강수량	범주 (1 mm)
     * REH	습도	 %
     * S06	6시간 신적설	범주(1 cm)
     * SKY	하늘상태	코드값 (맑음(1), 구름많음(3), 흐림(4))
     * T3H	3시간 기온	 ℃
     * TMN	아침 최저기온	 ℃
     * TMX	낮 최고기온	 ℃
     * UUU	풍속(동서성분)	 m/s
     * VVV	풍속(남북성분)	 m/s
     * VEC  풍향      deg 10bit
     */
    private fun parseItems(items: ITEMS) {
        Log.d(onairTag, items.item.toString())
        Log.d(onairTag, "- 날짜: " + items.item[0].baseDate)
        Log.d(onairTag, "- 발표시간: " + items.item[0].baseTime)
        Log.d(onairTag, "- 예보시간: " + items.item[0].fcstTime)
        mDateText = "- 날짜: "+items.item[0].baseDate +"\n"
        mDateText += "- 발표시간: "+items.item[0].baseTime +"\n"
        mDateText += "- 예보시간: "+items.item[0].fcstTime
        timeTextView.setText(mDateText)
        mWeatherText = ""
        for (i in items.item.indices) {
            when( items.item[i].category ) {
                "POP" -> {
                    Log.d(onairTag, "- 강수확률(%): " + items.item[i].fcstValue)
                    mWeatherText += "- 강수확률(%): " + items.item[i].fcstValue + "\n"
                }
                "PTY" -> {
                    Log.d(onairTag, "- 강수형태(code): " + getRainType(items.item[i].fcstValue.toInt()))
                    mWeatherText += "- 강수형태(code): " + getRainType(items.item[i].fcstValue.toInt()) + "\n"
                }
                "R06" -> {
                    Log.d(
                        onairTag,
                        "- 6시간 강수량(mm): " + getRainAmount(items.item[i].fcstValue.toDouble())
                    )
                    mWeatherText += "- 6시간 강수량(mm): " + getRainAmount(items.item[i].fcstValue.toDouble()) + "\n"
                }
                "REH" -> {
                    Log.d(onairTag, "- 습도(%): " + items.item[i].fcstValue)
                    mWeatherText += "- 습도(%): " + items.item[i].fcstValue + "\n"
                }
                "S06" -> {
                    Log.d(
                        onairTag,
                        "- 6시간 신적설(mm): " + getSnowAmount(items.item[i].fcstValue.toDouble())
                    )
                    mWeatherText += "- 6시간 신적설(mm): " + getSnowAmount(items.item[i].fcstValue.toDouble()) + "\n"
                }
                "SKY" -> {
                    Log.d(onairTag, "- 하늘상태(code): " + getSkyType(items.item[i].fcstValue.toInt()))
                    mWeatherText += "- 하늘상태(code): " + getSkyType(items.item[i].fcstValue.toInt()) + "\n"
                }
                "T3H" -> {
                    Log.d(onairTag, "- 3시간 기온(℃): " + items.item[i].fcstValue)
                    mWeatherText += "- 3시간 기온(℃): " + items.item[i].fcstValue + "\n"
                }
                "TMN" -> {
                    Log.d(onairTag, "- 아침 최저 기온(℃): " + items.item[i].fcstValue)
                    mWeatherText += "- 아침 최저 기온(℃): " + items.item[i].fcstValue + "\n"
                }
                "TMX" -> {
                    Log.d(onairTag, "- 낮 최고 기온(℃): " + items.item[i].fcstValue)
                    mWeatherText += "- 낮 최고 기온(℃): " + items.item[i].fcstValue + "\n"
                }
                "UUU" -> {
                    Log.d(onairTag, "- 풍속(동서성분)(m/s): " + items.item[i].fcstValue)
                    mWeatherText += "- 풍속(동서성분)(m/s): " + items.item[i].fcstValue + "\n"
                }
                "VVV" -> {
                    Log.d(onairTag, "- 풍속(남북성분)(m/s): " + items.item[i].fcstValue)
                    mWeatherText += "- 풍속(남북성분)(m/s): " + items.item[i].fcstValue + "\n"
                }
                "VEC" -> {
                    Log.d(
                        onairTag,
                        "- 풍향: " + getWindDirectionString(items.item[i].fcstValue.toInt())
                    )
                    mWeatherText += "- 풍향: " + getWindDirectionString(items.item[i].fcstValue.toInt()) + "\n"
                }
                else -> Log.d(onairTag, "Invalid category: " + items.item[i].category)
            }
        }
        Log.d(onairTag, "")
        weatherTextView.setText(mWeatherText)
    }

    private fun updateAddressView() {
        Log.d(onairTag, "current_address : " + mAddressText)
        addrTextView.setText("- 현재 위치: " + mAddressText)
    }

    private fun requestAddressInfo() {
        var call = geoObj.retrofitService.getGeoInfo(mLatitude.toString() + "," + mLongitude.toString())
        Log.d(onairTag, "req URL: " + call.request().url().toString())
        var findit: Boolean = false
        call.enqueue(object : retrofit2.Callback<GEO_RESPONSE> {
            override fun onResponse(call: Call<GEO_RESPONSE>, response: Response<GEO_RESPONSE>) {
                if (response.isSuccessful && response.body() != null) {
                    Log.d(onairTag, "address req. response: " + response.body())
                    for (i in response.body()!!.results.indices) {
                        for (j in response.body()!!.results[i].types.indices) {
                            Log.d(
                                onairTag,
                                "search result[" + i + "].types[" + j + "]: " + response.body()!!.results[i].types[j]
                            )
                            if (response.body()!!.results[i].types[j].equals("postal_code")) {
                                Log.d(
                                    onairTag,
                                    "find it! : " + response.body()!!.results[i].formatted_address
                                )
                                mAddressText = response.body()!!.results[i].formatted_address
                                findit = true
                                break
                            }
                        }
                        if (findit) break;
                    }
                }

                if (findit) {
                    updateAddressView()
                }
            }

            override fun onFailure(call: Call<GEO_RESPONSE>, t: Throwable) {
                Log.d(onairTag, "requestAddressInfo fail : " + t.message)
            }

        })
    }

    private fun requestWeather() {

        // get gps and x, y location
        Log.d(onairTag, "Check GPS. Latitude: " + mLatitude + " , Longitude: " + mLongitude)
        val CurGPS = convertGRID_GPS(TO_GRID, Math.abs(mLatitude), Math.abs(mLongitude))
        Log.d(onairTag, "Current Location.  x: " + CurGPS.x + ", y: " + CurGPS.y)
        var nx = CurGPS.x.toInt().toString()
        var ny = CurGPS.y.toInt().toString()

        // time
        val current: LocalDateTime = LocalDateTime.now()
        val formatter1 = DateTimeFormatter.ofPattern("yyyyMMdd")
        val currdate1 = current.format(formatter1)
        val f: NumberFormat = DecimalFormat("00")

        var curtime = getLastBaseTime(Calendar.getInstance())
        var curtime2 = f.format(curtime.get(Calendar.HOUR_OF_DAY)) + f.format(curtime.get(Calendar.MINUTE))

        var base_date = currdate1
        var base_time = curtime2

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

        Log.d(onairTag, "URL: " + call.request().url().toString())

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
    private val locationPermissionCode = 204
    var REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private fun checkPermissions() {
        val finePerm = ContextCompat.checkSelfPermission(mContext, REQUIRED_PERMISSIONS[0])
        val coastPerm = ContextCompat.checkSelfPermission(mContext, REQUIRED_PERMISSIONS[1])

        if ( finePerm == PackageManager.PERMISSION_GRANTED
            && coastPerm == PackageManager.PERMISSION_GRANTED )
        {
            Log.d(onairTag, "Permissions ok")
            getGPSInfo()
        }
        else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(mContext as Activity, REQUIRED_PERMISSIONS[0])
                || ActivityCompat.shouldShowRequestPermissionRationale(mContext as Activity, REQUIRED_PERMISSIONS[1])) {

                makeToast("이 앱을 실행하려면 위치 권한이 필요합니다.")
                Log.d(onairTag, "Permissions are requested")
                ActivityCompat.requestPermissions( mContext as Activity, REQUIRED_PERMISSIONS, locationPermissionCode )
            } else {
                Log.d(onairTag, "Permissions are requested")
                ActivityCompat.requestPermissions( mContext as Activity, REQUIRED_PERMISSIONS, locationPermissionCode )
            }
        }
    }

    private fun getGPSInfo() {
        Log.d(onairTag, "getGPSInfo()")
        locationManager = getActivity()?.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ActivityCompat.checkSelfPermission(
                mContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                mContext,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(onairTag, "permission denied")
            makeToast("위치 권한을 허용해주세요")
            return
        }
        Log.d(onairTag, "getGPSInfo() 1")
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 5f, this)
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 5f, this)
        Log.d(onairTag, "getGPSInfo() 2")
    }

    override fun onLocationChanged(location: Location) {
        Log.d(onairTag,"Get GPS. Latitude: " + location.latitude + " , Longitude: " + location.longitude )
        mLatitude = location.latitude
        mLongitude = location.longitude

        // call address information
        requestAddressInfo()

        // call weather after get location
        requestWeather()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        Log.d(onairTag, "onRequestPermissionsResult: " + requestCode)
        if (requestCode == locationPermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(mContext, "Permission Granted", Toast.LENGTH_SHORT).show()
                getGPSInfo()
            }
            else {
                Toast.makeText(mContext, "Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        Log.d(onairTag, "onStatusChanged")
    }

    override fun onProviderEnabled(provider: String?) {
        Log.d(onairTag, "onProviderEnabled")
    }

    override fun onProviderDisabled(provider: String?) {
        Log.d(onairTag, "onProviderDisabled")
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

