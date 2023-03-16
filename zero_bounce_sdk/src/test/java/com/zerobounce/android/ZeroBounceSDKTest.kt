package com.zerobounce.android

import android.content.Context
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.nhaarman.mockitokotlin2.mock
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import java.io.File
import java.util.*
import java.util.concurrent.CountDownLatch

@RunWith(MockitoJUnitRunner::class)
class ZeroBounceSDKTest {

    @Mock
    private lateinit var context: Context

    private val server = MockWebServer()

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .registerTypeAdapter(Date::class.java, GsonDateDeserializer())
        .create()

    companion object {
        private const val API_KEY = "some-api-key"
    }

    @Before
    fun setUp() {
        context = mock()
        `when`(context.getExternalFilesDir(null)).thenReturn(
            File(ZeroBounceSDKTest::class.java.getResource("/")?.path!!)
        )
        server.start()
        // Prepare the server's url and overwrite the ZeroBounceSDK's base url
        ZeroBounceSDK.apiBaseUrl = server.url("/").toString()
        ZeroBounceSDK.bulkApiBaseUrl = server.url("/").toString()
        ZeroBounceSDK.bulkApiScoringBaseUrl = server.url("/").toString()
        ZeroBounceSDK.initialize(API_KEY)
    }

    @Test
    fun validateEmail_ReturnsSuccess() {
        val countDownLatch = CountDownLatch(1)

        // Prepare mock response and add it to the server
        val responseJson = "{\n" +
                "        \"address\":\"flowerjill@aol.com\",\n" +
                "        \"status\":\"valid\",\n" +
                "        \"sub_status\":\"\",\n" +
                "        \"free_email\":true,\n" +
                "        \"did_you_mean\":null,\n" +
                "        \"account\":\"flowerjill\",\n" +
                "        \"domain\":\"aol.com\",\n" +
                "        \"domain_age_days\": \"8426\",\n" +
                "        \"smtp_provider\":\"yahoo\",\n" +
                "        \"mx_record\":\"mx-aol.mail.gm0.yahoodns.net\",\n" +
                "        \"mx_found\": \"true\",\n" +
                "        \"firstname\":\"Jill\",\n" +
                "        \"lastname\":\"Stein\",\n" +
                "        \"gender\":\"female\",\n" +
                "        \"country\":\"United States\",\n" +
                "        \"region\":\"Florida\",\n" +
                "        \"city\":\"West Palm Beach\",\n" +
                "        \"zipcode\":\"33401\",\n" +
                "        \"processed_at\":\"2017-04-01 02:48:02.592\"\n" +
                "        }"
        val expectedResponse = gson.fromJson(responseJson, ZBValidateResponse::class.java)
        server.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        var actualResponse: Any? = null

        // Do the actual request
        ZeroBounceSDK.validate(
            "flowerjill@aol.com",
            null,
            { rsp ->
                actualResponse = rsp
                countDownLatch.countDown()
            },
            { error ->
                actualResponse = error
                countDownLatch.countDown()
            })

        // If this is null, then the request has been made and the server must take the request.
        // Otherwise, a check error has occurred and thus, there was no need to do the request. The
        // error is already in the actualResponse field.
        if (actualResponse == null) {
            // Catch the request on the server (this will trigger the callbacks above)
            val request = server.takeRequest()

            // Check the API Key is not missing
            assertEquals(API_KEY, request.requestUrl?.queryParameter("api_key"))
        }

        // Await for the response to be parsed
        countDownLatch.await()

        // Test that the response from the server matches the expected response.
        assertEquals(expectedResponse, actualResponse)

        // Test that the actualResponse is an instance of the ZBValidateResponse class
        assertTrue(actualResponse is ZBValidateResponse)
        val temp = (actualResponse as ZBValidateResponse).toJSON()
        assertEquals(actualResponse, gson.fromJson(temp, ZBValidateResponse::class.java))
    }

    @Test
    fun validateEmail_ReturnsError() {
        val countDownLatch = CountDownLatch(1)

        // Prepare mock response and add it to the server
        val responseJson = "{\"error\":[\"Invalid API Key or your account ran out of credits\"]}"
        val errorResponse = ErrorResponse.parseError(responseJson)

        server.enqueue(MockResponse().setResponseCode(400).setBody(responseJson))

        var actualResponse: Any? = null

        // Do the actual request
        ZeroBounceSDK.validate(
            "flowerjill@aol.com",
            null,
            {
                countDownLatch.countDown()
            },
            { error ->
                actualResponse = error
                countDownLatch.countDown()
            })

        // If this is null, then the request has been made and the server must take the request.
        // Otherwise, a check error has occurred and thus, there was no need to do the request. The
        // error is already in the actualResponse field.
        if (actualResponse == null) {
            // Catch the request on the server (this will trigger the callbacks above)
            val request = server.takeRequest()

            // Check the API Key is not missing
            assertEquals(API_KEY, request.requestUrl?.queryParameter("api_key"))
        }

        // Await for the response to be parsed
        countDownLatch.await()

        // Test that the error json from the server matches the expected error json.
        assertEquals(errorResponse, actualResponse)
    }

