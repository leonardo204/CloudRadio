package com.zerolive.cloudradio

import android.Manifest
import android.R.attr.phoneNumber
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Configuration.ORIENTATION_LANDSCAPE
import android.graphics.drawable.Drawable
import android.location.Location
import android.location.LocationManager
import android.os.*
import android.os.SystemClock.sleep
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.Surface
import android.view.View
import android.view.View.OnClickListener
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.viewpager.widget.ViewPager
import com.google.android.material.tabs.TabLayout
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerFullScreenListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.ui.PlayerUiController
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.NonCancellable.cancel
import java.util.*
import kotlin.concurrent.timer


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

data class LastYtbPlsStatus(
    val filename: String,
    val videoId: String,
    val state: PlayerConstants.PlayerState,
)

class MainActivity : AppCompatActivity() {

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var tabLayout: TabLayout
        lateinit var viewPager: ViewPager

        @SuppressLint("StaticFieldLeak")
        private var instance: MainActivity? = null
        var locationManager: LocationManager? = null
        @SuppressLint("StaticFieldLeak")
        var mContext: Context? = null
        var mWindow: Window? = null
        var youtubeView: YouTubePlayerView? = null
        var uiController: PlayerUiController? = null

        var ic_forward: Drawable? = null
        var ic_rewind: Drawable? = null

        // Get the default adapter
        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

        var mMediaSession : MediaSessionCompat? = null
        var mLastYtbPlsStatus: LastYtbPlsStatus? = null

        var bInitialized = false

        fun getInstance(): MainActivity =
                instance ?: synchronized(this) {
                    instance ?: MainActivity().also {
                        instance = it
                    }
                }

        var customProgressDialog: ProgressDialog? = null

