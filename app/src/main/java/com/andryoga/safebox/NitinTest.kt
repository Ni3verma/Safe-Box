package com.andryoga.safebox

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.FileInputStream
import java.io.ObjectInputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

fun main() {
//    val dataList = mutableListOf<TestSer>()
//    for (i in 1..100) {
//        dataList.add(TestSer("Nitin"))
//    }
//
//    val a = Json.encodeToString(
//        ListSerializer(TestSer.serializer()),
//        dataList
//    )
//
//    println("to encrypt = $a")
//    val encryptedMap = encryption(a)
//    println("map = $encryptedMap")
//
//    ObjectOutputStream(FileOutputStream("nitinTestEnc")).use {
//        it.writeObject(encryptedMap)
//    }

    ObjectInputStream(FileInputStream("nitinTestEnc")).use {
        val a: Map<String, ByteArray> = it.readObject() as Map<String, ByteArray>
        println("file data = $a")
        decrypt(a, "Qwerty@@135".toCharArray())
    }
}

fun encryption(dataToEncrypt: String): MutableMap<String, Any> {
    val map = mutableMapOf<String, Any>()
    val password = "Qwerty@@135".toCharArray()

    val random = SecureRandom()
    val salt = ByteArray(256)
    random.nextBytes(salt)

    val pbKeySpec = PBEKeySpec(password, salt, 1324, 256) // 1
    val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1") // 2
    val keyBytes = secretKeyFactory.generateSecret(pbKeySpec).encoded // 3
    val keySpec = SecretKeySpec(keyBytes, "AES") // 4

    val ivRandom = SecureRandom() // not caching previous seeded instance of SecureRandom
// 1
    val iv = ByteArray(16)
    ivRandom.nextBytes(iv)
    val ivSpec = IvParameterSpec(iv) // 2

    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding") // 1
    cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
    val encrypted = cipher.doFinal(dataToEncrypt.toByteArray()) // 2

    map["a"] = salt
    map["iv"] = iv
    map["encrypted"] = encrypted

    return map
//    return Base64.getEncoder().encodeToString(encrypted)
}

fun decrypt(map: Map<String, ByteArray>, password: CharArray) {
    val salt = map["a"]
    val iv = map["iv"]
    val encrypted = map["encrypted"]

// 2
// regenerate key from password
    val pbKeySpec = PBEKeySpec(password, salt, 1324, 256)
    val secretKeyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
    val keyBytes = secretKeyFactory.generateSecret(pbKeySpec).encoded
    val keySpec = SecretKeySpec(keyBytes, "AES")

// 3
// Decrypt
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    val ivSpec = IvParameterSpec(iv)
    cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
    val decrypted = cipher.doFinal(encrypted)
    val jsonDecrypted = String(decrypted)
    println("decrypted string = $jsonDecrypted")

    val objects = Json.decodeFromString(ListSerializer(TestSer.serializer()), jsonDecrypted)
    println(objects.size)
}
@Serializable
data class TestSer(
    val name: String
)
