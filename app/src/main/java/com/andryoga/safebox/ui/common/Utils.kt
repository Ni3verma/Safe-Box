package com.andryoga.safebox.ui.common

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.constraintlayout.motion.widget.MotionLayout

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

    fun startMotionLayoutTransition(
        motionLayout: MotionLayout,
        endState: Int,
        startState: Int = motionLayout.currentState,
        duration: Int = 700
    ) {
        if (motionLayout.currentState != endState) {
            motionLayout.setTransition(startState, endState)
            motionLayout.setTransitionDuration(duration)
            motionLayout.transitionToEnd()
        }
    }

    fun longestCommonSubstring(string1: String, string2: String): Int {
        val matrix = Array(string1.length + 1) {
            IntArray(string2.length + 1)
        }
        var maxLength = 0
        for (i in 1 until matrix.size) {
            for (j in 1 until matrix[0].size) {
                val text1 = string1[i - 1]
                val text2 = string2[j - 1]
                if (text1 != text2) {
                    matrix[i][j] = 0
                } else {
                    matrix[i][j] = matrix[i - 1][j - 1] + 1
                }
                if (matrix[i][j] > maxLength) {
                    maxLength = matrix[i][j]
                }
            }
        }
        return maxLength
    }
}
