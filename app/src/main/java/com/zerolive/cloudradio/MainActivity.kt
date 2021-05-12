package com.zerolive.cloudradio

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.SystemClock.sleep
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import java.io.File


var mainTag = "CR_Main"



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
        @SuppressLint("StaticFieldLeak")
        private var instance: MainActivity? = null
        var locationManager: LocationManager? = null
        @SuppressLint("StaticFieldLeak")
        var mContext: Context? = null
        var youtubeView: YouTubePlayerView? = null

        var bluetoothHeadset: BluetoothHeadset? = null

        // Get the default adapter
        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

        fun getInstance(): MainActivity =
                instance ?: synchronized(this) {
                    instance ?: MainActivity().also {
                        instance = it
                    }
                }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CRLog.d("onCreate")

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
        // viewPager 는 미리 다음 화면을 생성한다.
        // 이전 화면은 지우게 되는데, 몇 개를 만들어둘지를 아래 옵션으로 설정할 수 있음
        // 3개를 미리 만들어두면 온에어/프로그램/모어 모두 한 번에 만들어두고 계속 쓰게 됨
        viewPager.offscreenPageLimit = 3
        viewPager.adapter = adapter

        viewPager.addOnPageChangeListener(TabLayout.TabLayoutOnPageChangeListener(tabLayout))
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                CRLog.d("onTabSelected: ${tab.position}")
                viewPager.currentItem = tab.position

                if ( More.bInitialized ) {
                    // keyboard auto hiding
                    val imm = More.mContext?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(More.txt_ytb_url.windowToken, 0)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                //CRLog.d( "onTabUnselected: ${tab.position}")
            }

            override fun onTabReselected(tab: TabLayout.Tab) {
                //CRLog.d( "onTabReselected: ${tab.position}")
            }
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if ( checkNetworkStatus() ) {
                init()
            } else {
                makeNoticePopup()
            }
        } else {
            CRLog.d("skip checking network status. reason: version")
            init()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        CRLog.d("MainAcitivity onDestroyed")
        finishAffinity()

        Intent(OnAir.mContext, RadioService::class.java).run {
            var intent = Intent(OnAir.mContext, RadioService::class.java)
            intent.setAction(Constants.ACTION.STOPFOREGROUND_ACTION)
            stopService(intent)
        }

        youtubeView?.release()

        unregisterReceiver(HeadSetConnectReceiver)
    }

    fun systemRestart() {
        sleep(5000)
        finishAffinity()
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        System.exit(0)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun checkNetworkStatus(): Boolean {
        if ( NetworkStatus.getConnectivityStatus(applicationContext) == NetworkStatus.TYPE_NOT_CONNECTED) {
            CRLog.d("Network is not available")
            return false
        }
        return true
    }

    private fun makeNoticePopup() {
        val dlg = NoticePopupActivity(this)
        dlg.setOnOKClickedListener{ content ->
            CRLog.d("received message: " + content)
            CRLog.d("received on OK Click event")
            systemRestart()
        }
        dlg.start("인터넷 연결 확인 필요\n확인을 누르면 5초 후 앱을 다시 시작합니다.")
    }

    private fun init() {
        CRLog.d("MainActivity init")

        // 초기화
        RadioChannelResources.clearResources()
        Program.resetAction()
        OnAir.resetAll()

        // 리소스 로딩
        RadioChannelResources.initResources(this)

        locationManager = this.getSystemService(LOCATION_SERVICE) as LocationManager

        checkPermissions()

        RadioPlayer.init()

        // youtube
        youtubeView = YouTubePlayerView(this)
        CRLog.d("youtubeView: $youtubeView")
        // ui
        var uiController = youtubeView?.getPlayerUiController()
        uiController?.showCurrentTime(false)
        uiController?.showFullscreenButton(false)
        uiController?.showPlayPauseButton(false)
        uiController?.showSeekBar(false)
        uiController?.showSeekBar(false)
        uiController?.showVideoTitle(false)
        uiController?.showDuration(false)
        uiController?.showUi(false)
        uiController?.showYouTubeButton(false)
        youtubeView?.addYouTubePlayerListener(YoutubeHandler)

        // bluetooth
        val filter1 = IntentFilter()
        filter1.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter1.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(HeadSetConnectReceiver, filter1)

        val filter2 = IntentFilter(Intent.ACTION_HEADSET_PLUG)
        registerReceiver(HeadSetConnectReceiver, filter2)

    }

    private val locationPermissionCode = 204
    var REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )

    fun checkPermissions() {
        CRLog.d("checkPermissions")

        val finePerm = ContextCompat.checkSelfPermission(this, REQUIRED_PERMISSIONS[0])
        val coastPerm = ContextCompat.checkSelfPermission(this, REQUIRED_PERMISSIONS[1])

        if ( finePerm == PackageManager.PERMISSION_GRANTED
                && coastPerm == PackageManager.PERMISSION_GRANTED )
        {
            CRLog.d("Permissions ok")
            getGPSInfo()
        }
        else {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, REQUIRED_PERMISSIONS[0])
                    || ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    REQUIRED_PERMISSIONS[1]
                )) {

                CRLog.d("Permissions are requested 1")
                ActivityCompat.requestPermissions(
                    this,
                    REQUIRED_PERMISSIONS,
                    locationPermissionCode
                )
            } else {
                CRLog.d("Permissions are requested 2")
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
        CRLog.d("onRequestPermissionsResult: " + requestCode)
        if (requestCode == locationPermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "권한 승인 완료", Toast.LENGTH_LONG).show()
                CRLog.d("Permissions CB: Granted")
                getGPSInfo()
            }
            else {
                CRLog.d("Permissions CB: Denied")
                Toast.makeText(this, "앱을 정상적으로 실행하기 위해\n앱 설정에서 권한을 설정해 주세요.", Toast.LENGTH_LONG).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun getGPSInfo() {
        CRLog.d("getGPSInfo()")

        locationManager?.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            10000,
            10f,
            CRLocationListener
        )
        locationManager?.requestLocationUpdates(
            LocationManager.NETWORK_PROVIDER,
            10000,
            10f,
            CRLocationListener
        )
        CRLog.d("getGPSInfo end")
    }

    fun removeGPSTracking() {
        CRLog.d("removeGPSTracking()")
        locationManager?.removeUpdates(CRLocationListener)
    }

    @SuppressLint("RestrictedApi")
    fun installApp(path: String) {
        CRLog.d("install: $path")
        val toInstall = File(path)
        CRLog.d("install2: $toInstall")
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
        CRLog.d("this: $this")
        CRLog.d("context: $mContext")
        mContext!!.startActivity(intent)
    }

    fun makeToast(message: String) {
        Toast.makeText(mContext, message, Toast.LENGTH_LONG).show()
    }
}