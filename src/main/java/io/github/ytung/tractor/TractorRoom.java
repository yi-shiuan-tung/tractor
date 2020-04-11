
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
import io.github.ytung.tractor.api.FindAFriendDeclaration;
import io.github.ytung.tractor.api.GameStatus;
import io.github.ytung.tractor.api.IncomingMessage;
import io.github.ytung.tractor.api.IncomingMessage.DeclareRequest;
import io.github.ytung.tractor.api.IncomingMessage.FindAFriendDeclarationRequest;
import io.github.ytung.tractor.api.IncomingMessage.ForfeitRequest;
import io.github.ytung.tractor.api.IncomingMessage.GameConfigurationRequest;
import io.github.ytung.tractor.api.IncomingMessage.MakeKittyRequest;
import io.github.ytung.tractor.api.IncomingMessage.PlayRequest;
import io.github.ytung.tractor.api.IncomingMessage.PlayerOrderRequest;
import io.github.ytung.tractor.api.IncomingMessage.ReadyForPlayRequest;
import io.github.ytung.tractor.api.IncomingMessage.SetNameRequest;
import io.github.ytung.tractor.api.OutgoingMessage;
import io.github.ytung.tractor.api.OutgoingMessage.CardInfo;
import io.github.ytung.tractor.api.OutgoingMessage.Declare;
import io.github.ytung.tractor.api.OutgoingMessage.DoneDealing;
import io.github.ytung.tractor.api.OutgoingMessage.Draw;
import io.github.ytung.tractor.api.OutgoingMessage.FindAFriendDeclarationMessage;
import io.github.ytung.tractor.api.OutgoingMessage.FinishTrick;
import io.github.ytung.tractor.api.OutgoingMessage.Forfeit;
import io.github.ytung.tractor.api.OutgoingMessage.FriendJoined;
import io.github.ytung.tractor.api.OutgoingMessage.GameConfiguration;
import io.github.ytung.tractor.api.OutgoingMessage.InvalidAction;
import io.github.ytung.tractor.api.OutgoingMessage.MakeKitty;
import io.github.ytung.tractor.api.OutgoingMessage.PlayMessage;
import io.github.ytung.tractor.api.OutgoingMessage.ReadyForPlay;
import io.github.ytung.tractor.api.OutgoingMessage.StartRound;
import io.github.ytung.tractor.api.OutgoingMessage.TakeKitty;
import io.github.ytung.tractor.api.OutgoingMessage.UpdatePlayers;
import io.github.ytung.tractor.api.Play;

@ManagedService(path = "/tractor/{roomCode: [a-zA-Z][a-zA-Z_0-9]*}")
public class TractorRoom {

    /**
     * When this flag is on, certain properties will be simplified to facilitate easier testing:
     *
     * <pre>
     * 1. Only one person is needed to start the game or to confirm the declare phase is over.
     * 2. The deal phase becomes very fast.
     * </pre>
     */
    private static final boolean DEV_MODE = false;

    private final Map<String, AtmosphereResource> resources = new ConcurrentHashMap<>();
    private final Map<String, String> playerNames = new ConcurrentHashMap<>();
    private final Map<String, Boolean> playerReadyForPlay = new ConcurrentHashMap<>();
    private final Game game = new Game();

    @PathParam("roomCode")
    private String roomCode;

    @Ready(encoders = {JacksonEncoder.class})
    @DeliverTo(DeliverTo.DELIVER_TO.BROADCASTER)
    public OutgoingMessage onReady(AtmosphereResource r) {
        if (resources.containsKey(r.uuid()))
            return null;

        resources.put(r.uuid(), r);
        playerNames.put(r.uuid(), Names.generateRandomName());
        playerReadyForPlay.put(r.uuid(), false);
        game.addPlayer(r.uuid());
        return new UpdatePlayers(
            game.getPlayerIds(),
            game.getPlayerRankScores(),
            game.isFindAFriend(),
            game.getKittySize(),
            playerNames,
            playerReadyForPlay);
    }

