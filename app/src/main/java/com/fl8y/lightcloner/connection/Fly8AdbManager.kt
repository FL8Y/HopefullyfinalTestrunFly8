package com.fl8y.lightcloner.connection

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.concurrent.TimeUnit
import javax.security.auth.x500.X500Principal

/**
 * FLY8's ADB connection manager.
 *
 * Wireless debugging and Shizuku are separate paths:
 * this class owns only the ADB path.
 */
class Fly8AdbManager(context: Context) : AbsAdbConnectionManager() {

    private val appContext = context.applicationContext
    private val alias = "fly8-adb-auth-v1"

    init {
        setApi(Build.VERSION.SDK_INT)
        setTimeout(15, TimeUnit.SECONDS)
        setThrowOnUnauthorised(true)
    }

    override fun getPrivateKey(): PrivateKey = ensureKeyPair().private

    override fun getCertificate(): Certificate = ensureKeyPair().certificate

    override fun getDeviceName(): String {
        val model = Build.MODEL?.trim().orEmpty()
        return if (model.isBlank()) "FLY8" else "FLY8-$model"
    }

    fun autoConnect(timeoutMs: Long): Boolean {
        return super.autoConnect(appContext, timeoutMs)
    }

    private fun ensureKeyPair(): StoredKeyPair {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        if (!keyStore.containsAlias(alias)) {
            val generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA,
                "AndroidKeyStore"
            )

            val calendar = java.util.Calendar.getInstance()
            val notBefore = calendar.time
            calendar.add(java.util.Calendar.YEAR, 20)
            val notAfter = calendar.time

            generator.initialize(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setKeySize(2048)
                    .setDigests(
                        KeyProperties.DIGEST_SHA256,
                        KeyProperties.DIGEST_SHA512
                    )
                    .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                    .setCertificateSubject(X500Principal("CN=FLY8 ADB"))
                    .setCertificateSerialNumber(BigInteger.ONE)
                    .setCertificateNotBefore(notBefore)
                    .setCertificateNotAfter(notAfter)
                    .build()
            )

            generator.generateKeyPair()
        }

        val privateKey = (keyStore.getKey(alias, null) as? PrivateKey)
            ?: error("FLY8 ADB private key is unavailable")

        val certificate = keyStore.getCertificate(alias)
            ?: error("FLY8 ADB certificate is unavailable")

        return StoredKeyPair(privateKey, certificate)
    }

    private data class StoredKeyPair(
        val private: PrivateKey,
        val certificate: Certificate,
    )
}
