package com.slothbucket.blackduck.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface MessageIoBridge {
    void write(ServiceRequest request, OutputStream outputStream) throws IOException;
    ServiceResponse read(InputStream inputStream) throws IOException;
}
