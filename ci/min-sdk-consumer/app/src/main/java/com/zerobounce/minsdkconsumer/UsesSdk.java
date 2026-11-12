package com.zerobounce.minsdkconsumer;

import com.zerobounce.android.ZeroBounceSDK;

import kotlin.Unit;

/** Makes the app compile and dex against the SDK's classes, not only merge its manifest. */
final class UsesSdk {
    private UsesSdk() {}

    static Object sdk() {
        return ZeroBounceSDK.INSTANCE;
    }

    /**
     * A call whose callback takes a Gson JsonElement. This app declares no Gson
     * dependency of its own, so it compiles only while the published SDK lists
     * Gson as a compile (api) dependency.
     */
    static void usesAJsonElementCallback() {
        ZeroBounceSDK.INSTANCE.getInboxPlacementProviders(providers -> Unit.INSTANCE, error -> Unit.INSTANCE);
    }
}
