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
     * Login.
     */
    // TODO(MR5): becomes the designed legacy-key-lost state with its own copy; MR 6 adds Erase and start over
    LEGACY_KEY_LOST,
}