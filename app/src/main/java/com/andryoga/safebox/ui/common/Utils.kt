package com.andryoga.safebox.ui.common

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView

object Utils {
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

    fun setKeyBoardVisibility(context: Context, show: Boolean) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (show) {
            imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
        } else {
            imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0)
        }
    }

    fun switchText(view: TextView, originalText: String, changeToText: String) {
        if (view.text == originalText) {
            view.text = changeToText
        } else {
            view.text = originalText
        }
    }
}
