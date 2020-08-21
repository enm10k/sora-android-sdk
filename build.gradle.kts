import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

buildscript {
    val kotlin_version by extra("1.3.72")
    val libwebrtc_version by extra("83.4103.12.2")
    val dokka_version by extra("0.10.1")

    repositories {
        jcenter()
        google()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:4.0.1")
        classpath("com.github.dcendents:android-maven-gradle-plugin:2.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${kotlin_version}")
        classpath("org.ajoberstar.grgit:grgit-gradle:3.1.1")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:${dokka_version}")
        classpath("com.github.ben-manes:gradle-versions-plugin:0.29.0")
    }

}

allprojects {
    repositories {
        jcenter()
        google()
        maven(url = "https://jitpack.io")
    }
}

task<Delete>("clean") {
    delete(rootProject.buildDir)
}

task<DependencyUpdatesTask>("dependencyUpdates") {
    resolutionStrategy {
        componentSelection {
            all {
                val rejected = listOf("alpha", "beta", "rc").any { qualifier ->
                    Regex("(?i).*[.-]$qualifier[.\\d-]*").containsMatchIn(candidate.version)
                }
                if (rejected) {
                    reject("Release candidate")
                }
            }
        }
    }
}
