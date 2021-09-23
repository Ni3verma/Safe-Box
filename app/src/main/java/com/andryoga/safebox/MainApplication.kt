package com.andryoga.safebox

import android.app.Application
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp
import org.jetbrains.annotations.NotNull
import timber.log.Timber
import java.util.*

@HiltAndroidApp
class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(object : Timber.DebugTree() {
                override fun createStackElementTag(element: StackTraceElement): String {
                    return String.format(
                        Locale.US,
                        "Nitin Class:%s, Line: %s, Method: %s",
                        super.createStackElementTag(element),
                        element.lineNumber,
                        element.methodName
                    )
                }
            })
        } else {
            Timber.plant(ReleaseTree())
        }
    }
}

class ReleaseTree : @NotNull Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.log(message)

        if (priority == Log.ERROR || priority == Log.WARN) {
            // SEND ERROR REPORTS TO Crashlytics.
            if (t != null) {
                crashlytics.recordException(t)
            }
            crashlytics.sendUnsentReports()
        }
    }
}
