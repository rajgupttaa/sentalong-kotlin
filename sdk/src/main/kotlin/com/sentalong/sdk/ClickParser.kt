package com.sentalong.sdk

/** The attribution-relevant values extracted from a landing URL or referrer. */
internal data class ClickData(
    val via: String,
    val subId: String?,
    val adIds: Map<String, String>,
)

internal object ClickParser {

    /** The six ad-click ids the server accepts alongside `via`. */
    val AD_CLICK_IDS = listOf("gclid", "fbclid", "ttclid", "twclid", "li_fat_id", "msclkid")

    /**
     * Builds a [ClickData] from already-parsed query params.
     * Returns null when the attribution param ([urlParam], default "via")
     * is absent or blank — there is nothing to attribute.
     * `sub_id` is preferred; `sub1` is accepted as a fallback alias.
     */
    fun fromParams(params: Map<String, String>, urlParam: String): ClickData? {
        val via = params[urlParam]?.trim()
        if (via.isNullOrEmpty()) return null

        val subId = params["sub_id"]?.takeIf { it.isNotBlank() }
            ?: params["sub1"]?.takeIf { it.isNotBlank() }

        val adIds = LinkedHashMap<String, String>()
        for (id in AD_CLICK_IDS) {
            val value = params[id]
            if (!value.isNullOrBlank()) adIds[id] = value
        }

        return ClickData(via = via, subId = subId, adIds = adIds)
    }
}
