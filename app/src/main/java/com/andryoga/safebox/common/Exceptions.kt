package com.andryoga.safebox.common

object Exceptions {
    data class DebugFatalException(val errorMessage: String) : Exception(errorMessage)
}