package com.example.cloudradio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.SystemClock.sleep
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.viewpager.widget.ViewPager
import com.google.android.material.internal.ContextUtils.getActivity
import com.google.android.material.tabs.TabLayout
import java.io.File

var mainTag = "CR_Main"

class LocListener: LocationListener {

    //위치 정보 전달 목적으로 호출
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onLocationChanged(location: Location?) {

        if (location != null) {
            Log.d(
                mainTag,
                "Get GPS. Latitude: " + location.latitude + " , Longitude: " + location.longitude
            )

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
        Log.d(mainTag, "onStatusChanged. Not yet implemented")
    }

    //provider가 사용 가능한 상태가 되는 순간 호출
    override fun onProviderEnabled(provider: String?) {
        Log.d(mainTag, "onProviderEnabled. Not yet implemented")
    }

    //provider가 사용 불가능 상황이 되는 순간 호출
    override fun onProviderDisabled(provider: String?) {
        Log.d(mainTag, "onProviderDisabled. Not yet implemented")
    }
}

class GeoInfoTask: AsyncTask<Location, Void, Void>() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun doInBackground(vararg params: Location?): Void? {
        // call address information
        GeoInfomation.requestAddressInfo(params[0]!!.latitude, params[0]!!.longitude)
        return null
    }
}

class WeatherTask: AsyncTask<Location, Void, Void>() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun doInBackground(vararg params: Location?): Void? {
        // call weather after get location
        WeatherStatus.requestWeather(params[0]!!.latitude, params[0]!!.longitude)
        return null
    }
}

class MainActivity : AppCompatActivity() {

    lateinit var tabLayout: TabLayout
    lateinit var viewPager: ViewPager

    companion object {
        private var instance: MainActivity? = null
        var locationManager: LocationManager? = null
        var locListener: LocListener? = null
        var mContext: Context? = null

        fun getInstance(): MainActivity =
                instance ?: synchronized(this) {
                    instance ?: MainActivity().also {
                        instance = it
                    }
                }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mContext = this

        setContentView(R.layout.activity_main)
        title = "CloudRadio"

        tabLayout = findViewById(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)

        tabLayout.addTab(tabLayout.newTab().setText("OnAir"))
        tabLayout.addTab(tabLayout.newTab().setText("Program"))
        tabLayout.addTab(tabLayout.newTab().setText("More"))

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if ( checkNetworkStatus() ) {
                init()
            } else {
                makeNoticePopup()
            }
        } else {
            Log.d(mainTag, "skip checking network status. reason: version")
            init()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(mainTag, "MainAcitivity onDestroyed")
        finishAffinity()

        Intent(OnAir.mContext, RadioService::class.java).run {
            var intent = Intent(OnAir.mContext, RadioService::class.java)
            intent.setAction(Constants.ACTION.STOPFOREGROUND_ACTION)
            stopService(intent)
        }
    }

    fun systemRestart() {
        sleep(5000)
        finishAffinity()
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        System.exit(0)
    }

    // network 연결이 되어 있는 경우 여기서 init 을 불러준다.
    @RequiresApi(Build.VERSION_CODES.M)
    fun checkNetworkStatus(): Boolean {
        if ( NetworkStatus.getConnectivityStatus(applicationContext) == NetworkStatus.TYPE_NOT_CONNECTED ) {
            Log.d(mainTag, "Network is not available")
            return false
        }
        return true
    }

    private fun makeNoticePopup() {
        val dlg = NoticePopupActivity(this)
        dlg.setOnOKClickedListener{ content ->
            Log.d(mainTag, "received message: " + content)
            Log.d(mainTag, "received on OK Click event")
            systemRestart()
        }
        dlg.start("인터넷 연결 확인 필요\n확인을 누르면 5초 후 앱을 다시 시작합니다.")
    }

    private fun init() {
        Log.d(mainTag, "MainActivity init")
        RadioChannelResources.initResources(this)

        locationManager = this.getSystemService(LOCATION_SERVICE) as LocationManager

        checkPermissions()

        RadioPlayer.init()
    }

    private val locationPermissionCode = 204
    var REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    fun checkPermissions() {
        Log.d(mainTag, "checkPermissions")

        val finePerm = ContextCompat.checkSelfPermission(this, REQUIRED_PERMISSIONS[0])
        val coastPerm = ContextCompat.checkSelfPermission(this, REQUIRED_PERMISSIONS[1])

        if ( finePerm == PackageManager.PERMISSION_GRANTED
                && coastPerm == PackageManager.PERMISSION_GRANTED )
        {
            Log.d(mainTag, "Permissions ok")
            getGPSInfo()
        }
        else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, REQUIRED_PERMISSIONS[0])
                    || ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    REQUIRED_PERMISSIONS[1]
                )) {

                Log.d(mainTag, "Permissions are requested 1")
                ActivityCompat.requestPermissions(
                    this,
                    REQUIRED_PERMISSIONS,
                    locationPermissionCode
                )
            } else {
                Log.d(mainTag, "Permissions are requested 2")
                ActivityCompat.requestPermissions(
                    this,
                    REQUIRED_PERMISSIONS,
                    locationPermissionCode
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        Log.d(mainTag, "onRequestPermissionsResult: " + requestCode)
        if (requestCode == locationPermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission Granted", Toast.LENGTH_LONG).show()
                Log.d(mainTag, "Permissions CB: Granted")
                getGPSInfo()
            }
            else {
                Log.d(mainTag, "Permissions CB: Denied")
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_LONG).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun getGPSInfo() {
        Log.d(mainTag, "getGPSInfo()")

        locListener = LocListener()

        locationManager?.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            10000,
            10f,
            locListener
        )
        locationManager?.requestLocationUpdates(
            LocationManager.NETWORK_PROVIDER,
            10000,
            10f,
            locListener
        )
        Log.d(mainTag, "getGPSInfo end")
    }

    fun removeGPSTracking() {
        Log.d(mainTag, "removeGPSTracking()")
        locListener?.let { locationManager?.removeUpdates(it) }
        locListener = null
    }

    @SuppressLint("RestrictedApi")
    fun installApp(path: String) {
        Log.d(mainTag, "install: $path")
        val toInstall = File(path)
        Log.d(mainTag, "install2: $toInstall")
        val intent: Intent
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//            val apkUri = FileProvider.getUriForFile(
//                this,
//                BuildConfig.APPLICATION_ID,// + ".fileprovider",
//                toInstall
//            )
//            intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
//            intent.data = apkUri
//            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
//        }
//        else
//        {
//            val apkUri = Uri.fromFile(toInstall)
//            intent = Intent(Intent.ACTION_VIEW)
//            intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
//            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
//        }


            val apkUri = FileProvider.getUriForFile(
                mContext!!,
                BuildConfig.APPLICATION_ID,// + ".fileprovider",
                toInstall
            )
            intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
            intent.data = apkUri
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        Log.d(mainTag, "this: $this")
        Log.d(mainTag, "context: $mContext")
        mContext!!.startActivity(intent)
    }

    fun makeToast(message: String) {
        Toast.makeText(mContext, message, Toast.LENGTH_LONG)
    }
}