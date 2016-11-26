package com.slothbucket.blackduck.common;

public final class AndroidUtils {

    public static String tagNameFor(Class<?> clazz) {
        return "BlackDuck:" + clazz.getSimpleName();
    }

    private AndroidUtils() {}
}
