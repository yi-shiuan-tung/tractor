package io.github.ytung.tractor.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;

import io.github.ytung.tractor.api.IncomingMessage.CreateRoomRequest;
import io.github.ytung.tractor.api.IncomingMessage.DeclareRequest;
import io.github.ytung.tractor.api.IncomingMessage.FindAFriendDeclarationRequest;
import io.github.ytung.tractor.api.IncomingMessage.ForfeitRequest;
import io.github.ytung.tractor.api.IncomingMessage.GameConfigurationRequest;
import io.github.ytung.tractor.api.IncomingMessage.JoinRoomRequest;
import io.github.ytung.tractor.api.IncomingMessage.MakeKittyRequest;
import io.github.ytung.tractor.api.IncomingMessage.PlayRequest;
import io.github.ytung.tractor.api.IncomingMessage.PlayerOrderRequest;
import io.github.ytung.tractor.api.IncomingMessage.ReadyForPlayRequest;
import io.github.ytung.tractor.api.IncomingMessage.SetNameRequest;
import io.github.ytung.tractor.api.IncomingMessage.StartRoundRequest;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = As.WRAPPER_OBJECT)
@JsonSubTypes({
    @JsonSubTypes.Type(value = CreateRoomRequest.class, name = "CREATE_ROOM"),
    @JsonSubTypes.Type(value = JoinRoomRequest.class, name = "JOIN_ROOM"),
    @JsonSubTypes.Type(value = SetNameRequest.class, name = "SET_NAME"),
    @JsonSubTypes.Type(value = PlayerOrderRequest.class, name = "PLAYER_ORDER"),
    @JsonSubTypes.Type(value = GameConfigurationRequest.class, name = "GAME_CONFIGURATION"),
    @JsonSubTypes.Type(value = StartRoundRequest.class, name = "START_ROUND"),
    @JsonSubTypes.Type(value = DeclareRequest.class, name = "DECLARE"),
    @JsonSubTypes.Type(value = FindAFriendDeclarationRequest.class, name = "FRIEND_DECLARE"),
    @JsonSubTypes.Type(value = MakeKittyRequest.class, name = "MAKE_KITTY"),
    @JsonSubTypes.Type(value = ReadyForPlayRequest.class, name = "READY_FOR_PLAY"),
    @JsonSubTypes.Type(value = PlayRequest.class, name = "PLAY"),
    @JsonSubTypes.Type(value = ForfeitRequest.class, name = "FORFEIT"),
})
public interface IncomingMessage {

    @Data
    @NoArgsConstructor
    public static class CreateRoomRequest implements IncomingMessage {

    }

    @Data
    @NoArgsConstructor
    public static class JoinRoomRequest implements IncomingMessage {

        private String roomCode;
    }

    @Data
    @NoArgsConstructor
    public static class SetNameRequest implements IncomingMessage {

        private String name;
    }

    @Data
    @NoArgsConstructor
    public static class PlayerOrderRequest implements IncomingMessage {

        private List<String> playerIds;
    }

    @Data
    @NoArgsConstructor
    public static class GameConfigurationRequest implements IncomingMessage {

        private int numDecks;
        private boolean findAFriend;
    }

    @Data
    @NoArgsConstructor
    public static class StartRoundRequest implements IncomingMessage {
    }

    @Data
    @NoArgsConstructor
    public static class DeclareRequest implements IncomingMessage {

        private List<Integer> cardIds;
    }

    @Data
    @NoArgsConstructor
    public static class ReadyForPlayRequest implements IncomingMessage {

        private boolean ready;
    }

    @Data
    @NoArgsConstructor
    public static class FindAFriendDeclarationRequest implements IncomingMessage {

        private FindAFriendDeclaration declaration;
    }

    @Data
    @NoArgsConstructor
    public static class MakeKittyRequest implements IncomingMessage {

        private List<Integer> cardIds;
    }

    @Data
    @NoArgsConstructor
    public static class PlayRequest implements IncomingMessage {

        private List<Integer> cardIds;
        private boolean confirmDoesItFly;
    }

    @Data
    @NoArgsConstructor
    public static class ForfeitRequest implements IncomingMessage {
    }
}
