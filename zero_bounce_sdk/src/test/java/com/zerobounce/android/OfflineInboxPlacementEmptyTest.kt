package com.zerobounce.android

import android.content.Context
import com.nhaarman.mockitokotlin2.mock
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * An empty 2xx from the Inbox Placement Tester: a success with a null result on
 * DELETE (the API answers 204 No Content there), and an error carrying its status
 * on every other call. The methods are every public request method of
 * [ZeroBounceClient] whose name mentions InboxPlacement, found by reflection.
 */
class OfflineInboxPlacementEmptyTest {

    private val context: Context = mock()
    private lateinit var dir: File
    private lateinit var csv: File

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "zb-android-ipt-${System.nanoTime()}")
        dir.mkdirs()
        csv = File(dir, "in.csv").apply { writeText("email\na@example.com\n") }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun call(method: java.lang.reflect.Method, status: Int, contentType: String, body: String): RequestSweep.Outcome {
        val client = ZeroBounceClient()
        client.transportInterceptor = Interceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("Status")
                .body(body.toResponseBody(contentType.toMediaType())).build()
        }
        client.initialize("ipt-key")
        return RequestSweep(context, dir, csv).invoke(client, method)
    }

    private val methods = RequestSweep.requestMethods(ZeroBounceClient::class.java)
        .filter { it.name.contains("InboxPlacement") }

    @Test
    fun anEmpty2xxIsASuccessOnDeleteOnlyAndAnErrorWithItsStatusElsewhere() {
        assertTrue("found ${methods.size} Inbox Placement methods", methods.size >= 8)
        val failures = mutableListOf<String>()
        for (method in methods) {
            val delete = method.name.startsWith("delete")
            for (status in listOf(200, 202, 204)) {
                val outcome = call(method, status, "application/json", "")
                val label = "$status $outcome"
                if (outcome.thrown != null || outcome.timedOut) {
                    failures.add(label)
                } else if (delete) {
                    if (outcome.successes.size != 1 || outcome.successes[0] != null || outcome.errors.isNotEmpty()) {
                        failures.add("expected a null success: $label")
                    }
                } else if (outcome.successes.isNotEmpty() || outcome.errors.size != 1 ||
                    outcome.errors[0]?.statusCode != status ||
                    !outcome.errors[0]?.errors.toString().contains("Empty response")
                ) {
                    failures.add("expected an error with status $status: $label")
                }
            }
            // An empty non-2xx is an error with its status everywhere, DELETE included.
            for (status in listOf(404, 500)) {
                val outcome = call(method, status, "application/json", "")
                if (outcome.successes.isNotEmpty() || outcome.errors.size != 1 ||
                    outcome.errors[0]?.statusCode != status
                ) {
                    failures.add("expected an error with status $status: $outcome")
                }
            }
        }
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun aDeleteWithABodyIsStillReadAsJson() {
        val delete = methods.first { it.name == "deleteInboxPlacementTest" }
        val ok = call(delete, 200, "application/json", "{\"deleted\": true}")
        assertEquals(ok.toString(), "{\"deleted\":true}", ok.successes.single().toString())
        val html = call(delete, 200, "text/html", "<html><title>Denied</title></html>")
        assertEquals(html.toString(), 200, html.errors.single()?.statusCode)
    }
}
