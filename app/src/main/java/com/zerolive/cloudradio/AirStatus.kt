package com.zerolive.cloudradio

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/*
// 동 이름으로 tmx, tmy  좌표를 얻어서
http://apis.data.go.kr/B552584/MsrstnInfoInqireSvc/getTMStdrCrdnt?serviceKey=ZZSvyzoRPHWzl9Uj650WLGx37OJ%2FQA0VdvtKq4SD8K6au7LhEI4X1l2jx4J4iB05XOq9H%2BQGU%2FmvNTSkC22Fqg%3D%3D&returnType=json&numOfRows=100&pageNo=1&umdName=%ED%98%9C%ED%99%94%EB%8F%99

// tmx, tmy 좌표로 측정소 정보를 얻고
http://apis.data.go.kr/B552584/MsrstnInfoInqireSvc/getNearbyMsrstnList?serviceKey=ZZSvyzoRPHWzl9Uj650WLGx37OJ%2FQA0VdvtKq4SD8K6au7LhEI4X1l2jx4J4iB05XOq9H%2BQGU%2FmvNTSkC22Fqg%3D%3D&returnType=json&tmX=244148.546388&tmY=412423.75772&ver=1.0

// 측정소를 입력하여 미세먼지 얻기
http://apis.data.go.kr/B552584/ArpltnInforInqireSvc/getMsrstnAcctoRltmMesureDnsty?stationName=종로구&dataTerm=month&pageNo=1&numOfRows=1&returnType=json&serviceKey=ZZSvyzoRPHWzl9Uj650WLGx37OJ%2FQA0VdvtKq4SD8K6au7LhEI4X1l2jx4J4iB05XOq9H%2BQGU%2FmvNTSkC22Fqg%3D%3D
 */

var airTag = "CR_AirStatus"

// common use
data class AIR_HEADER(
    val resultCode: String,
    val resultMsg: String
)
private fun httpLoggingInterceptor(): HttpLoggingInterceptor? {
    val interceptor = HttpLoggingInterceptor(object : HttpLoggingInterceptor.Logger {
        override fun log(message: String) {
            CRLog.d(message + "")
        }
    })
    return interceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
}
val airclient = OkHttpClient.Builder()
//    .addInterceptor(FixEncodingInterceptor())
//    .addInterceptor(httpLoggingInterceptor())         // http request inspector debuggin
    .followRedirects(false)
    .followSslRedirects(false)
    .build()
private val air_retrofit = Retrofit.Builder()
    .baseUrl("http://apis.data.go.kr/B552584/") // 마지막 / 반드시 들어가야 함
    .addConverterFactory(GsonConverterFactory.create()) // converter 지정
    .client(airclient)
    .build() // retrofit 객체 생성



// for step 1 - tmx, tmy
data class GETTMXY(
    val response: AIR_TMXY_RESPONSE
)
data class AIR_TMXY_RESPONSE(
    val header: AIR_HEADER,
    val body: AIR_TMXY_BODY
)
data class AIR_TMXY_BODY(
    val totalCount: Int,
    val pageNo: Int,
    val numOfRows: Int,
    val items: List<AIR_TMXY_ITEM>
)
data class AIR_TMXY_ITEM(
    val sggName: String,
    val umdName: String,
    val tmX: String,
    val tmY: String,
    val sidoName: String
)
// &returnType=json&numOfRows=100&pageNo=1&umdName=%ED%98%9C%ED%99%94%EB%8F%99
interface AirTMXYInterface {
    @GET("MsrstnInfoInqireSvc/getTMStdrCrdnt?serviceKey=ZZSvyzoRPHWzl9Uj650WLGx37OJ%2FQA0VdvtKq4SD8K6au7LhEI4X1l2jx4J4iB05XOq9H%2BQGU%2FmvNTSkC22Fqg%3D%3D")
    fun GetTMCoordination(
        @Query("returnType") returnType: String,
        @Query("numOfRows") numOfRows: Int,
        @Query("pageNo") pageNo: Int,
        @Query("umdName") umdName: String,
    ): Call<GETTMXY>
}
object AirTMXYObject {
    val retrofitService: AirTMXYInterface by lazy {
        air_retrofit.create(AirTMXYInterface::class.java)
    }
}

