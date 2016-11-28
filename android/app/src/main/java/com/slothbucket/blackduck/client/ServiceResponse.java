package com.slothbucket.blackduck.client;

import android.os.Parcelable;
import android.support.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.auto.value.AutoValue;

@AutoValue
@JsonDeserialize(builder = AutoValue_ServiceResponse.Builder.class)
public abstract class ServiceResponse implements Parcelable {
    @JsonProperty("request_id")
    public abstract int requestId();

    @JsonProperty("status")
    public abstract String status();

    @JsonProperty("payload")
    public abstract ResponsePayload payload();

    @JsonProperty("error")
    public abstract String error();

    @AutoValue.Builder
    public abstract static class Builder {
        @JsonProperty("request_id")
        public abstract Builder setRequestId(int id);

        @JsonProperty("status")
        public abstract Builder setStatus(String status);

        @JsonProperty("payload")
        public abstract Builder setPayload(ResponsePayload payload);

        @JsonProperty(value = "error")
        public abstract Builder setError(String error);

        abstract ResponsePayload payload();
        abstract String error();
        abstract ServiceResponse autoBuild();

        public ServiceResponse build() {
            try {
                payload();
            } catch (IllegalStateException expected) {
                setPayload(ResponsePayload.empty());
            }

            try {
                error();
            } catch (IllegalStateException expected) {
                setError("");
            }

            return autoBuild();
        }
    }
}
