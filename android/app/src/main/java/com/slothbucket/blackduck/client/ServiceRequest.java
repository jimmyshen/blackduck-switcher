package com.slothbucket.blackduck.client;

import android.os.Parcelable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.auto.value.AutoValue;

/**
 * A single request that needs to be sent to the BlackDuck API.
 */
@AutoValue
@JsonDeserialize(builder = AutoValue_ServiceRequest.Builder.class)
abstract class ServiceRequest implements Parcelable {
    @JsonProperty("request_id")
    public abstract int requestId();

    @JsonProperty("command")
    public abstract String command();

    @JsonProperty("payload")
    public abstract RequestPayload payload();

    @AutoValue.Builder
    public abstract static class Builder {
        @JsonProperty("request_id")
        public abstract Builder setRequestId(int requestId);

        @JsonProperty("command")
        public abstract Builder setCommand(String command);

        @JsonProperty("payload")
        public abstract Builder setPayload(RequestPayload payload);

        public abstract ServiceRequest build();
    }

    public static Builder builder() {
        return new AutoValue_ServiceRequest.Builder();
    }
}