// for step 2 - location information
data class GETVIEWLOCATION(
    val response: AIR_VIEWLOCATION_RESPONSE
)
data class AIR_VIEWLOCATION_RESPONSE(
    val header: AIR_HEADER,
    val body: AIR_VIEWLOCATION_BODY
)
data class AIR_VIEWLOCATION_BODY(
    val totalCount: Int,
    val pageNo: Int,
    val numOfRows: Int,
    val items: List<AIR_VIEWLOCATION_ITEM>
)
data class AIR_VIEWLOCATION_ITEM(
    val tm: Float,
    val addr: String,
    val stationName: String
)
// &returnType=json&tmX=244148.546388&tmY=412423.75772&ver=1.0
interface AirViewLocatoinInterface {
    @GET("MsrstnInfoInqireSvc/getNearbyMsrstnList?serviceKey=ZZSvyzoRPHWzl9Uj650WLGx37OJ%2FQA0VdvtKq4SD8K6au7LhEI4X1l2jx4J4iB05XOq9H%2BQGU%2FmvNTSkC22Fqg%3D%3D")
    fun GetViewLocation(
        @Query("returnType") returnType: String,
        @Query("tmX") tmX: String,
        @Query("tmY") tmY: String,
    ): Call<GETVIEWLOCATION>
}
object AirViewLocationObject {
    val retrofitService: AirViewLocatoinInterface by lazy {
        air_retrofit.create(AirViewLocatoinInterface::class.java)
    }
}

// for step 3 - get air pm value
data class GETPMDATA(
    val response: AIR_PMDATA_RESPONSE
)
data class AIR_PMDATA_RESPONSE(
    val header: AIR_HEADER,
    val body: AIR_PMDATA_BODY
)
data class AIR_PMDATA_BODY(
    val totalCount: Int,
    val pageNo: Int,
    val numOfRows: Int,
    val items: List<AIR_PMDATA_ITEM>
)
// - 적용 항목명 : khaiGrade, so2Grade, coGrade, o3Grade, no2Grade, pm10Grade, pm25Grade, pm25Grade1h, pm25Grade1h
// 등급	   좋음	보통	나쁨	매우나쁨
//Grade 값	1	2	3	4
data class AIR_PMDATA_ITEM(
    val pm25Grade1h: String,
    val pm10Grade1h: String,
    val so2Grade: String,
    val coFlag: String,
    val khaiValue: String,
    val so2Value: String,
    val coValue: String,
    val pm10Flag: String,
    val pm10Value: String,
    val o3Grade: String,
    val khaiGrade: String,
    val no2Flag: String,
    val no2Grade: String,
    val o3Flag: String,
    val so2Flag: String,
    val dataTime: String,
    val coGrade: String,
    val no2Value: String,
    val pm10Grade: String,
    val o3Value: String,
    val pm25Value24: String,
    val pm25Value: String
)
// stationName=종로구&dataTerm=month&pageNo=1&numOfRows=1&returnType=json&
interface AirPMDataInterface {
    @GET("ArpltnInforInqireSvc/getMsrstnAcctoRltmMesureDnsty?serviceKey=ZZSvyzoRPHWzl9Uj650WLGx37OJ%2FQA0VdvtKq4SD8K6au7LhEI4X1l2jx4J4iB05XOq9H%2BQGU%2FmvNTSkC22Fqg%3D%3D")
    fun GetPMData(
        @Query("returnType") returnType: String,
        @Query("dataTerm") dataTerm: String,
        @Query("pageNo") pageNo: String,
        @Query("numOfRows") numOfRows: String,
        @Query("stationName") stationName: String,
        @Query("ver") ver: String,
    ): Call<GETPMDATA>
}
object AirPMDataObject {
    val retrofitService: AirPMDataInterface by lazy {
        air_retrofit.create(AirPMDataInterface::class.java)
    }
}

// 등급	   좋음	보통	나쁨	매우나쁨
//Grade 값	1	2	3	4
data class PMData (
    val pm25Grade1h: String?,
    val pm25Value: String?,
    val pm10Grade1h: String?,
    val pm10Value: String?
)

object AirStatus {

    fun getGradeString(grade: String?): String {
        when(grade) {
            "1" -> return "좋음"
            "2" -> return "보통"
            "3" -> return "나쁨"
            "4" -> return "매우나쁨"
            else -> return "알 수 없음"
        }
    }

    private fun dumpAirStatus(data: PMData) {
        CRLog.d("==   AirStatus  ==")
        CRLog.d("1. 미세먼지(PM10): " + data.pm10Value + " (" + getGradeString(data?.pm10Grade1h) + ")")
        CRLog.d("1. 초미세먼지(PM2.5): " + data.pm25Value + " (" + getGradeString(data?.pm25Grade1h) + ")")

        data?.let { OnAir.updateAirStatus(it) }
        MainActivity.getInstance().makeToast("날씨 정보가 업데이트 되었습니다.")

        if (OnAir.bUpdateReady) {
            YoutubeLiveUpdater.update()
            YoutubePlaylistUpdater.update()
        }
    }

