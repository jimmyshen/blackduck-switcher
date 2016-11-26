package com.slothbucket.blackduck.client;

public final class Constants {

    // Actions
    static final String ACTION_CONNECT_DEVICE = pkgAction("CONNECT_DEVICE");
    static final String ACTION_DEVICE_CONNECTED = pkgAction("DEVICE_CONNECTED");
    static final String ACTION_SERVICE_REQUEST = pkgAction("SERVICE_REQUEST");
    static final String ACTION_SERVICE_RESPONSE = pkgAction("SERVICE_RESPONSE");

    // Extras
    static final String EXTRA_DEVICE = pkgExtra("DEVICE");
    static final String EXTRA_SERVICE_REQUEST = pkgExtra("SERVICE_REQUEST");
    static final String EXTRA_SERVICE_RESPONSE = pkgExtra("SERVICE_RESPONSE");

    private static String pkgAction(String name) {
        return "com.slothbucket.blackduck.action." + name;
    }

    private static String pkgExtra(String name) {
        return "com.slothbucket.blackduck.extra." + name;
    }

    private Constants() {}
}
