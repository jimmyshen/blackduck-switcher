package com.slothbucket.blackduck.client;

import com.google.auto.value.AutoValue;

import java.util.Arrays;

/**
 * A single request that needs to be sent to the BlackDuck API.
 */
@AutoValue
abstract class ServiceRequest {
    abstract int requestId();
    abstract String commandName();
    abstract byte[] data();

    long dataLength() {
        return data().length;
    }

    static ServiceRequest create(int requestId, String commandName, byte[] data) {
        // TODO: Check if AutoValue performs a defensive copy on its own.
        return new AutoValue_ServiceRequest(
            requestId, commandName, Arrays.copyOf(data, data.length));
    }
}