    fun requestTMCoordination(umdName: String) {
        CRLog.d("umdName:(" + umdName + ")")

        val call = AirTMXYObject.retrofitService.GetTMCoordination("json", 1, 1, umdName )
        CRLog.d("requestTMCoordination: " + call.request().url().toString())
        call.enqueue(object : retrofit2.Callback<GETTMXY> {
            override fun onResponse(call: Call<GETTMXY>, response: Response<GETTMXY>) {
                if ( response.isSuccessful ) {
                    CRLog.d("TMXY: " + response.body())
                    if ( response.body()!!.response.body.items.size > 0) {
                        var tmx: String = response.body()!!.response.body.items[0].tmX
                        var tmy: String = response.body()!!.response.body.items[0].tmY
                        requestViewLocation(tmx, tmy)
                    } else {
                        CRLog.d("There is no TM data")
                        MainActivity.getInstance().makeToast("미세먼지 데이터를 찾을 수 없습니다.")
                        var data = PMData("알 수 없음", "-", "알 수 없음", "-")
                        dumpAirStatus(data)
                    }
                }
            }

            override fun onFailure(call: Call<GETTMXY>, t: Throwable) {
                CRLog.d("failed to get TMXY: " + t.message)
            }
        })
    }

    fun requestViewLocation(tmx: String, tmy: String) {
        CRLog.d("requestViewLocation(tmx:" + tmx + ", tmy:" + tmy + ")")
        val call = AirViewLocationObject.retrofitService.GetViewLocation("json", tmx, tmy)//, 1.0 )
        call.enqueue(object : retrofit2.Callback<GETVIEWLOCATION> {
            override fun onResponse(call: Call<GETVIEWLOCATION>, response: Response<GETVIEWLOCATION>) {
                if ( response.isSuccessful ) {
                    CRLog.d("requestViewLocation: " + response.body())
                    var leastIndex: Float = 0.0f
                    var stationName: String = "N/A"
                    for(i in response.body()!!.response.body.items.indices) {
                        if ( i == 0) {
                            leastIndex = response.body()!!.response.body.items[i].tm.toFloat()
                            stationName = response.body()!!.response.body.items[i].stationName
                        } else {
                            if ( leastIndex > response.body()!!.response.body.items[i].tm.toFloat() ) {
                                leastIndex = response.body()!!.response.body.items[i].tm.toFloat()
                                stationName = response.body()!!.response.body.items[i].stationName
                            }
                        }
                    }
                    CRLog.d("least stationName: " + stationName)
                    getAirPMData( stationName )
                }
            }

            override fun onFailure(call: Call<GETVIEWLOCATION>, t: Throwable) {
                CRLog.d("failed to requestViewLocation: " + t.message)
            }
        })
    }

    fun getAirPMData(stationName: String) {
        CRLog.d("getAirPMData(" + stationName + ")")

        val call = AirPMDataObject.retrofitService.GetPMData("json","DAILY", "1", "1", stationName , "1.3")
        call.enqueue(object : retrofit2.Callback<GETPMDATA> {
            override fun onResponse(call: Call<GETPMDATA>, response: Response<GETPMDATA>) {
                if ( response.isSuccessful ) {
                    CRLog.d("getAirPMData: " + response.body())
                    var pm10Value: String = response.body()!!.response.body.items[0].pm10Value
                    var pm10Grade1h: String = response.body()!!.response.body.items[0].pm10Grade1h
                    var pm25Value: String = response.body()!!.response.body.items[0].pm25Value
                    var pm25Grade1h: String = response.body()!!.response.body.items[0].pm25Grade1h
                    CRLog.d("pm10Value: ${pm10Value} pm10Grade1h: ${pm10Grade1h} pm25Value: ${pm25Value} pm25Grade1h: ${pm25Grade1h}")
                    if ( pm25Value.equals("-") ) {
                        pm25Value = "-1"
                        pm25Grade1h = "알 수 없음"
                    }
                    if ( pm10Value.equals("-") ) {
                        pm10Value = "-1"
                        pm10Grade1h = "알 수 없음"
                    }
                    val data = PMData(pm25Grade1h, pm25Value, pm10Grade1h, pm10Value)
                    dumpAirStatus(data)
                }
            }

            override fun onFailure(call: Call<GETPMDATA>, t: Throwable) {
                CRLog.d("failed to getAirPMData: " + t.message)
            }
        })
    }
}