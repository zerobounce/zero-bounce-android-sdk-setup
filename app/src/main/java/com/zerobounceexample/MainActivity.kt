package com.zerobounceexample

import android.annotation.TargetApi
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.zerobounce.android.ZBValidateBatchData
import com.zerobounce.android.ZeroBounceSDK
import java.io.File
import java.util.*

class MainActivity : AppCompatActivity(), View.OnClickListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        ZeroBounceSDK.initialize("<YOUR_API_KEY>")

        if (shouldAskPermissions()) {
            askPermissions()
        }

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.fab -> {
//                validate("<EMAIL_TO_TEST>")

//                validateBatch()

                getCredits()

//                getApiUsage()

//                sendFile()

//                fileStatus("<YOUR_FILE_ID>")

//                getFile("<YOUR_FILE_ID>")

//                deleteZBFile("<YOUR_FILE_ID>")

//                getActivityData("<EMAIL_TO_TEST>")
            }
        }
    }

    private fun shouldAskPermissions() = Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1

    @TargetApi(23)
    private fun askPermissions() {
        val permissions = arrayOf(
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        val requestCode = 200
        requestPermissions(permissions, requestCode)
    }

    /**
     * Calls the *validate* method of the [ZeroBounceSDK].
     *
     * @param email the email address you want to validate
     */
    fun validate(email: String) {
        ZeroBounceSDK.validate(
            email,
            null,
            { rsp ->
                Log.d("MainActivity", "validate rsp: $rsp")
                // your implementation
            },
            { error ->
                Log.e("MainActivity", "validate error: $error")
                // your implementation
            }
        )
    }

    /**
     * Calls the *validate batch* method of the [ZeroBounceSDK].
     */
    fun validateBatch() {
        val emailsData = listOf(
            ZBValidateBatchData(email = "valid@example.com", ip = "1.1.1.1"),
            ZBValidateBatchData(email = "invalid@example.com", ip = "1.1.1.1"),
            ZBValidateBatchData(email = "disposable@example.com", ip = null)
        )
        ZeroBounceSDK.validateBatch(
            emailsData,
            { rsp ->
                Log.d("MainActivity", "validateBatch rsp: $rsp")
                // your implementation
            },
            { error ->
                Log.e("MainActivity", "validateBatch error: $error")
                // your implementation
            }
        )
    }

    /**
     * Calls the *getCredits* method of the [ZeroBounceSDK].
     */
    fun getCredits() {
        ZeroBounceSDK.getCredits(
            { rsp ->
                Log.d("MainActivity", "getCredits rsp: $rsp")
                // your implementation
            },
            { error ->
                Log.e("MainActivity", "getCredits error: $error")
                // your implementation
            }
        )
    }

    /**
     * Calls the *getApiUsage* method of the [ZeroBounceSDK].
     */
    fun getApiUsage() {
        val startDate = Date()
        startDate.time -= 5 * 24 * 60 * 60 * 1000 // previous 5 days
        val endDate = Date()
        ZeroBounceSDK.getApiUsage(
            startDate,
            endDate,
            { rsp ->
                Log.d("MainActivity", "getApiUsage rsp: $rsp")
            },
            { error ->
                Log.e("MainActivity", "getApiUsage error: $error")
            }
        )
    }

    /**
     * Calls the *sendFile* method of the [ZeroBounceSDK].
     */
    fun sendFile() {
        val file = File(getExternalFilesDir(null), "/email_file.csv")
        Log.d("MainActivity", "sendFile " + file.path + ", exists=" + file.exists())

        ZeroBounceSDK.sendFile(
            context = applicationContext,
            file = file,
            emailAddressColumnIndex = 1,
            returnUrl = null,
            firstNameColumnIndex = 2,
            lastNameColumnIndex = 3,
            genderColumnIndex = null,
            ipAddressColumnIndex = null,
            hasHeaderRow = true,
            responseCallback = { rsp ->
                Log.d("MainActivity", "sendFile rsp: $rsp")
                rsp?.fileId?.let { fileStatus(it) }
            },
            errorCallback = { error ->
                Log.e("MainActivity", "sendFile error: $error")
            },
        )
    }

    /**
     * Calls the *fileStatus* method of the [ZeroBounceSDK].
     *
     * @param fileId the id of the file you wish to check the status of
     */
    fun fileStatus(fileId: String) {
        ZeroBounceSDK.fileStatus(
            fileId,
            { rsp ->
                Log.d("MainActivity", "fileStatus rsp: $rsp")
            }, { error ->
                Log.e("MainActivity", "fileStatus error: $error")
            }
        )
    }

    /**
     * Calls the *getFile* method of the [ZeroBounceSDK].
     *
     * @param fileId the if of the file you wish to download
     */
    fun getFile(fileId: String) {
        ZeroBounceSDK.getFile(
            applicationContext,
            fileId,
            { rsp ->
                Log.d("MainActivity", "getFile rsp: $rsp, localFilePath=${rsp.localFilePath}")
            }, { error ->
                Log.e("MainActivity", "getFile error: $error")
            }
        )
    }

    /**
     * Calls the *deleteFile* method of the [ZeroBounceSDK].
     *
     * @param fileId the id of the file you wish to delete
     */
    fun deleteZBFile(fileId: String) {
        ZeroBounceSDK.deleteFile(
            fileId,
            { response ->
                Log.d("MainActivity", "deleteZBFile success: $response")
            }, { error ->
                Log.e("MainActivity", "deleteZBFile error: $error")
            }
        )
    }

    /**
     * Calls the *getActivityData* method of the [ZeroBounceSDK].
     *
     * @param email the email address
     */
    fun getActivityData(email: String) {
        ZeroBounceSDK.getActivityData(
            email,
            { rsp ->
                Log.d("MainActivity", "getActivityData rsp: $rsp")
            }, { error ->
                Log.e("MainActivity", "getActivityData error: $error")
            }
        )
    }
}