package com.andryoga.safebox.worker.backup.legacy

/**
 * Decrypts the record payloads of backup formats 1–3. Nothing encrypts with it any more.
 */
interface PasswordBasedEncryption {
    /**
     * @param password Raw backup password, not normalised; not modified.
     * @param data One encrypted record payload.
     * @param salt The file's salt.
     * @param iv The file's IV, shared by every payload of the file.
     * @return The payload's plaintext.
     */
    fun decrypt(
        password: CharArray,
        data: ByteArray,
        salt: ByteArray,
        iv: ByteArray,
    ): ByteArray
}
