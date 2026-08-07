import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.vanniktech.mavenPublish)
}

android {
    namespace = "com.akardas.kaptor.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":kaptor-core"))
    api(project(":kaptor-ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.core)

    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
}

mavenPublishing {
    // AGP's built-in javadoc task uses an old embedded Dokka that crashes on JDK 21+. Skip it and
    // attach an empty javadoc jar below (Maven Central only requires the jar to be present).
    configure(AndroidSingleVariantLibrary(variant = "release", sourcesJar = true, publishJavadocJar = false))
    publishToMavenCentral()
    if (providers.gradleProperty("signingInMemoryKey").isPresent ||
        providers.gradleProperty("signing.keyId").isPresent
    ) {
        signAllPublications()
    }
}

val androidJavadocJar = tasks.register<Jar>("androidJavadocJar") {
    archiveClassifier.set("javadoc")
}
publishing {
    publications.withType<MavenPublication>().configureEach {
        artifact(androidJavadocJar)
    }
}
