package com.andryoga.composeapp.common.sampleData

import kotlin.random.Random.Default.nextInt
import kotlin.random.Random.Default.nextLong

object RandomTestData {
    private val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9') + listOf(' ', '@', '#')
    private const val nine = 9
    private const val nineLong = 9L

    fun getRandomTestString(minLength: Int, maxLength: Int): String {
        val randomLength = nextInt(minLength, maxLength)
        return (minLength..randomLength)
            .map { nextInt(0, charPool.size) }
            .map(charPool::get)
            .joinToString("")
    }

    fun getRandomTestInt(minLength: Int, maxLength: Int): String {
        val randomLength = nextInt(minLength, maxLength)
        return (minLength..randomLength)
            .map { nextInt(0, nine) }
            .joinToString("")
    }

    fun getRandomTestLong(minLength: Int, maxLength: Int): String {
        val randomLength = nextInt(minLength, maxLength)
        return (minLength..randomLength)
            .map { nextLong(0L, nineLong) }
            .joinToString("")
    }
}
