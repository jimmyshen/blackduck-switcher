package com.slothbucket.blackduck.client;

import java.io.InputStream;
import java.io.OutputStream;

public interface MessageIoBridge {
    void write(ServiceRequest request, OutputStream outputStream);
    ServiceResponse read(InputStream inputStream);
}
