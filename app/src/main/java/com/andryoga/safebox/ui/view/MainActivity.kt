package com.andryoga.safebox.ui.view

import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.andryoga.safebox.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // TODO : remove it after using
        // Using  PBKDF2WithHmacSHA1 algorithm
        val scope = MainScope()
        scope.launch {
            for (i in 1..5) {
                val pswrd = "Qwerty@@135"

                val sr: SecureRandom = SecureRandom.getInstance("SHA1PRNG")
                val salt = ByteArray(16)
                sr.nextBytes(salt)

                val base64EncodedSalt = Base64.encodeToString(salt, Base64.DEFAULT)
                //Timber.i("salt = $base64EncodedSalt")

                val spec = PBEKeySpec(pswrd.toCharArray(), salt, 500, 64 * 8)
                val base64EncodedHash = Base64.encodeToString(
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                        .generateSecret(spec).encoded, Base64.DEFAULT
                )
                //Timber.i("hash(paswword+salt) = $base64EncodedHash")

                // checking password
                val pswrd2 = "Qwerty@@134"
                val spec2 = PBEKeySpec(
                    pswrd2.toCharArray(),
                    Base64.decode(base64EncodedSalt, Base64.DEFAULT),
                    500,
                    64 * 8
                )
                val base64EncodedHash2 = Base64.encodeToString(
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                        .generateSecret(spec2).encoded, Base64.DEFAULT
                )
                //Timber.i("hash(paswword+salt)2 = $base64EncodedHash2")

                if (base64EncodedHash == base64EncodedHash2) {
                    Log.i("$i hashing", "*****************passwords match")
                } else {
                    Log.i(
                        "$i hashing",
                        "!!!!!!!!!!!!!!!!!!!!!!!!passwords DO NOT match!!!!!!!!!!!!!!!!!!!"
                    )
                }
            }
        }
    }
}