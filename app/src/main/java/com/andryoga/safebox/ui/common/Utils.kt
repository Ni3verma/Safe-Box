package com.andryoga.safebox.ui.common

import android.view.View
import android.widget.TextView

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

        fun setTextViewLeftDrawable(view: TextView, drawableId: Int) {
            view.setCompoundDrawablesWithIntrinsicBounds(drawableId, 0, 0, 0)
        }
    }
}