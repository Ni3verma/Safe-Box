package com.andryoga.safebox.ui.common

import android.view.View
import androidx.core.widget.addTextChangedListener
import com.andryoga.safebox.R
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import timber.log.Timber

class RequiredFieldValidator(
    private val mandatoryViews: List<View>,
    private val validationOnViewId: View,
    private val tag: String,
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
                        view.error = view.context.getString(R.string.common_error_mandatory_field)
                        errorFields = errorFields.plusElement(view.id)
                        if (validationOnViewId.isEnabled) {
                            Timber.i("$tag --> disabling button")
                            validationOnViewId.isEnabled = false
                        }
                    } else {
                        view.isErrorEnabled = false
                        view.error = null
                        errorFields = errorFields.minusElement(view.id)
                        if (errorFields.isEmpty() && !validationOnViewId.isEnabled) {
                            Timber.i("$tag --> enabling button")
                            validationOnViewId.isEnabled = true
                        }
                    }
                }
            }
        }
    }
}
