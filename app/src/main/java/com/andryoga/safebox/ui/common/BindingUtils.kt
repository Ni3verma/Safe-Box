package com.andryoga.safebox.ui.common

import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter
import androidx.databinding.BindingConversion
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.view.home.child.common.UserDataType
import com.google.android.material.imageview.ShapeableImageView

@BindingAdapter("userDataTypeIcon")
fun ShapeableImageView.setUserDataTypeIcon(type: UserDataType) {
    setImageDrawable(
        when (type) {
            UserDataType.LOGIN_DATA -> ContextCompat.getDrawable(context, R.drawable.ic_person_24)
            UserDataType.BANK_ACCOUNT -> ContextCompat.getDrawable(context, R.drawable.ic_bank_24)
            UserDataType.BANK_CARD -> ContextCompat.getDrawable(context, R.drawable.ic_card_24)
            UserDataType.SECURE_NOTE -> ContextCompat.getDrawable(context, R.drawable.ic_key_24)
        }
    )
}

@BindingAdapter("userDataTypeText")
fun TextView.setUserDataTypeText(type: UserDataType) {
    text = (
        when (type) {
            UserDataType.LOGIN_DATA -> context.getString(R.string.login)
            UserDataType.BANK_ACCOUNT -> context.getString(R.string.bank)
            UserDataType.BANK_CARD -> context.getString(R.string.card)
            UserDataType.SECURE_NOTE -> context.getString(R.string.note)
        }
        )
}

@BindingConversion
fun convertBooleanToVisibility(visible: Boolean): Int {
    return if (visible) View.VISIBLE else View.GONE
}
