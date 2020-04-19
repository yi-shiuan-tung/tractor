
package io.github.ytung.tractor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.atmosphere.config.service.DeliverTo;
import org.atmosphere.config.service.Disconnect;
import org.atmosphere.config.service.ManagedService;
import org.atmosphere.config.service.Message;
import org.atmosphere.config.service.PathParam;
import org.atmosphere.config.service.Ready;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.cpr.Broadcaster;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Uninterruptibles;

import io.github.ytung.tractor.ai.AiClient;
import io.github.ytung.tractor.api.Card;
import io.github.ytung.tractor.api.FindAFriendDeclaration;
import io.github.ytung.tractor.api.GameStatus;
import io.github.ytung.tractor.api.IncomingMessage;
import io.github.ytung.tractor.api.IncomingMessage.AddAiRequest;
import io.github.ytung.tractor.api.IncomingMessage.DeclareRequest;
import io.github.ytung.tractor.api.IncomingMessage.FindAFriendDeclarationRequest;
import io.github.ytung.tractor.api.IncomingMessage.ForfeitRequest;
import io.github.ytung.tractor.api.IncomingMessage.GameConfigurationRequest;
import io.github.ytung.tractor.api.IncomingMessage.MakeKittyRequest;
import io.github.ytung.tractor.api.IncomingMessage.PlayRequest;
import io.github.ytung.tractor.api.IncomingMessage.PlayerOrderRequest;
import io.github.ytung.tractor.api.IncomingMessage.PlayerScoreRequest;
import io.github.ytung.tractor.api.IncomingMessage.ReadyForPlayRequest;
import io.github.ytung.tractor.api.IncomingMessage.RejoinRequest;
import io.github.ytung.tractor.api.IncomingMessage.RemovePlayerRequest;
import io.github.ytung.tractor.api.IncomingMessage.SetNameRequest;
import io.github.ytung.tractor.api.IncomingMessage.TakeBackRequest;
import io.github.ytung.tractor.api.OutgoingMessage;
import io.github.ytung.tractor.api.OutgoingMessage.CardInfo;
import io.github.ytung.tractor.api.OutgoingMessage.ConfirmDoesItFly;
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
import io.github.ytung.tractor.api.OutgoingMessage.MakeKitty;
import io.github.ytung.tractor.api.OutgoingMessage.PlayMessage;
import io.github.ytung.tractor.api.OutgoingMessage.ReadyForPlay;
import io.github.ytung.tractor.api.OutgoingMessage.ReconnectMessage;
import io.github.ytung.tractor.api.OutgoingMessage.Rejoin;
import io.github.ytung.tractor.api.OutgoingMessage.StartRound;
import io.github.ytung.tractor.api.OutgoingMessage.TakeBack;
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

    private final Set<AtmosphereResource> resources = ConcurrentHashMap.newKeySet();

    private final BiMap<String, AtmosphereResource> humanControllers = Maps.synchronizedBiMap(HashBiMap.create());
    private final Map<String, AiClient> aiControllers = new ConcurrentHashMap<>();

    private final Map<String, String> playerNames = new ConcurrentHashMap<>();
    private final Map<String, Boolean> playerReadyForPlay = new ConcurrentHashMap<>();
    private final Game game = new Game();

    @PathParam("roomCode")
    private String roomCode;

    @Ready
    public void onReady(AtmosphereResource r) {
        resources.add(r);

        Set<String> unmappedPlayerIds = game.getPlayerIds().stream()
            .filter(playerId -> !humanControllers.containsKey(playerId) && !aiControllers.containsKey(playerId))
            .collect(Collectors.toSet());
        String myPlayerId = null;

        if (unmappedPlayerIds.isEmpty() && game.getStatus() == GameStatus.START_ROUND) {
            addHumanController(r);
            myPlayerId = r.uuid();
            broadcastUpdatePlayers(r.getBroadcaster());
        }

        r.write(JacksonEncoder.INSTANCE.encode(new FullRoomState(
            game.getPlayerIds(),
            game.getNumDecks(),
            game.isFindAFriend(),
            game.getRoundNumber(),
            game.getDeclarerPlayerIndex(),
            game.getPlayerRankScores(),
            game.getWinningPlayerIds(),
            game.getStatus(),
            game.getCurrentPlayerIndex(),
            game.getIsDeclaringTeam(),
            game.getDeck(),
            game.getPublicCards(),
            game.getPlayerHands(),
            game.getDeclaredCards(),
            game.getKitty(),
            game.getFindAFriendDeclaration(),
            game.getPastTricks(),
            game.getCurrentTrick(),
            game.getCurrentRoundScores(),
            game.getCurrentTrump(),
            game.getKittySize(),
            humanControllers.keySet(),
            aiControllers.keySet(),
            playerNames,
            playerReadyForPlay,
            myPlayerId)));
    }

    @Disconnect
    public void onDisconnect(AtmosphereResourceEvent r) {
        resources.remove(r.getResource());

        String playerId = humanControllers.inverse().get(r.getResource());
        if (playerId == null)
            return;

        humanControllers.remove(playerId);
        broadcastUpdatePlayers(r.broadcaster());
        sendSync(r.broadcaster(), new DisconnectMessage(playerId));

        if (resources.isEmpty())
            TractorLobby.closeRoom(roomCode);
    }

    @Message(decoders = {JacksonDecoder.class})
    @DeliverTo(DeliverTo.DELIVER_TO.BROADCASTER)
    public void onMessage(AtmosphereResource r, IncomingMessage message) throws Exception {
        System.out.println(message);

        if (message instanceof RejoinRequest) {
            String playerId = ((RejoinRequest) message).getPlayerId();
            if (game.getPlayerIds().contains(playerId)
                    && !humanControllers.containsKey(playerId)
                    && !humanControllers.containsValue(r)
                    && !aiControllers.containsKey(playerId)) {
                humanControllers.put(playerId, r);
                sendSync(playerId, r.getBroadcaster(), new CardInfo(game.getPrivateCards(playerId)));
                sendSync(playerId, r.getBroadcaster(), new Rejoin(playerId));
                broadcastUpdatePlayers(r.getBroadcaster());
                sendSync(r.getBroadcaster(), new ReconnectMessage(playerId));
            } else {
                if (game.getStatus() == GameStatus.START_ROUND) {
                    addHumanController(r);
                    broadcastUpdatePlayers(r.getBroadcaster());
                }
                r.write(JacksonEncoder.INSTANCE.encode(new Rejoin(r.uuid())));
            }
        }

        String playerId = humanControllers.inverse().get(r);
        if (playerId != null)
            handleGameMessage(playerId, r.getBroadcaster(), message);
    }

    private void handleGameMessage(String playerId, Broadcaster broadcaster, IncomingMessage message) {

        if (message instanceof SetNameRequest) {
            String name = ((SetNameRequest) message).getName();
            playerNames.put(playerId, name);
            broadcastUpdatePlayers(broadcaster);
        }

        if (message instanceof PlayerOrderRequest) {
            List<String> playerIds = ((PlayerOrderRequest) message).getPlayerIds();
            game.setPlayerOrder(playerIds);
            playerReadyForPlay.replaceAll((k, v) -> v=false);
            broadcastUpdatePlayers(broadcaster);
        }

        if (message instanceof PlayerScoreRequest) {
            String updatedPlayerId = ((PlayerScoreRequest) message).getPlayerId();
            boolean increment = ((PlayerScoreRequest) message).isIncrement();
            game.updatePlayerScore(updatedPlayerId, increment);
            playerReadyForPlay.replaceAll((k, v) -> v=false);
            broadcastUpdatePlayers(broadcaster);
        }

        if (message instanceof AddAiRequest) {
            if (aiControllers.size() >= 5)
                return;
            String aiPlayerId = UUID.randomUUID().toString();
            aiControllers.put(aiPlayerId, new AiClient(aiPlayerId));
            playerNames.put(aiPlayerId, Names.generateRandomName());
            game.addPlayer(aiPlayerId);
            broadcastUpdatePlayers(broadcaster);
        }

        if (message instanceof RemovePlayerRequest) {
            String removePlayerId = ((RemovePlayerRequest) message).getPlayerId();
            if (game.getStatus() == GameStatus.START_ROUND) {
                if (aiControllers.containsKey(removePlayerId)) {
                    aiControllers.remove(removePlayerId);
                    playerNames.remove(removePlayerId);
                    game.removePlayer(removePlayerId);
                } else if (game.getPlayerIds().contains(removePlayerId)) {
                    if (removePlayerId.equals(playerId) || !humanControllers.containsKey(removePlayerId)) {
                        playerNames.remove(removePlayerId);
                        playerReadyForPlay.remove(removePlayerId);
                        game.removePlayer(removePlayerId);
                    }
                }
                broadcastUpdatePlayers(broadcaster);
            }
        }

        if (message instanceof GameConfigurationRequest) {
            game.setNumDecks(((GameConfigurationRequest) message).getNumDecks());
            game.setFindAFriend(((GameConfigurationRequest) message).isFindAFriend());
            playerReadyForPlay.replaceAll((k, v) -> v=false);
            sendSync(broadcaster, new GameConfiguration(
                game.getNumDecks(),
                game.isFindAFriend(),
                game.getKittySize(),
                playerReadyForPlay));
        }

        if (message instanceof DeclareRequest) {
            List<Integer> cardIds = ((DeclareRequest) message).getCardIds();
            try {
                game.declare(playerId, cardIds);
                Map<Integer, Card> cardsById = game.getCardsById();
                sendSync(broadcaster, new CardInfo(Maps.toMap(cardIds, cardsById::get)));
                playerReadyForPlay.replaceAll((k, v) -> v=false);
                sendSync(broadcaster, new Declare(
                    game.getDeclarerPlayerIndex(),
                    game.getIsDeclaringTeam(),
                    game.getPlayerHands(),
                    game.getDeclaredCards(),
                    game.getCurrentTrump(),
                    playerReadyForPlay));
            } catch (InvalidDeclareException e) {
                sendSync(playerId, broadcaster, new InvalidAction(e.getMessage()));
            }
        }

        if (message instanceof ReadyForPlayRequest) {
            if (playerReadyForPlay.containsKey(playerId))
                playerReadyForPlay.put(playerId, ((ReadyForPlayRequest) message).isReady());
            if (!playerReadyForPlay.containsValue(false) || DEV_MODE) {
                if (game.getStatus() == GameStatus.START_ROUND)
                    startRound(broadcaster);
                else if (game.getStatus() == GameStatus.DRAW_KITTY)
                    dealKitty(broadcaster);
                else
                    throw new IllegalStateException();
                playerReadyForPlay.replaceAll((k, v) -> v=false); // reset for next time
            }
            sendSync(broadcaster, new ReadyForPlay(playerReadyForPlay));
        }

        if (message instanceof FindAFriendDeclarationRequest) {
            FindAFriendDeclaration declaration = ((FindAFriendDeclarationRequest) message).getDeclaration();
            try {
                game.makeFindAFriendDeclaration(playerId, declaration);
                sendSync(broadcaster, new FindAFriendDeclarationMessage(game.getStatus(), game.getFindAFriendDeclaration()));
            } catch (InvalidFindAFriendDeclarationException e) {
                sendSync(playerId, broadcaster, new InvalidAction(e.getMessage()));
            }
        }

        if (message instanceof MakeKittyRequest) {
            List<Integer> cardIds = ((MakeKittyRequest) message).getCardIds();
            try {
                game.makeKitty(playerId, cardIds);
                sendSync(broadcaster, new MakeKitty(
                    game.getStatus(),
                    game.getKitty(),
                    game.getPlayerHands(),
                    game.getCurrentTrick()));
            } catch (InvalidKittyException e) {
                sendSync(playerId, broadcaster, new InvalidAction(e.getMessage()));
            }
        }

        if (message instanceof PlayRequest) {
            List<Integer> cardIds = ((PlayRequest) message).getCardIds();
            boolean  confirmDoesItFly = ((PlayRequest) message).isConfirmDoesItFly();
            try {
                PlayResult result = game.play(playerId, cardIds, confirmDoesItFly);
                Map<Integer, Card> cardsById = game.getCardsById();
                sendSync(broadcaster, new CardInfo(Maps.toMap(cardIds, cardsById::get)));
                sendSync(broadcaster, new PlayMessage(
                    game.getCurrentPlayerIndex(),
                    game.getPlayerHands(),
                    game.getCurrentTrick()));
                if (result.isTrickComplete())
                    scheduleFinishTrick(broadcaster);
                if (result.isDidFriendJoin())
                    sendSync(broadcaster, new FriendJoined(playerId, game.getIsDeclaringTeam()));
            } catch (InvalidPlayException e) {
                sendSync(playerId, broadcaster, new InvalidAction(e.getMessage()));
            } catch (DoesNotFlyException e) {
                if (confirmDoesItFly)
                    forfeit(playerId, "made an incorrect does-it-fly declaration", broadcaster);
                else
                    sendSync(playerId, broadcaster, new ConfirmDoesItFly(cardIds));
            }
        }

        if (message instanceof TakeBackRequest) {
            game.takeBack(playerId);
            sendSync(broadcaster, new TakeBack(
                playerId,
                game.getCurrentPlayerIndex(),
                game.getPlayerHands(),
                game.getCurrentTrick()));
        }

        if (message instanceof ForfeitRequest) {
            forfeit(playerId, "forfeited", broadcaster);
        }
    }

    private void addHumanController(AtmosphereResource r) {
        String playerId = r.uuid();
        humanControllers.put(playerId, r);
        playerNames.put(playerId, Names.generateRandomName());
        playerReadyForPlay.put(playerId, false);
        game.addPlayer(playerId);
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
            game.getFindAFriendDeclaration(),
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
                    sendSync(draw.getPlayerId(), broadcaster, new CardInfo(Maps.toMap(draw.getCardIds(), cardsById::get)));
                    sendSync(broadcaster, new Draw(
                        game.getStatus(),
                        game.getCurrentPlayerIndex(),
                        game.getDeck(),
                        game.getPlayerHands()));
                    Uninterruptibles.sleepUninterruptibly((DEV_MODE ? 10 : 1000) / game.getPlayerIds().size(), TimeUnit.MILLISECONDS);
                }
            }
        };

        dealingThread.setDaemon(true);
        dealingThread.start();
    }

    private void dealKitty(Broadcaster broadcaster) {
        Play kitty = game.takeKitty();
        if (kitty == null)
            return;
        sendSync(kitty.getPlayerId(), broadcaster, new CardInfo(Maps.toMap(kitty.getCardIds(), game.getCardsById()::get)));
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
                    prepareStartNewRound(broadcaster);
            }
        };

        finishTrickThread.setDaemon(true);
        finishTrickThread.start();
    }

    private void forfeit(String playerId, String message, Broadcaster broadcaster) {
        game.forfeitRound(playerId);
        sendSync(broadcaster, new Forfeit(
            playerId,
            message,
            game.getRoundNumber(),
            game.getDeclarerPlayerIndex(),
            game.getPlayerRankScores(),
            game.getStatus()));
        prepareStartNewRound(broadcaster);
    }

    private void prepareStartNewRound(Broadcaster broadcaster) {
        // game end, send kitty card info to all players
        sendSync(broadcaster, new CardInfo(Maps.toMap(game.getKitty(), game.getCardsById()::get)));

        // add any current observers to the game
        Set<AtmosphereResource> observers = Sets.filter(resources, r -> !humanControllers.containsValue(r));
        if (!observers.isEmpty()) {
            for (AtmosphereResource observer : observers)
                addHumanController(observer);
            broadcastUpdatePlayers(broadcaster);
        }
    }

    private void broadcastUpdatePlayers(Broadcaster broadcaster) {
        sendSync(broadcaster, new UpdatePlayers(
            game.getPlayerIds(),
            game.getPlayerRankScores(),
            game.isFindAFriend(),
            game.getKittySize(),
            aiControllers.keySet(),
            humanControllers.keySet(),
            playerNames,
            playerReadyForPlay));
    }

    private void sendSync(String playerId, Broadcaster broadcaster, OutgoingMessage message) {
        if (humanControllers.containsKey(playerId))
            humanControllers.get(playerId).write(JacksonEncoder.INSTANCE.encode(message));
        else if (aiControllers.containsKey(playerId))
            aiControllers.get(playerId).processMessage(game, message, inputMessage -> handleGameMessage(playerId, broadcaster, inputMessage));
        else
            throw new IllegalStateException();
    }

    private void sendSync(Broadcaster broadcaster, OutgoingMessage message) {
        try {
            broadcaster.broadcast(JacksonEncoder.INSTANCE.encode(message)).get();
        } catch (ExecutionException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
        aiControllers.forEach((playerId, aiController) -> {
            aiController.processMessage(game, message, inputMessage -> handleGameMessage(playerId, broadcaster, inputMessage));
        });
    }
}
