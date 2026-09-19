package com.andryoga.safebox.domain.models.record

import com.andryoga.safebox.totp.models.TotpConfig
import java.util.Date

/**
 * Domain model representing a 2FA TOTP authenticator record.
 *
 * @property config Seed plus the generation parameters, grouped because they are only ever used
 * together to derive a code.
 */
data class AuthenticatorData(
    val id: Int?,
    val title: String,
    val config: TotpConfig,
    val creationDate: Date,
    val updateDate: Date,
)
