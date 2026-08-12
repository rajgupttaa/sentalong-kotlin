plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    // Publishes to the Maven Central Portal with the machine-global
    // mavenCentral*/signingInMemory* credentials (~/.gradle/gradle.properties):
    //   gradle :sdk:publishAndReleaseToMavenCentral
    id("com.vanniktech.maven.publish") version "0.30.0"
}

group = "com.sentalong"
version = "0.1.0"

android {
    namespace = "com.sentalong.sdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.android.installreferrer:installreferrer:2.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    coordinates("com.sentalong", "sdk", "0.1.0")
    configure(
        com.vanniktech.maven.publish.AndroidSingleVariantLibrary(
            variant = "release",
            sourcesJar = true,
            publishJavadocJar = true,
        ),
    )
    pom {
        name.set("Sentalong Android SDK")
        description.set("Thin client over Sentalong's public tracking API.")
        url.set("https://github.com/rajgupttaa/sentalong-kotlin")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("rajgupttaa")
                name.set("Raj Kumar")
                email.set("theoneraj01@gmail.com")
            }
        }
        scm {
            url.set("https://github.com/rajgupttaa/sentalong-kotlin")
            connection.set("scm:git:https://github.com/rajgupttaa/sentalong-kotlin.git")
            developerConnection.set("scm:git:https://github.com/rajgupttaa/sentalong-kotlin.git")
        }
    }
}
