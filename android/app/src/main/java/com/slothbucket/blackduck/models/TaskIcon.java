package com.slothbucket.blackduck.models;

import com.google.auto.value.AutoValue;

/**
 * An icon bitmap displayed for individual tasks.
 */
@AutoValue
public abstract class TaskIcon {
    /** MD5 hash of the icon data. */
    public abstract String id();

    /** Width of icon in pixels. */
    public abstract int width();

    /** Height of icon in pixels. */
    public abstract int height();

    /** Raw RGB color info for icon. */
    public abstract int[] colorData();
}
