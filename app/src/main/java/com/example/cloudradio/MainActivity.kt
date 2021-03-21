package com.example.cloudradio

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout

var MainTag = "MainActivity"

class MainActivity : AppCompatActivity(), LocationListener {

    lateinit var tabLayout: TabLayout
    lateinit var viewPager: ViewPager

    lateinit var locationManager: LocationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        title = "CloudRadio"

        tabLayout = findViewById(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)

        tabLayout.addTab( tabLayout.newTab().setText("OnAir"))
        tabLayout.addTab( tabLayout.newTab().setText("Program"))
        tabLayout.addTab( tabLayout.newTab().setText("More"))

        tabLayout.tabGravity = TabLayout.GRAVITY_FILL

        val adapter = MyAdapter(this, supportFragmentManager, tabLayout.tabCount)
        viewPager.adapter = adapter

        viewPager.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(tabLayout))
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewPager.currentItem = tab.position
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        Log.d(onairTag, "set locationManager")
        locationManager = this.getSystemService(LOCATION_SERVICE) as LocationManager

        checkPermissions()
    }

    private val locationPermissionCode = 204
    var REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    )

    fun checkPermissions() {
        Log.d(onairTag, "checkPermissions")

        val finePerm = ContextCompat.checkSelfPermission(this, REQUIRED_PERMISSIONS[0])
        val coastPerm = ContextCompat.checkSelfPermission(this, REQUIRED_PERMISSIONS[1])

        if ( finePerm == PackageManager.PERMISSION_GRANTED
                && coastPerm == PackageManager.PERMISSION_GRANTED )
        {
            Log.d(onairTag, "Permissions ok")
            getGPSInfo()
        }
        else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, REQUIRED_PERMISSIONS[0])
                    || ActivityCompat.shouldShowRequestPermissionRationale(this, REQUIRED_PERMISSIONS[1])) {

                //makeToast("이 앱을 실행하려면 위치 권한이 필요합니다.")
                Log.d(onairTag, "Permissions are requested 1")
                ActivityCompat.requestPermissions( this, REQUIRED_PERMISSIONS, locationPermissionCode )
            } else {
                //makeToast("이 앱을 실행하려면 위치 권한이 필요합니다.")
                Log.d(onairTag, "Permissions are requested 2")
                ActivityCompat.requestPermissions( this, REQUIRED_PERMISSIONS, locationPermissionCode )
            }
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    ) {
        Log.d(onairTag, "onRequestPermissionsResult: " + requestCode)
        if (requestCode == locationPermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show()
                Log.d(onairTag, "Permissions CB: Granted")
                getGPSInfo()
            }
            else {
                Log.d(onairTag, "Permissions CB: Denied")
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun getGPSInfo() {
        Log.d(onairTag, "getGPSInfo() 1")
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 5f, this)
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 5000, 5f, this)
        Log.d(onairTag, "getGPSInfo() 2")
    }

    override fun onLocationChanged(location: Location) {
        Log.d(onairTag,"Get GPS. Latitude: " + location.latitude + " , Longitude: " + location.longitude )

        // call address information
        OnAir.getInstance().requestAddressInfo(location.latitude, location.longitude)

        // call weather after get location
        OnAir.getInstance().requestWeather(location.latitude, location.longitude)
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        Log.d(onairTag,"onStatusChanged. Not yet implemented")
    }

    override fun onProviderEnabled(provider: String?) {
        Log.d(onairTag,"onProviderEnabled. Not yet implemented")
    }

    override fun onProviderDisabled(provider: String?) {
        Log.d(onairTag,"onProviderDisabled. Not yet implemented")
    }
}