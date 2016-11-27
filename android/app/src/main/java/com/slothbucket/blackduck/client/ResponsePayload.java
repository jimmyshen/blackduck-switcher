package com.slothbucket.blackduck.client;

import android.os.Parcelable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.auto.value.AutoValue;
import com.slothbucket.blackduck.models.Task;
import com.slothbucket.blackduck.models.TaskIcon;

import java.util.ArrayList;
import java.util.List;

@AutoValue
@JsonDeserialize(builder = AutoValue_ResponsePayload.Builder.class)
public abstract class ResponsePayload implements Parcelable {

    @JsonProperty("tasks")
    public abstract List<Task> tasks();

    @JsonProperty("icons")
    public abstract List<TaskIcon> icons();

    @AutoValue.Builder
    public abstract static class Builder {
        @JsonProperty("tasks")
        public abstract Builder setTasks(List<Task> tasks);

        @JsonProperty("icons")
        public abstract Builder setIcons(List<TaskIcon> icons);

        abstract List<Task> tasks();
        abstract List<TaskIcon> icons();
        abstract ResponsePayload autoBuild();

        public ResponsePayload build() {
            try {
                tasks();
            } catch (IllegalStateException expected) {
                setTasks(new ArrayList<Task>());
            }
            try {
                icons();
            } catch (IllegalStateException expected) {
                setIcons(new ArrayList<TaskIcon>());
            }
            return autoBuild();
        }
    }
}