    @Test
    fun getCredits_ReturnsSuccess() {
        val countDownLatch = CountDownLatch(1)

        // Prepare mock response and add it to the server
        val responseJson = "{\"Credits\":2375323}"
        val expectedResponse = gson.fromJson(responseJson, ZBCreditsResponse::class.java)
        server.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        var actualResponse: Any? = null

        // Do the actual request
        ZeroBounceSDK.getCredits(
            { rsp ->
                actualResponse = rsp
                countDownLatch.countDown()
            },
            { error ->
                actualResponse = error
                countDownLatch.countDown()
            })

        // If this is null, then the request has been made and the server must take the request.
        // Otherwise, a check error has occurred and thus, there was no need to do the request. The
        // error is already in the actualResponse field.
        if (actualResponse == null) {
            // Catch the request on the server (this will trigger the callbacks above)
            val request = server.takeRequest()

            // Check the API Key is not missing
            assertEquals(API_KEY, request.requestUrl?.queryParameter("api_key"))
        }

        // Await for the response to be parsed
        countDownLatch.await()

        // Test that the response from the server matches the expected response.
        assertEquals(expectedResponse, actualResponse)

        // Test that the actualResponse is an instance of the ZBCreditsResponse
        assertTrue(actualResponse is ZBCreditsResponse)
        val temp = (actualResponse as ZBCreditsResponse).toJSON()
        assertEquals(actualResponse, gson.fromJson(temp, ZBCreditsResponse::class.java))
    }

    @Test
    fun getCredits_ReturnsError() {
        val countDownLatch = CountDownLatch(1)

        // Prepare mock response and add it to the server
        val responseJson = "{\"Credits\":-1}"
        val errorResponse = ErrorResponse.parseError(responseJson)

        server.enqueue(MockResponse().setResponseCode(400).setBody(responseJson))

        var actualResponse: Any? = null

        // Do the actual request
        ZeroBounceSDK.getCredits(
            {
                countDownLatch.countDown()
            },
            { error ->
                actualResponse = error
                countDownLatch.countDown()
            })

        // If this is null, then the request has been made and the server must take the request.
        // Otherwise, a check error has occurred and thus, there was no need to do the request. The
        // error is already in the actualResponse field.
        if (actualResponse == null) {
            // Catch the request on the server (this will trigger the callbacks above)
            val request = server.takeRequest()

            // Check the API Key is not missing
            assertEquals(API_KEY, request.requestUrl?.queryParameter("api_key"))
        }

        // Await for the response to be parsed
        countDownLatch.await()

        // Test that the response from the server matches the expected response.
        assertEquals(errorResponse, actualResponse)
    }

    @Test
    fun getApiUsage_ReturnsSuccess() {
        val countDownLatch = CountDownLatch(1)

        // Prepare mock response and add it to the server
        val responseJson = "{\n" +
                "      \"total\": 3,\n" +
                "      \"status_valid\": 1,\n" +
                "      \"status_invalid\": 2,\n" +
                "      \"status_catch_all\": 0,\n" +
                "      \"status_do_not_mail\": 0,\n" +
                "      \"status_spamtrap\": 0,\n" +
                "      \"status_unknown\": 0,\n" +
                "      \"sub_status_toxic\": 0,\n" +
                "      \"sub_status_disposable\": 0,\n" +
                "      \"sub_status_role_based\": 0,\n" +
                "      \"sub_status_possible_trap\": 0,\n" +
                "      \"sub_status_global_suppression\": 0,\n" +
                "      \"sub_status_timeout_exceeded\": 0,\n" +
                "      \"sub_status_mail_server_temporary_error\": 0,\n" +
                "      \"sub_status_mail_server_did_not_respond\": 0,\n" +
                "      \"sub_status_greylisted\": 0,\n" +
                "      \"sub_status_antispam_system\": 0,\n" +
                "      \"sub_status_does_not_accept_mail\": 0,\n" +
                "      \"sub_status_exception_occurred\": 0,\n" +
                "      \"sub_status_failed_syntax_check\": 0,\n" +
                "      \"sub_status_mailbox_not_found\": 2,\n" +
                "      \"sub_status_unroutable_ip_address\": 0,\n" +
                "      \"sub_status_possible_typo\": 0,\n" +
                "      \"sub_status_no_dns_entries\": 0,\n" +
                "      \"sub_status_role_based_catch_all\": 0,\n" +
                "      \"sub_status_mailbox_quota_exceeded\": 0,\n" +
                "      \"sub_status_forcible_disconnect\": 0,\n" +
                "      \"sub_status_failed_smtp_connection\": 0,\n" +
                "      \"start_date\": \"1/1/2018\",\n" +
                "      \"end_date\": \"12/12/2019\"\n" +
                "    }"
        val expectedResponse = gson.fromJson(responseJson, ZBGetApiUsageResponse::class.java)
        server.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        var actualResponse: Any? = null

        // Do the actual request
        val startDate = Date()
        startDate.time -= 5 * 24 * 60 * 60 * 1000 // previous 5 days
        val endDate = Date()

        ZeroBounceSDK.getApiUsage(
            startDate,
            endDate,
            { rsp ->
                actualResponse = rsp
                countDownLatch.countDown()
            },
            { error ->
                actualResponse = error
                countDownLatch.countDown()
            })

        // If this is null, then the request has been made and the server must take the request.
        // Otherwise, a check error has occurred and thus, there was no need to do the request. The
        // error is already in the actualResponse field.
        if (actualResponse == null) {
            // Catch the request on the server (this will trigger the callbacks above)
            val request = server.takeRequest()

            // Check the API Key is not missing
            assertEquals(API_KEY, request.requestUrl?.queryParameter("api_key"))
        }

        // Await for the response to be parsed
        countDownLatch.await()

        // Test that the response from the server matches the expected response.
        assertEquals(expectedResponse, actualResponse)

        // Test that the actualResponse is an instance of the ZBGetApiUsageResponse class
        assertTrue(actualResponse is ZBGetApiUsageResponse)
        val temp = (actualResponse as ZBGetApiUsageResponse).toJSON()
        assertEquals(actualResponse, gson.fromJson(temp, ZBGetApiUsageResponse::class.java))
    }

