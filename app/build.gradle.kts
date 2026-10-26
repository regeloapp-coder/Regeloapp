plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

val releaseSigningValues = listOf(
    "REGELO_STORE_FILE",
    "REGELO_STORE_PASSWORD",
    "REGELO_KEY_ALIAS",
    "REGELO_KEY_PASSWORD",
).map { providers.environmentVariable(it).orNull?.takeIf(String::isNotBlank) }
val releaseSigningConfigured = releaseSigningValues.all { it != null }
require(releaseSigningConfigured || releaseSigningValues.all { it == null }) {
    "Release signing requires REGELO_STORE_FILE, REGELO_STORE_PASSWORD, REGELO_KEY_ALIAS, and REGELO_KEY_PASSWORD."
}

android {
    namespace = "hu.regelo.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "hu.regelo.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 131
        versionName = "1.3.1"

        buildConfigField("String", "SUPABASE_URL", "\"https://pqmfezkvaslqedalwwnn.supabase.co\"")
        // Nyilvános (publishable) kulcs – a sorszintű jogosultságok (RLS) védik az adatokat.
        buildConfigField("String", "SUPABASE_KEY", "\"sb_publishable_vtSi7ZP9AiR0bYYlT_WErQ_ltqX6ZBF\"")
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(requireNotNull(releaseSigningValues[0]))
                storePassword = requireNotNull(releaseSigningValues[1])
                keyAlias = requireNotNull(releaseSigningValues[2])
                keyPassword = requireNotNull(releaseSigningValues[3])
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Store key is injected via environment; never sign release builds with the debug key.
            if (releaseSigningConfigured) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/io.netty.versions.properties",
                "META-INF/versions/9/previous-compilation-data.bin",
            )
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.sqlite)
    implementation(libs.zetetic.sqlcipher)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.fragment)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.functions)
    implementation(libs.ktor.client.okhttp)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(libs.pdfbox.android)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
