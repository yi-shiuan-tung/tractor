package io.github.ytung.tractor.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;

import io.github.ytung.tractor.api.IncomingMessage.DrawRequest;
import io.github.ytung.tractor.api.IncomingMessage.StartGameRequest;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = As.WRAPPER_OBJECT)
@JsonSubTypes({
    @JsonSubTypes.Type(value = StartGameRequest.class, name = "START_GAME"),
    @JsonSubTypes.Type(value = DrawRequest.class, name = "DRAW"),
})
public interface IncomingMessage {

    @Data
    @NoArgsConstructor
    public static class StartGameRequest implements IncomingMessage {
    }

    @Data
    @NoArgsConstructor
    public static class DrawRequest implements IncomingMessage {
    }
}
