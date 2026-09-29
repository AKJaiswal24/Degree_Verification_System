import java.security.SecureRandom
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val secretsFile = rootProject.file("encryption.properties")

if (!secretsFile.exists()) {
    val random = SecureRandom()
    fun randomHex(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
    secretsFile.writeText(
        "cert.master.secret=${randomHex()}\ncert.hash.pepper=${randomHex()}\n"
    )
}

val secrets = Properties().apply {
    secretsFile.inputStream().use { load(it) }
}

val masterSecret = secrets.getProperty("cert.master.secret") ?: ""
val hashPepper = secrets.getProperty("cert.hash.pepper") ?: ""

android {
    namespace = "com.example.ibu_verification"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.ibu_verification"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "CERT_MASTER_SECRET", "\"$masterSecret\"")
        buildConfigField("String", "CERT_HASH_PEPPER", "\"$hashPepper\"")
    }

    buildFeatures {
        buildConfig = true
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.cardview)
    implementation(libs.recyclerview)
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
