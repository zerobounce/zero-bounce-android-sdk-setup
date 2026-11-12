package com.zerobounce.android

import okhttp3.Interceptor
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch

/**
 * A real HTTP/1.1 server on a loopback socket that answers every request with raw
 * bytes the test chooses: a body cut short of its Content-Length, a redirect with a
 * Location, or nothing at all. [reroute] is an application interceptor that sends
 * the SDK's real requests here over plain http, so the real OkHttp client (its
 * redirect policy, timeouts and body reads) is what gets tested.
 */
internal class LoopbackServer(private val respond: (Request, OutputStream) -> Unit) : AutoCloseable {

    class Request(val method: String, val target: String, val headers: Map<String, String>)

    private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    private val sockets = Collections.synchronizedList(mutableListOf<Socket>())
    private val closed = CountDownLatch(1)
    val requests = CopyOnWriteArrayList<Request>()

    val port: Int get() = server.localPort

    init {
        Thread({
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (_: IOException) {
                    break
                }
                sockets.add(socket)
                Thread({ handle(socket) }, "loopback-conn").apply { isDaemon = true }.start()
            }
        }, "loopback-accept").apply { isDaemon = true }.start()
    }

    private fun handle(socket: Socket) {
        try {
            socket.use {
                val input = it.getInputStream()
                val request = readRequest(input) ?: return
                requests.add(request)
                val out = it.getOutputStream()
                respond(request, out)
                out.flush()
                it.shutdownOutput()
            }
        } catch (_: IOException) {
            // The client gave up or the test closed the server.
        }
    }

    /** Reads and discards the request, until the server is closed. */
    fun waitForClose() {
        closed.await()
    }

    override fun close() {
        closed.countDown()
        server.close()
        synchronized(sockets) { sockets.forEach { runCatching { it.close() } } }
    }

    /** Sends every request of the client it is installed on to this server, over http. */
    val reroute = Interceptor { chain ->
        val original = chain.request()
        val url = if (original.url.host == "127.0.0.1") {
            original.url
        } else {
            original.url.newBuilder().scheme("http").host("127.0.0.1").port(port).build()
        }
        chain.proceed(original.newBuilder().url(url).build())
    }

    companion object {
        private fun readLine(input: InputStream): String? {
            val line = ByteArrayOutputStream()
            var b = input.read()
            if (b < 0) return null
            while (b >= 0 && b != '\n'.code) {
                if (b != '\r'.code) line.write(b)
                b = input.read()
            }
            return line.toString(Charsets.ISO_8859_1.name())
        }

        private fun readRequest(input: InputStream): Request? {
            val first = readLine(input)
            if (first.isNullOrEmpty()) return null
            val parts = first.split(" ")
            val headers = linkedMapOf<String, String>()
            while (true) {
                val line = readLine(input)
                if (line.isNullOrEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }
            // Read the whole request body first, so an upload is never cut off.
            val length = headers["content-length"]
            if (length != null) {
                input.readNBytesCompat(length.toInt())
            } else if (headers["transfer-encoding"].equals("chunked", ignoreCase = true)) {
                while (true) {
                    val size = readLine(input) ?: break
                    val chunk = size.substringBefore(';').trim().toInt(16)
                    if (chunk == 0) {
                        while (!readLine(input).isNullOrEmpty()) Unit
                        break
                    }
                    input.readNBytesCompat(chunk)
                    readLine(input)
                }
            }
            return Request(parts[0], parts.getOrElse(1) { "" }, headers)
        }

        private fun InputStream.readNBytesCompat(count: Int) {
            var left = count
            val buffer = ByteArray(8192)
            while (left > 0) {
                val n = read(buffer, 0, minOf(left, buffer.size))
                if (n < 0) break
                left -= n
            }
        }

        private fun head(status: Int, headers: Map<String, String>): ByteArray {
            val text = StringBuilder("HTTP/1.1 $status Status\r\n")
            headers.forEach { (name, value) -> text.append("$name: $value\r\n") }
            text.append("Connection: close\r\n\r\n")
            return text.toString().toByteArray(Charsets.ISO_8859_1)
        }

        /** A complete response. */
        fun answer(status: Int, contentType: String, body: ByteArray): (Request, OutputStream) -> Unit = { _, out ->
            out.write(head(status, mapOf("Content-Type" to contentType, "Content-Length" to "${body.size}")))
            out.write(body)
        }

        /** A 200 whose header declares [declared] bytes, then [body] and a closed connection. */
        fun cutShort(contentType: String, body: ByteArray, declared: Int): (Request, OutputStream) -> Unit = { _, out ->
            out.write(head(200, mapOf("Content-Type" to contentType, "Content-Length" to "$declared")))
            out.write(body)
        }

        /** A redirect with a Location header, so a client that follows redirects would go there. */
        fun redirect(status: Int, location: String): (Request, OutputStream) -> Unit = { _, out ->
            out.write(head(status, mapOf("Location" to location, "Content-Length" to "0")))
        }
    }
}
