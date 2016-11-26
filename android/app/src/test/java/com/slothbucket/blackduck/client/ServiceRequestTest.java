package com.slothbucket.blackduck.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;
import org.msgpack.jackson.dataformat.MessagePackFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

import static org.junit.Assert.assertTrue;

/**
 * Test for {@link ServiceRequest}.
 */
public class ServiceRequestTest {

    private final ObjectMapper mapper = new ObjectMapper(new MessagePackFactory());

    @Test
    public void serialize_msgpack_roundTripSucceeds() throws Exception {
        ArrayList<String> iconIds = new ArrayList<>();
        iconIds.add("a");
        iconIds.add("b");
        iconIds.add("c");

        RequestPayload payload =
            RequestPayload.builder()
                .setIconIds(iconIds)
                .setLastUpdateTimestamp(123456)
                .build();

        ServiceRequest original =
            ServiceRequest.builder()
                .setRequestId(123)
                .setCommand("command")
                .setPayload(payload)
                .build();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        mapper.writerFor(ServiceRequest.class).writeValue(outputStream, original);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
        ServiceRequest result = mapper.readerFor(ServiceRequest.class).readValue(inputStream);
        assertTrue(result.equals(original));
    }
}
