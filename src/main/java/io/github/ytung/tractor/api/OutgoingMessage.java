package io.github.ytung.tractor.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;

import io.github.ytung.tractor.api.OutgoingMessage.Declare;
import io.github.ytung.tractor.api.OutgoingMessage.Draw;
import io.github.ytung.tractor.api.OutgoingMessage.Forfeit;
import io.github.ytung.tractor.api.OutgoingMessage.Goodbye;
import io.github.ytung.tractor.api.OutgoingMessage.MakeKitty;
import io.github.ytung.tractor.api.OutgoingMessage.SetName;
import io.github.ytung.tractor.api.OutgoingMessage.StartGame;
import io.github.ytung.tractor.api.OutgoingMessage.Welcome;
import io.github.ytung.tractor.api.OutgoingMessage.YourDraw;
import lombok.Data;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = As.WRAPPER_OBJECT)
@JsonSubTypes({
    @JsonSubTypes.Type(value = Welcome.class, name = "WELCOME"),
    @JsonSubTypes.Type(value = Goodbye.class, name = "GOODBYE"),
    @JsonSubTypes.Type(value = SetName.class, name = "SET_NAME"),
    @JsonSubTypes.Type(value = StartGame.class, name = "START_GAME"),
    @JsonSubTypes.Type(value = Draw.class, name = "DRAW"),
    @JsonSubTypes.Type(value = YourDraw.class, name = "YOUR_DRAW"),
    @JsonSubTypes.Type(value = Declare.class, name = "DECLARE"),
    @JsonSubTypes.Type(value = MakeKitty.class, name = "MAKE_KITTY"),
    @JsonSubTypes.Type(value = Forfeit.class, name = "FORFEIT"),
})
public interface OutgoingMessage {

    @Data
    public static class Welcome implements OutgoingMessage {

        private final String playerId;
    }

    @Data
    public static class Goodbye implements OutgoingMessage {

        private final String playerId;
    }

    @Data
    public static class SetName implements OutgoingMessage {

        private final String playerId;
        private final String name;
    }

    @Data
    public static class StartGame implements OutgoingMessage {
    }

    @Data
    public static class Draw implements OutgoingMessage {

        private final String playerId;
        private final List<Integer> cardIds;
    }

    @Data
    public static class YourDraw implements OutgoingMessage {

        private final String playerId;
        private final List<Card> cards;
    }

    @Data
    public static class Declare implements OutgoingMessage {

        private final String playerId;
        private final List<Integer> cardIds;
    }

    @Data
    public static class MakeKitty implements OutgoingMessage {

        private final String playerId;
        private final List<Integer> cardIds;
    }

    @Data
    public static class Forfeit implements OutgoingMessage {

        private final String playerId;
    }
}
