package com.slothbucket.blackduck.common;


public final class Preconditions {

    public static <T> T checkNotNull(T reference) {
        return checkNotNull(reference, "Value may not be null.");
    }

    public static <T> T checkNotNull(T reference, String message, Object... args) {
        if (reference == null) {
            throw new NullPointerException(String.format(message, args));
        }

        return reference;
    }

    public static void checkArgument(boolean condition, String message, Object... args) {
        if (!condition) {
            throw new IllegalArgumentException(String.format(message, args));
        }
    }

    public static void checkState(boolean condition, String message, Object... args) {
        if (!condition) {
            throw new IllegalStateException(String.format(message, args));
        }
    }

    private Preconditions() {}
}
