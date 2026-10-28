import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localConfig = Properties().apply {
    val configFile = rootProject.file("local.properties")
    if (configFile.exists()) configFile.inputStream().use { load(it) }
}
val supabaseUrl = providers.gradleProperty("SUPABASE_URL")
    .orElse(providers.environmentVariable("WEBINTOAPK_SUPABASE_URL"))
    .orElse(localConfig.getProperty("supabase.url", ""))
val supabaseAnonKey = providers.gradleProperty("SUPABASE_ANON_KEY")
    .orElse(providers.environmentVariable("WEBINTOAPK_SUPABASE_ANON_KEY"))
    .orElse(localConfig.getProperty("supabase.anonKey", ""))

fun buildConfigString(value: String) =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""

android {
    namespace = "com.webintoapk"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.webintoapk"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "SUPABASE_URL", buildConfigString(supabaseUrl.get()))
        buildConfigField("String", "SUPABASE_ANON_KEY", buildConfigString(supabaseAnonKey.get()))
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.11.0")
}
