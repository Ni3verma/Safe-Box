package com.andryoga.safebox.domain.models.record

import java.util.Date

/**
 * Domain model representing a 2FA TOTP authenticator record.
 */
data class AuthenticatorData(
    val id: Int?,
    val title: String,
    val secretKey: String,
    val creationDate: Date,
    val updateDate: Date,
)
