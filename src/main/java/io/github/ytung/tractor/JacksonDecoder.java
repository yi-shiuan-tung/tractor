package io.github.ytung.tractor;

import java.io.IOException;

import org.atmosphere.config.managed.Decoder;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JacksonDecoder implements Decoder<String, IncomingMessage> {

    private ObjectMapper mapper = new ObjectMapper();

    @Override
    public IncomingMessage decode(String s) {
        try {
            return mapper.readValue(s, IncomingMessage.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
