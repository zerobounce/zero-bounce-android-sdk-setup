package com.zerobounce.android

import android.content.Context
import com.google.gson.JsonParser
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Offline API suite. A fake OkHttp interceptor records every call and answers it.
 * Nothing here contacts api.zerobounce.net or uses a real key.
 */
class OfflineApiTest {

    private val fake = FakeApi()
    private val context: Context = mock()
    private lateinit var downloads: File
    private lateinit var csvFile: File

    @Before
    fun setUp() {
        downloads = File(System.getProperty("java.io.tmpdir"), "zb-android-offline-${System.nanoTime()}")
        downloads.mkdirs()
        whenever(context.getExternalFilesDir(null)).thenReturn(downloads)
        csvFile = File(OfflineApiTest::class.java.getResource("/email_file.csv")!!.path)
        resetSharedUrls()
        ZeroBounceSDK.transportInterceptor = fake
    }

    @After
    fun tearDown() {
        ZeroBounceSDK.transportInterceptor = null
        downloads.deleteRecursively()
    }

    @Test
    fun getCreditsReachesEndpointAndEchoesNothingFromTheNetwork() {
        useSharedKey("credits-key")
        val outcome = await<ZBCreditsResponse> { ok, err -> ZeroBounceSDK.getCredits(ok, err) }
        assertNull(outcome.errorText())
        assertEquals("7", outcome.value?.credits)
        val request = onlyRequest()
        assertEquals("GET", request.method)
        assertEquals("api.zerobounce.net", request.host)
        assertEquals("/v2/getcredits", request.path)
        assertEquals("credits-key", request.apiKey)
        val userAgent = request.userAgent.orEmpty()
        if (userAgent.contains("zerobounce", ignoreCase = true)) {
            assertTrue(userAgent.contains(publishVersion()))
        }
    }

    @Test
    fun validateOmitsUnsetOptionsAndSendsFalse() {
        useSharedKey("validate-key")
        val outcome = await<ZBValidateResponse> { ok, err ->
            ZeroBounceSDK.validate(
                "person@example.com",
                null,
                ZBValidateOptions(activityData = false, verifyPlus = true),
                ok,
                err,
            )
        }
        assertNull(outcome.errorText())
        assertEquals("validate-key", outcome.value?.address)
        val request = onlyRequest()
        assertEquals("GET", request.method)
        assertEquals("/v2/validate", request.path)
        assertEquals("person@example.com", request.fields["email"])
        assertEquals("", request.fields["ip_address"])
        assertEquals("false", request.fields["activity_data"])
        assertEquals("true", request.fields["verify_plus"])
        assertFalse(request.fields.containsKey("timeout"))
    }

    @Test
    fun validatePostFormAndTimeout() {
        useSharedKey("post-key")
        val outcome = await<ZBValidateResponse> { ok, err ->
            ZeroBounceSDK.validate(
                "person@example.com",
                "203.0.113.5",
                ZBValidateOptions(timeout = 15, usePost = true),
                ok,
                err,
            )
        }
        assertNull(outcome.errorText())
        val request = onlyRequest()
        assertEquals("POST", request.method)
        assertEquals("api.zerobounce.net", request.host)
        assertEquals("/v2/validate", request.path)
        assertEquals("post-key", request.fields["api_key"])
        assertEquals("person@example.com", request.fields["email"])
        assertEquals("203.0.113.5", request.fields["ip_address"])
        assertEquals("15", request.fields["timeout"])
        assertFalse(request.fields.containsKey("activity_data"))
        assertFalse(request.fields.containsKey("verify_plus"))
    }

    @Test
    fun validateEncodesEmailAndApiKey() {
        useSharedKey("enc&key=1")
        await<ZBValidateResponse> { ok, err ->
            ZeroBounceSDK.validate("a&b@example.com", "203.0.113.5", ok, err)
        }
        val request = onlyRequest()
        assertEquals("enc&key=1", request.apiKey)
        assertEquals("a&b@example.com", request.fields["email"])
        assertNull(request.fields["key"])
        assertNull(request.fields["b@example.com"])
    }

    @Test
    fun validateBatchUsesApiHostAndOmitsUnsetOptions() {
        useSharedKey("batch-key")
        val outcome = await<ZBValidateBatchResponse> { ok, err ->
            ZeroBounceSDK.validateBatch(
                listOf(
                    ZBValidateBatchData("a@example.com", null),
                    ZBValidateBatchData("b@example.com", "203.0.113.9"),
                ),
                ok,
                err,
            )
        }
        assertNull(outcome.errorText())
        assertEquals("batch-key", outcome.value?.emailBatch?.firstOrNull()?.address)
        val request = onlyRequest()
        assertEquals("POST", request.method)
        assertEquals("api.zerobounce.net", request.host)
        assertEquals("/v2/validatebatch", request.path)
        assertEquals("batch-key", request.fields["api_key"])
        assertTrue(request.fields["email_batch"]!!.contains("\"email_address\":\"a@example.com\""))
        assertFalse(request.fields["email_batch"]!!.contains("\"ip_address\":null"))
        assertTrue(request.fields["email_batch"]!!.contains("\"ip_address\":\"203.0.113.9\""))
        assertFalse(request.fields.containsKey("timeout"))
        assertFalse(request.fields.containsKey("activity_data"))
        assertFalse(request.fields.containsKey("verify_plus"))
    }

    @Test
    fun validateBatchSendsExplicitOptions() {
        useSharedKey("batch-opt")
        await<ZBValidateBatchResponse> { ok, err ->
            ZeroBounceSDK.validateBatch(
                listOf(ZBValidateBatchData("a@example.com", null)),
                ZBValidateOptions(timeout = 30, activityData = false, verifyPlus = true),
                ok,
                err,
            )
        }
        val request = onlyRequest()
        assertEquals("30", request.fields["timeout"])
        assertEquals("false", request.fields["activity_data"])
        assertEquals("true", request.fields["verify_plus"])
    }

