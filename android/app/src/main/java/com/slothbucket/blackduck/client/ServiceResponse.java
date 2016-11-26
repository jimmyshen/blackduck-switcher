package com.slothbucket.blackduck.client;

import android.os.Parcelable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.auto.value.AutoValue;
import java.util.Map;

@AutoValue
// TODO @JsonDeserialize(builder = )
public abstract class ServiceResponse implements Parcelable {
    @JsonProperty("request_id")
    public abstract int requestId();

    @JsonProperty("status")
    public abstract String status();

    @JsonProperty("payload")
    public abstract Map<String, Object> payload();

    @AutoValue.Builder
    public abstract static class Builder {
        @JsonProperty("request_id")
        public abstract Builder setRequestId(String id);

        @JsonProperty("status")
        public abstract Builder setStatus(String status);

        @JsonProperty("payload")
        public abstract Builder setPayload(Map<String, Object> payload);

        public abstract ServiceResponse build();
    }
}
