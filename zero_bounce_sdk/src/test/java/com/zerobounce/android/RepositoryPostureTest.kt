package com.zerobounce.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Repository only: guards the build, CI and documentation posture that the audit fixed. Skipped
 * when the repository root cannot be found (for example when the tests run from a copy).
 */
class RepositoryPostureTest {

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
    fun testDependenciesAreMaintained() {
        val gradle = text("zero_bounce_sdk/build.gradle")
        assertFalse(gradle.contains("powermock"))
        assertFalse(gradle.contains("com.nhaarman"))
        assertTrue(gradle.contains("org.mockito.kotlin:mockito-kotlin"))
    }

    @Test
    fun dokkaGeneratorDependenciesArePatched() {
        val gradle = text("zero_bounce_sdk/build.gradle")
        for (coordinate in listOf(
            "com.fasterxml.jackson.core:jackson-databind",
            "org.freemarker:freemarker",
            "org.jsoup:jsoup",
        )) {
            assertTrue("no Dokka override for $coordinate", gradle.contains("'$coordinate'"))
        }
    }

    @Test
    fun buildClasspathLibrariesArePatched() {
        val gradle = text("build.gradle")
        for (coordinate in listOf("bcprov-jdk18on", "jdom2", "jose4j", "guava", "commons-lang3")) {
            assertTrue("no build classpath override for $coordinate", gradle.contains(coordinate))
        }
    }

    @Test
    fun deprecatedGradlePropertiesAreGone() {
        val props = text("gradle.properties")
        assertFalse(props.contains("android.enableJetifier"))
        assertFalse(props.contains("android.nonFinalResIds"))
    }

    @Test
    fun contributingNamesTheDefaultBranch() {
        val text = text("CONTRIBUTING.md")
        assertFalse(text.contains("`main`"))
        assertTrue(text.contains("`master`"))
    }

    @Test
    fun blankLocalPropertiesValuesCountAsAbsent() {
        val script = text("scripts/publish-root.gradle")
        assertTrue(script.contains("trim()"))
    }

    @Test
    fun dockerImageRunsAsAUserThatCanWrite() {
        val docker = text("Dockerfile")
        assertTrue(docker.contains("COPY --chown=65532:65532"))
        assertTrue(docker.contains("GRADLE_USER_HOME"))
    }

    @Test
    fun workflowsStayPinnedAndSafe() {
        for (name in listOf("codeql.yml", "sdk_ci.yml", "publish.yml")) {
            val workflow = text(".github/workflows/$name")
            for (line in workflow.lineSequence().filter { it.trim().startsWith("uses:") || it.contains("- uses:") }) {
                assertTrue("$name: $line is not SHA-pinned", Regex("@[0-9a-f]{40}\\b").containsMatchIn(line))
            }
            assertFalse("$name: stale v6 comment", Regex("# v6\\s*$", RegexOption.MULTILINE).containsMatchIn(workflow))
            assertFalse("$name: input expanded in a script", workflow.contains("TAG=\"\${{ github.event.inputs.tag }}\""))
        }
        val codeql = text(".github/workflows/codeql.yml")
        assertFalse(codeql.contains("android-36"))
        assertEquals("one top-level permissions block", 1, Regex("^permissions:", RegexOption.MULTILINE).findAll(codeql).count())
        assertTrue(text(".github/workflows/sdk_ci.yml").contains("lintDebug"))
    }

    @Test
    fun changelogDoesNotClaimUsersHad301() {
        val changelog = text("CHANGELOG.md")
        assertTrue(changelog.contains("[3.0.1] - 2026-11-11 (tagged, never published)"))
        assertFalse(Regex("(Up to|In) 3\\.0\\.1\\b").containsMatchIn(changelog))
    }
}