    @Test
    fun sendFileEncodesBooleansAndKeepsExplicitZeroColumn() {
        useSharedKey("file-key")
        val outcome = await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.sendFile(
                context = context,
                file = csvFile,
                emailAddressColumnIndex = 1,
                firstNameColumnIndex = 0,
                returnUrl = "https://example.com/hook",
                hasHeaderRow = true,
                removeDuplicate = false,
                allowPhase2 = false,
                responseCallback = ok,
                errorCallback = err,
            )
        }
        assertNull(outcome.errorText())
        assertEquals("file-key", outcome.value?.fileId)
        val request = onlyRequest()
        assertEquals("POST", request.method)
        assertEquals("bulkapi.zerobounce.net", request.host)
        assertEquals("/v2/sendfile", request.path)
        assertEquals("file-key", request.fields["api_key"])
        assertEquals("1", request.fields["email_address_column"])
        assertEquals("0", request.fields["first_name_column"])
        assertEquals("<file>", request.fields["file"])
        assertEquals("true", request.fields["has_header_row"])
        assertEquals("false", request.fields["remove_duplicate"])
        assertEquals("false", request.fields["allow_phase_2"])
        assertEquals("https://example.com/hook", request.fields["return_url"])
        assertFalse(request.fields.containsKey("last_name_column"))
        assertFalse(request.fields.containsKey("gender_column"))
        assertFalse(request.fields.containsKey("ip_address_column"))
    }

    @Test
    fun sendFileStreamOmitsUnsetColumns() {
        useSharedKey("stream-key")
        await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.sendFile(
                inputStream = ByteArrayInputStream("a@example.com\n".toByteArray()),
                fileName = "people.csv",
                emailAddressColumnIndex = 1,
                responseCallback = ok,
                errorCallback = err,
            )
        }
        val request = onlyRequest()
        assertEquals("/v2/sendfile", request.path)
        assertEquals("false", request.fields["has_header_row"])
        assertFalse(request.fields.containsKey("allow_phase_2"))
        assertFalse(request.fields.containsKey("remove_duplicate"))
        assertFalse(request.fields.containsKey("first_name_column"))
        assertEquals("people.csv", request.fields["file_name"])
    }

    @Test
    fun scoringSendFileOmitsValidationOnlyFields() {
        useSharedKey("score-file")
        await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.scoringSendFile(
                inputStream = ByteArrayInputStream("a@example.com\n".toByteArray()),
                fileName = "people.csv",
                emailAddressColumnIndex = 1,
                hasHeaderRow = true,
                removeDuplicate = true,
                responseCallback = ok,
                errorCallback = err,
            )
        }
        val request = onlyRequest()
        assertEquals("bulkapi.zerobounce.net", request.host)
        assertEquals("/v2/scoring/sendfile", request.path)
        assertEquals("true", request.fields["has_header_row"])
        assertEquals("true", request.fields["remove_duplicate"])
        assertFalse(request.fields.containsKey("allow_phase_2"))
        assertFalse(request.fields.containsKey("first_name_column"))
    }

    @Test
    fun scoringSendFileFromDiskUsesScoringHost() {
        useSharedKey("score-disk")
        await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.scoringSendFile(
                context = context,
                file = csvFile,
                emailAddressColumnIndex = 1,
                responseCallback = ok,
                errorCallback = err,
            )
        }
        assertEquals("/v2/scoring/sendfile", onlyRequest().path)
    }

    @Test
    fun fileStatusAndDeleteUseBulkHost() {
        useSharedKey("status-key")
        val status = await<ZBFileStatusResponse> { ok, err ->
            ZeroBounceSDK.fileStatus("file 1", ok, err)
        }
        assertNull(status.errorText())
        val statusRequest = onlyRequest()
        assertEquals("/v2/filestatus", statusRequest.path)
        assertEquals("bulkapi.zerobounce.net", statusRequest.host)
        assertEquals("file 1", statusRequest.fields["file_id"])

        await<ZBFileStatusResponse> { ok, err -> ZeroBounceSDK.scoringFileStatus("file-2", ok, err) }
        assertEquals("/v2/scoring/filestatus", onlyRequest().path)

        await<ZBDeleteFileResponse> { ok, err -> ZeroBounceSDK.deleteFile("file-1", ok, err) }
        assertEquals("/v2/deletefile", onlyRequest().path)

        await<ZBDeleteFileResponse> { ok, err -> ZeroBounceSDK.scoringDeleteFile("file-2", ok, err) }
        assertEquals("/v2/scoring/deletefile", onlyRequest().path)
    }

    @Test
    fun getFileSendsOptionsAndDoesNotSaveHtml() {
        useSharedKey("getfile-key")
        val outcome = await<ZBGetFileResponse> { ok, err ->
            ZeroBounceSDK.getFile(
                context,
                "file-9",
                ok,
                err,
                downloadType = ZBDownloadType.PHASE_2,
                activityData = true,
            )
        }
        assertNull(outcome.errorText())
        val saved = File(outcome.value!!.localFilePath).readText()
        assertTrue(saved.contains("getfile-key@example.com"))
        assertFalse(saved.contains("<html"))
        val request = onlyRequest()
        assertEquals("/v2/getfile", request.path)
        assertEquals("bulkapi.zerobounce.net", request.host)
        assertEquals("file-9", request.fields["file_id"])
        assertEquals("phase_2", request.fields["download_type"])
        assertEquals("true", request.fields["activity_data"])

        await<ZBGetFileResponse> { ok, err ->
            ZeroBounceSDK.scoringGetFile(
                context,
                "file-8",
                ok,
                err,
                downloadType = ZBDownloadType.PHASE_1,
            )
        }
        val scoring = onlyRequest()
        assertEquals("/v2/scoring/getfile", scoring.path)
        assertEquals("phase_1", scoring.fields["download_type"])
        assertFalse(scoring.fields.containsKey("activity_data"))
    }

    @Test
    fun activityUsageGuessFormatScoreEndpoints() {
        useSharedKey("usage-key")
        val start = GregorianCalendar(2018, Calendar.JANUARY, 1).time
        val end = GregorianCalendar(2018, Calendar.JANUARY, 31).time
        val usage = await<ZBGetApiUsageResponse> { ok, err ->
            ZeroBounceSDK.getApiUsage(start, end, ok, err)
        }
        assertNull(usage.errorText())
        assertEquals(1, usage.value?.total)
        val usageRequest = onlyRequest()
        assertEquals("/v2/getapiusage", usageRequest.path)
        assertEquals("2018-01-01", usageRequest.fields["start_date"])
        assertEquals("2018-01-31", usageRequest.fields["end_date"])

        val activity = await<ZBActivityDataResponse> { ok, err ->
            ZeroBounceSDK.getActivityData("person@example.com", ok, err)
        }
        assertNull(activity.errorText())
        assertEquals(true, activity.value?.found)
        val activityRequest = onlyRequest()
        assertEquals("/v2/activity", activityRequest.path)
        assertEquals("person@example.com", activityRequest.fields["email"])

        val scored = await<ZBScoreEmailResponse> { ok, err ->
            ZeroBounceSDK.scoreEmail("person@example.com", ok, err)
        }
        assertNull(scored.errorText())
        assertEquals("usage-key", scored.value?.email)
        val score = onlyRequest()
        assertEquals("GET", score.method)
        assertEquals("/v2/scoring", score.path)
        assertEquals("api.zerobounce.net", score.host)

        await<ZBScoreEmailResponse> { ok, err ->
            ZeroBounceSDK.scoreEmailPost("person@example.com", ok, err)
        }
        val posted = onlyRequest()
        assertEquals("POST", posted.method)
        assertEquals("/v2/scoring", posted.path)
        assertEquals("person@example.com", posted.fields["email"])

        await<ZBEmailFinderResponse> { ok, err ->
            ZeroBounceSDK.guessFormat("example.com", "Ada", null, "Lovelace", ok, err)
        }
        val guessed = onlyRequest()
        assertEquals("/v2/guessformat", guessed.path)
        assertEquals("example.com", guessed.fields["domain"])
        assertEquals("Ada", guessed.fields["first_name"])
        assertEquals("Lovelace", guessed.fields["last_name"])
        assertFalse(guessed.fields.containsKey("middle_name"))
    }

    @Test
    fun finderAndDomainSearchOmitUnsetNames() {
        useSharedKey("finder-key")
        val byDomain = await<ZBFindEmailResponse> { ok, err ->
            ZeroBounceSDK.findEmail("Ada", domain = "example.com", middleName = "M", lastName = "Lovelace", responseCallback = ok, errorCallback = err)
        }
        assertNull(byDomain.errorText())
        val domainRequest = onlyRequest()
        assertEquals("/v2/guessformat", domainRequest.path)
        assertEquals("example.com", domainRequest.fields["domain"])
        assertEquals("Ada", domainRequest.fields["first_name"])
        assertEquals("M", domainRequest.fields["middle_name"])
        assertEquals("Lovelace", domainRequest.fields["last_name"])
        assertFalse(domainRequest.fields.containsKey("company_name"))

        await<ZBFindEmailResponse> { ok, err ->
            ZeroBounceSDK.findEmail("Ada", companyName = "ZeroBounce", responseCallback = ok, errorCallback = err)
        }
        val company = onlyRequest()
        assertEquals("ZeroBounce", company.fields["company_name"])
        assertEquals("Ada", company.fields["first_name"])
        assertFalse(company.fields.containsKey("domain"))
        assertFalse(company.fields.containsKey("last_name"))

        await<ZBFindDomainResponse> { ok, err ->
            ZeroBounceSDK.findDomain(domain = "example.com", responseCallback = ok, errorCallback = err)
        }
        assertFalse(onlyRequest().fields.containsKey("company_name"))

        await<ZBFindDomainResponse> { ok, err ->
            ZeroBounceSDK.findDomain(companyName = "ZeroBounce", responseCallback = ok, errorCallback = err)
        }
        val byCompany = onlyRequest()
        assertEquals("ZeroBounce", byCompany.fields["company_name"])
        assertFalse(byCompany.fields.containsKey("domain"))
    }

    @Test
    fun priority3EndpointsZeroColumnAndFalseHeader() {
        useSharedKey("p3-key")
        await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.sendEmailFinderFile(
                csvFile,
                1,
                // A full name column satisfies the name requirement, and the
                // 0 first name column is still left out.
                ZBEmailFinderSendFileOptions(
                    hasHeaderRow = false,
                    firstNameColumn = 0,
                    fullNameColumn = 2,
                ),
                ok,
                err,
            )
        }
        val sent = onlyRequest()
        assertEquals("bulkapi.zerobounce.net", sent.host)
        assertEquals("/email-finder/sendfile", sent.path)
        assertEquals("1", sent.fields["domain_column"])
        assertEquals("false", sent.fields["has_header_row"])
        assertEquals("2", sent.fields["full_name_column"])
        assertFalse(sent.fields.containsKey("first_name_column"))
        assertEquals("p3-key", sent.apiKey)

        await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.sendEmailFinderFileStream(
                ByteArrayInputStream("example.com\n".toByteArray()),
                "finder.csv",
                2,
                ZBEmailFinderSendFileOptions(firstNameColumn = 1, lastNameColumn = 3),
                ok,
                err,
            )
        }
        val streamed = onlyRequest()
        assertEquals("/email-finder/sendfile", streamed.path)
        assertEquals("2", streamed.fields["domain_column"])
        assertEquals("1", streamed.fields["first_name_column"])
        assertEquals("3", streamed.fields["last_name_column"])
        assertFalse(streamed.fields.containsKey("has_header_row"))

        await<ZBFileStatusResponse> { ok, err -> ZeroBounceSDK.emailFinderFileStatus("fid", ok, err) }
        assertEquals("/email-finder/filestatus", onlyRequest().path)
        val finderDownload = File(downloads, "finder.csv")
        await<ZBGetFileResponse> { ok, err ->
            ZeroBounceSDK.emailFinderGetFile("fid", finderDownload.path, ok, err)
        }
        assertEquals("/email-finder/getfile", onlyRequest().path)
        assertTrue(finderDownload.readText().contains("p3-key@example.com"))
        await<ZBDeleteFileResponse> { ok, err -> ZeroBounceSDK.emailFinderDeleteFile("fid", ok, err) }
        assertEquals("/email-finder/deletefile", onlyRequest().path)

        await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.sendDomainSearchFile(
                csvFile,
                2,
                ZBDomainSearchSendFileOptions(hasHeaderRow = false),
                ok,
                err,
            )
        }
        val domain = onlyRequest()
        assertEquals("/domain-search/sendfile", domain.path)
        assertEquals("2", domain.fields["domain_column"])
        assertEquals("false", domain.fields["has_header_row"])

        await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.sendDomainSearchFileStream(
                ByteArrayInputStream("example.com\n".toByteArray()),
                "domains.csv",
                1,
                null,
                ok,
                err,
            )
        }
        val domainStream = onlyRequest()
        assertEquals("/domain-search/sendfile", domainStream.path)
        assertFalse(domainStream.fields.containsKey("has_header_row"))

        await<ZBFileStatusResponse> { ok, err -> ZeroBounceSDK.domainSearchFileStatus("fid", ok, err) }
        assertEquals("/domain-search/filestatus", onlyRequest().path)
        val domainDownload = File(downloads, "domain.csv")
        await<ZBGetFileResponse> { ok, err ->
            ZeroBounceSDK.domainSearchGetFile("fid", domainDownload.path, ok, err)
        }
        assertEquals("/domain-search/getfile", onlyRequest().path)
        await<ZBDeleteFileResponse> { ok, err -> ZeroBounceSDK.domainSearchDeleteFile("fid", ok, err) }
        assertEquals("/domain-search/deletefile", onlyRequest().path)

        await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.sendListEvaluatorFile(csvFile, 1, ok, err)
        }
        val evaluator = onlyRequest()
        assertEquals("/v2/listevaluator/", evaluator.path)
        assertEquals("1", evaluator.fields["email_address_column"])

        await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.sendListEvaluatorFileStream(
                ByteArrayInputStream("a@example.com\n".toByteArray()),
                "list.csv",
                4,
                ok,
                err,
            )
        }
        assertEquals("4", onlyRequest().fields["email_address_column"])

        val result = await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.getListEvaluatorResult("fid", ok, err)
        }
        assertNull(result.errorText())
        assertTrue(result.value.toString().contains("p3-key"))
        assertEquals("/v2/listevaluator/fid/", onlyRequest().path)

        // LIVE: send-evaluated-file takes the evaluator's file_id as a form
        // field, not the file upload the docs describe.
        await<ZBSendFileResponse> { ok, err -> ZeroBounceSDK.sendEvaluatedFile("eval-file-1", ok, err) }
        val evaluated = onlyRequest()
        assertEquals("/v2/send-evaluated-file/", evaluated.path)
        assertEquals("POST", evaluated.method)
        assertEquals("eval-file-1", evaluated.fields["file_id"])

        await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.addFilter("exclude", "domain", "example.com", ok, err)
        }
        val added = onlyRequest()
        assertEquals("POST", added.method)
        assertEquals("api.zerobounce.net", added.host)
        assertEquals("/v2/filters/add", added.path)
        assertEquals("exclude", added.fields["rule"])
        assertEquals("domain", added.fields["target"])
        assertEquals("example.com", added.fields["value"])

        val listed = await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.listFilters(false, ok, err)
        }
        assertTrue(listed.value.toString().contains("p3-key"))
        val listedRequest = onlyRequest()
        assertEquals("GET", listedRequest.method)
        assertEquals("/v2/filters/list", listedRequest.path)

        await<com.google.gson.JsonElement> { ok, err -> ZeroBounceSDK.listFilters(true, ok, err) }
        assertEquals("POST", onlyRequest().method)

        await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.deleteFilter("exclude", "domain", "example.com", ok, err)
        }
        assertEquals("/v2/filters/delete", onlyRequest().path)

        await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.validateS3File(
                ZBValidateS3FileOptions(
                    fileName = "inbox.csv",
                    emailAddressColumn = 0,
                    hasHeaderRow = false,
                    removeDuplicate = true,
                    returnUrl = "https://example.com/done",
                ),
                ok,
                err,
            )
        }
        val validated = onlyRequest()
        assertEquals("POST", validated.method)
        assertEquals("/validateS3file", validated.path)
        assertEquals("inbox.csv", validated.fields["file_name"])
        assertEquals("https://example.com/done", validated.fields["return_url"])
        assertEquals("0", validated.fields["email_address_column"])
        assertEquals("false", validated.fields["has_header_row"])
        assertEquals("true", validated.fields["remove_duplicate"])
        assertFalse(validated.fields.containsKey("remove_duplicates"))
        assertFalse(validated.fields.containsKey("first_name_column"))

        await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.scoreS3File(
                ZBScoreS3FileOptions(fileName = "inbox.csv", returnUrl = "https://example.com/done"),
                ok,
                err,
            )
        }
        val scored = onlyRequest()
        assertEquals("/scoreS3file", scored.path)
        assertEquals("inbox.csv", scored.fields["file_name"])
        assertEquals("https://example.com/done", scored.fields["return_url"])
        assertFalse(scored.fields.containsKey("email_address_column"))

        await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.exportS3File("fid", "bucket-out", ok, err)
        }
        val exported = onlyRequest()
        assertEquals("/exportS3file", exported.path)
        assertEquals("fid", exported.fields["file_id"])
        assertEquals("bucket-out", exported.fields["export_bucket"])

        val files = await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.listS3Files(responseCallback = ok, errorCallback = err)
        }
        assertTrue(files.value.toString().contains("p3-key"))
        val listedFiles = onlyRequest()
        assertEquals("/listS3files", listedFiles.path)
        assertFalse(listedFiles.fields.containsKey("bucket"))
        assertFalse(listedFiles.fields.containsKey("search"))

        await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.listS3Files("inbox", "csv", ok, err)
        }
        val filtered = onlyRequest()
        assertEquals("inbox", filtered.fields["bucket"])
        assertEquals("csv", filtered.fields["search"])
    }

    @Test
    fun rejectsMissingKeyAndInvalidArgumentsBeforeRequest() {
        useSharedKey("")
        val rejected = listOf(
            await<ZBCreditsResponse> { ok, err -> ZeroBounceSDK.getCredits(ok, err) },
            await<ZBFindEmailResponse> { ok, err ->
                ZeroBounceSDK.findEmail("Ada", domain = "example.com", responseCallback = ok, errorCallback = err)
            },
            await<ZBFindDomainResponse> { ok, err ->
                ZeroBounceSDK.findDomain(companyName = "ZeroBounce", responseCallback = ok, errorCallback = err)
            },
            await<com.google.gson.JsonElement> { ok, err -> ZeroBounceSDK.listFilters(false, ok, err) },
            await<ZBDeleteFileResponse> { ok, err -> ZeroBounceSDK.emailFinderDeleteFile("fid", ok, err) },
            await<ZBSendFileResponse> { ok, err ->
                ZeroBounceSDK.sendDomainSearchFile(csvFile, 1, null, ok, err)
            },
        )
        rejected.forEach { outcome ->
            assertNotNull(outcome.errorText())
            assertTrue(outcome.errorText()!!.contains("initialize"))
            expectShort(outcome.errorText()!!)
        }
        assertEquals(0, fake.size())

        useSharedKey("keyed")
        val badTimeout = await<ZBValidateResponse> { ok, err ->
            ZeroBounceSDK.validate("a@example.com", null, ZBValidateOptions(timeout = 2), ok, err)
        }
        assertTrue(badTimeout.errorText()!!.contains("timeout"))
        val badBatch = await<ZBValidateBatchResponse> { ok, err ->
            ZeroBounceSDK.validateBatch(
                listOf(ZBValidateBatchData("a@example.com", null)),
                ZBValidateOptions(timeout = 9),
                ok,
                err,
            )
        }
        assertTrue(badBatch.errorText()!!.contains("timeout"))
        val badColumn = await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.sendEmailFinderFile(csvFile, 0, null, ok, err)
        }
        assertTrue(badColumn.errorText()!!.contains("domain_column"))
        val noNameColumn = await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.sendEmailFinderFile(csvFile, 1, null, ok, err)
        }
        assertTrue(noNameColumn.errorText()!!.contains("first_name_column or full_name_column"))
        val missingName = await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.validateS3File(ZBValidateS3FileOptions(fileName = "  "), ok, err)
        }
        assertTrue(missingName.errorText()!!.contains("file_name"))
        val missingDomain = await<ZBFindEmailResponse> { ok, err ->
            ZeroBounceSDK.findEmail("Ada", responseCallback = ok, errorCallback = err)
        }
        assertTrue(missingDomain.errorText()!!.contains("domain"))
        var threw = false
        try {
            ZeroBounceSDK.sendFile(
                context = context,
                file = csvFile,
                emailAddressColumnIndex = 0,
                responseCallback = {},
                errorCallback = {},
            )
        } catch (_: ZBException) {
            threw = true
        }
        assertTrue(threw)
        assertEquals(0, fake.size())
    }

    @Test
    fun html403IsAShortError() {
        useSharedKey("html-403-user")
        val outcome = await<ZBCreditsResponse> { ok, err -> ZeroBounceSDK.getCredits(ok, err) }
        assertNotNull(outcome.errorText())
        expectShort(outcome.errorText()!!)
        assertTrue(outcome.errorText()!!.contains("Access denied"))
        assertTrue(FakeApi.accessDeniedPage().length > 1_000_000)
        assertEquals(1, fake.size())
    }

    @Test
    fun html200OnValidateIsAnError() {
        useSharedKey("html-200-user")
        val outcome = await<ZBValidateResponse> { ok, err ->
            ZeroBounceSDK.validate("a@example.com", null, ok, err)
        }
        assertNull(outcome.value?.address)
        assertNotNull(outcome.errorText())
        expectShort(outcome.errorText()!!)
        assertTrue(outcome.errorText()!!.contains("Access denied"))
    }

    @Test
    fun html200GetFileIsNotSaved() {
        useSharedKey("html-200-file")
        val outcome = await<ZBGetFileResponse> { ok, err ->
            ZeroBounceSDK.getFile(context, "fid", ok, err)
        }
        assertNotNull(outcome.errorText())
        expectShort(outcome.errorText()!!)
        assertTrue(outcome.errorText()!!.contains("Access denied"))
        assertTrue(downloads.listFiles().isNullOrEmpty())
        val finder = File(downloads, "finder-html.csv")
        val finderOutcome = await<ZBGetFileResponse> { ok, err ->
            ZeroBounceSDK.emailFinderGetFile("fid", finder.path, ok, err)
        }
        assertNotNull(finderOutcome.errorText())
        expectShort(finderOutcome.errorText()!!)
        assertFalse(finder.exists())
        val domain = File(downloads, "domain-html.csv")
        val domainOutcome = await<ZBGetFileResponse> { ok, err ->
            ZeroBounceSDK.domainSearchGetFile("fid", domain.path, ok, err)
        }
        assertNotNull(domainOutcome.errorText())
        assertFalse(domain.exists())
    }

    @Test
    fun jsonErrorGetFileIsNotSavedWhenContentTypeIsNotJson() {
        useSharedKey("jsonerr-user")
        val outcome = await<ZBGetFileResponse> { ok, err ->
            ZeroBounceSDK.getFile(context, "fid", ok, err)
        }
        assertNotNull(outcome.errorText())
        expectShort(outcome.errorText()!!)
        assertTrue(outcome.errorText()!!.contains("file not ready"))
        assertTrue(downloads.listFiles().isNullOrEmpty())
    }

    @Test
    fun cloudflareJson403DoesNotDumpTheBody() {
        useSharedKey("cf-json-user")
        val outcome = await<ZBCreditsResponse> { ok, err -> ZeroBounceSDK.getCredits(ok, err) }
        assertNotNull(outcome.errorText())
        expectShort(outcome.errorText()!!)
        assertFalse(outcome.errorText()!!.contains("developers.cloudflare.com"))
        assertTrue(outcome.errorText()!!.contains("Access denied"))
    }

    @Test
    fun emptyAndMalformedBodiesAreShortErrors() {
        useSharedKey("empty-user")
        val empty = await<ZBValidateResponse> { ok, err ->
            ZeroBounceSDK.validate("a@example.com", null, ok, err)
        }
        assertNotNull(empty.errorText())
        expectShort(empty.errorText()!!)
        assertTrue(empty.errorText()!!.contains("Empty response"))

        useSharedKey("malformed-user")
        val malformed = await<ZBValidateResponse> { ok, err ->
            ZeroBounceSDK.validate("a@example.com", null, ok, err)
        }
        assertNotNull(malformed.errorText())
        expectShort(malformed.errorText()!!)
        assertTrue(malformed.errorText()!!.contains("not valid JSON"))
        assertFalse(malformed.errorText()!!.contains("<<<not-json>>>"))
    }

    @Test
    fun doubledErrorPrefixIsCollapsed() {
        useSharedKey("double-error-user")
        val outcome = await<ZBActivityDataResponse> { ok, err ->
            ZeroBounceSDK.getActivityData("a@example.com", ok, err)
        }
        assertEquals("api_key is invalid", outcome.errorText())
    }

    @Test
    fun batchAccountErrorIsNotASuccess() {
        useSharedKey("batch-account-user")
        val outcome = await<ZBValidateBatchResponse> { ok, err ->
            ZeroBounceSDK.validateBatch(listOf(ZBValidateBatchData("a@example.com", null)), ok, err)
        }
        assertNull("account error was treated as success: ${outcome.value}", outcome.value)
        assertNotNull(outcome.errorText())
        expectShort(outcome.errorText()!!)
        assertTrue(outcome.errorText()!!.contains("Invalid API Key"))
        assertTrue(outcome.errorText()!!.contains("ran out of credits"))
    }

    @Test
    fun batchPartialErrorsStayOnTheSuccessResult() {
        useSharedKey("batch-partial-user")
        val outcome = await<ZBValidateBatchResponse> { ok, err ->
            ZeroBounceSDK.validateBatch(
                listOf(
                    ZBValidateBatchData("good@example.com", null),
                    ZBValidateBatchData("bad@", null),
                ),
                ok,
                err,
            )
        }
        assertNull(outcome.errorText())
        assertEquals("good@example.com", outcome.value?.emailBatch?.firstOrNull()?.address)
        val errors = outcome.value?.errors
        assertEquals(1, errors?.size)
        assertEquals("bad@", errors?.first()?.get("email_address")?.toString())
        assertEquals("Invalid email address", errors?.first()?.get("error")?.toString())
    }

    @Test
    fun redirectsAreNotFollowed() {
        val api = MockWebServer()
        val evil = MockWebServer()
        api.start()
        evil.start()
        try {
            evil.enqueue(MockResponse().setResponseCode(200).setBody("{\"Credits\":\"999\"}"))
            api.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .setHeader("Location", evil.url("/steal").toString())
                    .setBody("go away")
            )
            ZeroBounceSDK.transportInterceptor = null
            ZeroBounceSDK.initialize("redir-key", api.url("/v2").toString().trimEnd('/'))
            assertFalse(ZeroBounceSDK.client.followRedirects)
            assertFalse(ZeroBounceSDK.client.followSslRedirects)
            val outcome = await<ZBCreditsResponse> { ok, err -> ZeroBounceSDK.getCredits(ok, err) }
            assertNull(outcome.value?.credits)
            assertNotNull(outcome.errorText())
            expectShort(outcome.errorText()!!)
            assertEquals(0, evil.requestCount)
        } finally {
            api.shutdown()
            evil.shutdown()
            ZeroBounceSDK.transportInterceptor = fake
            resetSharedUrls()
        }
    }

    @Test
    fun concurrentClientsDoNotShareKeys() {
        val calls = 12
        val echoes = arrayOfNulls<String>(calls)
        val failures = arrayOfNulls<String>(calls)
        val barrier = CyclicBarrier(calls)
        val threads = (0 until calls).map { index ->
            Thread {
                val key = "conc-$index"
                val sdk = openClient(key)
                barrier.await()
                val latch = CountDownLatch(1)
                var echo: String? = null
                var error: String? = null
                sdk.validate(
                    "person@example.com",
                    null,
                    { response ->
                        echo = response?.address
                        latch.countDown()
                    },
                    { err ->
                        error = err?.errors?.joinToString()
                        latch.countDown()
                    },
                )
                latch.await(8, TimeUnit.SECONDS)
                echoes[index] = echo
                failures[index] = error
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        val records = fake.take()
        assertEquals(calls, records.size)
        val seen = records.groupingBy { it.apiKey }.eachCount()
        for (index in 0 until calls) {
            val key = "conc-$index"
            assertEquals(failures[index], 1, seen[key])
            assertEquals(key, echoes[index])
            assertEquals(key, records.first { it.apiKey == key }.fields["api_key"])
        }
    }

    @Test
    fun validateSendsTheNewOptionsOnlyWhenSet() {
        useSharedKey("validate-key")

        await<ZBValidateResponse> { ok, err ->
            ZeroBounceSDK.validate("a@example.com", null, ZBValidateOptions(), ok, err)
        }
        var request = onlyRequest()
        for (absent in listOf("domain_info", "greylist_processing", "callback_url")) {
            assertFalse("$absent should not be sent", request.fields.containsKey(absent))
        }

        await<ZBValidateResponse> { ok, err ->
            ZeroBounceSDK.validate(
                "a@example.com",
                null,
                ZBValidateOptions(
                    domainInfo = true,
                    greylistProcessing = true,
                    callbackUrl = "https://example.com/greylist",
                ),
                ok,
                err,
            )
        }
        request = onlyRequest()
        assertEquals("GET", request.method)
        // Booleans go on the wire as the strings "true"/"false".
        assertEquals("true", request.fields["domain_info"])
        assertEquals("true", request.fields["greylist_processing"])
        assertEquals("https://example.com/greylist", request.fields["callback_url"])

        await<ZBValidateResponse> { ok, err ->
            ZeroBounceSDK.validate(
                "a@example.com",
                null,
                ZBValidateOptions(
                    domainInfo = false,
                    greylistProcessing = true,
                    callbackUrl = "https://example.com/greylist",
                    usePost = true,
                ),
                ok,
                err,
            )
        }
        request = onlyRequest()
        assertEquals("POST", request.method)
        assertEquals("false", request.fields["domain_info"])
        assertEquals("https://example.com/greylist", request.fields["callback_url"])
    }

    @Test
    fun domainInfoAndGreylistFieldsParseOntoTheResponse() {
        // The fake answers every /validate the same way, so this pins the
        // mapping rather than the request.
        val json = """{"address":"a@example.com","status":"valid",""" +
            """"domain_website_exists":"true",""" +
            """"domain_registrant_company_name":"Example Inc",""" +
            """"Message":"Greylisted email, we will retry"}"""
        val response = com.google.gson.Gson().fromJson(json, ZBValidateResponse::class.java)

        assertEquals("true", response.domainWebsiteExists)
        assertEquals("Example Inc", response.domainRegistrantCompanyName)
        // While a greylist retry is still running the API answers with
        // "Message" instead of a result.
        assertEquals("Greylisted email, we will retry", response.message)
    }

    @Test
    fun callbackUrlWithoutGreylistProcessingSendsNoRequest() {
        useSharedKey("validate-key")

        var outcome = await<ZBValidateResponse> { ok, err ->
            ZeroBounceSDK.validate(
                "a@example.com", null,
                ZBValidateOptions(callbackUrl = "https://example.com/cb"), ok, err,
            )
        }
        assertTrue(outcome.errorText().orEmpty().contains("greylist_processing"))

        outcome = await { ok, err ->
            ZeroBounceSDK.validate(
                "a@example.com", null,
                ZBValidateOptions(greylistProcessing = false, callbackUrl = "https://example.com/cb"),
                ok, err,
            )
        }
        assertTrue(outcome.errorText().orEmpty().contains("greylist_processing"))

        outcome = await { ok, err ->
            ZeroBounceSDK.validate(
                "a@example.com", null,
                ZBValidateOptions(greylistProcessing = true, callbackUrl = "   "), ok, err,
            )
        }
        assertTrue(outcome.errorText().orEmpty().contains("callback_url"))

        assertEquals(0, fake.size())
    }

    @Test
    fun s3AndSftpFileJobsSendTheDocumentedFieldsOnly() {
        useSharedKey("bulk-key")

        await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.emailFinderS3File(
                ZBBulkFileJobOptions(
                    fileName = "lists/leads.csv",
                    hasHeaderRow = true,
                    returnUrl = "https://example.com/done",
                ),
                ok, err,
            )
        }
        var request = onlyRequest()
        assertEquals("POST", request.method)
        assertEquals("bulkapi.zerobounce.net", request.host)
        // The S3 and SFTP jobs live on the bulkapi root, WITHOUT /v2.
        assertEquals("/emailFinderS3file", request.path)
        assertEquals("lists/leads.csv", request.fields["file_name"])
        assertEquals("true", request.fields["has_header_row"])
        assertEquals("https://example.com/done", request.fields["return_url"])
        // Unset options stay off the request entirely.
        assertFalse(request.fields.containsKey("remove_duplicates"))

        await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.domainSearchS3File(
                ZBBulkFileJobOptions(
                    fileName = "lists/leads.csv",
                    hasHeaderRow = false,
                    removeDuplicates = true,
                    returnUrl = "https://example.com/done",
                ),
                ok, err,
            )
        }
        request = onlyRequest()
        assertEquals("/domainSearchS3file", request.path)
        assertEquals("false", request.fields["has_header_row"])
        assertEquals("true", request.fields["remove_duplicates"])
        assertEquals("https://example.com/done", request.fields["return_url"])

        // LIVE: file_name is required here too, although the docs list it as
        // optional for this one endpoint.
        val named = ZBBulkFileJobOptions(
            fileName = "my_file.txt",
            returnUrl = "https://example.com/done",
        )
        await<ZBSendFileResponse> { ok, err -> ZeroBounceSDK.validateSFTPFile(named, ok, err) }
        request = onlyRequest()
        assertEquals("/validateSFTPfile", request.path)
        assertEquals("my_file.txt", request.fields["file_name"])
        assertEquals("true", request.fields["has_header_row"])
        val jobs = listOf<Pair<String, (ZBBulkFileJobOptions, (ZBSendFileResponse?) -> Unit, (ErrorResponse?) -> Unit) -> Unit>>(
            "/validateSFTPfile" to ZeroBounceSDK::validateSFTPFile,
            "/scoreSFTPfile" to ZeroBounceSDK::scoreSFTPFile,
            "/emailFinderSFTPfile" to ZeroBounceSDK::emailFinderSFTPFile,
            "/domainSearchSFTPfile" to ZeroBounceSDK::domainSearchSFTPFile,
        )
        for ((path, call) in jobs) {
            await<ZBSendFileResponse> { ok, err -> call(named, ok, err) }
            request = onlyRequest()
            assertEquals(path, request.path)
            assertEquals("my_file.txt", request.fields["file_name"])
        }

        // exportSFTPfile takes export_path, not the export_bucket exportS3file uses.
        await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.exportSFTPFile("fid", "/exports", ok, err)
        }
        request = onlyRequest()
        assertEquals("/exportSFTPfile", request.path)
        assertEquals("fid", request.fields["file_id"])
        assertEquals("/exports", request.fields["export_path"])
        assertFalse(request.fields.containsKey("export_bucket"))

        await<ZBSendFileResponse> { ok, err -> ZeroBounceSDK.exportSFTPFile("fid", null, ok, err) }
        assertFalse(onlyRequest().fields.containsKey("export_path"))

        // exportS3file's bucket is optional in the same way.
        await<ZBSendFileResponse> { ok, err -> ZeroBounceSDK.exportS3File("fid", null, ok, err) }
        assertFalse(onlyRequest().fields.containsKey("export_bucket"))
    }

    @Test
    fun bulkJobsRejectInvalidArgumentsBeforeARequest() {
        useSharedKey("bulk-key")
        val blank = ZBBulkFileJobOptions(fileName = "   ")
        val calls = listOf<(ZBBulkFileJobOptions, (ZBSendFileResponse?) -> Unit, (ErrorResponse?) -> Unit) -> Unit>(
            ZeroBounceSDK::emailFinderS3File,
            ZeroBounceSDK::domainSearchS3File,
            ZeroBounceSDK::scoreSFTPFile,
            ZeroBounceSDK::emailFinderSFTPFile,
            // LIVE: validateSFTPfile needs a file_name too, although the docs
            // call it optional for that one job.
            ZeroBounceSDK::validateSFTPFile,
        )
        for (call in calls) {
            val outcome = await<ZBSendFileResponse> { ok, err -> call(blank, ok, err) }
            assertTrue(outcome.errorText().orEmpty().contains("file_name"))
        }

        val missing = await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.emailFinderS3File(ZBBulkFileJobOptions(), ok, err)
        }
        assertTrue(missing.errorText().orEmpty().contains("file_name"))

        val fileId = await<ZBSendFileResponse> { ok, err -> ZeroBounceSDK.exportSFTPFile("  ", null, ok, err) }
        assertTrue(fileId.errorText().orEmpty().contains("file_id"))
        val path = await<ZBSendFileResponse> { ok, err -> ZeroBounceSDK.exportSFTPFile("fid", " ", ok, err) }
        assertTrue(path.errorText().orEmpty().contains("export_path"))
        val bucket = await<ZBSendFileResponse> { ok, err -> ZeroBounceSDK.exportS3File("fid", " ", ok, err) }
        assertTrue(bucket.errorText().orEmpty().contains("export_bucket"))

        assertEquals(0, fake.size())
    }

    @Test
    fun listEvaluatorUsesItsOwnBaseUrlAndEncodesTheFileId() {
        useSharedKey("eval-key")

        await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.getListEvaluatorResult("a b/c", ok, err)
        }
        val request = onlyRequest()
        assertEquals("bulkapi.zerobounce.net", request.host)
        // The file id is one path segment, so a slash in it must stay encoded.
        assertEquals("/v2/listevaluator/a%20b%2Fc/", request.path)

        val blank = await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.getListEvaluatorResult("  ", ok, err)
        }
        assertTrue(blank.errorText().orEmpty().contains("file_id"))
        assertEquals(0, fake.size())
    }

    @Test
    fun inboxPlacementUsesItsOwnHostAndCamelCaseJson() {
        useSharedKey("ipt-key")

        await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.getInboxPlacementProviders(ok, err)
        }
        var request = onlyRequest()
        assertEquals("/api/ipt/providers", request.path)
        // LIVE: the Inbox Placement Tester authenticates with a Bearer header,
        // and the key must not reach the query string, where it would be logged.
        assertEquals("Bearer ipt-key", request.authorization)
        assertNull(request.fields["api_key"])

        await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.getInboxPlacementTestTypes(ok, err)
        }
        assertEquals("/api/ipt/test-types", onlyRequest().path)

        await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.createInboxPlacementTest(
                "Launch",
                ZBCreateInboxPlacementTestOptions(
                    testType = 1,
                    senderEmail = "news@example.com",
                    webhookUrl = "https://example.com/ipt",
                ),
                ok, err,
            )
        }
        request = onlyRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/ipt/test", request.path)
        // Bodies are camelCase JSON, and unset fields are left out.
        assertEquals("Launch", request.fields["name"])
        assertEquals("1", request.fields["testType"])
        assertEquals("news@example.com", request.fields["senderEmail"])
        assertEquals("https://example.com/ipt", request.fields["webhookUrl"])
        assertFalse(request.fields.containsKey("providerIds"))
        assertFalse(request.fields.containsKey("seedIds"))

        await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.listInboxPlacementTests(
                ZBListInboxPlacementTestsOptions(
                    status = "finished",
                    page = 1,
                    pageSize = 25,
                    createdAfter = "2026-01-01",
                    createdBefore = "2026-02-01",
                ),
                ok, err,
            )
        }
        request = onlyRequest()
        assertEquals("/api/ipt/tests", request.path)
        assertEquals("finished", request.fields["status"])
        assertEquals("1", request.fields["page"])
        assertEquals("2026-01-01", request.fields["createdAfter"])
        assertEquals("2026-02-01", request.fields["createdBefore"])
        // LIVE: the page size is page_size. Sent as pageSize it is ignored and
        // the whole list comes back.
        assertEquals("25", request.fields["page_size"])
        assertFalse(request.fields.containsKey("pageSize"))

        await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.listInboxPlacementTests(null, ok, err)
        }
        request = onlyRequest()
        assertFalse(request.fields.containsKey("status"))
        assertFalse(request.fields.containsKey("page"))
        assertFalse(request.fields.containsKey("page_size"))
        assertFalse(request.fields.containsKey("createdAfter"))
        assertFalse(request.fields.containsKey("createdBefore"))

        await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.deleteInboxPlacementTest("a b/c", ok, err)
        }
        request = onlyRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/ipt/test/a%20b%2Fc", request.path)

        await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.setInboxPlacementWebhook("https://example.com/ipt", ok, err)
        }
        request = onlyRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/ipt/webhook", request.path)
        assertEquals("https://example.com/ipt", request.fields["webhookUrl"])

        await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.getInboxPlacementWebhook(ok, err)
        }
        assertEquals("/api/ipt/webhook", onlyRequest().path)

        val testId = await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.getInboxPlacementTest("  ", ok, err)
        }
        assertTrue(testId.errorText().orEmpty().contains("test_id"))
        val name = await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.createInboxPlacementTest(" ", null, ok, err)
        }
        assertTrue(name.errorText().orEmpty().contains("name"))
        val webhook = await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.setInboxPlacementWebhook("", ok, err)
        }
        assertTrue(webhook.errorText().orEmpty().contains("webhook_url"))
        assertEquals(0, fake.size())
    }

    @Test
    fun theIsoUploadDateTheLiveApiSendsParses() {
        // LIVE: bulk filestatus answers with an ISO-8601 upload_date, for
        // example 2026-10-24T05:24:13Z. It used to fail to parse, and the whole
        // response then came back as an error.
        val gson = com.google.gson.GsonBuilder()
            .registerTypeAdapter(java.util.Date::class.java, GsonDateDeserializer())
            .create()
        val accepted = listOf(
            "2026-10-24T05:24:13Z",
            "2026-10-24T05:24:13.123Z",
            "2026-10-24T05:24:13+02:00",
            "2023-04-18 14:40:08.123",
            "04/18/2023 2:40:08 PM",
        )
        for (text in accepted) {
            val parsed = gson.fromJson("""{"upload_date":"$text"}""", ZBFileStatusResponse::class.java)
            assertNotNull("could not parse $text", parsed)
        }
    }

    @Test
    fun successFalseOnAnHttp200IsAnError() {
        useSharedKey("success-false-key")

        // LIVE: the API answers an unknown file id with an HTTP 200 whose body
        // says success: false.
        val outcome = await<ZBFileStatusResponse> { ok, err ->
            ZeroBounceSDK.fileStatus("0d1b7a4e-1111-2222-3333-444455556666", ok, err)
        }
        assertTrue(outcome.errorText().orEmpty().contains("File cannot be found."))
        assertEquals(200, outcome.error?.statusCode)
    }

    @Test
    fun validateBatchSendsTimeoutAsAString() {
        useSharedKey("batch-key")

        // LIVE: sent as a JSON number the API rejects the whole batch with the
        // account-level "Invalid API Key or your account ran out of credits".
        await<ZBValidateBatchResponse> { ok, err ->
            ZeroBounceSDK.validateBatch(
                listOf(ZBValidateBatchData("a@example.com", null)),
                ZBValidateOptions(timeout = 30, verifyPlus = true),
                ok,
                err,
            )
        }
        val request = onlyRequest()
        assertEquals("30", request.fields["timeout"])
        assertEquals("true", request.fields["verify_plus"])
    }

    @Test
    fun bulkJobsRequireFileNameAndReturnUrl() {
        useSharedKey("bulk-key")
        val done = "https://example.com/done"

        // LIVE: both fields are required on every one of these jobs.
        val jobs = listOf<(ZBBulkFileJobOptions, (ZBSendFileResponse?) -> Unit, (ErrorResponse?) -> Unit) -> Unit>(
            ZeroBounceSDK::emailFinderS3File,
            ZeroBounceSDK::domainSearchS3File,
            ZeroBounceSDK::validateSFTPFile,
            ZeroBounceSDK::scoreSFTPFile,
            ZeroBounceSDK::emailFinderSFTPFile,
            ZeroBounceSDK::domainSearchSFTPFile,
        )
        for (job in jobs) {
            val missingName = await<ZBSendFileResponse> { ok, err ->
                job(ZBBulkFileJobOptions(returnUrl = done), ok, err)
            }
            assertTrue(missingName.errorText().orEmpty().contains("file_name"))

            val missingUrl = await<ZBSendFileResponse> { ok, err ->
                job(ZBBulkFileJobOptions(fileName = "f.csv"), ok, err)
            }
            assertTrue(missingUrl.errorText().orEmpty().contains("return_url"))
        }
        assertEquals(0, fake.size())
    }

    @Test
    fun inboxPlacementDetailErrorBecomesTheMessage() {
        useSharedKey("ipt-detail")

        val outcome = await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.getInboxPlacementTest("123", ok, err)
        }

        assertTrue(outcome.errorText().orEmpty().contains("Inbox placement test not found."))
    }

    @Test
    fun listEvaluatorAndInboxPlacementBaseUrlsAreOverridable() {
        // These are separate hosts, not paths built from the bulk base, so an
        // override of the main base URL alone would leave them on the live API.
        useSharedKey("eval-key")
        ZeroBounceSDK.setListEvaluatorBaseUrl("https://bulk.example.test/v2/listevaluator")
        ZeroBounceSDK.setInboxPlacementBaseUrl("https://bulk.example.test/api/ipt")

        await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.getListEvaluatorResult("fid", ok, err)
        }
        assertEquals("bulk.example.test", onlyRequest().host)

        await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.getInboxPlacementProviders(ok, err)
        }
        assertEquals("bulk.example.test", onlyRequest().host)

        var rejected = false
        try {
            ZeroBounceSDK.setListEvaluatorBaseUrl("http://evil.example")
        } catch (e: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("an http:// base URL should be rejected", rejected)
        resetSharedUrls()
    }

    @Test
    fun s3ValidateAndScoreRequireFileNameAndReturnUrl() {
        useSharedKey("s3-key")
        val done = "https://example.com/done"

        // LIVE: POST /validateS3file without a return_url answers 400
        // "Provided return URL is not permitted: None", and scoreS3file had no
        // way to send one at all, so it could never succeed.
        val validateNoUrl = await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.validateS3File(ZBValidateS3FileOptions(fileName = "inbox.csv"), ok, err)
        }
        assertTrue(validateNoUrl.errorText().orEmpty().contains("return_url"))

        val scoreNoUrl = await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.scoreS3File(ZBScoreS3FileOptions(fileName = "inbox.csv"), ok, err)
        }
        assertTrue(scoreNoUrl.errorText().orEmpty().contains("return_url"))

        val validateBlankUrl = await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.validateS3File(
                ZBValidateS3FileOptions(fileName = "inbox.csv", returnUrl = "  "),
                ok,
                err,
            )
        }
        assertTrue(validateBlankUrl.errorText().orEmpty().contains("return_url"))

        val scoreNoName = await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.scoreS3File(ZBScoreS3FileOptions(fileName = " ", returnUrl = done), ok, err)
        }
        assertTrue(scoreNoName.errorText().orEmpty().contains("file_name"))

        assertEquals(0, fake.size())

        // With both fields the call goes out, on the bulkapi root and as a form POST.
        await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.scoreS3File(
                ZBScoreS3FileOptions(
                    fileName = "inbox.csv",
                    returnUrl = done,
                    hasHeaderRow = true,
                    emailAddressColumn = 0,
                ),
                ok,
                err,
            )
        }
        val scored = onlyRequest()
        assertEquals("POST", scored.method)
        assertEquals("bulkapi.zerobounce.net", scored.host)
        assertEquals("/scoreS3file", scored.path)
        assertEquals("inbox.csv", scored.fields["file_name"])
        assertEquals(done, scored.fields["return_url"])
        assertEquals("true", scored.fields["has_header_row"])
        assertEquals("0", scored.fields["email_address_column"])
        assertEquals("s3-key", scored.fields["api_key"])
        assertNull(scored.authorization)
    }

    @Test
    fun s3JobsSurfaceAFailedBody() {
        useSharedKey("success-false-s3")
        val done = "https://example.com/done"

        // LIVE: a rejected job answers HTTP 200 with success: false.
        val validated = await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.validateS3File(
                ZBValidateS3FileOptions(fileName = "inbox.csv", returnUrl = done),
                ok,
                err,
            )
        }
        assertNull(validated.value)
        assertTrue(validated.errorText().orEmpty().contains("File cannot be found."))
        assertEquals(200, validated.error?.statusCode)

        val scored = await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.scoreS3File(
                ZBScoreS3FileOptions(fileName = "inbox.csv", returnUrl = done),
                ok,
                err,
            )
        }
        assertNull(scored.value)
        assertTrue(scored.errorText().orEmpty().contains("File cannot be found."))
    }

    @Test
    fun emailFinderNeedsAFirstNameOrFullNameColumn() {
        useSharedKey("finder-key")

        // The docs require first_name_column or full_name_column next to
        // domain_column: without a name there is nothing to build an address from.
        val noOptions = await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.sendEmailFinderFile(csvFile, 1, null, ok, err)
        }
        assertTrue(noOptions.errorText().orEmpty().contains("first_name_column or full_name_column"))

        val onlyLastName = await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.sendEmailFinderFile(
                csvFile,
                1,
                ZBEmailFinderSendFileOptions(lastNameColumn = 2),
                ok,
                err,
            )
        }
        assertTrue(onlyLastName.errorText().orEmpty().contains("first_name_column or full_name_column"))

        val stream = await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.sendEmailFinderFileStream(
                ByteArrayInputStream("example.com\n".toByteArray()),
                "finder.csv",
                1,
                ZBEmailFinderSendFileOptions(hasHeaderRow = true),
                ok,
                err,
            )
        }
        assertTrue(stream.errorText().orEmpty().contains("first_name_column or full_name_column"))
        assertEquals(0, fake.size())

        // A full name column on its own is enough.
        await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.sendEmailFinderFileStream(
                ByteArrayInputStream("example.com,Ada Lovelace\n".toByteArray()),
                "finder.csv",
                1,
                ZBEmailFinderSendFileOptions(fullNameColumn = 2),
                ok,
                err,
            )
        }
        val request = onlyRequest()
        assertEquals("POST", request.method)
        assertEquals("bulkapi.zerobounce.net", request.host)
        assertEquals("/email-finder/sendfile", request.path)
        assertEquals("2", request.fields["full_name_column"])
        assertEquals("finder-key", request.fields["api_key"])
    }

    @Test
    fun createInboxPlacementTestChecksTheDocumentedLimits() {
        useSharedKey("ipt-limits")

        val longName = await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.createInboxPlacementTest("n".repeat(65), null, ok, err)
        }
        assertTrue(longName.errorText().orEmpty().contains("name"))
        assertTrue(longName.errorText().orEmpty().contains("64"))

        val badType = await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.createInboxPlacementTest(
                "Launch",
                ZBCreateInboxPlacementTestOptions(testType = 4, senderEmail = "news@example.com"),
                ok, err,
            )
        }
        assertTrue(badType.errorText().orEmpty().contains("test_type"))

        // Type 1 mails the seeds from your own address, so it has to be given.
        val noSender = await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.createInboxPlacementTest(
                "Launch",
                ZBCreateInboxPlacementTestOptions(testType = 1),
                ok, err,
            )
        }
        assertTrue(noSender.errorText().orEmpty().contains("sender_email"))

        val blankSender = await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.createInboxPlacementTest(
                "Launch",
                ZBCreateInboxPlacementTestOptions(testType = 1, senderEmail = " "),
                ok, err,
            )
        }
        assertTrue(blankSender.errorText().orEmpty().contains("sender_email"))
        assertEquals(0, fake.size())

        // A name of exactly 64 characters is allowed, and type 2 needs no sender.
        await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.createInboxPlacementTest(
                "n".repeat(64),
                ZBCreateInboxPlacementTestOptions(testType = 2),
                ok, err,
            )
        }
        val request = onlyRequest()
        assertEquals("POST", request.method)
        assertEquals("bulkapi.zerobounce.net", request.host)
        assertEquals("/api/ipt/test", request.path)
        assertEquals("Bearer ipt-limits", request.authorization)
        assertEquals("n".repeat(64), request.fields["name"])
        assertEquals("2", request.fields["testType"])
        assertFalse(request.fields.containsKey("senderEmail"))
    }

    @Test
    fun listInboxPlacementTestsBoundsThePageSize() {
        useSharedKey("ipt-page")

        for (size in listOf(0, 101)) {
            val outcome = await<com.google.gson.JsonElement> { ok, err ->
                ZeroBounceSDK.listInboxPlacementTests(
                    ZBListInboxPlacementTestsOptions(pageSize = size), ok, err,
                )
            }
            assertTrue(outcome.errorText().orEmpty().contains("page_size"))
        }
        assertEquals(0, fake.size())

        for (size in listOf(1, 100)) {
            await<com.google.gson.JsonElement> { ok, err ->
                ZeroBounceSDK.listInboxPlacementTests(
                    ZBListInboxPlacementTestsOptions(pageSize = size), ok, err,
                )
            }
            assertEquals(size.toString(), onlyRequest().fields["page_size"])
        }
    }

    @Test
    fun inboxPlacementListAndCreateSurfaceTheDetailError() {
        useSharedKey("ipt-detail")

        val listed = await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.listInboxPlacementTests(ZBListInboxPlacementTestsOptions(pageSize = 10), ok, err)
        }
        assertNull(listed.value)
        assertTrue(listed.errorText().orEmpty().contains("Inbox placement test not found."))
        expectShort(listed.errorText()!!)

        val created = await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.createInboxPlacementTest("Launch", null, ok, err)
        }
        assertNull(created.value)
        assertTrue(created.errorText().orEmpty().contains("Inbox placement test not found."))
    }

    @Test
    fun theWholeListEvaluatorFlowFollowsItsBaseUrl() {
        // Overriding the List Evaluator host used to move only the result
        // fetch: the upload and send-evaluated-file were built from the bulk
        // base and kept going to the live API.
        useSharedKey("eval-key")
        ZeroBounceSDK.setListEvaluatorBaseUrl("https://bulk.example.test/v2/listevaluator")

        await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.sendListEvaluatorFile(csvFile, 1, ok, err)
        }
        var request = onlyRequest()
        assertEquals("POST", request.method)
        assertEquals("bulk.example.test", request.host)
        assertEquals("/v2/listevaluator/", request.path)
        assertEquals("1", request.fields["email_address_column"])

        await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.sendListEvaluatorFileStream(
                ByteArrayInputStream("a@example.com\n".toByteArray()),
                "list.csv",
                1,
                ok,
                err,
            )
        }
        request = onlyRequest()
        assertEquals("bulk.example.test", request.host)
        assertEquals("/v2/listevaluator/", request.path)

        await<com.google.gson.JsonElement> { ok, err ->
            ZeroBounceSDK.getListEvaluatorResult("fid", ok, err)
        }
        request = onlyRequest()
        assertEquals("bulk.example.test", request.host)
        assertEquals("/v2/listevaluator/fid/", request.path)

        // send-evaluated-file is the third leg of the same flow and sits one
        // path segment above the base.
        await<ZBSendFileResponse> { ok, err -> ZeroBounceSDK.sendEvaluatedFile("eval-1", ok, err) }
        request = onlyRequest()
        assertEquals("POST", request.method)
        assertEquals("bulk.example.test", request.host)
        assertEquals("/v2/send-evaluated-file/", request.path)
        assertEquals("eval-1", request.fields["file_id"])
        assertEquals("eval-key", request.fields["api_key"])

        resetSharedUrls()
    }

    private fun openClient(key: String): ZeroBounceClient {
        val sdk = ZeroBounceClient()
        sdk.transportInterceptor = fake
        sdk.initialize(key)
        return sdk
    }

    private fun useSharedKey(key: String) {
        resetSharedUrls()
        ZeroBounceSDK.transportInterceptor = fake
        ZeroBounceSDK.initialize(key)
    }

    private fun resetSharedUrls() {
        ZeroBounceSDK.apiBaseUrl = "https://api.zerobounce.net/v2"
        ZeroBounceSDK.bulkApiBaseUrl = "https://bulkapi.zerobounce.net/v2"
        ZeroBounceSDK.bulkApiScoringBaseUrl = "https://bulkapi.zerobounce.net/v2/scoring"
        ZeroBounceSDK.bulkApiRootUrl = "https://bulkapi.zerobounce.net"
        ZeroBounceSDK.listEvaluatorBaseUrl = "https://bulkapi.zerobounce.net/v2/listevaluator"
        ZeroBounceSDK.inboxPlacementBaseUrl = "https://bulkapi.zerobounce.net/api/ipt"
    }

    private fun onlyRequest(): RecordedRequest {
        val records = fake.take()
        assertEquals(records.joinToString { it.path }, 1, records.size)
        return records.first()
    }

    private fun expectShort(message: String) {
        assertTrue("error is too long: ${message.take(120)}", message.length < 500)
        assertFalse(message.contains("<html"))
        assertFalse(message.contains("<!DOCTYPE"))
        assertFalse(message.contains("<p>blocked</p>"))
        assertFalse(message.contains("Error: Error:"))
        assertFalse(message.contains("<<<not-json>>>"))
    }

    private fun publishVersion(): String {
        var dir = File(System.getProperty("user.dir") ?: ".")
        for (i in 0..5) {
            val candidate = File(dir, "zero_bounce_sdk/build.gradle")
            if (candidate.isFile) {
                val match = Regex("PUBLISH_VERSION\\s*=\\s*'([^']+)'").find(candidate.readText())
                return match?.groupValues?.get(1) ?: ""
            }
            dir = dir.parentFile ?: break
        }
        return ""
    }

    private data class Outcome<T>(val value: T?, val error: ErrorResponse?) {
        fun errorText(): String? {
            val text = error?.errors?.joinToString("\n")?.trim().orEmpty()
            return text.ifEmpty { null }
        }
    }

    private fun <T> await(start: ((T?) -> Unit, (ErrorResponse?) -> Unit) -> Unit): Outcome<T> {
        val latch = CountDownLatch(1)
        var value: T? = null
        var error: ErrorResponse? = null
        start(
            { parsed ->
                value = parsed
                latch.countDown()
            },
            { parsed ->
                error = parsed
                latch.countDown()
            },
        )
        assertTrue("timed out with ${fake.size()} request(s)", latch.await(8, TimeUnit.SECONDS))
        return Outcome(value, error)
    }
}