    @Disconnect
    public void onDisconnect(AtmosphereResourceEvent r) {
        String playerId = r.getResource().uuid();
        if (resources.remove(playerId) == null)
            return;

        if (game.getStatus() != GameStatus.START_ROUND) {
            game.forfeitRound(playerId);
            sendSync(r.getResource().getBroadcaster(), new Forfeit(
                playerId,
                game.getRoundNumber(),
                game.getDeclarerPlayerIndex(),
                game.getPlayerRankScores(),
                game.getStatus()));
        }

        game.removePlayer(playerId);
        playerNames.remove(playerId);
        playerReadyForPlay.remove(playerId);
        sendSync(r.getResource().getBroadcaster(), new UpdatePlayers(
            game.getPlayerIds(),
            game.getPlayerRankScores(),
            game.isFindAFriend(),
            game.getKittySize(),
            playerNames,
            playerReadyForPlay));
        if (resources.isEmpty()) {
            TractorLobby.closeRoom(roomCode);
        }
    }

    @Message(encoders = {JacksonEncoder.class}, decoders = {JacksonDecoder.class})
    @DeliverTo(DeliverTo.DELIVER_TO.BROADCASTER)
    public OutgoingMessage onMessage(AtmosphereResource r, IncomingMessage message) throws Exception {
        System.out.println(message);

        if (message instanceof SetNameRequest) {
            String name = ((SetNameRequest) message).getName();
            playerNames.put(r.uuid(), name);
            return new UpdatePlayers(
                game.getPlayerIds(),
                game.getPlayerRankScores(),
                game.isFindAFriend(),
                game.getKittySize(),
                playerNames,
                playerReadyForPlay);
        }

        if (message instanceof PlayerOrderRequest) {
            List<String> playerIds = ((PlayerOrderRequest) message).getPlayerIds();
            game.setPlayerOrder(playerIds);
            playerReadyForPlay.replaceAll((k, v) -> v=false);
            return new UpdatePlayers(
                game.getPlayerIds(),
                game.getPlayerRankScores(),
                game.isFindAFriend(),
                game.getKittySize(),
                playerNames,
                playerReadyForPlay);
        }

        if (message instanceof GameConfigurationRequest) {
            game.setNumDecks(((GameConfigurationRequest) message).getNumDecks());
            game.setFindAFriend(((GameConfigurationRequest) message).isFindAFriend());
            playerReadyForPlay.replaceAll((k, v) -> v=false);
            return new GameConfiguration(game.getNumDecks(), game.isFindAFriend(), game.getKittySize(), playerReadyForPlay);
        }

        if (message instanceof DeclareRequest) {
            List<Integer> cardIds = ((DeclareRequest) message).getCardIds();
            try {
                game.declare(r.uuid(), cardIds);
                Map<Integer, Card> cardsById = game.getCardsById();
                sendSync(r.getBroadcaster(), new CardInfo(Maps.toMap(cardIds, cardsById::get)));
                playerReadyForPlay.replaceAll((k, v) -> v=false);
                return new Declare(
                    game.getDeclarerPlayerIndex(),
                    game.getIsDeclaringTeam(),
                    game.getPlayerHands(),
                    game.getDeclaredCards(),
                    game.getCurrentTrump(),
                    playerReadyForPlay);
            } catch (InvalidDeclareException e) {
                sendSync(r, new InvalidAction(e.getMessage()));
                return null;
            }
        }

        if (message instanceof ReadyForPlayRequest) {
            playerReadyForPlay.put(r.uuid(), ((ReadyForPlayRequest) message).isReady());
            if (!playerReadyForPlay.containsValue(false) || DEV_MODE) {
                if (game.getStatus() == GameStatus.START_ROUND)
                    startRound(r.getBroadcaster());
                else if (game.getStatus() == GameStatus.DRAW_KITTY)
                    dealKitty(r.getBroadcaster());
                else
                    throw new IllegalStateException();
                playerReadyForPlay.replaceAll((k, v) -> v=false); // reset for next time
            }
            return new ReadyForPlay(playerReadyForPlay);
        }

        if (message instanceof FindAFriendDeclarationRequest) {
            FindAFriendDeclaration declaration = ((FindAFriendDeclarationRequest) message).getDeclaration();
            try {
                game.makeFindAFriendDeclaration(r.uuid(), declaration);
                return new FindAFriendDeclarationMessage(declaration);
            } catch (InvalidFindAFriendDeclarationException e) {
                sendSync(resources.get(r.uuid()), new InvalidAction(e.getMessage()));
                return null;
            }
        }

        if (message instanceof MakeKittyRequest) {
            List<Integer> cardIds = ((MakeKittyRequest) message).getCardIds();
            try {
                game.makeKitty(r.uuid(), cardIds);
                return new MakeKitty(game.getStatus(), game.getKitty(), game.getPlayerHands(), game.getCurrentTrick());
            } catch (InvalidKittyException e) {
                sendSync(resources.get(r.uuid()), new InvalidAction(e.getMessage()));
                return null;
            }
        }

        if (message instanceof PlayRequest) {
            List<Integer> cardIds = ((PlayRequest) message).getCardIds();
            try {
                PlayResult result = game.play(r.uuid(), cardIds);
                Map<Integer, Card> cardsById = game.getCardsById();
                sendSync(r.getBroadcaster(), new CardInfo(Maps.toMap(cardIds, cardsById::get)));
                sendSync(r.getBroadcaster(), new PlayMessage(
                    game.getCurrentPlayerIndex(),
                    game.getPlayerHands(),
                    game.getCurrentTrick()));
                if (result.isTrickComplete())
                    scheduleFinishTrick(r.getBroadcaster());
                if (result.isDidFriendJoin())
                    sendSync(r.getBroadcaster(), new FriendJoined(r.uuid(), game.getIsDeclaringTeam()));
            } catch (InvalidPlayException e) {
                sendSync(r, new InvalidAction(e.getMessage()));
            }
            return null;
        }

        if (message instanceof ForfeitRequest) {
            game.forfeitRound(r.uuid());
            return new Forfeit(
                r.uuid(),
                game.getRoundNumber(),
                game.getDeclarerPlayerIndex(),
                game.getPlayerRankScores(),
                game.getStatus());
        }

        throw new IllegalArgumentException("Invalid message.");
    }

