package com.opengps.locationsharing

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.RSA
import dev.whyoleg.cryptography.algorithms.SHA512
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.random.Random

class Networking {
    companion object {

        private fun getUrl() = if(getPlatform().dataStoreUtils.getBoolean("useTor") == true) "d5u5c37mmg337kgce5jpjkuuqnq7e5xc44w2vsc4wcjrrqlyo3jjvbqd.onion" else "api.findfamily.cc"

        private val client = HttpClient() {
            install(ContentNegotiation) {
                json()
            }
        }
        private val crypto = CryptographyProvider.Default.get(RSA.OAEP)
        private var publickey: RSA.OAEP.PublicKey? = null
        private var privatekey: RSA.OAEP.PrivateKey? = null
        var userid: ULong? = null
            private set

        suspend fun init() {
            val platform = getPlatform()
            val (privateKey, publicKey) = crypto.keyPairGenerator(digest = SHA512).generateKey().let { Pair(it.privateKey, it.publicKey) }
            platform.dataStoreUtils.setByteArray("privateKey", privateKey.encodeToByteArray(RSA.PrivateKey.Format.PEM), true)
            platform.dataStoreUtils.setByteArray("publicKey", publicKey.encodeToByteArray(RSA.PublicKey.Format.PEM), true)
            platform.dataStoreUtils.setLong("userid", Random.nextLong(), true)
            platform.dataStoreUtils.setBoolean("useTor", false, true)

            delay(100)
            publickey = crypto.publicKeyDecoder(SHA512).decodeFromByteArray(RSA.PublicKey.Format.PEM, platform.dataStoreUtils.getByteArray("publicKey")!!)
            privatekey = crypto.privateKeyDecoder(SHA512).decodeFromByteArray(RSA.PrivateKey.Format.PEM, platform.dataStoreUtils.getByteArray("privateKey")!!)
            userid = platform.dataStoreUtils.getLong("userid")!!.toULong()
        }

        private suspend fun register() {
            @Serializable
            data class Register(val userid: ULong, val key: String)
            getPlatform().torDNSChecker {
                client.post("https://${getUrl()}/register") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        Register(
                            userid!!,
                            publickey!!.encodeToByteArray(RSA.PublicKey.Format.PEM).encodeBase64()
                        )
                    )
                }
            }
        }

        suspend fun ensureUserExists() {
            if(getKey(userid!!) == null) {
                register()
            }
        }

        private suspend fun getKey(userid: ULong): RSA.OAEP.PublicKey? {
            try {
                val response = getPlatform().torDNSChecker { client.post("https://${getUrl()}/getkey") {
                    contentType(ContentType.Application.Json)
                    setBody("{\"userid\": $userid}")
                } } ?: return null
                if(response.status != HttpStatusCode.OK) {
                    return null
                }
                return crypto.publicKeyDecoder(SHA512).decodeFromByteArray(RSA.PublicKey.Format.PEM, response.bodyAsText().decodeBase64Bytes())
            }  catch (e: ServerResponseException) {
                return null
            } catch(e: Exception) {
                println("EXCEPTION ${e.message}")
                return null
            }
        }

        suspend fun publishLocation(location: LocationValue, user: User): Boolean {
            try {
                val key = getKey(user.id) ?: return false
                getPlatform().torDNSChecker { client.post("https://${getUrl()}/location/publish") {
                    contentType(ContentType.Application.Json)
                    setBody(encryptLocation(location, user.id, key))
                } } ?: return false
                return true
            } catch(e: SocketTimeoutException) {
                return false
            }
        }

        suspend fun receiveLocations(): List<LocationValue>? {
            try {
                val response = getPlatform().torDNSChecker { client.post("https://${getUrl()}/location/receive") {
                    contentType(ContentType.Application.Json)
                    setBody("{\"userid\": $userid}")
                } } ?: return null
                if(response.status != HttpStatusCode.OK) return null
                val locationsEncrypted = response.body<List<String>>()
                val locations = locationsEncrypted.map { decryptLocation(it) }
                return locations
            } catch(e: SocketTimeoutException) {
                return null
            }
        }

        private suspend fun encryptLocation(location: LocationValue, recipientUserID: ULong, key: RSA.OAEP.PublicKey): LocationSharingData {
            val cipher = key.encryptor()
            val str = Json.encodeToString(location)
            val encryptedData = cipher.encrypt(str.encodeToByteArray()).encodeBase64()
            return LocationSharingData(recipientUserID, encryptedData)
        }

        private suspend fun decryptLocation(encryptedLocation: String): LocationValue {
            val cipher = privatekey!!.decryptor()
            val decryptedData = cipher.decrypt(encryptedLocation.decodeBase64Bytes()).decodeToString()
            return Json.decodeFromString(decryptedData)
        }

        @Serializable
        private data class LocationSharingData(val recipientUserID: ULong, val encryptedLocation: String)
    }
}