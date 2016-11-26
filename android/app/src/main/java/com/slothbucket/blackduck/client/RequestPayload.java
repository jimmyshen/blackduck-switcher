package com.slothbucket.blackduck.client;

import android.os.Parcelable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.auto.value.AutoValue;

import java.util.ArrayList;
import java.util.List;

@AutoValue
@JsonDeserialize(builder = AutoValue_RequestPayload.Builder.class)
public abstract class RequestPayload implements Parcelable {

    @JsonProperty("icon_ids")
    public abstract List<String> iconIds();

    @JsonProperty("last_update_ts")
    public abstract long lastUpdateTimestamp();

    @AutoValue.Builder
    public abstract static class Builder {
        @JsonProperty("icon_ids")
        public abstract Builder setIconIds(List<String> iconIds);

        @JsonProperty("last_update_ts")
        public abstract Builder setLastUpdateTimestamp(long lastUpdateTimestamp);

        public abstract RequestPayload build();
    }

    public static Builder builder() {
        return new AutoValue_RequestPayload.Builder();
    }

    public static RequestPayload empty() {
        return builder().setIconIds(new ArrayList<String>()).setLastUpdateTimestamp(0).build();
    }
}
