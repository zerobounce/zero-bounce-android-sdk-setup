package com.zerobounce.android

import android.content.Context
import com.google.gson.FieldNamingPolicy
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import java.util.Date

/**
 * Field types are part of the shape (check E7, standard.md section 3): on a typed
 * call, a field that arrives with a JSON type other than the documented one is an
 * API error with status 200, never coerced (`true` read as `"true"`), defaulted or
 * dropped. `getCredits` needs `Credits` as a whole number (a JSON string or a
 * JSON integer), `validate` and every `validateBatch` row need `address` and
 * `status` as strings, and a JSON array is not an object. Every example response
 * still decodes.
 */
class OfflineFieldTypeTest {

    /** JSON names a call cannot answer without (null is an error for these). */
    private val REQUIRED = setOf("Credits", "address", "status")

    private val context: Context = mock()
    private lateinit var dir: File
    private lateinit var csv: File

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "zb-android-fields-${System.nanoTime()}")
        dir.mkdirs()
        whenever(context.getExternalFilesDir(null)).thenReturn(dir)
        csv = File(dir, "in.csv").apply { writeText("email\na@example.com\n") }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private val kinds: Map<String, Class<*>> = mapOf(
        "credits" to ZBCreditsResponse::class.java,
        "apiusage" to ZBGetApiUsageResponse::class.java,
        "activity" to ZBActivityDataResponse::class.java,
        "validate" to ZBValidateResponse::class.java,
        "validatebatch" to ZBValidateBatchResponse::class.java,
        "sendfile" to ZBSendFileResponse::class.java,
        "filestatus" to ZBFileStatusResponse::class.java,
        "deletefile" to ZBDeleteFileResponse::class.java,
        "guessformat" to ZBEmailFinderResponse::class.java,
        "findemail" to ZBFindEmailResponse::class.java,
        "domainsearch" to ZBFindDomainResponse::class.java,
    )

    /** The documented example responses, by the success type of the calls they answer. */
    private fun documented(): Map<Class<*>, List<String>> {
        val stream = javaClass.getResourceAsStream("/documented-responses.tsv")
        assertNotNull("documented-responses.tsv is on the test classpath", stream)
        val bodies = LinkedHashMap<Class<*>, MutableList<String>>()
        stream!!.use { it.readBytes().toString(Charsets.UTF_8) }
            .split("\n")
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { line ->
                val fields = line.split("\t", limit = 2)
                val type = kinds[fields[0]]
                assertNotNull("unknown kind ${fields[0]}", type)
                bodies.getOrPut(type!!) { mutableListOf() }.add(JsonParser.parseString(fields[1]).asString)
            }
        assertEquals("every kind has an example", kinds.size, bodies.size)
        return bodies
    }

    private fun isDownload(method: Method): Boolean {
        val name = method.name.lowercase()
        return name.contains("getfile") || name.startsWith("download")
    }

    private fun unwrap(type: Type): Type =
        if (type is WildcardType) (type.lowerBounds.firstOrNull() ?: type.upperBounds[0]) else type

    private fun responseType(method: Method): Type {
        val callback = method.genericParameterTypes.first {
            it is ParameterizedType && it.rawType == Function1::class.java
        } as ParameterizedType
        return unwrap(callback.actualTypeArguments[0])
    }

    /** Every typed request method: client calls that are not downloads and do not return raw JSON. */
    private fun typedMethods(): List<Method> =
        RequestSweep.requestMethods(ZeroBounceClient::class.java)
            .filter { !isDownload(it) && responseType(it) != JsonElement::class.java }
            .also { assertTrue("typed calls: ${it.size}", it.size > 40) }

    private fun run(
        methods: List<Method>,
        body: String,
        check: (String, RequestSweep.Outcome) -> Unit,
        status: Int = 200,
        contentType: String = "application/json",
    ) {
        val interceptor = Interceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("OK")
                .body(body.toByteArray(Charsets.UTF_8).toResponseBody(contentType.toMediaType()))
                .build()
        }
        val sweep = RequestSweep(context, dir, csv)
        val client = ZeroBounceClient()
        client.transportInterceptor = interceptor
        client.initialize("field-type-key")
        for (method in methods) {
            check(body, sweep.invoke(client, method))
        }
    }

    private fun expectError(failures: MutableList<String>): (String, RequestSweep.Outcome) -> Unit = { body, outcome ->
        if (outcome.thrown != null || outcome.timedOut || outcome.successes.isNotEmpty() ||
            outcome.errors.size != 1 || outcome.errors[0]?.statusCode != 200
        ) {
            failures.add("$body $outcome")
        }
    }

    private fun expectSuccess(failures: MutableList<String>): (String, RequestSweep.Outcome) -> Unit = { body, outcome ->
        if (outcome.thrown != null || outcome.timedOut || outcome.successes.size != 1 || outcome.errors.isNotEmpty()) {
            failures.add("$body $outcome")
        }
    }

    private fun jsonName(field: Field): String =
        field.getAnnotation(SerializedName::class.java)?.value
            ?: FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES.translateName(field)

    private fun modelFields(type: Class<*>): List<Field> =
        generateSequence(type) { it.superclass }.takeWhile { it != Any::class.java }
            .flatMap { it.declaredFields.asSequence() }
            .filter { !Modifier.isStatic(it.modifiers) && !Modifier.isTransient(it.modifiers) && !it.isSynthetic }
            .toList()

    private fun isModel(type: Class<*>) = type.packageName == "com.zerobounce.android" && !type.isEnum

    private fun rawClass(type: Type): Class<*> = when (val t = unwrap(type)) {
        is ParameterizedType -> t.rawType as Class<*>
        else -> t as Class<*>
    }

    /**
     * JSON values of the wrong type for a field of [type]. A list also gets each
     * wrong element, and a model each of its own fields wrong, one at a time.
     */
    private fun wrongValues(type: Type, field: Field? = null): List<JsonElement> {
        val raw = rawClass(type)
        val wrong = when {
            // Credits is a number or a string holding one, so a number is not wrong there.
            raw == String::class.java && field?.getAnnotation(JsonAdapter::class.java) != null ->
                listOf("true", "[]", "[5]", "{}")
            raw == String::class.java -> listOf("true", "5", "[]", "[5]", "{}")
            raw.isEnum -> listOf("5", "true", "[]", "{}")
            raw == java.lang.Boolean::class.java || raw == java.lang.Boolean.TYPE -> listOf("\"yes\"", "[]", "{}")
            raw == java.lang.Integer::class.java || raw == Integer.TYPE || raw == java.lang.Long::class.java ||
                raw == java.lang.Long.TYPE || raw == java.lang.Double::class.java || raw == java.lang.Double.TYPE ->
                listOf("true", "\"abc\"", "[]", "{}")
            raw == Date::class.java -> listOf("true", "5", "[]", "{}")
            List::class.java.isAssignableFrom(raw) -> listOf("5", "\"x\"", "true", "{}")
            Map::class.java.isAssignableFrom(raw) || isModel(raw) -> listOf("5", "\"x\"", "true", "[]")
            else -> throw AssertionError("no wrong values for $type")
        }
        val values = wrong.map { JsonParser.parseString(it) }.toMutableList()
        if (List::class.java.isAssignableFrom(raw)) {
            val element = unwrap((unwrap(type) as ParameterizedType).actualTypeArguments[0])
            val inner = wrongValues(element) +
                (if (element is Class<*> && isModel(element)) nestedWrongObjects(element) else emptyList())
            inner.forEach { values.add(JsonArray().apply { add(it) }) }
        } else if (isModel(raw)) {
            values.addAll(nestedWrongObjects(raw))
        }
        return values
    }

    private fun nestedWrongObjects(model: Class<*>): List<JsonElement> =
        modelFields(model).flatMap { field ->
            wrongValues(field.genericType, field).map { value -> JsonObject().apply { add(jsonName(field), value) } }
        }

    /**
     * Complete by construction: every field of every typed call's result type
     * (found by reflection, nested rows included) is sent once with each JSON type
     * it does not have, inside a body that is otherwise a valid answer, and every
     * typed call reports one error with status 200.
     */
    @Test
    fun aFieldOfTheWrongJsonTypeIsAnErrorWithStatus200OnEveryTypedCall() {
        val documented = documented()
        val failures = mutableListOf<String>()
        var bodies = 0
        for ((type, methods) in typedMethods().groupBy { responseType(it) }) {
            val list = type is ParameterizedType && type.rawType == List::class.java
            val model = if (list) rawClass((type as ParameterizedType).actualTypeArguments[0]) else rawClass(type)
            val base = documented[model]?.first() ?: "{}"
            // The base alone is a valid answer, so each failure below is the wrong field's.
            run(methods, if (list) "[$base]" else base, expectSuccess(failures))
            for (field in modelFields(model)) {
                for (value in wrongValues(field.genericType, field)) {
                    if (model in listOf(ZBSendFileResponse::class.java, ZBDeleteFileResponse::class.java,
                            ZBFileStatusResponse::class.java) && field.name == "message" &&
                        value.isJsonArray && value.asJsonArray.size() == 0
                    ) {
                        // A file job's message may be an array of strings; an empty one is an empty message.
                        continue
                    }
                    val body = JsonParser.parseString(base).asJsonObject.apply { add(jsonName(field), value) }
                    run(methods, if (list) "[$body]" else body.toString(), expectError(failures))
                    bodies++
                }
            }
        }
        assertTrue("wrong-typed bodies sent: $bodies", bodies > 300)
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * An upload's message may be an array of strings: the SDK joins the items with a
     * newline into its one string (standard.md section 3). An empty array is an empty
     * message, and an item that is not a string is an error with status 200.
     */
    @Test
    fun anUploadMessageListIsJoinedAndEveryItemIsAString() {
        val uploads = typedMethods().filter { responseType(it) == ZBSendFileResponse::class.java }
        assertTrue("uploads: ${uploads.size}", uploads.size >= 4)
        val failures = mutableListOf<String>()
        for (message in listOf("[5]", "[\"ok\",5]", "[\"ok\",null]", "[\"ok\",true]", "[\"ok\",{}]", "[\"ok\",[]]")) {
            run(uploads, "{\"success\":true,\"message\":$message,\"file_id\":\"x\"}", expectError(failures))
        }
        for ((message, joined) in listOf("[\"ok\",\"also\"]" to "ok\nalso", "[\"only\"]" to "only", "[]" to "")) {
            run(uploads, "{\"success\":true,\"message\":$message,\"file_id\":\"x\"}", { body: String, outcome: RequestSweep.Outcome ->
                val text = (outcome.successes.singleOrNull() as? ZBSendFileResponse)?.message
                if (outcome.thrown != null || outcome.timedOut || outcome.errors.isNotEmpty() || text != joined) {
                    failures.add("$body expected message ${joined.replace("\n", "\\n")}: $outcome")
                }
            })
        }
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * null is accepted for an optional field (standard.md section 3): on every typed call,
     * each top-level field, and each validateBatch row field, set to null one at a time,
     * leaves an otherwise valid answer a success. Credits, address and status are excluded.
     */
    @Test
    fun nullInAnOptionalFieldIsNotAnError() {
        val documented = documented()
        val failures = mutableListOf<String>()
        var bodies = 0
        for ((type, methods) in typedMethods().groupBy { responseType(it) }) {
            val list = type is ParameterizedType && type.rawType == List::class.java
            val model = if (list) rawClass((type as ParameterizedType).actualTypeArguments[0]) else rawClass(type)
            val base = documented[model]?.first() ?: "{}"
            for (field in modelFields(model)) {
                if (jsonName(field) in REQUIRED) continue
                val body = JsonParser.parseString(base).asJsonObject.apply { add(jsonName(field), JsonNull.INSTANCE) }
                run(methods, if (list) "[$body]" else body.toString(), expectSuccess(failures))
                bodies++
            }
            if (model == ZBValidateBatchResponse::class.java) {
                for (field in modelFields(ZBValidateBatchData::class.java)) {
                    if (jsonName(field) in REQUIRED) continue
                    val row = JsonParser.parseString("{\"address\":\"a@b.c\",\"status\":\"valid\"}")
                        .asJsonObject.apply { add(jsonName(field), JsonNull.INSTANCE) }
                    run(methods, "{\"email_batch\":[$row]}", expectSuccess(failures))
                    bodies++
                }
            }
        }
        assertTrue("null bodies sent: $bodies", bodies > 100)
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * "success" is a JSON boolean. The string "false" (any case) is neither a failure
     * report read as false nor a success: on every request method, JSON and download
     * alike, it is an error with status 200 (standard.md section 3, E4).
     */
    @Test
    fun aSuccessStringFalseIsAnErrorWithStatus200OnEveryRequestMethod() {
        val methods = RequestSweep.requestMethods(ZeroBounceClient::class.java)
        assertTrue("request methods: ${methods.size}", methods.size > 40)
        val failures = mutableListOf<String>()
        for (body in listOf("{\"success\":\"false\",\"message\":\"nope\"}",
            "{\"success\":\"False\",\"message\":\"nope\"}", "{\"success\":\"false\"}",
            "{\"success\":\"FALSE\",\"error\":\"nope\"}")) {
            run(methods, body, expectError(failures))
        }
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * The AI score is an integer in the API (check E7): a JSON integer, or a string holding
     * one, within the int range is a result; a fraction, an exponent, a non-numeric string, a
     * value outside the int range, a boolean, an array or an object is an error with status 200
     * on both scoring calls.
     */
    @Test
    fun theScoreIsAnIntegerOnEveryScoringCall() {
        val scoring = typedMethods().filter { responseType(it) == ZBScoreEmailResponse::class.java }
        assertTrue("scoring overloads: ${scoring.size}", scoring.size >= 2)
        val failures = mutableListOf<String>()
        for (body in listOf("{\"score\":5.5}", "{\"score\":5.0}", "{\"score\":1e2}", "{\"score\":1e400}",
            "{\"score\":\"5.9\"}", "{\"score\":\"abc\"}", "{\"score\":\"\"}", "{\"score\":\" 9\"}",
            "{\"score\":true}", "{\"score\":[9]}", "{\"score\":{}}", "{\"score\":2147483648}",
            "{\"score\":\"2147483648\"}", "{\"score\":\"+9\"}")) {
            run(scoring, body, expectError(failures))
        }
        for (body in listOf("{\"score\":9}", "{\"score\":\"9\"}", "{\"score\":\"-1\"}", "{\"score\":0}",
            "{\"score\":null}", "{\"score\":2147483647}", "{\"score\":-2147483648}")) {
            run(scoring, body, expectSuccess(failures))
        }
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /** The bodies check E7 names, on getCredits and validate. */
    @Test
    fun creditsAndValidateNeedTheirFieldsAsStrings() {
        val credits = typedMethods().filter { responseType(it) == ZBCreditsResponse::class.java }
        val validate = typedMethods().filter { responseType(it) == ZBValidateResponse::class.java }
        assertEquals("getCredits", 1, credits.size)
        assertTrue("validate overloads: $validate", validate.isNotEmpty())
        val failures = mutableListOf<String>()
        for (body in listOf(
            "{}", "{\"Credits\":null}", "{\"Credits\":true}", "{\"Credits\":5.5}", "{\"Credits\":5.0}",
            "{\"Credits\":1e3}", "{\"Credits\":\"abc\"}", "{\"Credits\":\"5.0\"}", "{\"Credits\":\"\"}",
            "{\"Credits\":1.5}", "{\"Credits\":[\"5\"]}", "{\"Credits\":[5]}", "{\"Credits\":{}}",
        )) run(credits, body, expectError(failures))
        for (body in listOf(
            "{\"address\":true,\"status\":\"valid\"}", "{\"address\":\"a@b.c\",\"status\":5}",
            "{\"address\":\"a@b.c\"}", "{\"status\":\"valid\"}", "{\"address\":null,\"status\":\"valid\"}",
            "{\"address\":\"a@b.c\",\"status\":null}", "{\"address\":\"a@b.c\",\"status\":\"valid\",\"sub_status\":7}", "{}",
        )) run(validate, body, expectError(failures))
        // Credits is an integer sent as a JSON string (the live API) or as a JSON
        // integer (zerobounce.net's own example, {"Credits":2375323}).
        for (body in listOf(
            "{\"Credits\":\"-1\"}", "{\"Credits\":\"12345\"}", "{\"Credits\":2375323}", "{\"Credits\":-1}",
            "{\"Credits\":0}", "{\"Credits\":2147483647}",
        )) run(credits, body, expectSuccess(failures))
        // Every validatebatch row is an object with address and status as strings.
        val batch = typedMethods().filter { responseType(it) == ZBValidateBatchResponse::class.java }
        assertTrue("validateBatch overloads", batch.isNotEmpty())
        for (row in listOf(
            "{\"status\":\"valid\"}", "{\"address\":\"a@b.c\"}", "{}", "{\"address\":null,\"status\":\"valid\"}",
            "{\"address\":\"a@b.c\",\"status\":null}", "{\"address\":true,\"status\":\"valid\"}",
            "{\"address\":\"a@b.c\",\"status\":5}", "{\"Message\":\"Greylist validation still in progress.\"}",
            "5", "null", "\"x\"", "[]",
        )) {
            run(batch, "{\"email_batch\":[$row]}", expectError(failures))
            run(batch, "{\"email_batch\":[{\"address\":\"ok@b.c\",\"status\":\"valid\"},$row]}", expectError(failures))
        }
        run(batch, "{\"email_batch\":[{\"address\":\"a@b.c\",\"status\":\"valid\"}]}", expectSuccess(failures))
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * A JSON array where the call's result is an object (check E7): an empty
     * array, an array of one string and a positional array (values in field
     * order) are an error with status 200 on every typed call. A call whose
     * result is a list accepts the empty array, and still rejects the others.
     */
    @Test
    fun anArrayInPlaceOfAnObjectIsAnErrorWithStatus200OnEveryTypedCall() {
        val (lists, objects) = typedMethods().partition {
            val type = responseType(it)
            type is ParameterizedType && List::class.java.isAssignableFrom(type.rawType as Class<*>)
        }
        val failures = mutableListOf<String>()
        for (body in listOf(
            "[]", "[\"x\"]", "[\"a@example.com\",10]", "[true,\"3\"]", "[\"a@example.com\",\"valid\"]", "[[]]", "[{}]",
        )) run(objects, body, expectError(failures))
        for (body in listOf("[\"x\"]", "[\"a@example.com\",10]", "[true,\"3\"]", "[[]]", "[5]")) {
            run(lists, body, expectError(failures))
        }
        if (lists.isNotEmpty()) run(lists, "[]", expectSuccess(failures))
        assertTrue("object calls: ${objects.size}", objects.size > 40)
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /** Every documented example response still decodes on every call that returns its type (ACCEPT). */
    @Test
    fun everyDocumentedExampleResponseStillDecodes() {
        val failures = mutableListOf<String>()
        var checked = 0
        for ((type, bodies) in documented()) {
            val methods = typedMethods().filter { responseType(it) == type }
            assertTrue("a call returns $type", methods.isNotEmpty())
            for (body in bodies) {
                run(methods, body, expectSuccess(failures))
                checked++
            }
        }
        assertTrue("documented bodies: $checked", checked >= 20)
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * Dead key on a 200 (check E2) on every client call, the multipart uploads
     * included: `error`, `error_message` and `Error`, each swept on its own, reach
     * the error callback with status 200, never the response callback.
     */
    @Test
    fun aDeadKeyBodyIsAnErrorWithStatus200OnEveryCall() {
        val methods = RequestSweep.requestMethods(ZeroBounceClient::class.java).filter { !isDownload(it) }
        assertTrue("uploads swept", methods.count { it.name.lowercase().contains("sendfile") } >= 4)
        val failures = mutableListOf<String>()
        for (key in listOf("error", "error_message", "Error")) {
            run(methods, "{\"$key\":\"Invalid API key or your account ran out of credits\"}", expectError(failures))
        }
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * An error body whose list holds numbers (`{"error": [5]}`) is reported with its
     * status and its items as text, on every client call, never thrown out of the
     * call as a ClassCastException.
     */
    @Test
    fun anErrorListOfNumbersIsReportedWithItsStatus() {
        val methods = RequestSweep.requestMethods(ZeroBounceClient::class.java).filter { !isDownload(it) }
        val failures = mutableListOf<String>()
        run(methods, "{\"error\":[5],\"message\":[true]}", { body, outcome ->
            if (outcome.thrown != null || outcome.timedOut || outcome.successes.isNotEmpty() ||
                outcome.errors.size != 1 || outcome.errors[0]?.statusCode != 400 ||
                outcome.errors[0]?.errors?.any { (it as Any?) !is String } != false
            ) {
                failures.add("$body $outcome")
            }
        }, status = 400)
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * The non-null properties of a model, by name, with their declared type. Kotlin's
     * nullability is not visible at run time, so the model's source is read (a header scan).
     */
    private fun nonNullProperties(model: Class<*>): Map<String, String> {
        val source = File("src/main/java/com/zerobounce/android/${model.simpleName}.kt").readText()
        val declared = Regex("""\b(?:val|var)\s+(\w+)\s*:\s*(List<[^>]*>|String|Boolean|Int)\s*[,)=\n]""")
        return declared.findAll(source).associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun emptyValueOf(declared: String): Any = when (declared) {
        "String" -> ""
        "Boolean" -> false
        "Int" -> 0
        else -> emptyList<Any>()
    }

    private fun readField(target: Any, name: String): Any? {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(target)
    }

    /**
     * standard.md section 3: where the public type is not nullable, a JSON null reads as
     * that type's empty value ("", false, 0, an empty list), never as null. Every non-null
     * property of every typed model, and of the nested DomainFormat rows, is sent as null
     * once and must read back as its empty value.
     */
    @Test
    fun aNullInANonNullPropertyReadsAsItsEmptyValue() {
        val documented = documented()
        val failures = mutableListOf<String>()
        var checks = 0
        for ((type, methods) in typedMethods().groupBy { responseType(it) }) {
            val list = type is ParameterizedType && type.rawType == List::class.java
            val model = if (list) rawClass((type as ParameterizedType).actualTypeArguments[0]) else rawClass(type)
            val base = documented[model]?.first() ?: "{}"
            val declared = nonNullProperties(model)
            for (field in modelFields(model)) {
                val kind = declared[field.name] ?: continue
                if (jsonName(field) in REQUIRED) continue
                val body = JsonParser.parseString(base).asJsonObject.apply { add(jsonName(field), JsonNull.INSTANCE) }
                val expected = emptyValueOf(kind)
                run(methods, if (list) "[$body]" else body.toString(), { sent, outcome ->
                    val first = outcome.successes.singleOrNull()
                    val decoded = if (first is List<*>) first.firstOrNull() else first
                    val actual = decoded?.let { readField(it, field.name) }
                    if (outcome.thrown != null || outcome.timedOut || outcome.errors.isNotEmpty() || actual != expected) {
                        failures.add("$sent ${field.name} expected $expected got $actual $outcome")
                    }
                })
                checks++
            }
        }
        // The nested rows of the email finder and domain search answers.
        val rowKind = nonNullProperties(DomainFormat::class.java)
        val finders = typedMethods().filter {
            responseType(it) == ZBFindDomainResponse::class.java || responseType(it) == ZBEmailFinderResponse::class.java
        }
        for (name in rowKind.keys) {
            val row = JsonObject().apply { addProperty("format", "x"); addProperty("confidence", "x") }
            row.add(name, JsonNull.INSTANCE)
            run(finders, "{\"other_domain_formats\":[$row]}", { sent, outcome ->
                val rows = outcome.successes.singleOrNull()?.let { readField(it, "otherDomainFormats") } as? List<*>
                val actual = rows?.firstOrNull()?.let { readField(it, name) }
                if (outcome.thrown != null || outcome.errors.isNotEmpty() || actual != emptyValueOf(rowKind.getValue(name))) {
                    failures.add("$sent DomainFormat.$name got $actual $outcome")
                }
            })
            checks++
        }
        assertTrue("null checks: $checks", checks > 20)
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * A file job's message is one string on every file-job answer (standard.md section 3):
     * a list of strings is joined with a newline on delete, scoring delete and file status
     * as on upload, an empty list is an empty message, and a list with anything but strings
     * is an error with status 200.
     */
    @Test
    fun aFileJobMessageListIsJoinedOnEveryFileJobAnswer() {
        val fileJobs = typedMethods().filter {
            responseType(it) in listOf(ZBSendFileResponse::class.java, ZBDeleteFileResponse::class.java,
                ZBFileStatusResponse::class.java)
        }
        assertTrue("file-job calls: ${fileJobs.size}", fileJobs.size >= 12)
        val failures = mutableListOf<String>()
        for (message in listOf("[5]", "[\"ok\",5]", "[\"ok\",null]", "[true]", "[{}]", "[[]]")) {
            run(fileJobs, "{\"success\":true,\"message\":$message,\"file_id\":\"x\"}", expectError(failures))
        }
        for ((message, joined) in listOf("[\"ok\",\"also\"]" to "ok\nalso", "[\"only\"]" to "only", "[]" to "",
            "\"plain\"" to "plain", "null" to null)) {
            run(fileJobs, "{\"success\":true,\"message\":$message,\"file_id\":\"x\"}", { body, outcome ->
                val text = when (val sent = outcome.successes.singleOrNull()) {
                    is ZBSendFileResponse -> sent.message
                    is ZBDeleteFileResponse -> sent.message
                    is ZBFileStatusResponse -> readField(sent, "message") as String?
                    else -> "<no answer>"
                }
                if (outcome.thrown != null || outcome.timedOut || outcome.errors.isNotEmpty() || text != joined) {
                    failures.add("$body expected ${joined?.replace("\n", "\\n")}: $outcome")
                }
            })
        }
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * A date field that no known format reads is an error with status 200 on every typed
     * call that returns it (standard.md section 3, the date closure), never a null date.
     */
    @Test
    fun anUnparseableDateIsAnErrorWithStatus200OnEveryTypedCall() {
        val documented = documented()
        val failures = mutableListOf<String>()
        var checked = 0
        for ((type, methods) in typedMethods().groupBy { responseType(it) }) {
            val list = type is ParameterizedType && type.rawType == List::class.java
            val model = if (list) rawClass((type as ParameterizedType).actualTypeArguments[0]) else rawClass(type)
            val base = documented[model]?.first() ?: "{}"
            for (field in modelFields(model)) {
                if (field.type != Date::class.java) continue
                val body = JsonParser.parseString(base).asJsonObject.apply { addProperty(jsonName(field), "not a date") }
                run(methods, if (list) "[$body]" else body.toString(), expectError(failures))
                checked++
            }
        }
        assertTrue("date fields checked: $checked", checked >= 2)
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * A message-only body is an error with status 200 on the email finder and domain
     * search, whatever its message value (standard.md section 3, E3): a non-blank string,
     * a blank one, null or a list. Validate's greylist Message is a result, not covered here.
     */
    @Test
    fun aMessageOnlyBodyIsAnErrorWithStatus200WhateverItsMessageValue() {
        val finders = RequestSweep.requestMethods(ZeroBounceClient::class.java)
            .filter { it.name in setOf("guessFormat", "findEmail", "findDomain") }
        assertEquals("finder and domain-search calls", 3, finders.size)
        val failures = mutableListOf<String>()
        for (body in listOf(
            "{\"Message\":\"Invalid API key or your account ran out of credits\"}",
            "{\"message\":\"Not found\"}", "{\"message\":\"\"}", "{\"message\":\"   \"}",
            "{\"message\":null}", "{\"Message\":null}", "{\"message\":[]}", "{\"message\":[\"a\",\"b\"]}",
            "{\"message\":[\"\"]}", "{\"message\":[null]}", "{\"message\":5}", "{\"message\":true}",
            "{\"message\":null,\"error\":\"\"}", "{\"detail\":null}",
        )) {
            run(finders, body, expectError(failures))
        }
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * Each marker of the HTML sniff counts on its own (check E, standard.md section 3): a
     * page with only `<html`, `<head` or `<body` (no doctype), under application/json or
     * text/csv, is an HTML error page with status 200 on every path, and a download writes
     * no file. Removing any one marker fails this test.
     */
    @Test
    fun eachHtmlMarkerAloneMakesAnHtmlPageOnEveryPath() {
        val methods = RequestSweep.requestMethods(ZeroBounceClient::class.java)
        val pages = listOf(
            "<html lang=\"en\"><p>Blocked</p></html>",
            "<head><title>Blocked</title></head><p>Try later</p>",
            "<body><p>Blocked. Try later.</p></body>",
        )
        val failures = mutableListOf<String>()
        for (page in pages) {
            for (contentType in listOf("application/json", "text/csv")) {
                run(methods, page, { body, outcome ->
                    val onlyHtml = outcome.successes.isEmpty() && outcome.errors.size == 1 &&
                        outcome.errors[0]?.statusCode == 200 &&
                        outcome.errors[0]?.errors?.any { it.contains("HTML error page") } == true
                    if (outcome.thrown != null || outcome.timedOut || !onlyHtml) {
                        failures.add("$contentType $body $outcome")
                    }
                    val written = outcome.out?.let { File(it).exists() } == true
                    if (written) failures.add("$contentType wrote ${outcome.out} for ${outcome.signature}")
                }, contentType = contentType)
            }
        }
        assertTrue("${failures.size} failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
