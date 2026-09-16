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
    ;

    companion object {
        /**
         * Resolves a stored algorithm name, falling back to the RFC 6238 default.
         *
         * Used on the restore path, where the name comes from a backup file that a newer build may
         * have written. Throwing there would fail the whole restore over a single record.
         *
         * @param name Algorithm name as written to the backup, case-insensitive.
         * @return Matching entry, or [SHA1] when the name is unknown or absent.
         */
        fun fromNameOrDefault(name: String?): TotpAlgorithm {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: SHA1
        }
    }
}
