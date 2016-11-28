package com.slothbucket.blackduck.models;

import android.graphics.Bitmap;
import android.os.Parcelable;
import android.util.Base64;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.auto.value.AutoValue;
import com.slothbucket.blackduck.common.FluentLog;

import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * An icon bitmap displayed for individual tasks.
 */
@AutoValue
@JsonDeserialize(builder = AutoValue_TaskIcon.Builder.class)
public abstract class TaskIcon implements Parcelable {
    private static final FluentLog logger = FluentLog.loggerFor("blackduck", TaskIcon.class);

    /** ID for the icon. */
    @JsonProperty("id")
    public abstract String id();

    /** Width of icon in pixels. */
    @JsonProperty("width")
    public abstract int width();

    /** Height of icon in pixels. */
    @JsonProperty("height")
    public abstract int height();

    /** Encoded pixel data. */
    @JsonProperty("pixels")
    public abstract String pixels();

    /**
     * Decodes the pixel string into 32-bit integers.
     */
    public Bitmap getPixelsAsBitmap() {
        // Assume 24-bit RGB and just max out the alpha channel.
        byte[] bytes = decodePixels();
        int[] colors = new int[width() * height()];
        for (int i = 0; i < bytes.length; i += 4) {
            int color = ((0xFF & bytes[i + 3]) << 24)
                    | ((0xFF & bytes[i]) << 16)
                    | ((0xFF & bytes[i + 1]) << 8)
                    | (0xFF & bytes[i + 2]);

            colors[i / 4] = color;
        }

        return Bitmap.createBitmap(colors, width(), height(), Bitmap.Config.ARGB_8888);
    }

    private byte[] decodePixels() {
        byte[] data = new byte[width() * height() * 4];
        Inflater inflater = new Inflater();
        inflater.setInput(Base64.decode(pixels(), Base64.DEFAULT));
        try {
            inflater.inflate(data);
        } catch (DataFormatException e) {
            logger.atError().withCause(e).log("Failed to decode pixels for icon %s", id());
        }
        return data;
    }

    @AutoValue.Builder
    public abstract static class Builder {
         @JsonProperty("id")
        public abstract Builder setId(String id);

        @JsonProperty("width")
        public abstract Builder setWidth(int width);

        @JsonProperty("height")
        public abstract Builder setHeight(int height);

        @JsonProperty("pixels")
        public abstract Builder setPixels(String pixels);

        public abstract TaskIcon build();
    }

    public static Builder builder() {
        return new AutoValue_TaskIcon.Builder();
    }
}
