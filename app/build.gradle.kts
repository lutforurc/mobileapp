import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Dev-only login prefill, sourced from local.properties (which is untracked) so
// real credentials never live in source control or ship inside a release binary.
// To enable prefill in your local debug builds, add to local.properties:
//   dev.login.identifier=you@example.com
//   dev.login.password=your-password
val devLoginProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Single source of truth for the backend API base URL. Change it here to
// repoint every build type (must end with a trailing slash for Retrofit).
// val baseUrl = "https://aft.cashbookbd.com/api/"
// val baseUrl = "https://eworld.cashbookbd.com/api/"
// val baseUrl = "https://sinthia.cashbookbd.com/api/" 
val baseUrl = "https://nibirnirman.cashbookbd.com/api/" 
// val baseUrl = "https://gme.cashbookbd.com/api/" 
// val baseUrl = "https://krf.cashbookbd.com/api/" 
// val baseUrl = "https://scn.cashbookbd.com/api/" 
// val baseUrl = "https://mbdpp.cashbookbd.com/api/" 

// val baseUrl = "https://kps.cashbookbd.com/api/" 
// val baseUrl = "https://kbr.cashbookbd.com/api/" 





/**
 * Per-tenant branding. The tenant key is the base URL's subdomain, so switching
 * `baseUrl` above also switches the logo — no second place to keep in step.
 *
 * Each tenant's assets live in `src/tenants/<key>/res` and are named exactly as
 * they are for every other tenant (`drawable/logo.png`), so the app can always
 * reference `R.drawable.logo`. Only the active tenant's folder is added to the
 * build, so one tenant's APK never carries another's artwork. A tenant with no
 * folder falls back to `src/tenants/default`.
 */
val tenantKey: String = Regex("""^https?://([^./]+)""")
    .find(baseUrl)?.groupValues?.get(1).orEmpty()

val tenantResDir: File = file("src/tenants/$tenantKey/res")
    .takeIf { it.isDirectory } ?: file("src/tenants/default/res")

/** Wraps a value as a valid Java string literal for buildConfigField. */
fun javaStringLiteral(value: String): String {
    val escaped = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
    return "\"$escaped\""
}

android {
    namespace = "com.example.cashbookbd"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    // Adds the active tenant's assets alongside the shared ones. Nothing in
    // src/main/res may declare a resource a tenant folder also declares (e.g.
    // `logo`), or the two would collide.
    sourceSets {
        getByName("main") {
            res.srcDir(tenantResDir)
        }
    }

    defaultConfig {
        applicationId = "com.example.cashbookbd"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Backend API base URL (single source of truth: `baseUrl` above).
        buildConfigField("String", "BASE_URL", javaStringLiteral(baseUrl))

        // Local-dev DNS override for NetworkModule's VhostDns: it maps BASE_URL's
        // host to this IP so an emulator/device can reach a ".test" Valet/Herd vhost
        // that only exists in your PC's hosts file. Values:
        //   - Emulator            : "10.0.2.2"  (host machine's localhost)
        //   - Physical device     : your PC's LAN IP, e.g. "192.168.1.50"
        //   - USB + `adb reverse`  : "127.0.0.1"
        // MUST be empty ("") for any real/resolvable domain, otherwise the override
        // hijacks that domain to the IP above. Production uses real DNS => "".
        buildConfigField("String", "LOCAL_HOST_IP", "\"\"")

        // Login prefill: empty by default (release stays empty); debug overrides
        // these from local.properties below. Never hardcode real credentials here.
        buildConfigField("String", "DEV_LOGIN_IDENTIFIER", "\"\"")
        buildConfigField("String", "DEV_LOGIN_PASSWORD", "\"\"")
    }

    buildTypes {
        debug {
            // Local backend (Herd/Valet vhost) reached from the Android emulator.
            // 10.0.2.2 is the emulator's alias for the host machine's localhost;
            // VhostDns maps cashbook_api.test -> 10.0.2.2. Cleartext HTTP to these
            // hosts is allowed by res/xml/network_security_config.xml.
            //
            // For a physical device over WiFi instead, use your PC's LAN IP
            // (e.g. "192.168.1.50") and add that IP to network_security_config.xml.
            // To point debug at production, restore the nibirnirman URL + empty IP.
            buildConfigField("String", "BASE_URL", javaStringLiteral(baseUrl))
            buildConfigField("String", "LOCAL_HOST_IP", "\"\"")

            // Prefill login ONLY in debug, and ONLY if the developer opted in via
            // local.properties. Empty when unset, so a debug build still shows
            // blank fields unless you explicitly add the keys.
            buildConfigField(
                "String", "DEV_LOGIN_IDENTIFIER",
                javaStringLiteral(devLoginProps.getProperty("dev.login.identifier", "")),
            )
            buildConfigField(
                "String", "DEV_LOGIN_PASSWORD",
                javaStringLiteral(devLoginProps.getProperty("dev.login.password", "")),
            )
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "BASE_URL", javaStringLiteral(baseUrl))
            // Empty => use real DNS (no vhost override in production).
            buildConfigField("String", "LOCAL_HOST_IP", "\"\"")
            // Never prefill credentials in a release build.
            buildConfigField("String", "DEV_LOGIN_IDENTIFIER", "\"\"")
            buildConfigField("String", "DEV_LOGIN_PASSWORD", "\"\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.coroutines.android)

    // Remote images (the account-menu avatar)
    implementation(libs.coil.compose)

    // Secure token storage
    implementation(libs.androidx.security.crypto)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}