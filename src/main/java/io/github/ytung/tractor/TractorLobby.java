package io.github.ytung.tractor;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.atmosphere.config.service.DeliverTo;
import org.atmosphere.config.service.ManagedService;
import org.atmosphere.config.service.Message;
import org.atmosphere.cpr.AtmosphereResource;

import io.github.ytung.tractor.api.IncomingMessage;
import io.github.ytung.tractor.api.IncomingMessage.CreateRoomRequest;
import io.github.ytung.tractor.api.IncomingMessage.JoinRoomRequest;
import io.github.ytung.tractor.api.OutgoingMessage;
import io.github.ytung.tractor.api.OutgoingMessage.CreateRoom;
import io.github.ytung.tractor.api.OutgoingMessage.JoinRoom;

@ManagedService(path = "/tractor")
public class TractorLobby {

    private static final Map<String, String> roomCodes = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private final String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    @Message(encoders = {JacksonEncoder.class}, decoders = {JacksonDecoder.class})
    @DeliverTo(DeliverTo.DELIVER_TO.RESOURCE)
    public OutgoingMessage onMessage(AtmosphereResource r, IncomingMessage message) {
        if (message instanceof CreateRoomRequest) {
            String roomCode = getNewRoomCode();
            roomCodes.put(roomCode, r.uuid());
            return new CreateRoom(roomCode);
        }

        if (message instanceof JoinRoomRequest) {
            String roomCode = ((JoinRoomRequest) message).getRoomCode();
            if (roomExists(roomCode)) {
                return new JoinRoom(roomCode);
            }
        }

        throw new IllegalArgumentException("Invalid message");
    }

    public static boolean roomExists(String roomCode) {
        return roomCodes.containsKey(roomCode);
    }

    private String getNewRoomCode() {
        String code = "";
        for (int i=0; i<4; i++) {
            code += alphabet.charAt(random.nextInt(alphabet.length()));
        }
        if (roomExists(code)) {
            return getNewRoomCode();
        }
        return code;
    }


}
