package com.andryoga.safebox.ui.singleRecord.dynamicLayout.models

enum class FieldId {
    UNKNOWN,

    // LOGIN field ids
    LOGIN_TITLE,
    LOGIN_URL,
    LOGIN_USER_ID,
    LOGIN_PASSWORD,
    LOGIN_NOTES,

    // BANK ACCOUNT field ids
    BANK_ACCOUNT_TITLE,
    BANK_ACCOUNT_ACCOUNT_NUMBER,
    BANK_ACCOUNT_CUSTOMER_NAME,
    BANK_ACCOUNT_CUSTOMER_ID,
    BANK_ACCOUNT_BRANCH_CODE,
    BANK_ACCOUNT_BRANCH_NAME,
    BANK_ACCOUNT_BRANCH_ADDRESS,
    BANK_ACCOUNT_IFSC_CODE,
    BANK_ACCOUNT_MICR_CODE,
    BANK_ACCOUNT_NOTES,

    // CARD field ids
    CARD_TITLE,
    CARD_NAME,
    CARD_NUMBER,
    CARD_PIN,
    CARD_CVV,
    CARD_EXPIRY_DATE,
    CARD_NOTES,

    // NOTE field ids
    NOTE_TITLE,
    NOTE_NOTES,

    // common field ids
    CREATION_DATE,
    UPDATE_DATE

    // todo: title and notes can also be common field ids ideally. see if possible
}