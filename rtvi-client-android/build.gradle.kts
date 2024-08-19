plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.jetbrains.dokka)
    `maven-publish`
    signing
}

android {
    namespace = "ai.rtvi.client"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    lint {
        targetSdk = 35
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}

publishing {
    repositories {
        maven {
            url = rootProject.layout.buildDirectory.dir("RTVILocalRepo").get().asFile.toURI()
            name = "RTVILocalRepo"
        }
    }

    publications {
        register<MavenPublication>("release") {
            groupId = "ai.rtvi"
            artifactId = "client"
            version = "0.1.0"

            pom {
                name.set("RTVI Client")
                description.set("Core RTVI client library for Android")
                url.set("https://github.com/rtvi-ai/rtvi-client-android")
            }

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}

signing {
    val signingKey = System.getenv("RTVI_GPG_SIGNING_KEY")
    val signingPassphrase = System.getenv("RTVI_GPG_SIGNING_PASSPHRASE")

    useInMemoryPgpKeys(signingKey, signingPassphrase)
    sign(publishing.publications)
}