    private void startRound(Broadcaster broadcaster) {
        game.startRound();
        sendSync(broadcaster, new StartRound(
            game.getRoundNumber(),
            game.getDeclarerPlayerIndex(),
            game.getStatus(),
            game.getCurrentPlayerIndex(),
            game.getIsDeclaringTeam(),
            game.getDeck(),
            new HashMap<>(), // no cards are known at beginning
            game.getPlayerHands(),
            game.getDeclaredCards(),
            game.getKitty(),
            game.getPastTricks(),
            game.getCurrentTrick(),
            game.getCurrentRoundScores(),
            game.getCurrentTrump()));
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
                    Uninterruptibles.sleepUninterruptibly((DEV_MODE ? 5 : 500) / game.getPlayerIds().size(), TimeUnit.MILLISECONDS);
                }
                sendSync(broadcaster, new DoneDealing());
            }
        };

        dealingThread.setDaemon(true);
        dealingThread.start();
    }

    private void dealKitty(Broadcaster broadcaster) {
        Play kitty = game.takeKitty();
        if (kitty == null)
            return;
        sendSync(resources.get(kitty.getPlayerId()), new CardInfo(Maps.toMap(kitty.getCardIds(), game.getCardsById()::get)));
        sendSync(broadcaster, new TakeKitty(
                game.getStatus(),
                game.getCurrentPlayerIndex(),
                game.getDeck(),
                game.getPlayerHands()));
    }

    private void scheduleFinishTrick(Broadcaster broadcaster) {
        Thread finishTrickThread = new Thread() {
            @Override
            public void run() {
                Uninterruptibles.sleepUninterruptibly(1500, TimeUnit.MILLISECONDS);
                game.finishTrick();
                sendSync(broadcaster, new FinishTrick(
                    game.getRoundNumber(),
                    game.getDeclarerPlayerIndex(),
                    game.getPlayerRankScores(),
                    game.isDoDeclarersWin(),
                    game.getWinningPlayerIds(),
                    game.getStatus(),
                    game.getCurrentPlayerIndex(),
                    game.getPastTricks(),
                    game.getCurrentTrick(),
                    game.getCurrentRoundScores(),
                    game.getCurrentTrump()));
                if (game.getStatus() != GameStatus.PLAY)
                    // game end, send kitty card info to all players
                    sendSync(broadcaster, new CardInfo(Maps.toMap(game.getKitty(), game.getCardsById()::get)));

            }
        };

        finishTrickThread.setDaemon(true);
        finishTrickThread.start();
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
