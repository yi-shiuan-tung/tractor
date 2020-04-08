package io.github.ytung.tractor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
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

import com.google.common.collect.Maps;
import com.google.common.util.concurrent.Uninterruptibles;

import io.github.ytung.tractor.api.Card;
import io.github.ytung.tractor.api.IncomingMessage;
import io.github.ytung.tractor.api.IncomingMessage.DeclareRequest;
import io.github.ytung.tractor.api.IncomingMessage.ForfeitRequest;
import io.github.ytung.tractor.api.IncomingMessage.MakeKittyRequest;
import io.github.ytung.tractor.api.IncomingMessage.SetNameRequest;
import io.github.ytung.tractor.api.IncomingMessage.StartRoundRequest;
import io.github.ytung.tractor.api.OutgoingMessage;
import io.github.ytung.tractor.api.OutgoingMessage.CardInfo;
import io.github.ytung.tractor.api.OutgoingMessage.Declare;
import io.github.ytung.tractor.api.OutgoingMessage.Draw;
import io.github.ytung.tractor.api.OutgoingMessage.Forfeit;
import io.github.ytung.tractor.api.OutgoingMessage.Goodbye;
import io.github.ytung.tractor.api.OutgoingMessage.InvalidKitty;
import io.github.ytung.tractor.api.OutgoingMessage.Kitty;
import io.github.ytung.tractor.api.OutgoingMessage.MakeKitty;
import io.github.ytung.tractor.api.OutgoingMessage.StartRound;
import io.github.ytung.tractor.api.OutgoingMessage.UpdatePlayers;
import io.github.ytung.tractor.api.OutgoingMessage.YourKitty;
import io.github.ytung.tractor.api.Play;

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
        return new UpdatePlayers(game.getPlayerIds(), playerNames);
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
    public OutgoingMessage onMessage(AtmosphereResource r, IncomingMessage message) throws Exception {
        if (message instanceof SetNameRequest) {
            String name = ((SetNameRequest) message).getName();
            playerNames.put(r.uuid(), name);
            return new UpdatePlayers(game.getPlayerIds(), playerNames);
        }

        if (message instanceof StartRoundRequest) {
            game.startRound();
            sendSync(r.getBroadcaster(), new StartRound(
                game.getStatus(),
                game.getCurrentPlayerIndex(),
                game.getIsDeclaringTeam(),
                game.getDeck(),
                new HashMap<>(), // no cards are known at beginning
                game.getPlayerHands(),
                game.getDeclaredCards(),
                game.getKitty(),
                game.getPastTricks(),
                game.getCurrentTrick()));
            startDealing(r.getBroadcaster());
            return null;
        }

        if (message instanceof DeclareRequest) {
            List<Integer> cardIds = ((DeclareRequest) message).getCardIds();
            game.declare(r.uuid(), cardIds);
            Map<Integer, Card> cardsById = game.getCardsById();
            sendSync(r.getBroadcaster(), new CardInfo(Maps.toMap(cardIds, cardsById::get)));
            return new Declare(game.getPlayerHands(), game.getDeclaredCards());
        }

        if (message instanceof MakeKittyRequest) {
            List<Integer> cardIds = ((MakeKittyRequest) message).getCardIds();
            try {
                game.makeKitty(r.uuid(), cardIds);
                return new MakeKitty(game.getPlayerHands());
            } catch (InvalidKittyException e) {
                sendSync(resources.get(r.uuid()), new InvalidKitty(e.getMessage()));
            }
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
                Map<Integer, Card> cardsById = game.getCardsById();
                while (true) {
                    Play draw = game.draw();
                    if (draw == null)
                        break;
                    sendSync(resources.get(draw.getPlayerId()), new CardInfo(Maps.toMap(draw.getCardIds(), cardsById::get)));
                    sendSync(broadcaster, new Draw(
                        game.getStatus(),
                        game.getCurrentPlayerIndex(),
                        game.getDeck(),
                        game.getPlayerHands()));
                    Uninterruptibles.sleepUninterruptibly(500 / game.getPlayerIds().size(), TimeUnit.MILLISECONDS);
                }
                Play kitty = game.takeKitty();
                if (kitty == null)
                    return;
                sendSync(broadcaster, new Kitty(kitty.getPlayerId(), kitty.getCardIds()));
                sendSync(resources.get(kitty.getPlayerId()), new CardInfo(Maps.toMap(kitty.getCardIds(), cardsById::get)));
                sendSync(resources.get(kitty.getPlayerId()), new YourKitty(game.getPlayerHands()));
            }
        };

        dealingThread.setDaemon(true);
        dealingThread.start();
    }

    private void sendSync(AtmosphereResource resource, OutgoingMessage message) {
        resource.write(JacksonEncoder.INSTANCE.encode(message));
    }

    private void sendSync(Broadcaster broadcaster, OutgoingMessage message) {
        try {
            broadcaster.broadcast(JacksonEncoder.INSTANCE.encode(message)).get();
        } catch (ExecutionException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
}
