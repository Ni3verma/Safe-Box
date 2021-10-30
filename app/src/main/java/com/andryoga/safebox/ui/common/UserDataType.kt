package com.andryoga.safebox.ui.common

import androidx.annotation.Keep

// keeping it because it is used as argument in navigation graph
@Keep
enum class UserDataType { LOGIN_DATA, BANK_ACCOUNT, BANK_CARD, SECURE_NOTE }
