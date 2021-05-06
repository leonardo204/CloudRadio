package com.zerolive.cloudradio

import android.app.Activity
import android.app.Application
import android.os.Bundle

object AppLifecycleHandler : Application.ActivityLifecycleCallbacks {

    override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {
        CRLog.d("onActivityCreated ${activity?.localClassName}")
    }

    override fun onActivityStarted(activity: Activity?) {
        CRLog.d("onActivityStarted ${activity?.localClassName}")
    }

    override fun onActivityResumed(activity: Activity?) {
        CRLog.d("onActivityResumed ${activity?.localClassName}")
    }

    override fun onActivityPaused(activity: Activity?) {
        CRLog.d("onActivityPaused ${activity?.localClassName}")
    }

    override fun onActivityStopped(activity: Activity?) {
        CRLog.d("onActivityStopped ${activity?.localClassName}")
    }

    override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) {
        CRLog.d("onActivitySaveInstanceState ${activity?.localClassName}")
    }

    override fun onActivityDestroyed(activity: Activity?) {
        CRLog.d("onActivityDestroyed ${activity?.localClassName}")
    }
}