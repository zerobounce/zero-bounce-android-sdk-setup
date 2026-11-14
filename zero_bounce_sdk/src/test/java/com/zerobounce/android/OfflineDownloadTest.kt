package com.zerobounce.android

import android.content.Context
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Download targets, JSON-bodied "files" and Content-Disposition names, against a loopback server. */
class OfflineDownloadTest {

    private val context: Context = mock()
    private lateinit var dir: File

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "zb-android-download-${System.nanoTime()}")
        dir.mkdirs()
        whenever(context.getExternalFilesDir(null)).thenReturn(dir)
    }

    @After
    fun tearDown() {
        dir.setWritable(true)
        dir.deleteRecursively()
    }

    private class Result {
        var path: String? = null
        var error: ErrorResponse? = null
        val done = CountDownLatch(1)
    }

    private fun <T> withServer(
        respond: (LoopbackServer.Request, OutputStream) -> Unit,
        block: (LoopbackServer, ZeroBounceClient) -> T,
    ): T = LoopbackServer(respond).use { server ->
        val client = ZeroBounceClient()
        client.transportInterceptor = server.reroute
        client.initialize("download-key")
        block(server, client)
    }

    private fun finder(client: ZeroBounceClient, path: String): Result {
        val r = Result()
        client.emailFinderGetFile("fid", path, { r.path = it.localFilePath; r.done.countDown() },
            { r.error = it; r.done.countDown() })
        assertTrue("a callback ran", r.done.await(10, TimeUnit.SECONDS))
        return r
    }

    private fun getFile(client: ZeroBounceClient): Result {
        val r = Result()
        client.getFile(context, "fid", { r.path = it.localFilePath; r.done.countDown() },
            { r.error = it; r.done.countDown() })
        assertTrue("a callback ran", r.done.await(10, TimeUnit.SECONDS))
        return r
    }

    private val csv = "email,status\na@example.com,valid\n"

    @Test
    fun aJsonBodyIsNeverSavedAsAFile() {
        val bodies = listOf(
            "text/csv" to "﻿{\"error\":\"bad key\"}",
            "text/csv" to "[{\"error\":\"x\"}]",
            "text/csv" to "{\"status\":\"fail\"}",
            "application/problem+json" to "email,status\na@b.c,valid\n",
            "application/vnd.api+json; charset=utf-8" to "email,status\na@b.c,valid\n",
            "text/csv" to "﻿[1,2]",
        )
        for ((type, body) in bodies) {
            val target = File(dir, "out-${System.nanoTime()}.csv")
            withServer(LoopbackServer.answer(200, type, body.toByteArray())) { _, client ->
                val r = finder(client, target.path)
                assertNull("$type $body", r.path)
                assertNotNull("$type $body", r.error)
                assertEquals(200, r.error!!.statusCode)
                assertFalse("$type $body wrote a file", target.exists())
                val g = getFile(client)
                assertNull("getFile $type $body", g.path)
                assertNotNull(g.error)
            }
        }
    }

    @Test
    fun aRealCsvIsStillSaved() {
        val target = File(dir, "ok.csv")
        withServer(LoopbackServer.answer(200, "text/csv", csv.toByteArray())) { _, client ->
            val r = finder(client, target.path)
            assertEquals(target.path, r.path)
            assertEquals(csv, target.readText())
        }
    }

    @Test
    fun aBadTargetIsRefusedBeforeAnyRequestIsSent() {
        val emptyDir = File(dir, "empty").apply { mkdirs() }
        val full = File(dir, "full").apply { mkdirs() }
        File(full, "x").writeText("x")
        for (path in listOf("", "   ", "${dir.path}/newdir/", emptyDir.path, full.path)) {
            withServer(LoopbackServer.answer(200, "text/csv", csv.toByteArray())) { server, client ->
                val r = finder(client, path)
                assertNull(path, r.path)
                assertNotNull("'$path'", r.error)
                assertTrue("'$path' sent a request", server.requests.isEmpty())
            }
        }
        assertTrue("an empty directory is still a directory", emptyDir.isDirectory)
        assertFalse(File(dir, "newdir").exists())
        assertTrue(File(full, "x").exists())
    }

    @Test
    fun aSymlinkTargetIsWrittenThrough() {
        val real = File(dir, "real.csv").apply { writeText("old") }
        val link = File(dir, "link.csv")
        Files.createSymbolicLink(link.toPath(), real.toPath())
        withServer(LoopbackServer.answer(200, "text/csv", csv.toByteArray())) { _, client ->
            val r = finder(client, link.path)
            assertEquals(link.path, r.path)
            assertTrue("the link stays a link", Files.isSymbolicLink(link.toPath()))
            assertEquals(csv, real.readText())
        }
    }

    @Test
    fun anExistingFileKeepsItsPermissions() {
        val target = File(dir, "perm.csv").apply { writeText("old") }
        val owner = PosixFilePermissions.fromString("rw-------")
        Files.setPosixFilePermissions(target.toPath(), owner)
        withServer(LoopbackServer.answer(200, "text/csv", csv.toByteArray())) { _, client ->
            finder(client, target.path)
            assertEquals(owner, Files.getPosixFilePermissions(target.toPath()))
            assertEquals(csv, target.readText())
        }
        val wide = PosixFilePermissions.fromString("rw-r--r--")
        Files.setPosixFilePermissions(target.toPath(), wide)
        withServer(LoopbackServer.answer(200, "text/csv", csv.toByteArray())) { _, client ->
            finder(client, target.path)
            assertEquals(wide, Files.getPosixFilePermissions(target.toPath()))
        }
    }

    @Test
    fun aReadOnlyDirectoryGivesAClearError() {
        val ro = File(dir, "ro").apply { mkdirs() }
        ro.setWritable(false)
        try {
            if (ro.canWrite()) return // running as root: nothing to prove
            val target = File(ro, "out.csv")
            withServer(LoopbackServer.answer(200, "text/csv", csv.toByteArray())) { _, client ->
                val r = finder(client, target.path)
                assertEquals(0, r.error!!.statusCode)
                assertEquals(listOf("Cannot write ${target.path} (permission denied)"), r.error!!.errors)
            }
        } finally {
            ro.setWritable(true)
        }
    }

    private fun disposition(value: String): (LoopbackServer.Request, OutputStream) -> Unit = { _, out ->
        val body = csv.toByteArray()
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/csv\r\nContent-Disposition: $value\r\n" +
            "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray())
        out.write(body)
    }

    @Test
    fun contentDispositionNamesAreParsed() {
        val cases = mapOf(
            "attachment; filename=\"results.csv\"" to "results.csv",
            "attachment; filename=results.csv" to "results.csv",
            "attachment; filename*=UTF-8''r%C3%A9sultats%20v1.csv" to "résultats v1.csv",
            "attachment; filename=\"fallback.csv\"; filename*=UTF-8''real.csv" to "real.csv",
            "attachment; filename=\"../../evil.csv\"" to "evil.csv",
            "attachment; filename=\"a\\b\\c.csv\"" to "c.csv",
        )
        for ((header, expected) in cases) {
            withServer(disposition(header)) { _, client ->
                val r = getFile(client)
                assertEquals(header, File(dir, expected).path, r.path)
                assertTrue(header, File(dir, expected).isFile)
                File(dir, expected).delete()
            }
        }
    }

    @Test
    fun aDirectoryIsNotAnUploadFile() {
        val sub = File(dir, "sub").apply { mkdirs() }
        withServer(LoopbackServer.answer(200, "application/json", "{}".toByteArray())) { server, client ->
            val r = Result()
            client.sendEmailFinderFile(sub, 1, null, { r.done.countDown() }, { r.error = it; r.done.countDown() })
            assertTrue(r.done.await(10, TimeUnit.SECONDS))
            assertNotNull(r.error)
            assertTrue(server.requests.isEmpty())
        }
    }
}
