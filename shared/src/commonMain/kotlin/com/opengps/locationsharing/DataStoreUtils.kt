package com.opengps.locationsharing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.delay

class DataStoreUtils(private val dataStore: () -> DataStore<Preferences>) {

    private var stateMap = mapOf<Preferences.Key<*>, Any>()

    init {
        SuspendScope {
            delay(100)
            dataStore().data.collect {
                stateMap = it.asMap()
                println("STATE UPDATE")
                println(stateMap)
            }
        }
    }

    fun getString(name: String): String? {
        return stateMap[stringPreferencesKey(name)] as String?
    }

    suspend fun setString(name: String, value: String, onlyIfAbsent: Boolean = false) {
        dataStore().edit {
            if(onlyIfAbsent && it.contains(stringPreferencesKey(name))) return@edit
            it[stringPreferencesKey(name)] = value
        }
    }

    fun getByteArray(name: String): ByteArray? {
        return stateMap[stringPreferencesKey(name)] as ByteArray?
    }

    suspend fun setByteArray(name: String, value: ByteArray, onlyIfAbsent: Boolean = false) {
        dataStore().edit {
            if(onlyIfAbsent && it.contains(byteArrayPreferencesKey(name))) return@edit
            it[byteArrayPreferencesKey(name)] = value
        }
    }

    fun getBoolean(name: String): Boolean? {
        return stateMap[booleanPreferencesKey(name)] as Boolean?
    }

    suspend fun setBoolean(name: String, value: Boolean, onlyIfAbsent: Boolean = false) {
        dataStore().edit {
            if(onlyIfAbsent && it.contains(booleanPreferencesKey(name))) return@edit
            it[booleanPreferencesKey(name)] = value
        }
    }

    fun getLong(name: String): Long? {
        return stateMap[longPreferencesKey(name)] as Long?
    }

    suspend fun setLong(s: String, userid: Long, onlyIfAbsent: Boolean = false) {
        dataStore().edit {
            if(onlyIfAbsent && it.contains(longPreferencesKey(s))) return@edit
            it[longPreferencesKey(s)] = userid
        }
    }
}