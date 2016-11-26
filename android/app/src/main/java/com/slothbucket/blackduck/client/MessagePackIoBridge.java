package com.slothbucket.blackduck.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class MessagePackIoBridge implements MessageIoBridge {
    private final ObjectMapper mapper = new ObjectMapper(new MessagePackFactory());

    @Override
    public void write(ServiceRequest request, OutputStream outputStream) throws IOException {
        mapper.writerFor(ServiceRequest.class).writeValue(outputStream, request);
    }

    @Override
    public ServiceResponse read(InputStream inputStream) throws IOException {
        return mapper.readerFor(ServiceResponse.class).readValue(inputStream);
    }
}
