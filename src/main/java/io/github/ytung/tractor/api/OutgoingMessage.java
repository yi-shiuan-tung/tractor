package io.github.ytung.tractor.api;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;

import io.github.ytung.tractor.api.OutgoingMessage.CardInfo;
import io.github.ytung.tractor.api.OutgoingMessage.CreateRoom;
import io.github.ytung.tractor.api.OutgoingMessage.Declare;
import io.github.ytung.tractor.api.OutgoingMessage.DoneDealing;
import io.github.ytung.tractor.api.OutgoingMessage.Draw;
import io.github.ytung.tractor.api.OutgoingMessage.FinishTrick;
import io.github.ytung.tractor.api.OutgoingMessage.Forfeit;
import io.github.ytung.tractor.api.OutgoingMessage.InvalidAction;
import io.github.ytung.tractor.api.OutgoingMessage.JoinRoom;
import io.github.ytung.tractor.api.OutgoingMessage.MakeKitty;
import io.github.ytung.tractor.api.OutgoingMessage.NumDecks;
import io.github.ytung.tractor.api.OutgoingMessage.PlayMessage;
import io.github.ytung.tractor.api.OutgoingMessage.ReadyForPlay;
import io.github.ytung.tractor.api.OutgoingMessage.StartRound;
import io.github.ytung.tractor.api.OutgoingMessage.TakeKitty;
import io.github.ytung.tractor.api.OutgoingMessage.UpdatePlayers;
import io.github.ytung.tractor.api.OutgoingMessage.Welcome;
import lombok.Data;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = As.WRAPPER_OBJECT)
@JsonSubTypes({
    @JsonSubTypes.Type(value = CreateRoom.class, name = "CREATE_ROOM"),
    @JsonSubTypes.Type(value = JoinRoom.class, name = "JOIN_ROOM"),
    @JsonSubTypes.Type(value = Welcome.class, name = "WELCOME"),
    @JsonSubTypes.Type(value = UpdatePlayers.class, name = "UPDATE_PLAYERS"),
    @JsonSubTypes.Type(value = NumDecks.class, name = "NUM_DECKS"),
    @JsonSubTypes.Type(value = StartRound.class, name = "START_ROUND"),
    @JsonSubTypes.Type(value = CardInfo.class, name = "CARD_INFO"),
    @JsonSubTypes.Type(value = Draw.class, name = "DRAW"),
    @JsonSubTypes.Type(value = Declare.class, name = "DECLARE"),
    @JsonSubTypes.Type(value = DoneDealing.class, name = "DONE_DEALING"),
    @JsonSubTypes.Type(value = TakeKitty.class, name = "TAKE_KITTY"),
    @JsonSubTypes.Type(value = MakeKitty.class, name = "MAKE_KITTY"),
    @JsonSubTypes.Type(value = ReadyForPlay.class, name = "READY_FOR_PLAY"),
    @JsonSubTypes.Type(value = PlayMessage.class, name = "PLAY"),
    @JsonSubTypes.Type(value = FinishTrick.class, name = "FINISH_TRICK"),
    @JsonSubTypes.Type(value = Forfeit.class, name = "FORFEIT"),
    @JsonSubTypes.Type(value = InvalidAction.class, name = "INVALID_ACTION"),
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
    public static class UpdatePlayers implements OutgoingMessage {

        private final List<String> playerIds;
        private final Map<String, Card.Value> playerRankScores;
        private final int kittySize;
        private final Map<String, String> playerNames;
        private final Map<String, Boolean> playerReadyForPlay;
    }

    @Data
    public static class NumDecks implements OutgoingMessage {

        private final int numDecks;
        private final int kittySize;
        private final Map<String, Boolean> playerReadyForPlay;
    }

    @Data
    public static class StartRound implements OutgoingMessage {

        private final int roundNumber;
        private final int declarerPlayerIndex;
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
        private final Map<String, Integer> currentRoundScores;
        private final Card currentTrump;
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
    public static class DoneDealing implements OutgoingMessage {

    }

    @Data
    public static class TakeKitty implements OutgoingMessage {

        private final GameStatus status;
        private final int currentPlayerIndex;
        private final Queue<Integer> deck;
        private final Map<String, List<Integer>> playerHands;
    }

    @Data
    public static class Declare implements OutgoingMessage {

        private final int declarerPlayerIndex;

        private final Map<String, Boolean> isDeclaringTeam;
        private final Map<String, List<Integer>> playerHands;
        private final List<Play> declaredCards;
        private final Card currentTrump;
        private final Map<String, Boolean> playerReadyForPlay;
    }

    @Data
    public static class ReadyForPlay implements OutgoingMessage {

        private final Map<String, Boolean> playerReadyForPlay;
    }

    @Data
    public static class MakeKitty implements OutgoingMessage {

        private final GameStatus status;
        private final List<Integer> kitty;
        private final Map<String, List<Integer>> playerHands;
        private final Trick currentTrick;
    }

    @Data
    public static class PlayMessage implements OutgoingMessage {

        private final int currentPlayerIndex;
        private final Map<String, List<Integer>> playerHands;
        private final Trick currentTrick;
    }

    @Data
    public static class FinishTrick implements OutgoingMessage {

        private final int roundNumber;
        private final int declarerPlayerIndex;
        private final Map<String, Card.Value> playerRankScores;
        private final boolean doDeclarersWin;
        private final Set<String> winningPlayerIds;

        private final GameStatus status;
        private final int currentPlayerIndex;
        private final List<Trick> pastTricks;
        private final Trick currentTrick;
        private final Map<String, Integer> currentRoundScores;
        private final Card currentTrump;
    }

    @Data
    public static class Forfeit implements OutgoingMessage {

        private final String playerId;

        private final int roundNumber;
        private final int declarerPlayerIndex;
        private final Map<String, Card.Value> playerRankScores;

        private final GameStatus status;
    }

    @Data
    public static class InvalidAction implements OutgoingMessage {

        private final String message;
    }
}
