package com.zerobounceexample

import android.annotation.TargetApi
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.support.v7.app.AppCompatActivity;
import android.util.Log
import com.zerobounce.android.ZeroBounceSDK

import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.util.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        ZeroBounceSDK.initialize(this.applicationContext, "<YOUR_API_KEY>")

        if (shouldAskPermissions()) {
            askPermissions()
        }

        fab.setOnClickListener { view ->

            //validate()

            getCredits()

            //getApiUsage()

            //sendFile()

            //fileStatus("<YOUR_FILE_ID>")

            //getFile("<YOUR_FILE_ID>")

            //deleteZBFile("<YOUR_FILE_ID>")
        }
    }

    fun validate() {
        ZeroBounceSDK.validate("<EMAIL_TO_TEST>", null,
            { rsp ->
                Log.d("MainActivity", "validate rsp: $rsp")
                // your implementation
            },
            { error ->
                Log.e("MainActivity", "validate error: $error")
                // your implementation
            })
    }


    fun getCredits() {
        ZeroBounceSDK.getCredits(
            { rsp ->
                Log.d("MainActivity", "getCredits rsp: $rsp")
                // your implementation
            },
            { error ->
                Log.e("MainActivity", "getCredits error: $error")
                // your implementation
            })
    }

    fun getApiUsage() {
        val startDate = Date()
        startDate.time -= 5 * 24 * 60 * 60 * 1000 // previous 5 days
        val endDate = Date()
        ZeroBounceSDK.getApiUsage(startDate, endDate,
            { rsp -> Log.d("MainActivity", "getApiUsage rsp: $rsp")},
            { error -> Log.e("MainActivity", "getApiUsage error: $error") })
    }

    fun sendFile() {
        val file = File(Environment.getExternalStorageDirectory().path + "/email_file.csv")
        Log.d("MainActivity", "sendFile " + file.path + ", exists=" + file.exists())

        ZeroBounceSDK.sendFile(this.applicationContext, file, 1,
            { rsp ->
                Log.d("MainActivity", "sendFile rsp: $rsp")
                rsp.fileId?.let { fileStatus(it) }
            },
            { error -> Log.e("MainActivity", "sendFile error: $error") },
            null, 2, 3,
            null, null, true
        )
    }

    fun fileStatus(fileId: String) {
        ZeroBounceSDK.fileStatus(fileId, { rsp ->
            Log.d("MainActivity", "fileStatus rsp: $rsp")
        }, { error ->
            Log.e("MainActivity", "fileStatus error: $error")
        })
    }

    fun deleteZBFile(fileId: String) {
        ZeroBounceSDK.deleteFile(fileId, { response ->
            Log.d("MainActivity", "deleteZBFile success: $response")
        }, { error ->
            Log.e("MainActivity", "deleteZBFile error: $error")
        })
    }

    fun getFile(fileId: String) {
        ZeroBounceSDK.getFile(applicationContext, fileId, { rsp ->
            Log.d("MainActivity", "getFile rsp: $rsp, localFilePath=${rsp.localFilePath}")
        }, { error ->
            Log.e("MainActivity", "getFile error: $error")
        })
    }

    protected fun shouldAskPermissions(): Boolean {
        return (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1)
    }

    @TargetApi(23)
    protected fun askPermissions() {
        val permissions = arrayOf(
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE"
        )

        val requestCode = 200
        requestPermissions(permissions, requestCode)
    }

}
