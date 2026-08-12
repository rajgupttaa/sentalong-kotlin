package com.sentalong.sdk

import android.content.Context
import android.content.SharedPreferences

/**
 * Tiny key/value persistence abstraction. The production implementation is
 * SharedPreferences; tests use an in-memory fake so the core logic runs on
 * the plain JVM.
 */
interface SentalongStorage {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

/** Storage keys shared across platforms (see SPEC.md). */
internal object StorageKeys {
    const val CID = "sentalong.cid"
    const val CID_EXPIRES_AT = "sentalong.cid_expires_at"
    const val REFERRER_CHECKED = "sentalong.referrer_checked"
}

/** SharedPreferences-backed storage used on-device. */
internal class SharedPreferencesStorage(context: Context) : SentalongStorage {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("sentalong_sdk", Context.MODE_PRIVATE)

    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }
}
