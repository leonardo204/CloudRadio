package com.example.cloudradio

import android.util.Log
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.IOException

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
    val interceptor = HttpLoggingInterceptor(object : HttpLoggingInterceptor.Logger {
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

class GeoInfomation {

    companion object {
        private var instance: GeoInfomation? = null

        fun getInstance(): GeoInfomation =
            instance ?: synchronized(this) {
                instance ?: GeoInfomation().also {
                    instance = it
                }
            }
    }

    fun requestAddressInfo(lat: Double, lng: Double) {
        var call = geoObj.retrofitService.getGeoInfo(lat.toString() + "," + lng.toString())
        Log.d(onairTag, "req URL: " + call.request().url().toString())
        var findit: Boolean = false
        call.enqueue(object : retrofit2.Callback<GEO_RESPONSE> {
            override fun onResponse(call: Call<GEO_RESPONSE>, response: Response<GEO_RESPONSE>) {
                var umdName: String = "N/A"
                if (response.isSuccessful && response.body() != null) {
                    Log.d(onairTag, "address req. response: " + response.body())
                    for (i in response.body()!!.results.indices) {
                        for (j in response.body()!!.results[i].types.indices) {
                            Log.d( onairTag,"search result[" + i + "].types[" + j + "]: " + response.body()!!.results[i].types[j])
                            if (response.body()!!.results[i].types[j].equals("postal_code")) {
                                Log.d(onairTag,"find it! : " + response.body()!!.results[i].formatted_address  )
                                OnAir.mAddressText = response.body()!!.results[i].formatted_address
                                findit = true

                                // 미세먼지를 위한 지역구 이름 획득
                                var findumdName:Boolean = false
                                for(k in response.body()!!.results[i].address_components.indices) {
                                    for (p in response.body()!!.results[i].address_components[k].types.indices ) {
                                        if ( response.body()!!.results[i].address_components[k].types[p].equals("sublocality_level_1") ) {
                                            findumdName = true
                                            umdName = response.body()!!.results[i].address_components[k].long_name
                                            Log.d(onairTag,"find umdName! : " + umdName  )
                                        }
                                        if (findumdName) break
                                    }
                                    if (findumdName) break
                                }
                               if (findit) break
                            }
                        }
                        if (findit) break;
                    }
                }

                if (findit) {
                    OnAir.getInstance().updateAddressView(true)

                    // air information
                    AirStatus.getInstance().requestTMCoordination(umdName)
                }
            }

            override fun onFailure(call: Call<GEO_RESPONSE>, t: Throwable) {
                Log.d(onairTag, "requestAddressInfo fail : " + t.message)
                OnAir.getInstance().updateAddressView(false)
            }

        })
    }

}

