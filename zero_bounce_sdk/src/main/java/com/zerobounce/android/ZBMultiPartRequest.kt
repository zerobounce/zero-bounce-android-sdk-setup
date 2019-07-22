package com.zerobounce.android

import android.util.Log
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.VolleyLog
import org.apache.http.HttpEntity
import org.apache.http.entity.mime.MultipartEntityBuilder
import java.io.ByteArrayOutputStream
import java.io.IOException

internal class ZBMultiPartRequest(
    url: String, private val builder: MultipartEntityBuilder,
    private val listener: Response.Listener<String>,
    errorListener: Response.ErrorListener,
    private val logEnabled: Boolean = false
) : Request<String>(Method.POST, url, errorListener) {

    init {
        buildMultipartEntity()
    }

    private lateinit var entity: HttpEntity

    private fun buildMultipartEntity() {
        entity = builder.build()
    }

    override fun getBodyContentType(): String {
        return entity.contentType.value
    }

    override fun getBody(): ByteArray {
        val bos = ByteArrayOutputStream()
        try {
            entity.writeTo(bos)
        } catch (e: IOException) {
            VolleyLog.e("IOException writing to ByteArrayOutputStream " + e.message)
        }
        return bos.toByteArray()
    }


    override fun parseNetworkResponse(response: NetworkResponse): Response<String> {
        val output = String(response.data, Charsets.UTF_8)
        if(logEnabled) Log.d(
            "MultipartRequest",
            "parseNetworkResponse response=$output"
        )
        return Response.success(output, cacheEntry)
    }

    override fun deliverResponse(response: String) {
        listener.onResponse(response)
    }
}