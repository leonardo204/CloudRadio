package com.example.cloudradio

import android.location.Location
import android.location.LocationListener
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi

object CRLocationListener: LocationListener {

    //위치 정보 전달 목적으로 호출
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onLocationChanged(location: Location?) {

        if (location != null) {
            CRLog.d( "Get GPS. Latitude: " + location.latitude + " , Longitude: " + location.longitude  )

            // call address information
            val geoInfoTask = GeoInfoTask()
            geoInfoTask.execute(location)

            // call weather after get location
            val weatherTask = WeatherTask()
            weatherTask.execute(location)
        }

        // remove gps tracking
        MainActivity.getInstance().removeGPSTracking()
    }

    //provider의 상태가 변경되때마다 호출
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        CRLog.d( "onStatusChanged. Not yet implemented")
    }

    //provider가 사용 가능한 상태가 되는 순간 호출
    override fun onProviderEnabled(provider: String?) {
        CRLog.d( "onProviderEnabled. Not yet implemented")
    }

    //provider가 사용 불가능 상황이 되는 순간 호출
    override fun onProviderDisabled(provider: String?) {
        CRLog.d( "onProviderDisabled. Not yet implemented")
    }
}