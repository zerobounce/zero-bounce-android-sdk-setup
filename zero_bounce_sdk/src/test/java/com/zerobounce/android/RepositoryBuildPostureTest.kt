package com.zerobounce.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Repository only: guards the build-file and documentation fixes of audit 2 (skipped from a copy). */
class RepositoryBuildPostureTest {

    private val root: File? = run {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null && !File(dir, "settings.gradle").isFile) dir = dir.parentFile
        dir
    }

    private fun text(path: String): String {
        assumeTrue("repository root not found", root != null)
        val file = File(root, path)
        assumeTrue("$path not found", file.isFile)
        return file.readText()
    }

    @Test
    fun wholeBouncyCastleFamilyIsForcedToTheSamePatchedVersion() {
        val gradle = text("build.gradle")
        for (name in listOf("bcprov", "bcpkix", "bcutil")) {
            assertTrue(name, gradle.contains("org.bouncycastle:$name-jdk18on:1.87"))
        }
    }

    @Test
    fun jacksonIsPatchedOnBothPluginClasspaths() {
        for (path in listOf("build.gradle", "zero_bounce_sdk/build.gradle")) {
            val gradle = text(path)
            assertTrue(path, gradle.contains("startsWith('com.fasterxml.jackson')"))
            assertTrue(path, gradle.contains("'2.22.4'"))
        }
    }

    @Test
    fun buildFilesUseAssignmentSyntaxAndNoProjectNotation() {
        assertFalse(text("build.gradle").contains("url \""))
        assertFalse(text("app/build.gradle").contains("namespace '"))
        assertFalse(text("zero_bounce_sdk/build.gradle").contains("namespace '"))
        assertFalse(text("app/build.gradle").contains("implementation project("))
        val publish = text("scripts/publish-module.gradle")
        assertFalse(Regex("(?m)^\\s*(groupId|artifactId|version) PUBLISH").containsMatchIn(publish))
    }

    @Test
    fun appDoesNotPullInUnneededMultidex() {
        val gradle = text("app/build.gradle")
        assertFalse(gradle.contains("multidex", ignoreCase = true))
        assertFalse(gradle.contains("multiDexEnabled"))
    }

    @Test
    fun docsUseCurrentMethodsAndStateTheKotlinFloor() {
        for (path in listOf("README.md", "documentation.md")) {
            val doc = text(path)
            val beforeMigration = doc.substringBefore("## Breaking Changes")
            assertFalse(path, beforeMigration.contains("ZeroBounceSDK.guessFormat("))
        }
        assertTrue(text("README.md").contains("Kotlin 2.2 or newer"))
    }

    @Test
    fun changelogCoversWhatIsOnMavenCentral() {
        val changelog = text("CHANGELOG.md")
        for (version in listOf("2.1.4", "2.1.3", "2.1.0", "2.0.21", "2.0.18")) {
            assertTrue(version, changelog.contains("## [$version] - "))
        }
        assertTrue(changelog.contains("## [2.0.17] - 2026-03-03 (tagged, never published)"))
        assertTrue(changelog.contains("## [1.5.1] - 2026-02-18 (tagged, never published)"))
    }
}
