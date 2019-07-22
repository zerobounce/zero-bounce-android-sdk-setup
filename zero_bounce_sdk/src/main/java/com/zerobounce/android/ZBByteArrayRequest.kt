package com.zerobounce.android

import android.util.Log
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.HttpHeaderParser

internal class ZBByteArrayRequest(
    method: Int,
    url: String,
    private val mListener: Response.Listener<FileResponse>?,
    errorListener: Response.ErrorListener,
    private val logEnabled: Boolean = false
) : Request<ZBByteArrayRequest.FileResponse>(method, url, errorListener) {

    internal class FileResponse(val data: ByteArray, val fileName: String?)

    override fun parseNetworkResponse(response: NetworkResponse): Response<FileResponse> {
        var fileName: String? = null
        val header = response.headers["Content-Disposition"]
        if (header != null) {
            val parts = header.split("filename=")
            if (parts.count() > 1) {
                fileName = parts[1]
            }
        }
        if(logEnabled) Log.d(
            "ByteArrayRequest",
            "fileName=$fileName"
        )

        val rsp = FileResponse(response.data, fileName)

        return Response.success(
            rsp,
            HttpHeaderParser.parseCacheHeaders(response)
        )
    }

    override fun deliverResponse(response: FileResponse) {
        mListener?.onResponse(response)
    }

    override fun getBodyContentType(): String {
        return "application/octet-stream"
    }
}