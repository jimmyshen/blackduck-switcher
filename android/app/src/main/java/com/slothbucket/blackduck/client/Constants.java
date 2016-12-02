package com.slothbucket.blackduck.client;

public final class Constants {

    // Commands
    public static final String COMMAND_LIST_TASKS = "list_tasks";
    public static final String COMMAND_BATCHGET_ICONS = "batchget_icons";
    public static final String COMMAND_LIST_UPDATED_TASKS = "list_updated_tasks";
    public static final String COMMAND_ACTIVATE_TASK = "activate_task";
    public static final String COMMAND_SCALE_TASK = "scale_task";

    // Actions
    public static final String ACTION_CONNECT_DEVICE = pkgAction("CONNECT_DEVICE");
    public static final String ACTION_DEVICE_CONNECTED = pkgAction("DEVICE_CONNECTED");
    public static final String ACTION_DEVICE_ERROR = pkgAction("DEVICE_ERROR");
    public static final String ACTION_SERVICE_REQUEST = pkgAction("SERVICE_REQUEST");
    public static final String ACTION_SERVICE_RESPONSE = pkgAction("SERVICE_RESPONSE");

    // Extras
    public static final String EXTRA_DEVICE = pkgExtra("DEVICE");
    public static final String EXTRA_ERROR_MESSAGE = pkgExtra("ERROR_MESSAGE");
    public static final String EXTRA_SERVICE_REQUEST = pkgExtra("SERVICE_REQUEST");
    public static final String EXTRA_SERVICE_RESPONSE = pkgExtra("SERVICE_RESPONSE");


    private static String pkgAction(String name) {
        return "com.slothbucket.blackduck.action." + name;
    }

    private static String pkgExtra(String name) {
        return "com.slothbucket.blackduck.extra." + name;
    }

    private Constants() {}
}
