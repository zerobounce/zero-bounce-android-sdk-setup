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
                ZBEmailFinderSendFileOptions(hasHeaderRow = false, firstNameColumn = 0),
                ok,
                err,
            )
        }
        val sent = onlyRequest()
        assertEquals("bulkapi.zerobounce.net", sent.host)
        assertEquals("/email-finder/sendfile", sent.path)
        assertEquals("1", sent.fields["domain_column"])
        assertEquals("false", sent.fields["has_header_row"])
        assertFalse(sent.fields.containsKey("first_name_column"))
        assertEquals("p3-key", sent.apiKey)

        await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.sendEmailFinderFileStream(
                ByteArrayInputStream("example.com\n".toByteArray()),
                "finder.csv",
                2,
                ZBEmailFinderSendFileOptions(lastNameColumn = 3),
                ok,
                err,
            )
        }
        val streamed = onlyRequest()
        assertEquals("/email-finder/sendfile", streamed.path)
        assertEquals("2", streamed.fields["domain_column"])
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

        await<ZBSendFileResponse> { ok, err -> ZeroBounceSDK.sendEvaluatedFile(csvFile, ok, err) }
        assertEquals("/v2/send-evaluated-file/", onlyRequest().path)

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
                ),
                ok,
                err,
            )
        }
        val validated = onlyRequest()
        assertEquals("POST", validated.method)
        assertEquals("/validateS3file", validated.path)
        assertEquals("inbox.csv", validated.fields["file_name"])
        assertEquals("0", validated.fields["email_address_column"])
        assertEquals("false", validated.fields["has_header_row"])
        assertEquals("true", validated.fields["remove_duplicate"])
        assertFalse(validated.fields.containsKey("remove_duplicates"))
        assertFalse(validated.fields.containsKey("first_name_column"))

        await<ZBSendFileResponse> { ok, err ->
            ZeroBounceSDK.scoreS3File(ZBScoreS3FileOptions(fileName = "inbox.csv"), ok, err)
        }
        val scored = onlyRequest()
        assertEquals("/scoreS3file", scored.path)
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
    val path: String,
    val apiKey: String,
    val fields: Map<String, String>,
    val userAgent: String?,
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
        val recorded = RecordedRequest(
            method = request.method,
            host = request.url.host,
            path = request.url.encodedPath,
            apiKey = fields["api_key"].orEmpty(),
            fields = fields,
            userAgent = request.header("User-Agent"),
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
