package com.andryoga.safebox.common

import com.andryoga.safebox.ui.common.Resource
import timber.log.Timber

object Utils {
    fun logResourceInfo(tag: String, resource: Resource<Any>) {
        Timber.i("$tag --> status = ${resource.status}, data = ${resource.data}, message = ${resource.message}")
    }
}
