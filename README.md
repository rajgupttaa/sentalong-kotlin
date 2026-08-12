# Sentalong Android SDK (Kotlin)

Thin client over Sentalong's public tracking API for Android apps. Four
operations — `configure`, `handleUrl`, `identify`, `qualify` — plus
`captureInstallReferrer` for Play Store install attribution.

- Zero heavy dependencies: `HttpURLConnection` + `SharedPreferences` +
  kotlinx-coroutines, and the official Play Install Referrer library.
- No analytics, no fingerprinting, no device identifiers. The SDK sends only
  what your app passes in — it never generates PII.
- minSdk 21. Version 0.1.0. MIT license.

## Install

```kotlin
// build.gradle.kts (app module)
dependencies {
    implementation("com.sentalong:sdk:0.1.0")
}
```

Or include it as a local module:

```kotlin
// settings.gradle.kts
include(":sentalong-sdk")
project(":sentalong-sdk").projectDir = file("path/to/sdks/kotlin/sdk")
```

The SDK needs the `INTERNET` permission (virtually every app already has it):

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## Configure

Call once, early — `Application.onCreate` is the natural place:

```kotlin
import com.sentalong.sdk.Sentalong

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Sentalong.configure(
            context = this,
            baseUrl = "https://sentalong.com",   // your Sentalong origin
            programId = "prg_XXXXXXXX",
            // urlParam = "via",                  // optional, default "via"
        )
    }
}
```

## Deep links → clicks

### Manifest setup

Declare the links your app can open. Both Android App Links (https) and
custom schemes work — `handleUrl` parses either.

```xml
<activity android:name=".MainActivity" android:exported="true">
    <!-- Android App Link: https://yourapp.com/... -->
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="https" android:host="yourapp.com" />
    </intent-filter>

    <!-- Custom scheme: yourapp://... -->
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="yourapp" />
    </intent-filter>
</activity>
```

### Handle the link

`handleUrl` extracts the attribution param (`via` by default), `sub_id`
(`sub1` also accepted) and any ad-click ids (`gclid`, `fbclid`, `ttclid`,
`twclid`, `li_fat_id`, `msclkid`), records the click, and persists the
returned click id (`cid`) with its expiry. It never throws — network errors
simply return null.

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let { uri ->
            lifecycleScope.launch { Sentalong.handleUrl(uri) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { uri ->
            lifecycleScope.launch { Sentalong.handleUrl(uri) }
        }
    }
}
```

Tip: open a link with `?via=test` to verify wiring — the SDK logs
`Sentalong: test click received` and sends/stores nothing.

## Install referrer (Play Store installs)

When a user lands on your Play Store page from a partner link, the referral
params survive the install via the Play Install Referrer API. Capture them
once at first launch:

```kotlin
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Sentalong.configure(this, "https://sentalong.com", "prg_XXXXXXXX")

        CoroutineScope(Dispatchers.IO).launch {
            Sentalong.captureInstallReferrer(this@App)
        }
    }
}
```

This reads the Play install referrer exactly once (guarded by a
`sentalong.referrer_checked` preference), URL-decodes it, parses it as query
params, and feeds it through the same click path with
`url = "android-app://<your.package.name>"`. Organic installs carry no
`via` param and send nothing.

Your Play Store links should look like:

```
https://play.google.com/store/apps/details?id=com.your.app&referrer=via%3Dpartner-handle%26sub_id%3Dcampaign-x
```

(the `referrer` value is URL-encoded).

## Identify at signup

Once the user signs up, link the stored click to them. Returns the
`referralId` or null (no stored click, expired click, or network failure):

```kotlin
suspend fun onSignupCompleted(user: User) {
    val referralId = Sentalong.identify(
        email = user.email,
        externalId = user.id,   // optional
    )
    // referralId is "ref_…" when the signup was attributed
}
```

The stored cid is kept after a successful identify — calling identify again
is safe (server-side idempotent).

## Qualify funnel stages

```kotlin
Sentalong.qualify("signup")     // true on success
Sentalong.qualify("onboarded")
Sentalong.qualify("demo")
```

## Storage & attribution window

The SDK stores exactly three preferences (in `sentalong_sdk` SharedPreferences):

| Key | Purpose |
| --- | --- |
| `sentalong.cid` | the click id |
| `sentalong.cid_expires_at` | ISO expiry (server-controlled `cookieDays`) |
| `sentalong.referrer_checked` | install-referrer one-shot guard |

An expired cid is discarded on the next read; `identify`/`qualify` then
return null/false.

## Testing

The URL parsing, click extraction, JSON and expiry logic live in plain
Kotlin classes with injected storage/transport, so the unit tests run on the
JVM without a device or emulator:

```sh
# from sdks/kotlin/ (requires Gradle 8.7+ and JDK 17)
gradle :sdk:testDebugUnitTest
```

## License

MIT — see [LICENSE](./LICENSE).
