package com.zerobounce.android

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The public request methods of [ZeroBounceClient] and [ZeroBounceWarmup], found by
 * reflection (every public method that takes a callback), with arguments that make
 * each one send a request. Sweeps built on it are complete by construction:
 * [missing] and [stale] name a public request method with no arguments here and an
 * entry that matches no method, so a new request path cannot skip a sweep.
 */
internal class RequestSweep(private val context: Context, private val dir: File, private val csv: File) {

    private val files = AtomicInteger()

    /** The download path handed to the most recent call, or null. */
    var lastOut: String? = null

    /** Called with each download path before the call is made, to put a file there first. */
    var onOut: (File) -> Unit = {}

    private fun out(): String = File(dir, "out-${files.incrementAndGet()}.csv").also { onOut(it) }.path
        .also { lastOut = it }

    private fun stream() = ByteArrayInputStream("email\na@example.com\n".toByteArray())

    private fun job() = ZBBulkFileJobOptions(fileName = "f.csv", returnUrl = "https://example.com/done")

    /** Signature, as [signature] prints it, to the arguments other than the callbacks. */
    val clientArgs: Map<String, () -> Array<Any?>> = run {
        val email = "a@example.com"
        linkedMapOf(
            "validate(String,String)" to { arrayOf(email, null) },
            "validate(String,String,ZBValidateOptions)" to { arrayOf(email, null, ZBValidateOptions()) },
            "validateBatch(List)" to { arrayOf(listOf(ZBValidateBatchData(email, null))) },
            "validateBatch(List,ZBValidateOptions)" to { arrayOf(listOf(ZBValidateBatchData(email, null)), null) },
            "guessFormat(String,String,String,String)" to { arrayOf("example.com", "Ann", null, null) },
            "findEmail(String,String,String,String,String)" to { arrayOf("Ann", "example.com", null, null, null) },
            "findDomain(String,String)" to { arrayOf("example.com", null) },
            "getApiUsage(Date,Date)" to { arrayOf(Date(0), Date(86_400_000L)) },
            "getCredits()" to { arrayOf() },
            "getActivityData(String)" to { arrayOf(email) },
            "scoreEmail(String)" to { arrayOf(email) },
            "scoreEmailPost(String)" to { arrayOf(email) },
            // bulk validation and scoring
            "sendFile(Context,File,int,Integer,Integer,Integer,Integer,String,boolean,Boolean,Boolean)" to
                { arrayOf(context, csv, 1, null, null, null, null, null, false, null, null) },
            "sendFile(InputStream,String,int,Integer,Integer,Integer,Integer,String,boolean,Boolean,Boolean)" to
                { arrayOf(stream(), "in.csv", 1, null, null, null, null, null, false, null, null) },
            "fileStatus(String)" to { arrayOf("fid") },
            "getFile(Context,String,ZBDownloadType,Boolean)" to { arrayOf(context, "fid", null, null) },
            "deleteFile(String)" to { arrayOf("fid") },
            "scoringSendFile(Context,File,int,String,boolean,Boolean)" to { arrayOf(context, csv, 1, null, false, null) },
            "scoringSendFile(InputStream,String,int,String,boolean,Boolean)" to { arrayOf(stream(), "in.csv", 1, null, false, null) },
            "scoringFileStatus(String)" to { arrayOf("fid") },
            "scoringGetFile(Context,String,ZBDownloadType)" to { arrayOf(context, "fid", null) },
            "scoringDeleteFile(String)" to { arrayOf("fid") },
            // email finder and domain search files
            "sendEmailFinderFile(File,int,ZBEmailFinderSendFileOptions)" to
                { arrayOf(csv, 1, ZBEmailFinderSendFileOptions(firstNameColumn = 2)) },
            "sendEmailFinderFileStream(InputStream,String,int,ZBEmailFinderSendFileOptions)" to
                { arrayOf(stream(), "in.csv", 1, ZBEmailFinderSendFileOptions(firstNameColumn = 2)) },
            "emailFinderFileStatus(String)" to { arrayOf("fid") },
            "emailFinderGetFile(String,String)" to { arrayOf("fid", out()) },
            "emailFinderDeleteFile(String)" to { arrayOf("fid") },
            "sendDomainSearchFile(File,int,ZBDomainSearchSendFileOptions)" to { arrayOf(csv, 1, null) },
            "sendDomainSearchFileStream(InputStream,String,int,ZBDomainSearchSendFileOptions)" to
                { arrayOf(stream(), "in.csv", 1, null) },
            "domainSearchFileStatus(String)" to { arrayOf("fid") },
            "domainSearchGetFile(String,String)" to { arrayOf("fid", out()) },
            "domainSearchDeleteFile(String)" to { arrayOf("fid") },
            // list evaluator
            "sendListEvaluatorFile(File,int)" to { arrayOf(csv, 1) },
            "sendListEvaluatorFileStream(InputStream,String,int)" to { arrayOf(stream(), "in.csv", 1) },
            "getListEvaluatorResult(String)" to { arrayOf("fid") },
            "sendEvaluatedFile(String)" to { arrayOf("fid") },
            // filters
            "addFilter(String,String,String)" to { arrayOf("allow", "email", email) },
            "deleteFilter(String,String,String)" to { arrayOf("allow", "email", email) },
            "listFilters(boolean)" to { arrayOf(false) },
            // S3 and SFTP jobs
            "listS3Files(String,String)" to { arrayOf("bucket", null) },
            "validateS3File(ZBValidateS3FileOptions)" to
                { arrayOf(ZBValidateS3FileOptions(fileName = "f.csv", returnUrl = "https://example.com/done")) },
            "scoreS3File(ZBScoreS3FileOptions)" to
                { arrayOf(ZBScoreS3FileOptions(fileName = "f.csv", returnUrl = "https://example.com/done")) },
            "exportS3File(String,String)" to { arrayOf("fid", "bucket") },
            "emailFinderS3File(ZBBulkFileJobOptions)" to { arrayOf(job()) },
            "domainSearchS3File(ZBBulkFileJobOptions)" to { arrayOf(job()) },
            "validateSFTPFile(ZBBulkFileJobOptions)" to { arrayOf(job()) },
            "scoreSFTPFile(ZBBulkFileJobOptions)" to { arrayOf(job()) },
            "emailFinderSFTPFile(ZBBulkFileJobOptions)" to { arrayOf(job()) },
            "domainSearchSFTPFile(ZBBulkFileJobOptions)" to { arrayOf(job()) },
            "exportSFTPFile(String,String)" to { arrayOf("fid", "/out") },
            // Inbox Placement Tester
            "getInboxPlacementProviders()" to { arrayOf() },
            "getInboxPlacementTestTypes()" to { arrayOf() },
            "createInboxPlacementTest(String,ZBCreateInboxPlacementTestOptions)" to { arrayOf("Launch", null) },
            "getInboxPlacementTest(String)" to { arrayOf("t1") },
            "listInboxPlacementTests(ZBListInboxPlacementTestsOptions)" to { arrayOf(null) },
            "deleteInboxPlacementTest(String)" to { arrayOf("t1") },
            "getInboxPlacementWebhook()" to { arrayOf() },
            "setInboxPlacementWebhook(String)" to { arrayOf("https://example.com/ipt") },
        )
    }

