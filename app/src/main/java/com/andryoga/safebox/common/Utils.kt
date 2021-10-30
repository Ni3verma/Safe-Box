package com.andryoga.safebox.common

import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import com.andryoga.safebox.ui.common.Resource
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

object Utils {
    fun logResource(tag: String, resource: Resource<Any>) {
        Timber.d("$tag --> status = ${resource.status}\ndata = ${resource.data}\nmessage = ${resource.message}\n")
    }

    fun String?.encryptNullableString(symmetricKeyUtils: SymmetricKeyUtils): String? {
        return if (this.isNullOrBlank()) null else symmetricKeyUtils.encrypt(this)
    }

    fun String?.decryptNullableString(symmetricKeyUtils: SymmetricKeyUtils): String? {
        return if (this.isNullOrBlank()) null else symmetricKeyUtils.decrypt(this)
    }

    fun getFormattedDate(date: Date, pattern: String = "EEEE, dd MMM yyyy hh-mm-ss a"): String {
        return SimpleDateFormat(pattern).format(date)
    }
}
