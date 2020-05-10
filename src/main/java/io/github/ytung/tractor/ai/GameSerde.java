
package io.github.ytung.tractor.ai;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import io.github.ytung.tractor.Game;
import io.github.ytung.tractor.api.Card;
import io.github.ytung.tractor.api.FindAFriendDeclaration;
import io.github.ytung.tractor.api.GameStatus;
import io.github.ytung.tractor.api.Play;
import io.github.ytung.tractor.api.Trick;
import lombok.Data;
import lombok.NoArgsConstructor;

public class GameSerde {

    private static int counter = 1;

    public static void writeGame(Game game) {
        GameState state = new GameState();
        state.setPlayerIds(game.getPlayerIds());
        state.setNumDecks(game.getNumDecks());
        state.setFindAFriend(game.isFindAFriend());
        state.setRoundNumber(game.getRoundNumber());
        state.setStarterPlayerIndex(game.getStarterPlayerIndex());
        state.setPlayerRankScores(game.getPlayerRankScores());
        state.setDoDeclarersWin(game.isDoDeclarersWin());
        state.setWinningPlayerIds(game.getWinningPlayerIds());
        state.setStatus(game.getStatus());
        state.setCurrentPlayerIndex(game.getCurrentPlayerIndex());
        state.setIsDeclaringTeam(game.getIsDeclaringTeam());
        state.setDeck(game.getDeck());
        state.setCardsById(Maps.transformValues(game.getCardsById(), CardState::fromCard));
        state.setPlayerHands(game.getPlayerHands());
        state.setDeclaredCards(Lists.transform(game.getDeclaredCards(), PlayState::fromPlay));
        state.setExposedBottomCards(game.getExposedBottomCards());
        state.setKitty(game.getKitty());
        state.setFindAFriendDeclaration(game.getFindAFriendDeclaration());
        state.setPastTricks(Lists.transform(game.getPastTricks(), TrickState::fromTrick));
        state.setCurrentTrick(TrickState.fromTrick(game.getCurrentTrick()));
        state.setCurrentRoundScores(game.getCurrentRoundScores());
        state.setCurrentRoundPenalties(game.getCurrentRoundPenalties());
        try {
            new ObjectMapper().writeValue(new File("data/turn-" + counter++), state);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Game readGame(int turn) {
        try {
            GameState state = new ObjectMapper().readValue(new File("data/turn-" + turn), GameState.class);
            Game game = new Game();
            game.setPlayerIds(state.playerIds);
            game.setNumDecks(state.numDecks);
            game.setFindAFriend(state.findAFriend);
            game.setRoundNumber(state.roundNumber);
            game.setStarterPlayerIndex(state.starterPlayerIndex);
            game.setPlayerRankScores(state.playerRankScores);
            game.setDoDeclarersWin(state.doDeclarersWin);
            game.setWinningPlayerIds(state.winningPlayerIds);
            game.setStatus(state.status);
            game.setCurrentPlayerIndex(state.currentPlayerIndex);
            game.setIsDeclaringTeam(state.isDeclaringTeam);
            game.setDeck(state.deck);
            game.setCardsById(Maps.transformValues(state.cardsById, CardState::toCard));
            game.setPlayerHands(state.playerHands);
            game.setDeclaredCards(Lists.transform(state.declaredCards, PlayState::toPlay));
            game.setExposedBottomCards(state.exposedBottomCards);
            game.setKitty(state.kitty);
            game.setFindAFriendDeclaration(state.findAFriendDeclaration);
            game.setPastTricks(Lists.transform(state.pastTricks, TrickState::toTrick));
            game.setCurrentTrick(state.currentTrick.toTrick());
            game.setCurrentRoundScores(state.currentRoundScores);
            game.setCurrentRoundPenalties(state.currentRoundPenalties);
            return game;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Data
    @NoArgsConstructor
    private static final class GameState {
        private List<String> playerIds;
        private int numDecks;
        private boolean findAFriend;
        private int roundNumber;
        private int starterPlayerIndex;
        private Map<String, Card.Value> playerRankScores;
        private boolean doDeclarersWin;
        private Set<String> winningPlayerIds;
        private GameStatus status;
        private int currentPlayerIndex;
        private Map<String, Boolean> isDeclaringTeam;
        private Queue<Integer> deck;
        private Map<Integer, CardState> cardsById;
        private Map<String, List<Integer>> playerHands;
        private List<PlayState> declaredCards;
        private List<Integer> exposedBottomCards;
        private List<Integer> kitty;
        private FindAFriendDeclaration findAFriendDeclaration;
        private List<TrickState> pastTricks;
        private TrickState currentTrick;
        private Map<String, Integer> currentRoundScores;
        private Map<String, Integer> currentRoundPenalties;
    }

    @Data
    @NoArgsConstructor
    private static final class CardState {
        private Card.Value value;
        private Card.Suit suit;

        Card toCard() {
            return new Card(value, suit);
        }

        static CardState fromCard(Card card) {
            CardState state = new CardState();
            state.value = card.getValue();
            state.suit = card.getSuit();
            return state;
        }
    }

    @Data
    @NoArgsConstructor
    private static final class PlayState {
        private String playerId;
        private List<Integer> cardIds;

        Play toPlay() {
            return new Play(playerId, cardIds);
        }

        static PlayState fromPlay(Play play) {
            PlayState state = new PlayState();
            state.playerId = play.getPlayerId();
            state.cardIds = play.getCardIds();
            return state;
        }
    }

    @Data
    @NoArgsConstructor
    private static final class TrickState {
        private String startPlayerId;
        private List<PlayState> plays;
        private String winningPlayerId;

        Trick toTrick() {
            Trick trick = new Trick(startPlayerId);
            trick.getPlays().addAll(Lists.transform(plays, PlayState::toPlay));
            trick.setWinningPlayerId(winningPlayerId);
            return trick;
        }

        static TrickState fromTrick(Trick trick) {
            TrickState state = new TrickState();
            state.setStartPlayerId(trick.getStartPlayerId());
            state.setPlays(Lists.transform(trick.getPlays(), PlayState::fromPlay));
            state.setWinningPlayerId(trick.getWinningPlayerId());
            return state;
        }
    }

    public static void main(String[] args) {
        Game game = GameSerde.readGame(1);
        Map<Integer, Card> cardsById = game.getCardsById();

        Collection<Integer> cards = new BayesianAiClientV2().play(game.getPlayerIds().get(game.getCurrentPlayerIndex()), game);

        System.out.println();
        System.out.println(cards.stream().map(cardsById::get).collect(Collectors.toList()));
    }
}