    val warmupArgs: Map<String, () -> Array<Any?>> = run {
        val from = "sender@example.com"
        linkedMapOf(
            "listSendingAccounts()" to { arrayOf() },
            "getSendingAccount(String)" to { arrayOf(from) },
            "addSendingAccount(String,String)" to { arrayOf(from, "zb_managed") },
            "addSendingAccount(String,String,ZBWarmupAddSendingAccountOptions)" to
                { arrayOf(from, "zb_managed", ZBWarmupAddSendingAccountOptions()) },
            "updateSendingAccount(String,int,int,boolean)" to { arrayOf(from, 0, 4, true) },
            "deleteSendingAccount(String)" to { arrayOf(from) },
            "changeFromAddress(String,String)" to { arrayOf(from, "new@example.com") },
            "listFromAddresses()" to { arrayOf() },
            "listFromAddresses(String)" to { arrayOf(null) },
            "listFromAddresses(String,Integer)" to { arrayOf(null, null) },
            "listFromAddresses(String,Integer,Integer)" to { arrayOf(null, null, null) },
            "getEngagementRules()" to { arrayOf() },
            "setEngagementRule(String,int)" to { arrayOf(from, 1) },
            "getCustomEngagement(String)" to { arrayOf(from) },
            "createCustomEngagement(String,int,int,int)" to { arrayOf(from, 10, 10, 10) },
            "updateCustomEngagement(String,int,int,int)" to { arrayOf(from, 10, 10, 10) },
            "deleteCustomEngagement(String,int)" to { arrayOf(from, 1) },
            "getSeeds()" to { arrayOf() },
            "downloadSeeds()" to { arrayOf() },
            "getSeedCount()" to { arrayOf() },
            "getProgressToday()" to { arrayOf() },
            "getSentToday()" to { arrayOf() },
            "getEmailsPerDay()" to { arrayOf() },
            "getStatistics(String)" to { arrayOf(from) },
            "getStatisticsPerDay(String,String,String)" to { arrayOf(from, "2026-01-01", "2026-01-31") },
            "getStatisticsDetailed(String)" to { arrayOf(from) },
            "downloadStatistics(String)" to { arrayOf(from) },
            "getFromAddressStatistics(String)" to { arrayOf(from) },
            "checkDomainDmarc(String)" to { arrayOf(from) },
            "checkDomainDmarc(String,String)" to { arrayOf(from, null) },
            "testSmtpConfiguration(String,String,String,String,int)" to
                { arrayOf(from, "smtp-user", SMTP_PASSWORD, "smtp.example.com", 587) },
        )
    }

