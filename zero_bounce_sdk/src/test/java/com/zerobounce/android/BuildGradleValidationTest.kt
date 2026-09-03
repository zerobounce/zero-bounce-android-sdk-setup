package com.zerobounce.android

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Validates that PUBLISH_VERSION is only used for the SDK version and not copy-pasted
 * onto plugin/dependency versions (same pattern as Java SDK PomValidationTest).
 * Fails during unit test run so issues are caught locally and in CI.
 *
 * A plugin or dependency can legitimately share a version number with the SDK by
 * coincidence — v2.2.0 collided with Dokka 2.2.0 and mockito-kotlin 2.2.0, which
 * failed the release even though nothing had been copy-pasted. Those coordinates are
 * listed in [COORDINATES_ALLOWED_TO_MATCH] so the check still catches real mistakes.
 * Adding an entry is deliberate: confirm the version is genuinely that dependency's
 * own version and not the SDK version pasted onto it.
 */
class BuildGradleValidationTest {

    private companion object {
        val COORDINATES_ALLOWED_TO_MATCH = listOf(
            "org.jetbrains.dokka",
            "org.jetbrains.dokka-javadoc",
            "com.nhaarman.mockitokotlin2:mockito-kotlin",
        )
    }

    @Test
    fun publishVersionMustNotAppearAsOtherVersionInBuildGradle() {
        val buildGradle = findBuildGradle()
        assertTrue("build.gradle not found at ${buildGradle.absolutePath}", buildGradle.isFile)

        val content = buildGradle.readText()
        val versionRegex = Regex("PUBLISH_VERSION\\s*=\\s*'([0-9]+\\.[0-9]+\\.[0-9]+)'")
        val match = versionRegex.find(content)
            ?: throw AssertionError("PUBLISH_VERSION not found in build.gradle")

        val publishVersion = match.groupValues[1]

        // Check line by line so a violation can be reported with the line that caused it,
        // and so an allowlisted coordinate only exempts its own line.
        val offenders = content.lineSequence()
            .withIndex()
            .filter { (_, line) -> line.contains(publishVersion) }
            .filterNot { (_, line) -> versionRegex.containsMatchIn(line) }
            .filterNot { (_, line) ->
                COORDINATES_ALLOWED_TO_MATCH.any { line.contains(it) }
            }
            .map { (index, line) -> "  line ${index + 1}: ${line.trim()}" }
            .toList()

        assertTrue(
            "PUBLISH_VERSION ($publishVersion) must not appear elsewhere in build.gradle " +
                "(copy-paste causes wrong plugin/dep versions). Only bump PUBLISH_VERSION in ext {}. " +
                "If one of these is genuinely that dependency's own version, add its coordinate to " +
                "COORDINATES_ALLOWED_TO_MATCH in this test.\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
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
