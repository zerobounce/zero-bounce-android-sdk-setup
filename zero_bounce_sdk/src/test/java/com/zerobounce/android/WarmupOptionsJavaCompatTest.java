package com.zerobounce.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Collections;

import org.junit.Test;

/**
 * Java callers of the Warmup option classes keep compiling: the constructor
 * signatures of 3.0.1 are still there (the primary constructors are
 * {@code @JvmOverloads}), and the fields added since have defaults.
 */
public class WarmupOptionsJavaCompatTest {

    @Test
    @SuppressWarnings("deprecation")
    public void theConstructorsOf301StillCompileFromJava() {
        ZBWarmupEmailContent content = new ZBWarmupEmailContent(
                Collections.singletonList("marketing"), "Software", "Acme", "https://acme.example");
        assertEquals("https://acme.example", content.getWebsite());
        assertNull(content.getCompanyWebsite());

        ZBWarmupManagedSending sending = new ZBWarmupManagedSending(-5, "2026-03-04", 50);
        assertEquals(Integer.valueOf(-5), sending.getTimezoneOffset());
        assertNull(sending.getTimezoneOffsetHours());

        ZBWarmupManagedSending fractional = new ZBWarmupManagedSending(
                null, "2026-03-04", null, true, "Asia/Kolkata", 5.5);
        assertEquals(Double.valueOf(5.5), fractional.getTimezoneOffsetHours());
        new ZBWarmupEmailContent();
        new ZBWarmupManagedSending();
    }
}