    @Test
    fun getApiUsage_ReturnsError() {
        val countDownLatch = CountDownLatch(1)

        // Prepare mock response and add it to the server
        val responseJson = "{\"error\":\"Invalid API Key\"}"
        val errorResponse = ErrorResponse.parseError(responseJson)
        server.enqueue(MockResponse().setResponseCode(400).setBody(responseJson))

        var actualResponse: Any? = null

        // Do the actual request
        val startDate = Date()
        startDate.time -= 5 * 24 * 60 * 60 * 1000 // previous 5 days
        val endDate = Date()

        // Do the actual request
        ZeroBounceSDK.getApiUsage(
            startDate,
            endDate,
            {
                countDownLatch.countDown()
            },
            { error ->
                actualResponse = error
                countDownLatch.countDown()
            })

        // If this is null, then the request has been made and the server must take the request.
        // Otherwise, a check error has occurred and thus, there was no need to do the request. The
        // error is already in the actualResponse field.
        if (actualResponse == null) {
            // Catch the request on the server (this will trigger the callbacks above)
            val request = server.takeRequest()

            // Check the API Key is not missing
            assertEquals(API_KEY, request.requestUrl?.queryParameter("api_key"))
        }

        // Await for the response to be parsed
        countDownLatch.await()

        // Test that the response from the server matches the expected response.
        assertEquals(errorResponse, actualResponse)
    }

