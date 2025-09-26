package com.andryoga.composeapp.common

object Exceptions {
    data class DebugFatalException(val errorMessage: String) : Exception(errorMessage)
}