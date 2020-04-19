package io.github.ytung.tractor.api;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;

import io.github.ytung.tractor.api.OutgoingMessage.CardInfo;
import io.github.ytung.tractor.api.OutgoingMessage.ConfirmDoesItFly;
import io.github.ytung.tractor.api.OutgoingMessage.CreateRoom;
import io.github.ytung.tractor.api.OutgoingMessage.Declare;
import io.github.ytung.tractor.api.OutgoingMessage.DisconnectMessage;
import io.github.ytung.tractor.api.OutgoingMessage.Draw;
import io.github.ytung.tractor.api.OutgoingMessage.FindAFriendDeclarationMessage;
import io.github.ytung.tractor.api.OutgoingMessage.FinishTrick;
import io.github.ytung.tractor.api.OutgoingMessage.Forfeit;
import io.github.ytung.tractor.api.OutgoingMessage.FriendJoined;
import io.github.ytung.tractor.api.OutgoingMessage.FullRoomState;
import io.github.ytung.tractor.api.OutgoingMessage.GameConfiguration;
import io.github.ytung.tractor.api.OutgoingMessage.InvalidAction;
import io.github.ytung.tractor.api.OutgoingMessage.JoinRoom;
import io.github.ytung.tractor.api.OutgoingMessage.LeaveRoom;
import io.github.ytung.tractor.api.OutgoingMessage.MakeKitty;
import io.github.ytung.tractor.api.OutgoingMessage.PlayMessage;
import io.github.ytung.tractor.api.OutgoingMessage.ReadyForPlay;
import io.github.ytung.tractor.api.OutgoingMessage.ReconnectMessage;
import io.github.ytung.tractor.api.OutgoingMessage.Rejoin;
import io.github.ytung.tractor.api.OutgoingMessage.StartRound;
import io.github.ytung.tractor.api.OutgoingMessage.TakeBack;
import io.github.ytung.tractor.api.OutgoingMessage.TakeKitty;
import io.github.ytung.tractor.api.OutgoingMessage.UpdatePlayers;
import lombok.Data;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = As.WRAPPER_OBJECT)
@JsonSubTypes({
    @JsonSubTypes.Type(value = CreateRoom.class, name = "CREATE_ROOM"),
    @JsonSubTypes.Type(value = JoinRoom.class, name = "JOIN_ROOM"),
    @JsonSubTypes.Type(value = LeaveRoom.class, name = "LEAVE_ROOM"),
    @JsonSubTypes.Type(value = FullRoomState.class, name = "ROOM_STATE"),
    @JsonSubTypes.Type(value = Rejoin.class, name = "REJOIN"),
    @JsonSubTypes.Type(value = UpdatePlayers.class, name = "UPDATE_PLAYERS"),
    @JsonSubTypes.Type(value = GameConfiguration.class, name = "GAME_CONFIGURATION"),
    @JsonSubTypes.Type(value = StartRound.class, name = "START_ROUND"),
    @JsonSubTypes.Type(value = CardInfo.class, name = "CARD_INFO"),
    @JsonSubTypes.Type(value = Draw.class, name = "DRAW"),
    @JsonSubTypes.Type(value = Declare.class, name = "DECLARE"),
    @JsonSubTypes.Type(value = TakeKitty.class, name = "TAKE_KITTY"),
    @JsonSubTypes.Type(value = FindAFriendDeclarationMessage.class, name = "FRIEND_DECLARE"),
    @JsonSubTypes.Type(value = MakeKitty.class, name = "MAKE_KITTY"),
    @JsonSubTypes.Type(value = ReadyForPlay.class, name = "READY_FOR_PLAY"),
    @JsonSubTypes.Type(value = PlayMessage.class, name = "PLAY"),
    @JsonSubTypes.Type(value = FinishTrick.class, name = "FINISH_TRICK"),
    @JsonSubTypes.Type(value = ConfirmDoesItFly.class, name = "CONFIRM_DOES_IT_FLY"),
    @JsonSubTypes.Type(value = FriendJoined.class, name = "FRIEND_JOINED"),
    @JsonSubTypes.Type(value = TakeBack.class, name = "TAKE_BACK"),
    @JsonSubTypes.Type(value = Forfeit.class, name = "FORFEIT"),
    @JsonSubTypes.Type(value = ReconnectMessage.class, name = "RECONNECT"),
    @JsonSubTypes.Type(value = DisconnectMessage.class, name = "DISCONNECT"),
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
    public static class LeaveRoom implements OutgoingMessage {
    }

    @Data
    public static class FullRoomState implements OutgoingMessage {

        private final List<String> playerIds;

        private final int numDecks;
        private final boolean findAFriend;

        private final int roundNumber;
        private final int declarerPlayerIndex;
        private final Map<String, Card.Value> playerRankScores;
        private final Set<String> winningPlayerIds;

        private final GameStatus status;
        private final int currentPlayerIndex;
        private final Map<String, Boolean> isDeclaringTeam;
        private final Queue<Integer> deck;
        private final Map<Integer, Card> cardsById;
        private final Map<String, List<Integer>> playerHands;
        private final List<Play> declaredCards;
        private final List<Integer> kitty;
        private final FindAFriendDeclaration findAFriendDeclaration;
        private final List<Trick> pastTricks;
        private final Trick currentTrick;
        private final Map<String, Integer> currentRoundScores;

        private final Card currentTrump;
        private final int kittySize;

        private final Set<String> humanControllers;
        private final Set<String> aiControllers;
        private final Map<String, String> playerNames;
        private final Map<String, Boolean> playerReadyForPlay;
        private final String myPlayerId;
    }

    @Data
    public static class Rejoin implements OutgoingMessage {

        private final String myPlayerId;
    }

    @Data
    public static class UpdatePlayers implements OutgoingMessage {

        private final List<String> playerIds;
        private final Map<String, Card.Value> playerRankScores;
        private final boolean findAFriend;
        private final int kittySize;
        private final Set<String> aiControllers;
        private final Set<String> humanControllers;
        private final Map<String, String> playerNames;
        private final Map<String, Boolean> playerReadyForPlay;
    }

    @Data
    public static class GameConfiguration implements OutgoingMessage {

        private final int numDecks;
        private final boolean findAFriend;
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
        private final FindAFriendDeclaration findAFriendDeclaration;
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
    public static class FindAFriendDeclarationMessage implements OutgoingMessage {

        private final GameStatus status;
        private final FindAFriendDeclaration findAFriendDeclaration;
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
    public static class ConfirmDoesItFly implements OutgoingMessage {

        private final List<Integer> cardIds;
    }

    @Data
    public static class FriendJoined implements OutgoingMessage {

        private final String playerId;
        private final Map<String, Boolean> isDeclaringTeam;
    }

    @Data
    public static class TakeBack implements OutgoingMessage {

        private final String playerId;

        private final int currentPlayerIndex;
        private final Map<String, List<Integer>> playerHands;
        private final Trick currentTrick;
    }

    @Data
    public static class Forfeit implements OutgoingMessage {

        private final String playerId;
        private final String message;

        private final int roundNumber;
        private final int declarerPlayerIndex;
        private final Map<String, Card.Value> playerRankScores;

        private final GameStatus status;
    }

    @Data
    public static class ReconnectMessage implements OutgoingMessage {

        private final String playerId;
    }

    @Data
    public static class DisconnectMessage implements OutgoingMessage {

        private final String playerId;
    }

    @Data
    public static class InvalidAction implements OutgoingMessage {

        private final String message;
    }
}
