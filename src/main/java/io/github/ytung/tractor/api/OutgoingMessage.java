package io.github.ytung.tractor.api;

import java.util.List;
import java.util.Map;
import java.util.Queue;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;

import io.github.ytung.tractor.api.OutgoingMessage.CardInfo;
import io.github.ytung.tractor.api.OutgoingMessage.CreateRoom;
import io.github.ytung.tractor.api.OutgoingMessage.Declare;
import io.github.ytung.tractor.api.OutgoingMessage.Draw;
import io.github.ytung.tractor.api.OutgoingMessage.Forfeit;
import io.github.ytung.tractor.api.OutgoingMessage.Goodbye;
import io.github.ytung.tractor.api.OutgoingMessage.InvalidKitty;
import io.github.ytung.tractor.api.OutgoingMessage.JoinRoom;
import io.github.ytung.tractor.api.OutgoingMessage.Kitty;
import io.github.ytung.tractor.api.OutgoingMessage.MakeKitty;
import io.github.ytung.tractor.api.OutgoingMessage.StartRound;
import io.github.ytung.tractor.api.OutgoingMessage.UpdatePlayers;
import io.github.ytung.tractor.api.OutgoingMessage.Welcome;
import io.github.ytung.tractor.api.OutgoingMessage.YourKitty;
import lombok.Data;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = As.WRAPPER_OBJECT)
@JsonSubTypes({
    @JsonSubTypes.Type(value = CreateRoom.class, name = "CREATE_ROOM"),
    @JsonSubTypes.Type(value = JoinRoom.class, name = "JOIN_ROOM"),
    @JsonSubTypes.Type(value = Welcome.class, name = "WELCOME"),
    @JsonSubTypes.Type(value = Goodbye.class, name = "GOODBYE"),
    @JsonSubTypes.Type(value = UpdatePlayers.class, name = "UPDATE_PLAYERS"),
    @JsonSubTypes.Type(value = StartRound.class, name = "START_ROUND"),
    @JsonSubTypes.Type(value = CardInfo.class, name = "CARD_INFO"),
    @JsonSubTypes.Type(value = Draw.class, name = "DRAW"),
    @JsonSubTypes.Type(value = Declare.class, name = "DECLARE"),
    @JsonSubTypes.Type(value = Kitty.class, name = "KITTY"),
    @JsonSubTypes.Type(value = YourKitty.class, name = "YOUR_KITTY"),
    @JsonSubTypes.Type(value = MakeKitty.class, name = "MAKE_KITTY"),
    @JsonSubTypes.Type(value = InvalidKitty.class, name = "INVALID_KITTY"),
    @JsonSubTypes.Type(value = Forfeit.class, name = "FORFEIT"),
})
public interface OutgoingMessage {

    @Data
    public static class CreateRoom implements OutgoingMessage {

        private final String roomCode;
    }

    @Data
    public static class JoinRoom implements OutgoingMessage {

        private final String roomCode;
    }


    @Data
    public static class Welcome implements OutgoingMessage {

        private final Map<String, String> playerNames;
    }

    @Data
    public static class Goodbye implements OutgoingMessage {

        private final String playerId;
    }

    @Data
    public static class UpdatePlayers implements OutgoingMessage {

        private final List<String> playerIds;
        private final Map<String, String> playerNames;
    }

    @Data
    public static class StartRound implements OutgoingMessage {

        private final GameStatus status;
        private final int currentPlayerIndex;
        private final Map<String, Boolean> isDeclaringTeam;
        private final Queue<Integer> deck;
        private final Map<Integer, Card> cardsById;
        private final Map<String, List<Integer>> playerHands;
        private final List<Play> declaredCards;
        private final List<Integer> kitty;
        private final List<Trick> pastTricks;
        private final Trick currentTrick;
    }

    @Data
    public static class CardInfo implements OutgoingMessage {

        private final Map<Integer, Card> cardsById;
    }

    @Data
    public static class Draw implements OutgoingMessage {

        private final GameStatus status;
        private final int currentPlayerIndex;
        private final Queue<Integer> deck;
        private final Map<String, List<Integer>> playerHands;
    }

    @Data
    public static class Kitty implements OutgoingMessage {

        private final String playerId;
        private final List<Integer> cardIds;
    }

    @Data
    public static class YourKitty implements OutgoingMessage {

        private final Map<String, List<Integer>> playerHands;
    }

    @Data
    public static class Declare implements OutgoingMessage {

        private final Map<String, List<Integer>> playerHands;
        private final List<Play> declaredCards;
    }

    @Data
    public static class MakeKitty implements OutgoingMessage {

        private final Map<String, List<Integer>> playerHands;
    }

    @Data
    public static class InvalidKitty implements OutgoingMessage {

        private final String message;
    }

    @Data
    public static class Forfeit implements OutgoingMessage {

        private final String playerId;
    }
}