    @Test
    fun sendFile_FileStatus_GetFile_DeleteFile_ReturnSuccess() {
        var countDownLatch = CountDownLatch(1)

        val uri = ZeroBounceSDKTest::class.java.getResource("/email_file.csv")
        assertNotNull(uri)
        assertNotNull(uri?.path)
        val file = File(uri?.path!!)

        // Prepare mock response and add it to the server
        val responseJson = "{\n" +
                "      \"success\": true,\n" +
                "      \"message\": \"File Accepted\",\n" +
                "      \"file_name\": \"email_file.csv\",\n" +
                "      \"file_id\": \"b222a0fd-90d5-416c-8f1a-9cc3851fc823\"\n" +
                "    }"
        val expectedResponse = gson.fromJson(responseJson, ZBSendFileResponse::class.java)
        server.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        var actualResponse: Any? = null

        // Do the actual request
        ZeroBounceSDK.sendFile(
            context = context,
            file = file,
            emailAddressColumnIndex = 1,
            returnUrl = null,
            firstNameColumnIndex = 2,
            lastNameColumnIndex = 3,
            genderColumnIndex = null,
            ipAddressColumnIndex = null,
            hasHeaderRow = true,
            responseCallback = { rsp ->
                actualResponse = rsp
                countDownLatch.countDown()
            },
            errorCallback = { error ->
                actualResponse = error
                countDownLatch.countDown()
            })

        // If this is null, then the request has been made and the server must take the request.
        // Otherwise, a check error has occurred and thus, there was no need to do the request. The
        // error is already in the actualResponse field.
        if (actualResponse == null) {
            // Catch the request on the server (this will trigger the callbacks above)
            val request = server.takeRequest()

            // Check the API Key is not missing
            assertTrue("name=\"api_key\"" in request.body.readUtf8())
        }

        // Await for the response to be parsed
        countDownLatch.await()

        // Test that the response from the server matches the expected response.
        assertEquals(expectedResponse, actualResponse)

        // Test that the actualResponse is an instance of the ZBSendFileResponse class
        assertTrue(actualResponse is ZBSendFileResponse)
        val tempSendFile = (actualResponse as ZBSendFileResponse).toJSON()
        assertEquals(actualResponse, gson.fromJson(tempSendFile, ZBSendFileResponse::class.java))

        // -----------------------------------
        // Prepare to check that the file status request works
        countDownLatch = CountDownLatch(1)

        val fileId = expectedResponse.fileId
        assertNotNull(fileId)
        fileId ?: return

        val fileStatusJson = "{\n" +
                "    \"success\": true,\n" +
                "    \"file_id\": \"b222a0fd-90d5-416c-8f1a-9cc3851fc823\",\n" +
                "    \"file_name\": \"email_file.csv\",\n" +
                "    \"upload_date\": \"10/20/2018 4:35:58 PM\",\n" +
                "    \"file_status\": \"Complete\",\n" +
                "    \"complete_percentage\": \"100%\",\n" +
                "    \"return_url\": \"Your return URL if provided when calling sendfile API\"\n" +
                "  }"
        val fileStatusResponse = gson.fromJson(fileStatusJson, ZBFileStatusResponse::class.java)
        server.enqueue(MockResponse().setResponseCode(200).setBody(fileStatusJson))
        actualResponse = null

        // Do the actual request
        ZeroBounceSDK.fileStatus(
            fileId,
            { rsp ->
                actualResponse = rsp
                countDownLatch.countDown()
            }, { error ->
                actualResponse = error
                countDownLatch.countDown()
            })

        // If this is null, then the request has been made and the server must take the request.
        // Otherwise, a check error has occurred and thus, there was no need to do the request. The
        // error is already in the actualResponse field.
        if (actualResponse == null) {
            // Catch the request on the server (this will trigger the callbacks above)
            val request = server.takeRequest()

            // Check the API Key is not missing
            assertEquals(API_KEY, request.requestUrl?.queryParameter("api_key"))
        }

        // Await for the response to be parsed
        countDownLatch.await()

        // Test that the response from the server matches the expected response.
        assertEquals(fileStatusResponse, actualResponse)

        // Test that the actualResponse is an instance of the ZBFileStatusResponse class
        assertTrue(actualResponse is ZBFileStatusResponse)
        val tempFileStatus = (actualResponse as ZBFileStatusResponse).toJSON()
        assertEquals(
            actualResponse,
            gson.fromJson(tempFileStatus, ZBFileStatusResponse::class.java)
        )

        // -----------------------------------
        // Prepare to get the file we just uploaded
        countDownLatch = CountDownLatch(1)

        val buffer = Buffer()
        buffer.write(file.readBytes())
        val getFileResponse = ZBGetFileResponse(
            localFilePath = File(
                context.getExternalFilesDir(null),
                "b222a0fd-90d5-416c-8f1a-9cc3851fc823.csv"
            ).path
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(buffer))
        actualResponse = null

        // Do the actual request
        ZeroBounceSDK.getFile(
            context,
            fileId,
            { rsp ->
                actualResponse = rsp
                countDownLatch.countDown()
            }, { error ->
                actualResponse = error
                countDownLatch.countDown()
            })

        // If this is null, then the request has been made and the server must take the request.
        // Otherwise, a check error has occurred and thus, there was no need to do the request. The
        // error is already in the actualResponse field.
        if (actualResponse == null) {
            // Catch the request on the server (this will trigger the callbacks above)
            val request = server.takeRequest()

            // Check the API Key is not missing
            assertEquals(API_KEY, request.requestUrl?.queryParameter("api_key"))
        }

        // Await for the response to be parsed
        countDownLatch.await()

        // Test that the response from the server matches the expected response.
        assertEquals(getFileResponse, actualResponse)

        // -----------------------------------
        // Prepare to delete the file we just uploaded
        countDownLatch = CountDownLatch(1)

        val deleteFileJson = "{\n" +
                "  \"success\": true,\n" +
                "  \"message\": \"File Deleted\",\n" +
                "  \"file_name\": \"test2\",\n" +
                "  \"file_id\": \"b222a0fd-90d5-416c-8f1a-9cc3851fc823\"\n" +
                "}"
        val deleteFileResponse = gson.fromJson(deleteFileJson, ZBDeleteFileResponse::class.java)
        server.enqueue(MockResponse().setResponseCode(200).setBody(deleteFileJson))
        actualResponse = null

        // Do the actual request
        ZeroBounceSDK.deleteFile(
            fileId,
            { rsp ->
                actualResponse = rsp
                countDownLatch.countDown()
            }, { error ->
                actualResponse = error
                countDownLatch.countDown()
            })

        // If this is null, then the request has been made and the server must take the request.
        // Otherwise, a check error has occurred and thus, there was no need to do the request. The
        // error is already in the actualResponse field.
        if (actualResponse == null) {
            // Catch the request on the server (this will trigger the callbacks above)
            val request = server.takeRequest()

            // Check the API Key is not missing
            assertEquals(API_KEY, request.requestUrl?.queryParameter("api_key"))
        }

        // Await for the response to be parsed
        countDownLatch.await()

        // Test that the response from the server matches the expected response.
        assertEquals(deleteFileResponse, actualResponse)

        // Test that the actualResponse is an instance of the ZBDeleteFileResponse class
        assertTrue(actualResponse is ZBDeleteFileResponse)
        val tempDeleteFile = (actualResponse as ZBDeleteFileResponse).toJSON()
        assertEquals(
            actualResponse,
            gson.fromJson(tempDeleteFile, ZBDeleteFileResponse::class.java)
        )
    }

