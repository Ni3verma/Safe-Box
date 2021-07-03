package com.andryoga.safebox.ui.common

import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter
import com.andryoga.safebox.R
import com.andryoga.safebox.ui.view.home.child.common.UserDataType
import com.google.android.material.imageview.ShapeableImageView

@BindingAdapter("userDataType")
fun ShapeableImageView.setUserDataTypeIcon(type: UserDataType) {
    setImageDrawable(
        when (type) {
            UserDataType.LOGIN_DATA -> ContextCompat.getDrawable(context, R.drawable.ic_person_24)
            UserDataType.BANK_ACCOUNT -> TODO()
            UserDataType.BANK_CARD -> TODO()
            UserDataType.SECURE_NOTE -> TODO()
        }
    )
}
