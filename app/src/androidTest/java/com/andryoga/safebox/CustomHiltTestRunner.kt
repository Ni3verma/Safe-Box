package com.andryoga.safebox

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

class CustomHiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application {
        return super.newApplication(cl, HiltTestApp_Application::class.java.name, context)
    }
}
