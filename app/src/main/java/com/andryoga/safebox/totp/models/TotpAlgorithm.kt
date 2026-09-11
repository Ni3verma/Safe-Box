package com.andryoga.safebox.totp.models

/**
 * Supported HMAC hashing algorithms for RFC 6238 Time-Based One-Time Password (TOTP) generation.
 *
 * @property hmacAlgorithm Standard Java Cryptography Architecture (JCA) Mac algorithm identifier.
 */
enum class TotpAlgorithm(val hmacAlgorithm: String) {
    SHA1("HmacSHA1"),
    SHA256("HmacSHA256"),
    SHA512("HmacSHA512"),
}
