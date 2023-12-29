package com.andryoga.safebox.ui.common

import android.view.View
import androidx.core.content.ContextCompat
import com.andryoga.safebox.R
import com.google.android.material.snackbar.Snackbar

object CommonSnackbar {
    fun showErrorSnackbar(
        view: View,
        message: String,
    ) {
        Snackbar.make(view, message, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(
                ContextCompat.getColor(
                    view.context,
                    android.R.color.holo_red_light,
                ),
            )
            .setTextColor(ContextCompat.getColor(view.context, R.color.white_50))
            .show()
    }

    fun showSuccessSnackbar(
        view: View,
        message: String,
    ) {
        Snackbar.make(view, message, Snackbar.LENGTH_SHORT)
            .setBackgroundTint(
                ContextCompat.getColor(
                    view.context,
                    R.color.colorPrimary,
                ),
            )
            .setTextColor(ContextCompat.getColor(view.context, R.color.white_50))
            .show()
    }

    fun getLoadingSnackbar(
        view: View,
        message: String,
        duration: Int = Snackbar.LENGTH_INDEFINITE,
    ): Snackbar {
        return Snackbar.make(view, message, duration)
            .setBackgroundTint(
                ContextCompat.getColor(
                    view.context,
                    R.color.colorAccent,
                ),
            )
            .setTextColor(ContextCompat.getColor(view.context, R.color.black_800))
    }
}
