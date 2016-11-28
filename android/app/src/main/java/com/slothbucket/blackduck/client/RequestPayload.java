package com.slothbucket.blackduck.client;

import android.app.DownloadManager;
import android.os.Parcelable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.auto.value.AutoValue;
import com.slothbucket.blackduck.models.TaskIcon;

import java.util.ArrayList;
import java.util.List;

@AutoValue
@JsonDeserialize(builder = AutoValue_RequestPayload.Builder.class)
public abstract class RequestPayload implements Parcelable {

    @JsonProperty("task_id")
    public abstract String taskId();

    @JsonProperty("icon_ids")
    public abstract List<String> iconIds();

    @JsonProperty("last_update_ts")
    public abstract long lastUpdateTimestamp();

    @AutoValue.Builder
    public abstract static class Builder {
        @JsonProperty("task_id")
        public abstract Builder setTaskId(String taskId);

        @JsonProperty("icon_ids")
        public abstract Builder setIconIds(List<String> iconIds);

        @JsonProperty("last_update_ts")
        public abstract Builder setLastUpdateTimestamp(long lastUpdateTimestamp);

        abstract String taskId();
        abstract List<String> iconIds();
        abstract long lastUpdateTimestamp();
        abstract RequestPayload autoBuild();

        public RequestPayload build() {
            try {
                taskId();
            } catch (IllegalStateException expected) {
                setTaskId("");
            }

            try {
                iconIds();
            } catch (IllegalStateException expected) {
                setIconIds(new ArrayList<String>());
            }

            try {
                lastUpdateTimestamp();
            } catch (IllegalStateException expected) {
                setLastUpdateTimestamp(0);
            }

            return autoBuild();
        }
    }

    public static Builder builder() {
        return new AutoValue_RequestPayload.Builder();
    }

    public static RequestPayload empty() {
        return builder().setIconIds(new ArrayList<String>()).setLastUpdateTimestamp(0).build();
    }
}