    @Test
    fun scoringSendFile_ScoringFileStatus_ScoringGetFile_ScoringDeleteFile_ReturnSuccess() {
        var countDownLatch = CountDownLatch(1)

        val uri = ZeroBounceSDKTest::class.java.getResource("/email_file.csv")
        assertNotNull(uri)
        assertNotNull(uri?.path)
        val file = File(uri?.path!!)

        // Prepare mock response and add it to the server
        val responseJson = "{\n" +
                "      \"success\": true,\n" +
                "      \"message\": \"File Accepted\",\n" +
                "      \"file_name\": \"email_file.csv\",\n" +
                "      \"file_id\": \"b222a0fd-90d5-416c-8f1a-9cc3851fc823\"\n" +
                "    }"
        val expectedResponse = gson.fromJson(responseJson, ZBSendFileResponse::class.java)
        server.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        var actualResponse: Any? = null

        // Do the actual request
        ZeroBounceSDK.scoringSendFile(
            context = context,
            file = file,
            emailAddressColumnIndex = 1,
            returnUrl = null,
            hasHeaderRow = true,
            responseCallback = { rsp ->
                actualResponse = rsp
                countDownLatch.countDown()
            },
            errorCallback = { error ->
                actualResponse = error
                countDownLatch.countDown()
            })

        // If this is null, then the request has been made and the server must take the request.
        // Otherwise, a check error has occurred and thus, there was no need to do the request. The
        // error is already in the actualResponse field.
        if (actualResponse == null) {
            // Catch the request on the server (this will trigger the callbacks above)
            val request = server.takeRequest()

            // Check the API Key is not missing
            assertTrue("name=\"api_key\"" in request.body.readUtf8())
        }

        // Await for the response to be parsed
        countDownLatch.await()

        // Test that the response from the server matches the expected response.
        assertEquals(expectedResponse, actualResponse)

        // Test that the actualResponse is an instance of the ZBSendFileResponse class
        assertTrue(actualResponse is ZBSendFileResponse)
        val tempSendFile = (actualResponse as ZBSendFileResponse).toJSON()
        assertEquals(actualResponse, gson.fromJson(tempSendFile, ZBSendFileResponse::class.java))

        // -----------------------------------
        // Prepare to check that the file status request works
        countDownLatch = CountDownLatch(1)

        val fileId = expectedResponse.fileId
        assertNotNull(fileId)
        fileId ?: return

        val fileStatusJson = "{\n" +
                "    \"success\": true,\n" +
                "    \"file_id\": \"b222a0fd-90d5-416c-8f1a-9cc3851fc823\",\n" +
                "    \"file_name\": \"email_file.csv\",\n" +
                "    \"upload_date\": \"10/20/2018 4:35:58 PM\",\n" +
                "    \"file_status\": \"Complete\",\n" +
                "    \"complete_percentage\": \"100%\",\n" +
                "    \"return_url\": \"Your return URL if provided when calling sendfile API\"\n" +
                "  }"
        val fileStatusResponse = gson.fromJson(fileStatusJson, ZBFileStatusResponse::class.java)
        server.enqueue(MockResponse().setResponseCode(200).setBody(fileStatusJson))
        actualResponse = null

        // Do the actual request
        ZeroBounceSDK.scoringFileStatus(
            fileId,
            { rsp ->
                actualResponse = rsp
                countDownLatch.countDown()
            }, { error ->
                actualResponse = error
                countDownLatch.countDown()
            })

        // If this is null, then the request has been made and the server must take the request.
        // Otherwise, a check error has occurred and thus, there was no need to do the request. The
        // error is already in the actualResponse field.
        if (actualResponse == null) {
            // Catch the request on the server (this will trigger the callbacks above)
            val request = server.takeRequest()

            // Check the API Key is not missing
            assertEquals(API_KEY, request.requestUrl?.queryParameter("api_key"))
        }

        // Await for the response to be parsed
        countDownLatch.await()

        // Test that the response from the server matches the expected response.
        assertEquals(fileStatusResponse, actualResponse)

        // Test that the actualResponse is an instance of the ZBFileStatusResponse class
        assertTrue(actualResponse is ZBFileStatusResponse)
        val tempFileStatus = (actualResponse as ZBFileStatusResponse).toJSON()
        assertEquals(
            actualResponse,
            gson.fromJson(tempFileStatus, ZBFileStatusResponse::class.java)
        )

        // -----------------------------------
        // Prepare to get the file we just uploaded
        countDownLatch = CountDownLatch(1)

        val buffer = Buffer()
        buffer.write(file.readBytes())
        val getFileResponse = ZBGetFileResponse(
            localFilePath = File(
                context.getExternalFilesDir(null),
                "b222a0fd-90d5-416c-8f1a-9cc3851fc823.csv"
            ).path
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(buffer))
        actualResponse = null

        // Do the actual request
        ZeroBounceSDK.scoringGetFile(
            context,
            fileId,
            { rsp ->
                actualResponse = rsp
                countDownLatch.countDown()
            }, { error ->
                actualResponse = error
                countDownLatch.countDown()
            })

        // If this is null, then the request has been made and the server must take the request.
        // Otherwise, a check error has occurred and thus, there was no need to do the request. The
        // error is already in the actualResponse field.
        if (actualResponse == null) {
            // Catch the request on the server (this will trigger the callbacks above)
            val request = server.takeRequest()

            // Check the API Key is not missing
            assertEquals(API_KEY, request.requestUrl?.queryParameter("api_key"))
        }

        // Await for the response to be parsed
        countDownLatch.await()

        // Test that the response from the server matches the expected response.
        assertEquals(getFileResponse, actualResponse)

        // -----------------------------------
        // Prepare to delete the file we just uploaded
        countDownLatch = CountDownLatch(1)

        val deleteFileJson = "{\n" +
                "  \"success\": true,\n" +
                "  \"message\": \"File Deleted\",\n" +
                "  \"file_name\": \"test2\",\n" +
                "  \"file_id\": \"b222a0fd-90d5-416c-8f1a-9cc3851fc823\"\n" +
                "}"
        val deleteFileResponse = gson.fromJson(deleteFileJson, ZBDeleteFileResponse::class.java)
        server.enqueue(MockResponse().setResponseCode(200).setBody(deleteFileJson))
        actualResponse = null

        // Do the actual request
        ZeroBounceSDK.scoringDeleteFile(
            fileId,
            { rsp ->
                actualResponse = rsp
                countDownLatch.countDown()
            }, { error ->
                actualResponse = error
                countDownLatch.countDown()
            })

        // If this is null, then the request has been made and the server must take the request.
        // Otherwise, a check error has occurred and thus, there was no need to do the request. The
        // error is already in the actualResponse field.
        if (actualResponse == null) {
            // Catch the request on the server (this will trigger the callbacks above)
            val request = server.takeRequest()

            // Check the API Key is not missing
            assertEquals(API_KEY, request.requestUrl?.queryParameter("api_key"))
        }

        // Await for the response to be parsed
        countDownLatch.await()

        // Test that the response from the server matches the expected response.
        assertEquals(deleteFileResponse, actualResponse)

        // Test that the actualResponse is an instance of the ZBDeleteFileResponse class
        assertTrue(actualResponse is ZBDeleteFileResponse)
        val tempDeleteFile = (actualResponse as ZBDeleteFileResponse).toJSON()
        assertEquals(
            actualResponse,
            gson.fromJson(tempDeleteFile, ZBDeleteFileResponse::class.java)
        )
    }