    private fun argsFor(type: Class<*>) = if (type == ZeroBounceWarmup::class.java) warmupArgs else clientArgs

    /** Public request methods of [type] with no arguments in the sweep. */
    fun missing(type: Class<*>): Set<String> =
        requestMethods(type).map { signature(it) }.toSortedSet() - argsFor(type).keys

    /** Sweep entries that match no public request method of [type]. */
    fun stale(type: Class<*>): Set<String> =
        argsFor(type).keys.toSortedSet() - requestMethods(type).map { signature(it) }.toSet()

    /** What one swept call reported. */
    class Outcome(val signature: String) {
        val successes = CopyOnWriteArrayList<Any?>()
        val errors = CopyOnWriteArrayList<ErrorResponse?>()
        /** The download path handed to the call, or null. */
        var out: String? = null
        var thrown: Throwable? = null
        var timedOut = false

        override fun toString(): String = "$signature: successes=$successes errors=" +
            errors.map { "${it?.statusCode} ${it?.errors}" } +
            (thrown?.let { " threw $it" } ?: "") + (if (timedOut) " NO CALLBACK" else "")
    }

    /**
     * Calls one public request method of [client] with its sweep arguments and
     * waits up to [seconds] for a callback. The first callback parameter is the
     * response callback, the last one the error callback.
     */
    fun invoke(client: Any, method: Method, seconds: Long = 10): Outcome {
        val signature = signature(method)
        val outcome = Outcome(signature)
        lastOut = null
        val before = argsFor(method.declaringClass)[signature]?.invoke()
            ?: throw IllegalStateException("$signature is not in the sweep")
        outcome.out = lastOut
        val latch = CountDownLatch(1)
        val callbacks = method.parameterTypes.indices.filter { isCallback(method.parameterTypes[it]) }
        val success: (Any?) -> Unit = { outcome.successes.add(it); latch.countDown() }
        val error: (Any?) -> Unit = { outcome.errors.add(it as ErrorResponse?); latch.countDown() }
        var next = 0
        val args = method.parameterTypes.indices.map { index ->
            when (index) {
                callbacks.first() -> success
                callbacks.last() -> error
                else -> before[next++]
            }
        }
        check(before.size == next) { "$signature: argument count" }
        try {
            method.invoke(client, *args.toTypedArray())
        } catch (e: InvocationTargetException) {
            outcome.thrown = e.cause
            return outcome
        }
        if (!latch.await(seconds, TimeUnit.SECONDS)) outcome.timedOut = true
        return outcome
    }

    companion object {
        /** The SMTP password the sweep sends, with a quote and a slash, which JSON escaping changes. */
        const val SMTP_PASSWORD = "smtp\"pa/ss"

        fun isCallback(type: Class<*>) = type == Function1::class.java

        /** The public request methods of [type]: public instance methods that take a callback. */
        fun requestMethods(type: Class<*>): List<Method> = type.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !Modifier.isStatic(it.modifiers) }
            .filter { !it.isSynthetic && !it.isBridge && !it.name.contains('$') }
            .filter { method -> method.parameterTypes.any { isCallback(it) } }
            .sortedBy { signature(it) }

        /** `name(Type,Type)` with simple type names, callbacks left out. */
        fun signature(method: Method): String = method.name + method.parameterTypes
            .filterNot { isCallback(it) }
            .joinToString(",", "(", ")") { it.simpleName }
    }
}
