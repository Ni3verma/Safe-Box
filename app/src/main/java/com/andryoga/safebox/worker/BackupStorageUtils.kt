package com.andryoga.safebox.worker

import android.net.Uri

object BackupStorageUtils {
    fun isRawFileScheme(uri: Uri): Boolean {
        return uri.scheme == "file" || uri.scheme == null || (uri.scheme != "content" && uri.path != null)
    }
}
