package dev.puckmouse

internal object AppVersion {
    val value: String = checkNotNull(AppVersion::class.java.getResourceAsStream("/puckmouse-version.txt")) {
        "The application version is missing from this build"
    }.bufferedReader().use { it.readText().trim() }.also { check(it.isNotEmpty()) }
}
