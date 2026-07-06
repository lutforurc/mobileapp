plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.cashbookbd"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.cashbookbd"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Backend API base URL (must end with a trailing slash for Retrofit).
        buildConfigField("String", "BASE_URL", "\"https://nibirnirman.cashbookbd.com/api/\"")

        // Local-dev DNS override for NetworkModule's VhostDns: it maps BASE_URL's
        // host to this IP so an emulator/device can reach a ".test" Valet/Herd vhost
        // that only exists in your PC's hosts file. Values:
        //   - Emulator            : "10.0.2.2"  (host machine's localhost)
        //   - Physical device     : your PC's LAN IP, e.g. "192.168.1.50"
        //   - USB + `adb reverse`  : "127.0.0.1"
        // MUST be empty ("") for any real/resolvable domain, otherwise the override
        // hijacks that domain to the IP above. Production uses real DNS => "".
        buildConfigField("String", "LOCAL_HOST_IP", "\"\"")
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
            buildConfigField("String", "BASE_URL", "\"https://nibirnirman.cashbookbd.com/api/\"")
            buildConfigField("String", "LOCAL_HOST_IP", "\"\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "BASE_URL", "\"https://nibirnirman.cashbookbd.com/api/\"")
            // Empty => use real DNS (no vhost override in production).
            buildConfigField("String", "LOCAL_HOST_IP", "\"\"")
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