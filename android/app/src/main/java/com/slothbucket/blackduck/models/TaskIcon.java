package com.slothbucket.blackduck.models;

import android.os.Parcelable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.auto.value.AutoValue;

/**
 * An icon bitmap displayed for individual tasks.
 */
@AutoValue
@JsonDeserialize(builder = AutoValue_TaskIcon.Builder.class)
public abstract class TaskIcon implements Parcelable {
    /** ID for the icon. */
    @JsonProperty("id")
    public abstract String id();

    /** Width of icon in pixels. */
    @JsonProperty("width")
    public abstract int width();

    /** Height of icon in pixels. */
    @JsonProperty("height")
    public abstract int height();

    /** Raw RGBA color info for icon. */
    @JsonProperty("pixels")
    public abstract int[] pixels();

    @AutoValue.Builder
    public abstract static class Builder {
         @JsonProperty("id")
        public abstract Builder setId(String id);

        @JsonProperty("width")
        public abstract Builder setWidth(int width);

        @JsonProperty("height")
        public abstract Builder setHeight(int height);

        @JsonProperty("pixels")
        public abstract Builder setPixels(int[] pixels);

        public abstract TaskIcon build();
    }
}
