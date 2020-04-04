package io.github.ytung.tractor;

import java.io.IOException;

import javax.inject.Inject;

import org.atmosphere.config.managed.Encoder;

import com.fasterxml.jackson.databind.ObjectMapper;

public class JacksonEncoder implements Encoder<Object, String> {

    @Inject
    private ObjectMapper mapper = new ObjectMapper();

    @Override
    public String encode(Object s) {
        try {
            return mapper.writeValueAsString(s);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
