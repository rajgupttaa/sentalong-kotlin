package com.sentalong.sdk

import android.content.Context
import android.net.Uri
import android.util.Log

/**
 * Sentalong Android SDK — a thin client over Sentalong's public tracking
 * API. Four operations: [configure], [handleUrl], [identify], [qualify],
 * plus the Android-only [captureInstallReferrer].
 *
 * The SDK never generates PII: it sends only what the host app passes in.
 * No analytics, no fingerprinting, no device identifiers.
 */
object Sentalong {

    private const val TAG = "Sentalong"

    @Volatile
    internal var core: SentalongCore? = null

    /**
     * Stores the SDK configuration. Call once, early (e.g. Application.onCreate).
     *
     * @param context   any Context; the application context is retained.
     * @param baseUrl   the merchant's Sentalong origin, e.g. "https://sentalong.com".
     * @param programId the program id, e.g. "prg_…".
     * @param urlParam  the attribution query param to look for (default "via").
     */
    @JvmStatic
    @JvmOverloads
    fun configure(
        context: Context,
        baseUrl: String,
        programId: String,
        urlParam: String = "via",
    ) {
        val existing = core
        val instance = existing ?: SentalongCore(
            storage = SharedPreferencesStorage(context),
            transport = UrlConnectionTransport(),
            log = { Log.i(TAG, it) },
        ).also { core = it }
        instance.configure(baseUrl, programId, urlParam)
    }

    /**
     * Parses a deep link / app link (https URLs and custom schemes both
     * work). When the attribution param is present, POSTs the click and
     * persists the returned cid with its expiry. Returns the cid or null.
     *
     * If the param value is the literal "test", nothing is sent or
     * persisted — a log line confirms the wiring and null is returned.
     *
     * Never throws; network errors return null.
     */
    suspend fun handleUrl(url: String): String? = core?.handleUrl(url)

    /** Convenience overload for `intent.data`. */
    suspend fun handleUrl(uri: Uri): String? = handleUrl(uri.toString())

    /**
     * Reads the Play Store install referrer exactly once (guarded by the
     * `sentalong.referrer_checked` preference) and, when it carries the
     * attribution param, feeds it through the same click path with
     * `url = "android-app://<packageName>"`. Returns the cid or null.
     */
    suspend fun captureInstallReferrer(context: Context): String? {
        val instance = core ?: return null
        if (instance.referrerChecked()) return null

        val appContext = context.applicationContext
        val rawReferrer = InstallReferrerFetcher.fetch(appContext)
        instance.markReferrerChecked()

        if (rawReferrer == null) return null
        return instance.handleInstallReferrer(rawReferrer, appContext.packageName)
    }

    /**
     * Links the stored click to a user at signup. Reads the stored cid
     * (discarding it when expired), POSTs /t/identify and returns the
     * referralId or null. The cid stays stored — repeat identifies are
     * server-side idempotent.
     */
    suspend fun identify(email: String, externalId: String? = null): String? =
        core?.identify(email, externalId)

    /**
     * Marks a funnel stage ("signup", "onboarded" or "demo") for the stored
     * click. Returns true on success.
     */
    suspend fun qualify(stage: String): Boolean = core?.qualify(stage) ?: false
}
