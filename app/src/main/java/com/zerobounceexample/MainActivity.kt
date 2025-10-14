package com.zerobounceexample

import android.annotation.TargetApi
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.zerobounce.android.ZBException
import com.zerobounce.android.ZBValidateBatchData
import com.zerobounce.android.ZeroBounceSDK
import java.io.File
import java.util.Date

class MainActivity : AppCompatActivity(), View.OnClickListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))

        ZeroBounceSDK.initialize(apiKey = "<YOUR_API_KEY>")

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

//                guessFormat("<DOMAIN_TO_TEST>")

                getCredits()

//                getApiUsage()

//                sendFile()

//                fileStatus("<YOUR_FILE_ID>")

//                getFile("<YOUR_FILE_ID>")

//                deleteZBFile("<YOUR_FILE_ID>")

//                getActivityData("<EMAIL_TO_TEST>")

//                findEmail(firstName = "<FIRST_NAME_TO_TEST>", domain = "<DOMAIN_TO_TEST>")

/*                findEmail(
                    firstName = "<FIRST_NAME_TO_TEST>",
                    companyName = "<COMPANY_NAME_TO_TEST>"
                )*/

//                findDomain(domain = "<DOMAIN_TO_TEST>")

//                findDomain(companyName = "COMPANY_NAME_TO_TEST")
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
            email = email,
            ipAddress = null,
            responseCallback = { rsp ->
                Log.d("MainActivity", "validate rsp: $rsp")
                // your implementation
            },
            errorCallback = { error ->
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
            emails = emailsData,
            responseCallback = { rsp ->
                Log.d("MainActivity", "validateBatch rsp: $rsp")
                // your implementation
            },
            errorCallback = { error ->
                Log.e("MainActivity", "validateBatch error: $error")
                // your implementation
            }
        )
    }

    /**
     * Calls the *guessFormat* method of the [ZeroBounceSDK].
     *
     * @param domain the email domain for which to find the email format
     */
    fun guessFormat(domain: String) {
        ZeroBounceSDK.guessFormat(
            domain = domain,
            responseCallback = { rsp ->
                Log.d("MainActivity", "guessFormat rsp: $rsp")
                // your implementation
            },
            errorCallback = { error ->
                Log.e("MainActivity", "guessFormat error: $error")
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
            startDate = startDate,
            endDate = endDate,
            responseCallback = { rsp ->
                Log.d("MainActivity", "getApiUsage rsp: $rsp")
            },
            errorCallback = { error ->
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

        try {
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
        } catch (e: ZBException) {
            Log.e("MainActivity", "sendFile exception: $e")
        }
    }

    /**
     * Calls the *fileStatus* method of the [ZeroBounceSDK].
     *
     * @param fileId the id of the file you wish to check the status of
     */
    fun fileStatus(fileId: String) {
        ZeroBounceSDK.fileStatus(
            fileId = fileId,
            responseCallback = { rsp ->
                Log.d("MainActivity", "fileStatus rsp: $rsp")
            }, errorCallback = { error ->
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
            context = applicationContext,
            fileId = fileId,
            responseCallback = { rsp ->
                Log.d("MainActivity", "getFile rsp: $rsp, localFilePath=${rsp.localFilePath}")
            }, errorCallback = { error ->
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
            fileId = fileId,
            successCallback = { response ->
                Log.d("MainActivity", "deleteZBFile success: $response")
            }, errorCallback = { error ->
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
            email = email,
            responseCallback = { rsp ->
                Log.d("MainActivity", "getActivityData rsp: $rsp")
            }, errorCallback = { error ->
                Log.e("MainActivity", "getActivityData error: $error")
            }
        )
    }

    /**
     * Calls the *findEmail* method of the [ZeroBounceSDK].
     *
     * @param firstName The first name of the person whose email format is being searched.
     * @param domain the email domain for which to find the email format
     * @param companyName The company name for which to find the email format.
     */
    fun findEmail(firstName: String, domain: String? = null, companyName: String? = null) {
        ZeroBounceSDK.findEmail(
            firstName = firstName,
            domain = domain,
            companyName = companyName,
            responseCallback = { rsp ->
                Log.d("MainActivity", "findEmail rsp: $rsp")
                // your implementation
            },
            errorCallback = { error ->
                Log.e("MainActivity", "findEmail error: $error")
                // your implementation
            }
        )
    }

    /**
     * Calls the *findEmail* method of the [ZeroBounceSDK].
     *
     * @param domain the email domain for which to find the email format
     * @param companyName The company name for which to find the email format.
     */
    fun findDomain(domain: String? = null, companyName: String? = null) {
        ZeroBounceSDK.findDomain(
            domain = domain,
            companyName = companyName,
            responseCallback = { rsp ->
                Log.d("MainActivity", "findDomain rsp: $rsp")
                // your implementation
            },
            errorCallback = { error ->
                Log.e("MainActivity", "findDomain error: $error")
                // your implementation
            }
        )
    }
}