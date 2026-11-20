package com.zerobounce.android

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.MockMakers
import org.mockito.Mockito
import org.mockito.exceptions.base.MockitoException
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.withSettings
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy

/**
 * [ZeroBounceApi] and [ZeroBounceWarmupApi] hold every public request method of
 * [ZeroBounceClient] and [ZeroBounceWarmup], and nothing else, so callers can mock them with
 * Mockito's proxy mock maker or a JDK [Proxy]. The classes stay final.
 */
class ClientInterfacesTest {

    /** Construction and configuration: on the classes, deliberately not on the interfaces. */
    private val clientSetup = setOf("initialize", "setListEvaluatorBaseUrl", "setInboxPlacementBaseUrl")
    private val warmupSetup = setOf("initialize")

    /**
     * The shorter Java signatures `@JvmOverloads` generated up to 3.1.2. They delegate to the full
     * method and stay off the interface (see the comment in [ZeroBounceWarmup]).
     */
    private val warmupJavaOverloads = setOf(
        "addSendingAccount(String,String,Function1,Function1)",
        "listFromAddresses(Function1,Function1)",
        "listFromAddresses(String,Function1,Function1)",
        "listFromAddresses(String,Integer,Function1,Function1)",
        "checkDomainDmarc(String,Function1,Function1)",
    )

    private fun params(m: Method) = m.parameterTypes.joinToString(",", "(", ")") { it.simpleName }
    private fun signature(m: Method) = m.name + params(m) + ":" + m.genericReturnType.typeName

