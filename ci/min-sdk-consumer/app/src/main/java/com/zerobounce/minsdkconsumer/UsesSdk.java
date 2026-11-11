package com.zerobounce.minsdkconsumer;

import com.zerobounce.android.ZeroBounceSDK;

/** Makes the app compile and dex against the SDK's classes, not only merge its manifest. */
final class UsesSdk {
    private UsesSdk() {}

    static Object sdk() {
        return ZeroBounceSDK.INSTANCE;
    }
}
