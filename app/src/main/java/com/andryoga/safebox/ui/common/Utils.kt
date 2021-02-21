package com.andryoga.safebox.ui.common

import android.view.View

class Utils {
    companion object {
        fun switchVisibility(vararg views: View) {
            views.forEach { view ->
                when (view.visibility) {
                    View.VISIBLE -> {
                        view.visibility = View.INVISIBLE
                    }
                    View.INVISIBLE -> {
                        view.visibility = View.VISIBLE
                    }
                    View.GONE -> {
                        view.visibility = View.VISIBLE
                    }
                }
            }
        }
    }
}