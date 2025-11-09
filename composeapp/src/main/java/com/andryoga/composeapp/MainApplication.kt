package com.andryoga.composeapp

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.andryoga.composeapp.worker.SafeBoxWorkerFactory
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp
import org.jetbrains.annotations.NotNull
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

@HiltAndroidApp
class MainApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var safeBoxWorkerFactory: SafeBoxWorkerFactory

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(safeBoxWorkerFactory)
                .setMinimumLoggingLevel(Log.INFO)
                .build()

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
    override fun isLoggable(tag: String?, priority: Int): Boolean {
        return (priority in listOf(Log.ERROR, Log.WARN, Log.INFO))
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.log(message)

        if (priority == Log.ERROR) {
            // SEND ERROR REPORTS TO Crashlytics.
            if (t != null) {
                crashlytics.recordException(t)
            }
            crashlytics.sendUnsentReports()
        }
    }
}
