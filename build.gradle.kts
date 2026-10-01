import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.1"
}
repositories { google(); mavenCentral() }
kotlin { jvmToolchain(21) }
dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("net.java.dev.jna:jna:5.19.1")
    implementation("net.java.dev.jna:jna-platform:5.19.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation(kotlin("test-junit"))
    testImplementation(compose.desktop.uiTestJUnit4)
}
tasks.test {
    System.getProperty("puckmouse.screenshots")?.let { systemProperty("puckmouse.screenshots", it) }
    systemProperty("puck.dll", layout.projectDirectory.file("native-resources/windows/puck-ffi-windows-x64.dll").asFile.absolutePath)
    testLogging { events("passed", "failed", "skipped") }
}
compose.desktop {
    application {
        mainClass = "dev.puckmouse.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "PuckMouse"
            packageVersion = "0.1.1"
            description = "Configure a SpaceMouse for everyday pointing and scrolling"
            vendor = "Puck Mouse contributors"
            modules("java.desktop", "jdk.unsupported")
            appResourcesRootDir.set(layout.projectDirectory.dir("native-resources"))
            windows { menuGroup = "Puck Mouse"; shortcut = true; upgradeUuid = "67848e5c-cc35-4c47-9431-a68ac2f6f792" }
        }
    }
}