        var packageInfo : PackageInfo? = null
        var mainVersionString : String? = null
        var bSkipWaitingGPS = false
        var mGPStimer: Timer? = null
        var mSystemRestartTimer: Timer? = null

    }



    @InternalCoroutinesApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        mainVersionString = getString(R.string.app_name) + " v" + packageInfo?.versionName

        CRLog.d("onCreate [ ${mainVersionString} ] init: ${bInitialized}")
        CRLog.d("ReleaseType: ${ReleaseType.TYPE.value}")

        // 앱 자체는 세로모드로 고정시킨다
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        mContext = this
        mWindow = window

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

                if (More.bInitialized) {
                    // keyboard auto hiding
                    val imm =
                        More.mContext?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
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

        bInitialized = true
    }

    fun removeLoading() {
        CRLog.d("removeLoading")
        customProgressDialog?.let {
            if ( it.isShowing() ) {
                CRLog.d("removeLoading - ok")
                it.hide()
            }
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

        mMediaSession?.isActive = true
    }

    override fun onBackPressed() {
        CRLog.d("MainAcitivity onBackPressed")
        moveTaskToBack(true)
//        super.onBackPressed()
    }

    fun moveToFront() {
        val am = mContext?.getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val tasks = am.getRunningTasks(Int.MAX_VALUE)
        if (!tasks.isEmpty()) {
            val tasksSize = tasks.size
            for (i in 0 until tasksSize) {
                val taskinfo = tasks[i]
                CRLog.d("task: ${taskinfo.topActivity?.packageName}")
                if (taskinfo.topActivity?.packageName == mContext?.applicationContext?.packageName) {
                    am.moveTaskToFront(taskinfo.id, 0)
                    break
                }
            }
        }
    }

    fun systemRestart() {
        mSystemRestartTimer?.cancel()
        finishAffinity()
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        System.exit(0)
    }

    fun systmeDestroy() {
        CRLog.d("System Destroy called")
        sleep(100)
        finishAffinity()
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
        dlg.setOnOKClickedListener { content ->
            CRLog.d("received message: " + content)
            var count = 0
            dlg.setBtnEnable(false)

            val systemRestartTimerTask = object : TimerTask() {
                override fun run() {
                    val hh = Handler(Looper.getMainLooper())
                    hh.postDelayed(Runnable {
                        dlg.setMessage("\n ${5 - count} 초 후 앱을 다시 시작합니다.")

                        if (count == 5) {
                            systemRestart()
                        }
                        count++
                    }, 0)
                }
            }
            mSystemRestartTimer = Timer()
            mSystemRestartTimer?.schedule(systemRestartTimerTask, 0, 1000)
        }

        dlg.init()
        dlg.setMessage("인터넷 연결 확인 필요\n\n확인을 누르면 5초 후 \n앱을 다시 시작합니다.")
        dlg.show()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        CRLog.d("onConfigurationChanged: ${newConfig}")

        if ( newConfig.orientation == ORIENTATION_LANDSCAPE ) {
            CRLog.d("landscape")
            val display = this.windowManager.defaultDisplay
            when( display.rotation ) {
                Surface.ROTATION_0 -> {
                    CRLog.d("rotaion 0")
                }
                Surface.ROTATION_90 -> {
                    CRLog.d("rotaion 90")
                }
                Surface.ROTATION_180 -> {
                    CRLog.d("rotaion 180")
                }
                Surface.ROTATION_270 -> {
                    CRLog.d("rotation 270")
                }
            }
        } else {
            CRLog.d("portrait")
        }
    }

    fun setFullScreen() {
        CRLog.d("window: ${mWindow}")
        mWindow?.decorView?.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                )
        if ( !FullScreenHelper.mFullScreen ) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
            youtubeView?.let {
                CRLog.d("enterFullScreen")
                it.enterFullScreen()
                tabLayout.visibility = View.GONE
                FullScreenHelper.mFullScreen = true
            }
        } else {
            CRLog.d("Already full screen")
        }
    }

    fun exitFullScreen() {
        if ( FullScreenHelper.mFullScreen ) {
            mWindow?.decorView?.systemUiVisibility = (View.SYSTEM_UI_FLAG_VISIBLE)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            youtubeView?.let {
                CRLog.d("exitFullScreen")
                it.exitFullScreen()
                tabLayout.visibility = View.VISIBLE
                FullScreenHelper.mFullScreen = false
            }
        } else {
            CRLog.d("Already protrait screen")
        }
    }

    private fun init() {
        CRLog.d("MainActivity init")

        // progress dialog
        customProgressDialog = ProgressDialog(this)
        customProgressDialog?.let {
            it.init()
            it.show()
        }

        if ( bInitialized && OnAir.mCurrentPlayFilename != null
            && OnAir.mVideoId != null ) {
            mLastYtbPlsStatus = LastYtbPlsStatus(
                OnAir.mCurrentPlayFilename!!,
                OnAir.mVideoId!!,
                OnAir.mYoutubeState!!
            )
        }

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
        ic_forward = ResourcesCompat.getDrawable(resources, R.drawable.forward, null)
        ic_rewind = ResourcesCompat.getDrawable(resources, R.drawable.rewind, null)

        youtubeView = YouTubePlayerView(this)
        CRLog.d("youtubeView: $youtubeView")
        // ui
        youtubeView?.let {
            it.addYouTubePlayerListener(YoutubeHandler)
            it.addFullScreenListener(object : YouTubePlayerFullScreenListener {
                override fun onYouTubePlayerEnterFullScreen() {
                    CRLog.d("onYouTubePlayerEnterFullScreen")
                    setFullScreen()
                }

                override fun onYouTubePlayerExitFullScreen() {
                    CRLog.d("onYouTubePlayerExitFullScreen")
                    exitFullScreen()
                }
            })
            uiController = it.getPlayerUiController()
        }
        uiController?.let {
            it.showCurrentTime(false)
            it.showFullscreenButton(true)
            it.showPlayPauseButton(true)
            it.showSeekBar(true)
            it.showVideoTitle(true)
            it.showDuration(false)
            it.showUi(true)
            it.showYouTubeButton(false)
            it.setCustomAction1(ic_rewind!!, onYoutubeRewFFClick("rewind"))
            it.setCustomAction2(ic_forward!!, onYoutubeRewFFClick("forward"))
            it.showCustomAction1(true)
            it.showCustomAction2(true)
        }

        // bluetooth
        val filter1 = IntentFilter()
        filter1.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        filter1.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        registerReceiver(HeadSetConnectReceiver, filter1)

        val filter2 = IntentFilter(Intent.ACTION_HEADSET_PLUG)
        registerReceiver(HeadSetConnectReceiver, filter2)

        mMediaSession = MediaSessionCompat(this, "cloudradio").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            val state = PlaybackStateCompat.Builder()
                .setActions(getFullActions())
                .build()
            setPlaybackState(state)
            setCallback(MediaSessoinCallback)
            val activityIntent = Intent(mContext, RadioService::class.java)
            setSessionActivity(PendingIntent.getActivity(mContext, 0, activityIntent, 0))
            isActive = true
        }
    }

    fun getFullActions() : Long {
        var actions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SEEK_TO
        return actions
    }

    fun getRadioActions() : Long {
        var actions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE
        return actions
    }

    private fun onYoutubeRewFFClick(command: String): OnClickListener {
        return OnClickListener {
            if ( command.equals("rewind") ) {
                CRLog.d("rewind!")
                OnAir.requestPlayPrevious()
            } else if ( command.equals("forward") ) {
                CRLog.d("forward!")
                OnAir.requestPlayNext()
            } else {
                CRLog.d("unknown command: ${command}")
            }
        }
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

        val finePerm = mContext?.let{ ContextCompat.checkSelfPermission(it, REQUIRED_PERMISSIONS[0]) }
        val coastPerm = mContext?.let{ ContextCompat.checkSelfPermission(
            it,
            REQUIRED_PERMISSIONS[1]
        ) }

        if ( finePerm == PackageManager.PERMISSION_GRANTED
                && coastPerm == PackageManager.PERMISSION_GRANTED )
        {
            CRLog.d("Permissions ok")
            getGPSInfo()
        }
        else {
            val activity = mContext?.let { it as Activity }
            if (activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(
                    it,
                    REQUIRED_PERMISSIONS[0]
                ) } == true
                    || activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(
                    it,
                    REQUIRED_PERMISSIONS[1]
                ) } == true) {

                CRLog.d("Permissions are requested 1")
                activity?.let { ActivityCompat.requestPermissions(
                    it,
                    REQUIRED_PERMISSIONS,
                    locationPermissionCode
                ) }
            } else {
                CRLog.d("Permissions are requested 2")
                activity?.let { ActivityCompat.requestPermissions(
                    it,
                    REQUIRED_PERMISSIONS,
                    locationPermissionCode
                ) }
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
                makeToast("권한 승인 완료")
                CRLog.d("Permissions CB: Granted")
                getGPSInfo()
            }
            else {
                CRLog.d("Permissions CB: Denied")
                makeToast("앱을 정상적으로 실행하기 위해\n앱 설정에서 권한을 설정해 주세요.")
                bSkipWaitingGPS = true
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun getGPSInfo() {

        CRLog.d("getGPSInfo()")
        val gpsTimerTask = object : TimerTask() {
            var waitCountForGPS = 0
            override fun run() {
                if ( waitCountForGPS == 0 ) {
                    val mHandler = Handler(Looper.getMainLooper())
                    mHandler.postDelayed({
                        locationManager?.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            10000,
                            10f,
                            CRLocationListenerGPS
                        )
                        locationManager?.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            10000,
                            10f,
                            CRLocationListenerNetwork
                        )
                        CRLog.d("getGPSInfo end")
                    }, 0)
                } else if ( waitCountForGPS == 10 ) {
                    // 10초 안에 GPS 정보 미획득 시 실패로 간주
                    removeGPSTracking(false)
                    bSkipWaitingGPS = true
                }
                waitCountForGPS++
                CRLog.d("wait to get GPS... ${waitCountForGPS} sec")
            }
        }

        mGPStimer = Timer()
        mGPStimer?.schedule(gpsTimerTask, 0, 1000)
    }

    fun removeGPSTracking(bSuccess: Boolean) {
        CRLog.d("removeGPSTracking() ${bSuccess}")
        locationManager?.removeUpdates(CRLocationListenerGPS)
        locationManager?.removeUpdates(CRLocationListenerNetwork)
        mGPStimer?.cancel()
        mGPStimer = null

        if ( !bSuccess ) {
            val hh = Handler(Looper.getMainLooper())
            hh.postDelayed(
                {
                    makeToast("현재 위치 정보 수신에 실패했습니다.")
                }, 0 )
        }
    }

    fun makeToast(message: String) {
        Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show()
    }
}