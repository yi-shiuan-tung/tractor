package io.github.ytung.tractor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.atmosphere.config.service.DeliverTo;
import org.atmosphere.config.service.Disconnect;
import org.atmosphere.config.service.ManagedService;
import org.atmosphere.config.service.Message;
import org.atmosphere.config.service.PathParam;
import org.atmosphere.config.service.Ready;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.cpr.Broadcaster;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Uninterruptibles;

import io.github.ytung.tractor.api.Card;
import io.github.ytung.tractor.api.IncomingMessage;
import io.github.ytung.tractor.api.IncomingMessage.DeclareRequest;
import io.github.ytung.tractor.api.IncomingMessage.ForfeitRequest;
import io.github.ytung.tractor.api.IncomingMessage.MakeKittyRequest;
import io.github.ytung.tractor.api.IncomingMessage.SetNameRequest;
import io.github.ytung.tractor.api.IncomingMessage.StartGameRequest;
import io.github.ytung.tractor.api.OutgoingMessage;
import io.github.ytung.tractor.api.OutgoingMessage.Declare;
import io.github.ytung.tractor.api.OutgoingMessage.Draw;
import io.github.ytung.tractor.api.OutgoingMessage.Forfeit;
import io.github.ytung.tractor.api.OutgoingMessage.Goodbye;
import io.github.ytung.tractor.api.OutgoingMessage.Kitty;
import io.github.ytung.tractor.api.OutgoingMessage.MakeKitty;
import io.github.ytung.tractor.api.OutgoingMessage.SetName;
import io.github.ytung.tractor.api.OutgoingMessage.StartGame;
import io.github.ytung.tractor.api.OutgoingMessage.Welcome;
import io.github.ytung.tractor.api.OutgoingMessage.YourDraw;
import io.github.ytung.tractor.api.OutgoingMessage.YourKitty;

@ManagedService(path = "/tractor/{roomCode: [a-zA-Z][a-zA-Z_0-9]*}")
public class TractorRoom {

    private final Map<String, AtmosphereResource> resources = new ConcurrentHashMap<>();
    private final Map<String, String> playerNames = new ConcurrentHashMap<>();
    private final Game game = new Game();

    @PathParam("roomCode")
    private String roomCode;

    @Ready(encoders = {JacksonEncoder.class})
    @DeliverTo(DeliverTo.DELIVER_TO.BROADCASTER)
    public OutgoingMessage onReady(AtmosphereResource r) {
        resources.put(r.uuid(), r);
        playerNames.put(r.uuid(), "Unknown");
        game.addPlayer(r.uuid());
        return new Welcome(r.uuid(), playerNames);
    }

    @Disconnect
    public void onDisconnect(AtmosphereResourceEvent r) {
        String playerId = r.getResource().uuid();
        resources.remove(playerId);
        game.removePlayer(playerId);
        playerNames.remove(playerId);
        if (resources.isEmpty()) {
            TractorLobby.closeRoom(roomCode);
        }
        r.broadcaster().broadcast(JacksonEncoder.INSTANCE.encode(new Goodbye(playerId)));
    }

    @Message(encoders = {JacksonEncoder.class}, decoders = {JacksonDecoder.class})
    @DeliverTo(DeliverTo.DELIVER_TO.BROADCASTER)
    public OutgoingMessage onMessage(AtmosphereResource r, IncomingMessage message) {
        if (message instanceof SetNameRequest) {
            String name = ((SetNameRequest) message).getName();
            playerNames.put(r.uuid(), name);
            return new SetName(r.uuid(), name);
        }

        if (message instanceof StartGameRequest) {
            game.startRound();
            startDealing(r.getBroadcaster());
            return new StartGame();
        }

        if (message instanceof DeclareRequest) {
            List<Integer> cardIds = ((DeclareRequest) message).getCardIds();
            Play play = game.declare(r.uuid(), cardIds);
            if (play != null)
                return new Declare(play.getPlayerId(), play.getCards());
        }

        if (message instanceof MakeKittyRequest) {
            List<Integer> cardIds = ((MakeKittyRequest) message).getCardIds();
            if (game.makeKitty(r.uuid(), cardIds))
                return new MakeKitty(r.uuid(), cardIds);
        }

        if (message instanceof ForfeitRequest) {
            game.forfeitRound(r.uuid());
            return new Forfeit(r.uuid());
        }

        throw new IllegalArgumentException("Invalid message.");
    }

    private void startDealing(Broadcaster broadcaster) {
        Thread dealingThread = new Thread() {
            @Override
            public void run() {
                while (true) {
                    Play draw = game.draw();
                    if (draw == null)
                        break;
                    broadcaster.broadcast(JacksonEncoder.INSTANCE.encode(new Draw(
                        draw.getPlayerId(),
                        draw.getCards().get(0).getId())));
                    resources.get(draw.getPlayerId()).write(JacksonEncoder.INSTANCE.encode(new YourDraw(draw.getCards().get(0))));
                    Uninterruptibles.sleepUninterruptibly(500 / game.getPlayerIds().size(), TimeUnit.MILLISECONDS);
                }
                Play kitty = game.takeKitty();
                if (kitty == null)
                    return;
                broadcaster.broadcast(JacksonEncoder.INSTANCE.encode(new Kitty(
                    kitty.getPlayerId(),
                    Lists.transform(kitty.getCards(), Card::getId))));
                resources.get(kitty.getPlayerId()).write(JacksonEncoder.INSTANCE.encode(new YourKitty(kitty.getCards())));
            }
        };

        dealingThread.setDaemon(true);
        dealingThread.start();
    }
}
