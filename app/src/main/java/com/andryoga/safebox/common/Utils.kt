package com.andryoga.safebox.common

import com.andryoga.safebox.security.interfaces.SymmetricKeyUtils
import com.andryoga.safebox.ui.common.Resource
import timber.log.Timber

object Utils {
    fun logResourceInfo(tag: String, resource: Resource<Any>) {
        Timber.i("$tag --> status = ${resource.status}, data = ${resource.data}, message = ${resource.message}")
    }

    fun String?.encryptNullableString(symmetricKeyUtils: SymmetricKeyUtils): String? {
        return if (this.isNullOrBlank()) null else symmetricKeyUtils.encrypt(this)
    }

    fun String?.decryptNullableString(symmetricKeyUtils: SymmetricKeyUtils): String? {
        return if (this.isNullOrBlank()) null else symmetricKeyUtils.decrypt(this)
    }
}
