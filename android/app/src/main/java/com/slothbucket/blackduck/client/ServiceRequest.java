package com.slothbucket.blackduck.client;

import android.os.Parcelable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.google.auto.value.AutoValue;

import java.util.Map;

/**
 * A single request that needs to be sent to the BlackDuck API.
 */
@AutoValue
@JsonDeserialize(builder = AutoValue_ServiceRequest.Builder.class)
abstract class ServiceRequest implements Parcelable {
    @JsonProperty("request_id")
    abstract int requestId();

    @JsonProperty("command")
    abstract String command();

    @JsonProperty("payload")
    abstract Map<String, Object> payload();

    @AutoValue.Builder
    public abstract static class Builder {
        @JsonProperty("request_id")
        public abstract Builder setRequestId(int requestId);

        @JsonProperty("command")
        public abstract Builder setCommand(String command);

        @JsonProperty("payload")
        public abstract Builder setPayload(Map<String, Object> payload);

        public abstract ServiceRequest build();
    }
}
