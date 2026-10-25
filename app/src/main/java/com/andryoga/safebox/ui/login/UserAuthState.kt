package com.andryoga.safebox.ui.login

enum class UserAuthState {
    /** initial vale for the state*/
    INITIAL,

    /** password entered is correct or user has verified with biometric and we can navigate to the next screen*/
    VERIFIED,

    /** error message should be displayed */
    INCORRECT_PASSWORD_ENTERED,

    /**
     * The credential was right but the legacy Keystore key that encrypts this vault is gone, so
     * nothing can be opened and the key is never regenerated. The screen stays on
     * Login in the dedicated legacy-key-lost state, whose only way forward is *Erase everything
     * and start over* (design §6.11).
     */
    // TODO(D3): legacy vault (crypto_version = 1) only
    LEGACY_KEY_LOST,
}