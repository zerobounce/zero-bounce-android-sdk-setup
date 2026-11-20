package com.zerobounce.android;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * Java callers keep the shorter Warmup signatures that {@code @JvmOverloads} generated up to
 * 3.1.2, now that the full methods override {@link ZeroBounceWarmupApi}.
 */
public class WarmupJavaOverloadsTest {

    @Test
    public void theShorterSignaturesOf312StillCompileFromJava() {
        ZeroBounceWarmup warmup = new ZeroBounceWarmup();
        List<ErrorResponse> errors = new ArrayList<>();
        warmup.addSendingAccount("a@example.com", "self_managed", r -> null, e -> errors.add(e) ? null : null);
        warmup.listFromAddresses(r -> null, e -> errors.add(e) ? null : null);
        warmup.listFromAddresses("a", r -> null, e -> errors.add(e) ? null : null);
        warmup.listFromAddresses("a", 1, r -> null, e -> errors.add(e) ? null : null);
        warmup.checkDomainDmarc("a@example.com", r -> null, e -> errors.add(e) ? null : null);
        assertEquals(5, errors.size());

        ZeroBounceWarmupApi api = warmup;
        api.listFromAddresses(null, null, 10, r -> null, e -> errors.add(e) ? null : null);
        assertEquals(6, errors.size());
    }
}
