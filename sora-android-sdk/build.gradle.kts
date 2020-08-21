/*
apply plugin: "com.android.library"
apply plugin: "com.github.dcendents.android-maven"

apply plugin: "kotlin-android"
apply plugin: "kotlin-android-extensions"
apply plugin: "org.jetbrains.dokka"

 */

plugins {
    id("com.android.library")
    id("org.ajoberstar.grgit")
}

val group = "com.github.shiguredo"

android {
    compileSdkVersion(29)

    defaultConfig {
        minSdkVersion(21)
        targetSdkVersion(29)
        versionName = "${grgit.describe()}"

        buildConfigField("String", "REVISION", "\"${grgit.head().abbreviatedId}\"")
        buildConfigField("String", "LIBWEBRTC_VERSION", "\"${libwebrtc_version}\"")
    }
    sourceSets {
        main {
            java.srcDirs += "src/main/kotlin"
        }
        test {
            java.srcDirs += "src/test/kotlin"
        }
    }
    buildTypes {
        debug {
            debuggable = true
        }
        release {
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_1_7
            targetCompatibility = JavaVersion.VERSION_1_7
        }
    }

    testOptions {
        unitTests.includeAndroidResources = true
    }
}

dokka {
    outputFormat = "javadoc"

    configuration {
        moduleName = "sora"

        sourceLink {
            path = "sora-android-sdk/src/main/kotlin"
            url = "https://github.com/shiguredo/sora-android-sdk/tree/master/sora-android-sdk/src/main/kotlin"
            lineSuffix = "#L"
        }
    }
}

dependencies {
    api("com.github.shiguredo:shiguredo-webrtc-android:${libwebrtc_version}")

    implementation("org.jetbrains.kotlin:kotlin-stdlib:${kotlin_version}")
    implementation("org.jetbrains.kotlin:kotlin-reflect:${kotlin_version}")

    // required by "signaling" part
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("com.squareup.okhttp3:okhttp:4.8.1")

    // required by "rtc" part
    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")
    implementation("io.reactivex.rxjava2:rxjava:2.2.19")
    implementation("io.reactivex.rxjava2:rxkotlin:2.4.0")

    testImplementation("junit:junit:4.13")
    testImplementation("androidx.test:core:1.2.0")
    testImplementation("org.robolectric:robolectric:4.3.1") {
        exclude(group = "com.google.auto.service", module = "auto-service")
    }
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:${kotlin_version}")
}

configurations.all {
    resolutionStrategy {
        cacheDynamicVersionsFor(0, "seconds")
        cacheChangingModulesFor(0, "seconds")
    }
}

task<Jar>("sourcesJar") {
    classifier("sources")
    from(android.sourceSets.main.java.srcDirs)
}

artifacts {
    archives = sourcesJar
}
