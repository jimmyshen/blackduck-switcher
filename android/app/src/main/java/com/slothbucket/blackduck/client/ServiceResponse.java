package com.slothbucket.blackduck.client;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class ServiceResponse {
    abstract int requestId();
}
