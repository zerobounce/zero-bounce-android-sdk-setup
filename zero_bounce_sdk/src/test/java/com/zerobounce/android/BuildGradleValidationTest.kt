package com.zerobounce.android

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates that PUBLISH_VERSION is only used for the SDK version and not copy-pasted
 * onto plugin/dependency versions (same pattern as Java SDK PomValidationTest).
 * Fails during unit test run so issues are caught locally and in CI.
 */
class BuildGradleValidationTest {

    @Test
    fun publishVersionMustNotAppearAsOtherVersionInBuildGradle() {
        val buildGradle = findBuildGradle()
        assertTrue("build.gradle not found at ${buildGradle.absolutePath}", buildGradle.isFile)

        val content = buildGradle.readText()
        val versionRegex = Regex("PUBLISH_VERSION\\s*=\\s*'([0-9]+\\.[0-9]+\\.[0-9]+)'")
        val match = versionRegex.find(content)
            ?: throw AssertionError("PUBLISH_VERSION not found in build.gradle")

        val publishVersion = match.groupValues[1]
        // Remove the defining line; rest of file must not contain this version as a standalone version (copy-paste mistake)
        val withoutDefiningLine = content.replaceFirst(match.value, "")
        val mistaken = publishVersion.toRegex().findAll(withoutDefiningLine).count()

        assertTrue(
            "PUBLISH_VERSION ($publishVersion) must not appear elsewhere in build.gradle (copy-paste causes wrong plugin/dep versions). Found $mistaken other occurrence(s). Only bump PUBLISH_VERSION in ext {}.",
            mistaken == 0
        )
    }

    private fun findBuildGradle(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        for (i in 0..5) {
            // Prefer module build.gradle (has PUBLISH_VERSION); root build.gradle does not
            val inModule = File(dir, "zero_bounce_sdk/build.gradle")
            if (inModule.isFile) return inModule
            val candidate = File(dir, "build.gradle")
            if (candidate.isFile && (candidate.readText().contains("PUBLISH_VERSION"))) return candidate
            dir = dir.parentFile ?: break
        }
        return File(System.getProperty("user.dir") ?: ".", "zero_bounce_sdk/build.gradle")
    }
}
