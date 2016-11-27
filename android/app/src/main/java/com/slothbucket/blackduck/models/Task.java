package com.slothbucket.blackduck.models;

import android.os.Parcelable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.auto.value.AutoValue;

/**
 * A task running on the host device.
 */
@AutoValue
@JsonDeserialize(builder = AutoValue_Task.Builder.class)
public abstract class Task implements Parcelable {

    /** Unique identifier for a task. */
    @JsonProperty("id")
    public abstract String id();

    /** Name of the application this task belongs to. */
    @JsonProperty("app_name")
    public abstract String applicationName();

    /** Title of the task. */
    @JsonProperty("title")
    public abstract String title();

    /** Unique identifier for task icon. */
    @JsonProperty("icon_id")
    public abstract String iconId();

    @JsonProperty("is_open")
    public abstract boolean isOpen();

    /** Synchronization timestamp (based on host time). */
    @JsonProperty("last_update_ts")
    public abstract long lastUpdateTimestamp();

    public boolean newerThan(Task otherTask) {
        return lastUpdateTimestamp() > otherTask.lastUpdateTimestamp();
    }

    /** Builder for {@link Task}. */
    @AutoValue.Builder
    public abstract static class Builder {
        @JsonProperty("id")
        public abstract Builder setId(String id);

        @JsonProperty("app_name")
        public abstract Builder setApplicationName(String name);

        @JsonProperty("title")
        public abstract Builder setTitle(String title);

        @JsonProperty("icon_id")
        public abstract Builder setIconId(String iconId);

        @JsonProperty("is_open")
        public abstract Builder setIsOpen(boolean isOpen);

        @JsonProperty("last_update_ts")
        public abstract Builder setLastUpdateTimestamp(long timestamp);

        public abstract Task build();
    }
}