internal class RecordedRequest(
    val method: String,
    val host: String,
    /** The percent-encoded path, which is what goes on the wire. */
    val path: String,
    val apiKey: String,
    val fields: Map<String, String>,
    val userAgent: String?,
    /** The Authorization header, for the Inbox Placement Tester's Bearer key. */
    val authorization: String? = null,
)

internal class FakeApi : Interceptor {
    private val records = CopyOnWriteArrayList<RecordedRequest>()

    fun take(): List<RecordedRequest> {
        val copy = records.toList()
        records.clear()
        return copy
    }

    fun size(): Int = records.size

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val fields = linkedMapOf<String, String>()
        for (name in request.url.queryParameterNames) {
            fields[name] = request.url.queryParameter(name).orEmpty()
        }
        readBody(request.body, fields)
        val authorization = request.header("Authorization")
        val recorded = RecordedRequest(
            method = request.method,
            host = request.url.host,
            path = request.url.encodedPath,
            // The Inbox Placement Tester authenticates with a Bearer header
            // rather than an api_key parameter, so the key can arrive either way.
            apiKey = fields["api_key"]
                ?: authorization?.removePrefix("Bearer ")?.takeIf { it != authorization }
                ?: "",
            fields = fields,
            userAgent = request.header("User-Agent"),
            authorization = authorization,
        )
        records.add(recorded)
        Thread.sleep(Random.nextInt(21).toLong())
        val answer = answer(recorded)
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(answer.code)
            .message(if (answer.code in 200..299) "OK" else "Error")
            .header("Content-Type", answer.contentType)
            .body(answer.body.toResponseBody(answer.contentType.toMediaType()))
            .build()
    }

    private fun readBody(body: okhttp3.RequestBody?, fields: MutableMap<String, String>) {
        when (body) {
            null -> return
            is FormBody -> {
                for (index in 0 until body.size) {
                    fields[body.name(index)] = body.value(index)
                }
            }
            is MultipartBody -> {
                for (part in body.parts) {
                    val disposition = part.headers?.get("Content-Disposition").orEmpty()
                    val name = NAME.find(disposition)?.groupValues?.get(1) ?: continue
                    val filename = FILE_NAME.find(disposition)?.groupValues?.get(1)
                    if (filename != null) {
                        fields[name] = "<file>"
                        fields["file_name"] = filename
                    } else {
                        val buffer = Buffer()
                        part.body.writeTo(buffer)
                        fields[name] = buffer.readUtf8()
                    }
                }
            }
            else -> {
                val buffer = Buffer()
                body.writeTo(buffer)
                val text = buffer.readUtf8()
                fields["__raw"] = text
                val trimmed = text.trim()
                if (trimmed.startsWith("{")) {
                    val obj = JsonParser.parseString(trimmed).asJsonObject
                    for ((key, value) in obj.entrySet()) {
                        fields[key] = when {
                            value.isJsonNull -> "null"
                            value.isJsonPrimitive && value.asJsonPrimitive.isString -> value.asString
                            else -> value.toString()
                        }
                    }
                }
            }
        }
    }

    private fun answer(request: RecordedRequest): Answer {
        val key = request.apiKey
        if (key.startsWith("html-403-") || key.startsWith("html-200-")) {
            val code = if (key.startsWith("html-403-")) 403 else 200
            return Answer(code, "text/html; charset=UTF-8", accessDeniedPage())
        }
        if (key.startsWith("success-false-")) {
            // LIVE: an unknown file id comes back as an HTTP 200 whose body
            // says the call failed.
            return Answer(
                200,
                "application/json",
                """{"success":false,"message":"File cannot be found."}""",
            )
        }
        if (key.startsWith("empty-")) {
            return Answer(200, "application/json", "")
        }
        if (key.startsWith("malformed-")) {
            return Answer(200, "application/json", "<<<not-json>>>" + "x".repeat(5000))
        }
        if (key.startsWith("cf-json-")) {
            return Answer(
                403,
                "application/json",
                """{"type":"https://developers.cloudflare.com/support/troubleshooting/http-status-codes/cloudflare-1xxx-errors/error-1020/","title":"Error 1020: Access denied","status":403,"detail":"The request was blocked by a Cloudflare firewall rule configured by the site owner.","error_code":1020,"cloudflare_error":true,"what_you_should_do":"Do not retry. This block is intentional."}""",
            )
        }
        if (key.startsWith("ipt-detail")) {
            // The Inbox Placement Tester is a Django REST framework app and
            // reports errors under "detail".
            return Answer(404, "application/json", """{"detail":"Inbox placement test not found."}""")
        }
        if (key.startsWith("double-error-")) {
            return Answer(400, "text/plain", "Error: Error: api_key is invalid")
        }
        if (key.startsWith("jsonerr-")) {
            return Answer(200, "text/plain", """{"success":false,"message":"file not ready"}""")
        }
        if (request.path.contains("validatebatch") && key.startsWith("batch-account-")) {
            return Answer(
                200,
                "application/json",
                """{"email_batch":[],"errors":[{"email_address":"all","error":"Invalid API Key or your account ran out of credits"}]}""",
            )
        }
        if (request.path.contains("validatebatch") && key.startsWith("batch-partial-")) {
            return Answer(
                200,
                "application/json",
                """{"email_batch":[{"address":"good@example.com","status":"valid"}],"errors":[{"email_address":"bad@","error":"Invalid email address"}]}""",
            )
        }
        val quoted = jsonString(key)
        val body = when {
            request.path.contains("getfile") -> return Answer(
                200,
                "application/octet-stream",
                "email\n$key@example.com\n",
            )
            request.path.contains("listS3files") -> """[{"name":$quoted,"size":1}]"""
            request.path.contains("validatebatch") ->
                """{"email_batch":[{"address":$quoted,"status":"valid"}],"errors":[]}"""
            request.path.endsWith("/validate") -> """{"address":$quoted,"status":"valid"}"""
            request.path.endsWith("/scoring") -> """{"email":$quoted,"score":10}"""
            request.path.contains("getcredits") -> """{"Credits":"7"}"""
            request.path.contains("getapiusage") -> """{"total":1}"""
            request.path.endsWith("/activity") -> """{"found":true,"active_in_days":3}"""
            request.path.contains("filters") ||
                (request.method == "GET" && request.path.contains("listevaluator")) ->
                """{"echo_api_key":$quoted}"""
            request.path.contains("guessformat") -> guessFormatBody(quoted)
            else ->
                """{"success":true,"message":"ok","file_id":$quoted,"file_name":"f.csv","email":$quoted,"domain":$quoted,"format":"first.last","status":"valid","sub_status":"","confidence":"high","email_confidence":"high","company_name":"","did_you_mean":"","failure_reason":"","other_domain_formats":[]}"""
        }
        return Answer(200, "application/json", body)
    }

    private fun guessFormatBody(quoted: String): String =
        """{"email":$quoted,"email_confidence":"high","domain":$quoted,"company_name":"","format":"first.last","status":"valid","sub_status":"","confidence":"high","did_you_mean":"","failure_reason":"","other_domain_formats":[]}"""

    private data class Answer(val code: Int, val contentType: String, val body: String)

    companion object {
        private val NAME = Regex("""name="([^"]*)"""")
        private val FILE_NAME = Regex("""filename="([^"]*)"""")

        fun accessDeniedPage(): String = buildString {
            append("<!DOCTYPE html><html lang=\"en-US\"><head><title>")
            append("Access denied | zerobounce.net | Zero Bounce")
            append("</title></head><body><p>blocked</p>")
            repeat(1_000_000) { append(' ') }
            append("</body></html>")
        }

        private fun jsonString(value: String): String =
            buildString {
                append('"')
                for (ch in value) {
                    when (ch) {
                        '\\' -> append("\\\\")
                        '"' -> append("\\\"")
                        else -> append(ch)
                    }
                }
                append('"')
            }
    }
}
