package com.slothbucket.blackduck.common;

import android.util.Log;

public final class FluentLog {
    private final String tag;
    private final int priority;
    private final Throwable cause;

    public static FluentLog loggerFor(String tag) {
        return new FluentLog(tag);
    }

    public static FluentLog loggerFor(Class<?> clazz) {
        return new FluentLog(clazz.getSimpleName());
    }

    public static FluentLog loggerFor(String prefix, Class<?> clazz) {
        return new FluentLog(String.format("%s%s", prefix, clazz.getSimpleName()));
    }

    private FluentLog(String tag) {
        this.tag = tag;
        priority = Log.INFO;
        cause = null;
    }

    private FluentLog(String tag, int priority, Throwable cause) {
        this.tag = Preconditions.checkNotNull(tag);
        this.priority = Preconditions.checkNotNull(priority);
        this.cause = cause;
    }

    public FluentLog atVerbose() {
        return new FluentLog(tag, Log.VERBOSE, cause);
    }

    public FluentLog atDebug() {
        return new FluentLog(tag, Log.DEBUG, cause);
    }

    public FluentLog atInfo() {
        return new FluentLog(tag, Log.INFO, cause);
    }

    public FluentLog atWarning() {
        return new FluentLog(tag, Log.WARN, cause);
    }

    public FluentLog atError() {
        return new FluentLog(tag, Log.ERROR, cause);
    }

    public FluentLog withCause(Throwable cause) {
        return new FluentLog(tag, priority, cause);
    }

    public void log(String message, Object... fmtArgs) {
        if (fmtArgs != null && fmtArgs.length > 0) {
            message = String.format(message, fmtArgs);
        }

        if (cause != null) {
            message = String.format("%s:%n%s", message, Log.getStackTraceString(cause));
        }

        Log.println(priority, tag, message);
    }
}