    @Test
    fun sendFile_ReturnsError() {
        val countDownLatch = CountDownLatch(1)

        val file = File(ZeroBounceSDKTest::class.java.getResource("/email_file.csv")?.path!!)

        // Prepare mock response and add it to the server
        val responseJson = "{\n" +
                "      \"success\": false,\n" +
                "      \"message\": [\n" +
                "          \"Error messages\"\n" +
                "      ]\n" +
                "  }"
        val errorResponse = ErrorResponse.parseError(responseJson)
        server.enqueue(MockResponse().setResponseCode(400).setBody(responseJson))

        var actualErrorJson: Any? = null

        // Do the actual request
        ZeroBounceSDK.sendFile(
            context = context,
            file = file,
            emailAddressColumnIndex = 1,
            returnUrl = null,
            firstNameColumnIndex = 2,
            lastNameColumnIndex = 3,
            genderColumnIndex = null,
            ipAddressColumnIndex = null,
            hasHeaderRow = true,
            responseCallback = {
                countDownLatch.countDown()
            },
            errorCallback = { error ->
                actualErrorJson = error
                countDownLatch.countDown()
            })

        // If this is null, then the request has been made and the server must take the request.
        // Otherwise, a check error has occurred and thus, there was no need to do the request. The
        // error is already in the actualResponse field.
        if (actualErrorJson == null) {
            // Catch the request on the server (this will trigger the callbacks above)
            val request = server.takeRequest()

            // Check the API Key is not missing
            assertTrue("name=\"api_key\"" in request.body.readUtf8())
        }

        // Await for the response to be parsed
        countDownLatch.await()

        // Test that the response from the server matches the expected response.
        assertEquals(errorResponse, actualErrorJson)
    }

    @Test
    fun scoringSendFile_ReturnsError() {
        val countDownLatch = CountDownLatch(1)

        val file = File(ZeroBounceSDKTest::class.java.getResource("/email_file.csv")?.path!!)

        // Prepare mock response and add it to the server
        val responseJson = "{\n" +
                "      \"success\": false,\n" +
                "      \"message\": [\n" +
                "          \"Error messages\"\n" +
                "      ]\n" +
                "  }"
        val errorResponse = ErrorResponse.parseError(responseJson)
        server.enqueue(MockResponse().setResponseCode(400).setBody(responseJson))

        var actualErrorJson: Any? = null

        // Do the actual request
        ZeroBounceSDK.scoringSendFile(
            context = context,
            file = file,
            emailAddressColumnIndex = 1,
            returnUrl = null,
            hasHeaderRow = true,
            responseCallback = {
                countDownLatch.countDown()
            },
            errorCallback = { error ->
                actualErrorJson = error
                countDownLatch.countDown()
            })

        // If this is null, then the request has been made and the server must take the request.
        // Otherwise, a check error has occurred and thus, there was no need to do the request. The
        // error is already in the actualResponse field.
        if (actualErrorJson == null) {
            // Catch the request on the server (this will trigger the callbacks above)
            val request = server.takeRequest()

            // Check the API Key is not missing
            assertTrue("name=\"api_key\"" in request.body.readUtf8())
        }

        // Await for the response to be parsed
        countDownLatch.await()

        // Test that the response from the server matches the expected response.
        assertEquals(errorResponse, actualErrorJson)
    }

