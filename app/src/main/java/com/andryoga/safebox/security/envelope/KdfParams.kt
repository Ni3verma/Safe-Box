package com.andryoga.safebox.security.envelope

import java.util.Base64

/**
 * Self-describing PBKDF2 parameter tuple persisted as `v1|<algorithm>|<iterations>|<base64 salt>`.
 */
class KdfParams(
    val algorithm: String,
    val iterations: Int,
    salt: ByteArray,
    val version: String = VERSION_1,
) {
    private val saltBytes: ByteArray = salt.copyOf()

    val salt: ByteArray
        get() = saltBytes.copyOf()

    init {
        require(version == VERSION_1) { "Unsupported KDF params version: $version" }
        require(algorithm in SUPPORTED_ALGORITHMS) { "Unsupported KDF algorithm: $algorithm" }
        require(iterations > 0) { "KDF iterations must be positive: $iterations" }
        require(saltBytes.size == SALT_SIZE_BYTES) {
            "KDF salt must be $SALT_SIZE_BYTES bytes, got ${saltBytes.size}"
        }
    }

    /**
     * Serializes these parameters into the canonical `v1|<algorithm>|<iterations>|<base64 salt>` wire string.
     *
     * @return Wire representation suitable for `vault_meta` and backup headers.
     */
    fun encode(): String {
        val encodedSalt = Base64.getEncoder().encodeToString(saltBytes)
        return "$version$SEPARATOR$algorithm$SEPARATOR$iterations$SEPARATOR$encodedSalt"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KdfParams) return false
        return version == other.version &&
            algorithm == other.algorithm &&
            iterations == other.iterations &&
            saltBytes.contentEquals(other.saltBytes)
    }

    override fun hashCode(): Int {
        var result = version.hashCode()
        result = 31 * result + algorithm.hashCode()
        result = 31 * result + iterations
        result = 31 * result + saltBytes.contentHashCode()
        return result
    }

    override fun toString(): String =
        "KdfParams(version=$version, algorithm=$algorithm, iterations=$iterations)"

    companion object {
        const val VERSION_1 = "v1"
        const val ALGORITHM_PBKDF2_SHA256 = "PBKDF2WithHmacSHA256"
        const val SALT_SIZE_BYTES = 16
        private const val SEPARATOR = "|"
        private const val EXPECTED_PARTS = 4
        private val SUPPORTED_ALGORITHMS = setOf(ALGORITHM_PBKDF2_SHA256)

        /**
         * Parses a serialized `v1|<algorithm>|<iterations>|<base64 salt>` string, failing closed on
         * unrecognized versions, algorithms, or malformed fields.
         *
         * @param encoded Wire string from `vault_meta` or a backup header.
         * @return Parsed [KdfParams].
         * @throws IllegalArgumentException If the string is malformed or uses an unsupported version or algorithm.
         */
        fun decode(encoded: String): KdfParams {
            val parts = encoded.split(SEPARATOR)
            require(parts.size == EXPECTED_PARTS) {
                "Malformed KdfParams: expected $EXPECTED_PARTS parts"
            }
            val version = parts[0]
            val algorithm = parts[1]
            val iterations = parts[2].toIntOrNull()
                ?: throw IllegalArgumentException("Invalid KDF iteration count")
            val salt = try {
                Base64.getDecoder().decode(parts[3])
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid Base64 salt in KdfParams", e)
            }
            return KdfParams(
                algorithm = algorithm,
                iterations = iterations,
                salt = salt,
                version = version,
            )
        }
    }
}
