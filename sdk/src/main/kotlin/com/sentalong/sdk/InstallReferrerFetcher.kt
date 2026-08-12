package com.sentalong.sdk

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * One-shot bridge to the Play Install Referrer API
 * (com.android.installreferrer:installreferrer:2.2). Returns the raw
 * (still URL-encoded) referrer string, or null when unavailable.
 */
internal object InstallReferrerFetcher {

    suspend fun fetch(context: Context): String? =
        suspendCancellableCoroutine { cont ->
            val resumed = AtomicBoolean(false)
            val client = InstallReferrerClient.newBuilder(context.applicationContext).build()

            fun finish(value: String?) {
                if (resumed.compareAndSet(false, true)) {
                    try {
                        client.endConnection()
                    } catch (_: Throwable) {
                        // ignore
                    }
                    cont.resume(value)
                }
            }

            cont.invokeOnCancellation {
                if (resumed.compareAndSet(false, true)) {
                    try {
                        client.endConnection()
                    } catch (_: Throwable) {
                        // ignore
                    }
                }
            }

            try {
                client.startConnection(object : InstallReferrerStateListener {
                    override fun onInstallReferrerSetupFinished(responseCode: Int) {
                        if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                            val referrer = try {
                                client.installReferrer.installReferrer
                            } catch (_: Throwable) {
                                null
                            }
                            finish(referrer?.takeIf { it.isNotBlank() })
                        } else {
                            finish(null)
                        }
                    }

                    override fun onInstallReferrerServiceDisconnected() {
                        finish(null)
                    }
                })
            } catch (_: Throwable) {
                finish(null)
            }
        }
}
