package com.slothbucket.blackduck.models;

import com.google.auto.value.AutoValue;

/**
 * A task running on the host device.
 */
@AutoValue
public abstract class Task {

    /** Unique identifier for a task. */
    public abstract String id();

    /** Unique identifier for task icon. */
    public abstract String iconId();

    /** Synchronization timestamp (based on host time). */
    public abstract int lastSynchronizedTimestamp();
}
