package com.slothbucket.blackduck.client;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

class MessagePackIoBridge implements MessageIoBridge {
    // Disable the default Jackson configuration that closes my sockets!!! >_<;
    private final JsonFactory jsonFactory =
        new MessagePackFactory()
            .disable(JsonParser.Feature.AUTO_CLOSE_SOURCE)
            .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
    private final ObjectMapper mapper = new ObjectMapper(jsonFactory);

    @Override
    public void write(ServiceRequest request, final OutputStream outputStream) throws IOException {
        mapper.writerFor(ServiceRequest.class).writeValue(outputStream, request);
    }

    @Override
    public ServiceResponse read(InputStream inputStream) throws IOException {
        return mapper.readerFor(ServiceResponse.class).readValue(inputStream);
    }
}