    @Test
    fun fileStatus_ReturnsError() {
        val countDownLatch = CountDownLatch(1)

        // Prepare mock response and add it to the server
        val responseJson = "{\n" +
                "    \"success\": false,\n" +
                "    \"message\": \"Error messages\"\n" +
                "  }"
        val errorResponse = ErrorResponse.parseError(responseJson)
        server.enqueue(MockResponse().setResponseCode(400).setBody(responseJson))

        var actualResponse: Any? = null

        // Do the actual request
        ZeroBounceSDK.fileStatus(
            "some-id",
            {
                countDownLatch.countDown()
            },
            { error ->
                actualResponse = error
                countDownLatch.countDown()
            })

        // If this is null, then the request has been made and the server must take the request.
        // Otherwise, a check error has occurred and thus, there was no need to do the request. The
        // error is already in the actualResponse field.
        if (actualResponse == null) {
            // Catch the request on the server (this will trigger the callbacks above)
            val request = server.takeRequest()

            // Check the API Key is not missing
            assertEquals(API_KEY, request.requestUrl?.queryParameter("api_key"))
        }

        // Await for the response to be parsed
        countDownLatch.await()

        // Test that the response from the server matches the expected response.
        assertEquals(errorResponse, actualResponse)
    }

    @Test
    fun scoringFileStatus_ReturnsError() {
        val countDownLatch = CountDownLatch(1)

        // Prepare mock response and add it to the server
        val responseJson = "{\n" +
                "    \"success\": false,\n" +
                "    \"message\": \"Error messages\"\n" +
                "  }"
        val errorResponse = ErrorResponse.parseError(responseJson)
        server.enqueue(MockResponse().setResponseCode(400).setBody(responseJson))

        var actualResponse: Any? = null

        // Do the actual request
        ZeroBounceSDK.scoringFileStatus(
            "some-id",
            {
                countDownLatch.countDown()
            },
            { error ->
                actualResponse = error
                countDownLatch.countDown()
            })

        // If this is null, then the request has been made and the server must take the request.
        // Otherwise, a check error has occurred and thus, there was no need to do the request. The
        // error is already in the actualResponse field.
        if (actualResponse == null) {
            // Catch the request on the server (this will trigger the callbacks above)
            val request = server.takeRequest()

            // Check the API Key is not missing
            assertEquals(API_KEY, request.requestUrl?.queryParameter("api_key"))
        }

        // Await for the response to be parsed
        countDownLatch.await()

        // Test that the response from the server matches the expected response.
        assertEquals(errorResponse, actualResponse)
    }

    @Test
    fun getFile_ReturnsError() {
        val countDownLatch = CountDownLatch(1)

        // Prepare mock response and add it to the server
        val responseJson = "{\n" +
                "    \"success\": false,\n" +
                "    \"message\": \"Error messages\"\n" +
                "}"
        val errorResponse = ErrorResponse.parseError(responseJson)
        server.enqueue(MockResponse().setResponseCode(400).setBody(responseJson))

        var actualResponse: Any? = null

        // Do the actual request
        ZeroBounceSDK.getFile(
            context,
            "some-id",
            {
                countDownLatch.countDown()
            },
            { error ->
                actualResponse = error
                countDownLatch.countDown()
            })

        // If this is null, then the request has been made and the server must take the request.
        // Otherwise, a check error has occurred and thus, there was no need to do the request. The
        // error is already in the actualResponse field.
        if (actualResponse == null) {
            // Catch the request on the server (this will trigger the callbacks above)
            val request = server.takeRequest()

            // Check the API Key is not missing
            assertEquals(API_KEY, request.requestUrl?.queryParameter("api_key"))
        }

        // Await for the response to be parsed
        countDownLatch.await()

        // Test that the response from the server matches the expected response.
        assertEquals(errorResponse, actualResponse)
    }

    @Test
    fun scoringGetFile_ReturnsError() {
        val countDownLatch = CountDownLatch(1)

        // Prepare mock response and add it to the server
        val responseJson = "{\n" +
                "    \"success\": false,\n" +
                "    \"message\": \"Error messages\"\n" +
                "}"
        val errorResponse = ErrorResponse.parseError(responseJson)
        server.enqueue(MockResponse().setResponseCode(400).setBody(responseJson))

        var actualResponse: Any? = null

        // Do the actual request
        ZeroBounceSDK.scoringGetFile(
            context,
            "some-id",
            {
                countDownLatch.countDown()
            },
            { error ->
                actualResponse = error
                countDownLatch.countDown()
            })

        // If this is null, then the request has been made and the server must take the request.
        // Otherwise, a check error has occurred and thus, there was no need to do the request. The
        // error is already in the actualResponse field.
        if (actualResponse == null) {
            // Catch the request on the server (this will trigger the callbacks above)
            val request = server.takeRequest()

            // Check the API Key is not missing
            assertEquals(API_KEY, request.requestUrl?.queryParameter("api_key"))
        }

        // Await for the response to be parsed
        countDownLatch.await()

        // Test that the response from the server matches the expected response.
        assertEquals(errorResponse, actualResponse)
    }

