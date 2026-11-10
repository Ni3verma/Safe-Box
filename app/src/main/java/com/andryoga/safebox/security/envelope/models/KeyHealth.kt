package com.andryoga.safebox.security.envelope.models

/**
 * Non-interactive health status of a Keystore wrapping key pair.
 */
sealed interface KeyHealth {
    data object Healthy : KeyHealth
    data object Missing : KeyHealth
    data object Invalidated : KeyHealth
}