    private fun requestMethods(type: Class<*>): Map<String, Method> =
        type.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !Modifier.isStatic(it.modifiers) }
            .filter { !it.isSynthetic && !it.isBridge }
            .associateBy(::signature)

    private fun assertMatches(type: Class<*>, api: Class<*>, setup: Set<String>, javaOverloads: Set<String>) {
        assertTrue("${type.simpleName} is final", Modifier.isFinal(type.modifiers))
        assertTrue("${type.simpleName} implements ${api.simpleName}", api.isAssignableFrom(type))
        assertTrue(api.isInterface)

        val onInterface = requestMethods(api)
        val onClass = requestMethods(type).filterValues { it.name !in setup }
        val overloads = onClass.filterValues { it.name + params(it) in javaOverloads }

        assertEquals(
            "public methods of ${type.simpleName} missing from ${api.simpleName} (add them there, " +
                "or to the setup list when they configure the client)",
            emptySet<String>(),
            onClass.keys - overloads.keys - onInterface.keys,
        )
        assertEquals(
            "methods of ${api.simpleName} that ${type.simpleName} does not declare with the same signature",
            emptySet<String>(),
            onInterface.keys - onClass.keys,
        )
        assertEquals(
            "every Java overload is still there",
            javaOverloads,
            overloads.values.map { it.name + params(it) }.toSet(),
        )
        onInterface.values.forEach {
            assertTrue("${api.simpleName}.${it.name} is abstract", Modifier.isAbstract(it.modifiers))
        }
    }

    @Test
    fun zeroBounceApiMatchesZeroBounceClient() {
        assertMatches(ZeroBounceClient::class.java, ZeroBounceApi::class.java, clientSetup, emptySet())
        assertEquals(58, requestMethods(ZeroBounceApi::class.java).size)
    }

    @Test
    fun zeroBounceWarmupApiMatchesZeroBounceWarmup() {
        assertMatches(ZeroBounceWarmup::class.java, ZeroBounceWarmupApi::class.java, warmupSetup, warmupJavaOverloads)
        assertEquals(26, requestMethods(ZeroBounceWarmupApi::class.java).size)
    }

    @Test
    fun theProxyMockMakerCannotMockTheFinalClassesButCanMockTheInterfaces() {
        val proxy = withSettings().mockMaker(MockMakers.PROXY)
        assertThrows(MockitoException::class.java) { Mockito.mock(ZeroBounceClient::class.java, proxy) }
        assertThrows(MockitoException::class.java) { Mockito.mock(ZeroBounceWarmup::class.java, proxy) }
        Mockito.mock(ZeroBounceApi::class.java, withSettings().mockMaker(MockMakers.PROXY))
        Mockito.mock(ZeroBounceWarmupApi::class.java, withSettings().mockMaker(MockMakers.PROXY))
    }

    /** Code under test that depends on the interface, as the README shows. */
    private class CreditsGate(private val zb: ZeroBounceApi) {
        fun hasCredits(answer: (Boolean) -> Unit) = zb.getCredits(
            responseCallback = { answer((it?.credits?.toIntOrNull() ?: 0) > 0) },
            errorCallback = { answer(false) },
        )
    }

    @Test
    fun zeroBounceApiCanBeMockedWithMockitosProxyMockMaker() {
        val zb = Mockito.mock(ZeroBounceApi::class.java, withSettings().mockMaker(MockMakers.PROXY))
        doAnswer { it.getArgument<(ZBCreditsResponse?) -> Unit>(0)(ZBCreditsResponse("42")) }
            .whenever(zb).getCredits(any(), any())

        var answer: Boolean? = null
        CreditsGate(zb).hasCredits { answer = it }
        assertEquals(true, answer)

        doAnswer { it.getArgument<(ErrorResponse?) -> Unit>(1)(ErrorResponse.parseError("Invalid API key")) }
            .whenever(zb).getCredits(any(), any())
        CreditsGate(zb).hasCredits { answer = it }
        assertEquals(false, answer)
    }

    @Test
    fun defaultArgumentsWorkThroughTheInterface() {
        val zb = Mockito.mock(ZeroBounceApi::class.java, withSettings().mockMaker(MockMakers.PROXY))
        zb.findDomain(domain = "example.com", responseCallback = {}, errorCallback = {})
        verify(zb).findDomain(eq("example.com"), isNull(), any(), any())
    }

    @Test
    fun zeroBounceWarmupApiCanBeFakedWithAJdkProxy() {
        val calls = mutableListOf<String>()
        val warmup = Proxy.newProxyInstance(
            ZeroBounceWarmupApi::class.java.classLoader,
            arrayOf(ZeroBounceWarmupApi::class.java),
        ) { _, method, args ->
            calls += method.name
            @Suppress("UNCHECKED_CAST")
            val respond = args[args.size - 2] as (JsonElement?) -> Unit
            respond(JsonParser.parseString("""{"seed_count": 3}"""))
            null
        } as ZeroBounceWarmupApi

        var seen: JsonElement? = null
        warmup.getSeedCount(responseCallback = { seen = it }, errorCallback = { throw AssertionError(it) })
        assertEquals(3, seen!!.asJsonObject["seed_count"].asInt)

        warmup.listFromAddresses(limit = 5, responseCallback = {}, errorCallback = {})
        assertEquals(listOf("getSeedCount", "listFromAddresses"), calls)
    }

    @Test
    fun zeroBounceWarmupApiCanBeMockedWithMockito() {
        val warmup = Mockito.mock(ZeroBounceWarmupApi::class.java, withSettings().mockMaker(MockMakers.PROXY))
        warmup.checkDomainDmarc("sender@example.com", responseCallback = {}, errorCallback = {})
        verify(warmup).checkDomainDmarc(eq("sender@example.com"), isNull(), any(), any())
        warmup.addSendingAccount("sender@example.com", "self_managed", responseCallback = {}, errorCallback = {})
        verify(warmup).addSendingAccount(eq("sender@example.com"), eq("self_managed"), anyOrNull(), any(), any())
    }

    @Test
    fun theWarmupJavaOverloadsDelegateToTheFullMethod() {
        val warmup = ZeroBounceWarmup() // not initialized: every call reports that, without a request
        val messages = mutableListOf<String?>()
        val fail: (JsonElement?) -> Unit = { throw AssertionError(it) }
        val record: (ErrorResponse?) -> Unit = { messages += it?.errors?.joinToString() }
        warmup.addSendingAccount("a@example.com", "self_managed", fail, record)
        warmup.listFromAddresses(fail, record)
        warmup.listFromAddresses("a", fail, record)
        warmup.listFromAddresses("a", 1, fail, record)
        warmup.checkDomainDmarc("a@example.com", fail, record)
        assertEquals(5, messages.size)
        messages.forEach { assertTrue(it, it!!.contains("not initialized")) }
    }
}
