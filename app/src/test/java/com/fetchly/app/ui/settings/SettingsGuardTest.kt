package com.fetchly.app.ui.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The design's hard rule: Fetchly resolves and downloads on-device and has no
 * backend of any kind. Settings is where a backend would sneak in, so this
 * test fails the build if its sources ever grow a URL, an endpoint, or an API
 * key — field or otherwise.
 */
class SettingsGuardTest {

    private val banned = listOf(
        "http://",
        "https://",
        "endpoint",
        "api key",
        "apikey",
        "base url",
        "server url",
        "server_url",
        "authorization",
    )

    @Test
    fun `the settings sources offer nothing to point at a server`() {
        val directory = sourceDirectory("src/main/java/com/fetchly/app/ui/settings")
        val files = directory.listFiles { file -> file.extension == "kt" }
        checkNotNull(files) { "could not list ${directory.absolutePath}" }
        assertTrue("no settings sources found in ${directory.absolutePath}", files.isNotEmpty())

        files.forEach { file ->
            val text = file.readText().lowercase()
            banned.forEach { word ->
                assertFalse(
                    "${file.name} must never mention \"$word\" (Settings configures nothing remote)",
                    text.contains(word),
                )
            }
        }
    }

    /** Unit tests may run from the module dir or the repo root; try both. */
    private fun sourceDirectory(relative: String): File =
        listOf(File(relative), File("app/$relative"))
            .firstOrNull { it.isDirectory }
            ?: error("could not find directory $relative in ${File(".").absolutePath}")
}
