package io.github.ytung.tractor.api;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;

import io.github.ytung.tractor.api.OutgoingMessage.Draw;
import io.github.ytung.tractor.api.OutgoingMessage.StartGame;
import io.github.ytung.tractor.api.OutgoingMessage.Welcome;
import lombok.Data;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = As.WRAPPER_OBJECT)
@JsonSubTypes({
    @JsonSubTypes.Type(value = Welcome.class, name = "WELCOME"),
    @JsonSubTypes.Type(value = StartGame.class, name = "START_GAME"),
    @JsonSubTypes.Type(value = Draw.class, name = "DRAW"),
})
public interface OutgoingMessage {

    @Data
    public static class Welcome implements OutgoingMessage {

        private final String playerId;
    }

    @Data
    public static class StartGame implements OutgoingMessage {
    }

    @Data
    public static class Draw implements OutgoingMessage {

        private final String playerId;
        private final Card card;
    }
}
