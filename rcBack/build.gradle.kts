plugins {
    kotlin("jvm") version "2.4.0"
    application
}

group = "com.mohsen.remotecompose"
version = "1.0-SNAPSHOT"

kotlin { jvmToolchain(17) }

application {
    mainClass.set("com.example.backend.ServerKt")
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation("io.ktor:ktor-server-core:3.5.0")
    implementation("io.ktor:ktor-server-netty:3.5.0")
    implementation("org.slf4j:slf4j-simple:2.0.17")

    implementation("androidx.compose.remote:remote-creation:1.0.0-alpha12")
    implementation("androidx.compose.remote:remote-creation-core:1.0.0-alpha12")
    implementation("androidx.compose.remote:remote-creation-jvm:1.0.0-alpha12")

    testImplementation(kotlin("test"))
}
