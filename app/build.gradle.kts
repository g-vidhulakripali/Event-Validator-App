import java.io.File

plugins {
    alias(libs.plugins.android.application)
}

fun loadDotEnv(file: File): Map<String, String> {
    if (!file.exists()) {
        return emptyMap()
    }

    return file.readLines()
        .mapNotNull { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) {
                null
            } else {
                val separatorIndex = line.indexOf('=')
                if (separatorIndex <= 0) {
                    null
                } else {
                    val key = line.substring(0, separatorIndex).trim()
                    var value = line.substring(separatorIndex + 1).trim()
                    if ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'"))
                    ) {
                        value = value.substring(1, value.length - 1)
                    }
                    key to value
                }
            }
        }
        .toMap()
}

fun Map<String, String>.envValue(key: String, defaultValue: String = ""): String {
    return this[key] ?: System.getenv(key) ?: defaultValue
}

fun String.asBuildConfigString(): String {
    return "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

val dotEnv = loadDotEnv(rootProject.file(".env"))

android {
    namespace = "com.projects.eventvalidator"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.projects.eventvalidator"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SCAN_API_URL", dotEnv.envValue("SCAN_API_URL", "https://eventticketgenerator.onrender.com/api/v1/scan").asBuildConfigString())
        buildConfigField("String", "EVENT_SLUG", dotEnv.envValue("EVENT_SLUG", "hsm-developer-community-2026").asBuildConfigString())
        buildConfigField("String", "GOOGLE_SHEETS_SPREADSHEET_ID", dotEnv.envValue("GOOGLE_SHEETS_SPREADSHEET_ID").asBuildConfigString())
        buildConfigField("String", "GOOGLE_SHEETS_RANGE", dotEnv.envValue("GOOGLE_SHEETS_RANGE", "Form Responses 1!A:Z").asBuildConfigString())
        buildConfigField("String", "GOOGLE_SHEETS_CLIENT_EMAIL", dotEnv.envValue("GOOGLE_SHEETS_CLIENT_EMAIL").asBuildConfigString())
        buildConfigField("String", "GOOGLE_SHEETS_PRIVATE_KEY", dotEnv.envValue("GOOGLE_SHEETS_PRIVATE_KEY").asBuildConfigString())
        buildConfigField("boolean", "GOOGLE_SHEETS_ENABLED", dotEnv.envValue("GOOGLE_SHEETS_ENABLED", "false"))
        buildConfigField("String", "GOOGLE_SHEETS_ATTENDED_VALUE", dotEnv.envValue("GOOGLE_SHEETS_ATTENDED_VALUE", "Attended").asBuildConfigString())
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