    @Test
    fun deleteFile_ReturnsError() {
        val countDownLatch = CountDownLatch(1)

        // Prepare mock response and add it to the server
        val responseJson = "{\n" +
                "  \"success\": false,\n" +
                "  \"message\": \"File cannot be found.\"\n" +
                "}"
        val errorResponse = ErrorResponse.parseError(responseJson)
        server.enqueue(MockResponse().setResponseCode(400).setBody(responseJson))

        var actualResponse: Any? = null

        // Do the actual request
        ZeroBounceSDK.deleteFile(
            "some-id",
            {
                countDownLatch.countDown()
            },
            { error ->
                actualResponse = error
                countDownLatch.countDown()
            })

        // If this is null, then the request has been made and the server must take the request.
        // Otherwise, a check error has occurred and thus, there was no need to do the request. The
        // error is already in the actualResponse field.
        if (actualResponse == null) {
            // Catch the request on the server (this will trigger the callbacks above)
            val request = server.takeRequest()

            // Check the API Key is not missing
            assertEquals(API_KEY, request.requestUrl?.queryParameter("api_key"))
        }

        // Await for the response to be parsed
        countDownLatch.await()

        // Test that the response from the server matches the expected response.
        assertEquals(errorResponse, actualResponse)
    }

    @Test
    fun scoringDeleteFile_ReturnsError() {
        val countDownLatch = CountDownLatch(1)

        // Prepare mock response and add it to the server
        val responseJson = "{\n" +
                "  \"success\": false,\n" +
                "  \"message\": \"File cannot be found.\"\n" +
                "}"
        val errorResponse = ErrorResponse.parseError(responseJson)
        server.enqueue(MockResponse().setResponseCode(400).setBody(responseJson))

        var actualResponse: Any? = null

        // Do the actual request
        ZeroBounceSDK.scoringDeleteFile(
            "some-id",
            {
                countDownLatch.countDown()
            },
            { error ->
                actualResponse = error
                countDownLatch.countDown()
            })

        // If this is null, then the request has been made and the server must take the request.
        // Otherwise, a check error has occurred and thus, there was no need to do the request. The
        // error is already in the actualResponse field.
        if (actualResponse == null) {
            // Catch the request on the server (this will trigger the callbacks above)
            val request = server.takeRequest()

            // Check the API Key is not missing
            assertEquals(API_KEY, request.requestUrl?.queryParameter("api_key"))
        }

        // Await for the response to be parsed
        countDownLatch.await()

        // Test that the response from the server matches the expected response.
        assertEquals(errorResponse, actualResponse)
    }

    @Test
    fun getActivityData_ReturnsSuccess() {
        val countDownLatch = CountDownLatch(1)

        // Prepare mock response and add it to the server
        val responseJson = "{\n" +
                "  \"found\": true,\n" +
                "  \"active_in_days\": 60\n" +
                "}"
        val expectedResponse = gson.fromJson(responseJson, ZBActivityDataResponse::class.java)
        server.enqueue(MockResponse().setResponseCode(200).setBody(responseJson))

        var actualResponse: Any? = null

        // Do the actual request
        ZeroBounceSDK.getActivityData(
            "flowerjill@aol.com",
            { rsp ->
                actualResponse = rsp
                countDownLatch.countDown()
            },
            { error ->
                actualResponse = error
                countDownLatch.countDown()
            })

        // If this is null, then the request has been made and the server must take the request.
        // Otherwise, a check error has occurred and thus, there was no need to do the request. The
        // error is already in the actualResponse field.
        if (actualResponse == null) {
            // Catch the request on the server (this will trigger the callbacks above)
            val request = server.takeRequest()

            // Check the API Key is not missing
            assertEquals(API_KEY, request.requestUrl?.queryParameter("api_key"))
        }

        // Await for the response to be parsed
        countDownLatch.await()

        // Test that the response from the server matches the expected response.
        assertEquals(expectedResponse, actualResponse)

        // Test that the actualResponse is an instance of the ZBActivityData class
        assertTrue(actualResponse is ZBActivityDataResponse)
        val temp = (actualResponse as ZBActivityDataResponse).toJSON()
        assertEquals(actualResponse, gson.fromJson(temp, ZBActivityDataResponse::class.java))
    }

    @Test
    fun getActivityData_ReturnsError() {
        val countDownLatch = CountDownLatch(1)

        // Prepare mock response and add it to the server
        val responseJson = "{\n" +
                "  \"found\": false,\n" +
                "  \"active_in_days\": null\n" +
                "}"
        val errorResponse = ErrorResponse.parseError(responseJson)
        server.enqueue(MockResponse().setResponseCode(400).setBody(responseJson))

        var actualResponse: Any? = null

        // Do the actual request
        ZeroBounceSDK.getActivityData(
            "flowerjill@aol.com",
            {
                countDownLatch.countDown()
            },
            { error ->
                actualResponse = error
                countDownLatch.countDown()
            })

        // If this is null, then the request has been made and the server must take the request.
        // Otherwise, a check error has occurred and thus, there was no need to do the request. The
        // error is already in the actualResponse field.
        if (actualResponse == null) {
            // Catch the request on the server (this will trigger the callbacks above)
            val request = server.takeRequest()

            // Check the API Key is not missing
            assertEquals(API_KEY, request.requestUrl?.queryParameter("api_key"))
        }

        // Await for the response to be parsed
        countDownLatch.await()

        // Test that the response from the server matches the expected response.
        assertEquals(errorResponse, actualResponse)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }
}