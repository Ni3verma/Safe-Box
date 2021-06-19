package com.andryoga.safebox.ui.common

import android.view.View
import android.widget.Button
import androidx.core.widget.addTextChangedListener
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import timber.log.Timber

class RequiredFieldValidator(
    private val mandatoryViews: List<View>,
    private val validationOnViewId: Button,
    private val tag: String
) {
    var errorFields = setOf<Int>()

    init {
        validationOnViewId.isEnabled = false
        for (view in mandatoryViews) {
            errorFields = errorFields.plusElement(view.id)
        }
        Timber.i("$tag --> init ${errorFields.size} error fields")
    }

    fun validate() {
        mandatoryViews.forEach { view ->
            if (view is TextInputLayout) {
                val editText: TextInputEditText = view.editText as TextInputEditText
                editText.addTextChangedListener { text ->
                    if (text.isNullOrBlank()) {
                        view.isErrorEnabled = true
                        view.error = "Mandatory Field"
                        errorFields = errorFields.plusElement(view.id)
                        Timber.i("$tag --> disabling button")
                        validationOnViewId.isEnabled = false
                    } else {
                        view.isErrorEnabled = false
                        view.error = null
                        errorFields = errorFields.minusElement(view.id)
                        if (errorFields.isEmpty()) {
                            Timber.i("$tag --> enabling button")
                            validationOnViewId.isEnabled = true
                        }
                    }
                }
            }
        }
    }
}
