package com.slothbucket.blackduck.client;

public final class Constants {

    public static final class Api {
        // Actions
        static final String ACTION_DEVICE_CONNECTED = pkgAction("DEVICE_CONNECTED");
        static final String ACTION_TASK_ACTIVATED = pkgAction("TASK_ACTIVATED");
        static final String ACTION_TASKS_RESPONSE = pkgAction("TASKS_RESPONSE");
        static final String ACTION_ICONS_RESPONSE = pkgAction("ICONS_RESPONSE");

        // Extras
        static final String EXTRA_REQUEST_ID = pkgExtra("REQUEST_ID");
        static final String EXTRA_TASKS = pkgExtra("TASKS");
        static final String EXTRA_TASK_ICONS = pkgExtra("TASK_ICONS");

        private Api() {}
    }

    static final class Internal {
        private Internal() {}
    }

    private static String pkgAction(String name) {
        return "com.slothbucket.blackduck.action." + name;
    }

    private static String pkgExtra(String name) {
        return "com.slothbucket.blackduck.extra." + name;
    }

    private Constants() {}
}
