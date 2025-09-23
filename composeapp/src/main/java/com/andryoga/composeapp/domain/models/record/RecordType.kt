package com.andryoga.composeapp.domain.models.record

import androidx.annotation.Keep

@Keep
enum class RecordType {
    LOGIN,
    CARD,
    BANK_ACCOUNT,
    NOTE
}