package io.github.ytung.tractor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import io.github.ytung.tractor.api.Card;
import lombok.Data;

@Data
public class Game {

    private List<String> playerIds = new ArrayList<>();

    // game configuration
    private int numDecks = 2;
    private int kittySize = 8;

    // constant over each round
    private int roundNumber = 0;
    private int declarerPlayerIndex = 0;
    private Map<String, Card.Value> playerRankScores = new HashMap<>();
    private Set<String> winningPlayerIds = new HashSet<>();

    // round state
    private GameStatus status = GameStatus.START_ROUND;
    private int currentPlayerIndex;
    private Map<String, Boolean> isDeclaringTeam;
    private Queue<Card> deck;
    private Map<Integer, Card> cardsById;
    private Map<String, List<Card>> playerHands;
    private List<Play> declaredCards;
    private List<Card> kitty;
    private List<Trick> pastTricks;
    private Trick currentTrick;
    private List<Card> pointCards;
    private Set<String> roundWinners;

    enum GameStatus {
        START_ROUND, DRAW, DRAW_KITTY, MAKE_KITTY, PLAY;
    }

    public synchronized void addPlayer(String playerId) {
        if (status != GameStatus.START_ROUND)
            return;
        if (playerIds.contains(playerId))
            return;

        playerIds.add(playerId);
        playerRankScores.put(playerId, Card.Value.TWO);

        kittySize = numDecks * Decks.SIZE % playerIds.size();
        while (kittySize < 5)
            kittySize += playerIds.size();
    }

    public synchronized void removePlayer(String playerId) {
        if (!playerIds.contains(playerId))
            return;

        if (status != GameStatus.START_ROUND)
            forfeitRound(playerId);

        playerIds.remove(playerId);
        playerRankScores.remove(playerId);

        kittySize = numDecks * Decks.SIZE % playerIds.size();
        while (kittySize < 5)
            kittySize += playerIds.size();
    }

    public synchronized void startRound() {
        if (status != GameStatus.START_ROUND)
            return;

        status = GameStatus.DRAW;
        currentPlayerIndex = declarerPlayerIndex;
        isDeclaringTeam = IntStream.range(0, playerIds.size())
            .boxed()
            .collect(Collectors.toMap(i -> playerIds.get(i), i -> (i + declarerPlayerIndex) % 2 == 0));
        deck = Decks.generate(numDecks);
        cardsById = new HashMap<>();
        playerHands = new HashMap<>();
        declaredCards = new ArrayList<>();
        kitty = new ArrayList<>();
        pastTricks = new ArrayList<>();
        currentTrick = null;
        pointCards = new ArrayList<>();
        roundWinners = new HashSet<>();

        for (Card card : deck)
            cardsById.put(card.getId(), card);
        for (String playerId : playerIds)
            playerHands.put(playerId, new ArrayList<>());
    }

    /**
     * The next player draws a card from the deck.
     */
    public synchronized Play draw() {
        if (status != GameStatus.DRAW)
            return null;

        String playerId = playerIds.get(currentPlayerIndex);
        Card card = deck.poll();
        playerHands.get(playerId).add(card);
        currentPlayerIndex = (currentPlayerIndex + 1) % playerIds.size();
        if (deck.size() <= kittySize)
            status = GameStatus.DRAW_KITTY;
        return new Play(playerId, Collections.singletonList(card));
    }

    public synchronized boolean declare(String playerId, List<Integer> cardIds) {
        Play play = new Play(playerId, cardIds.stream()
            .map(cardsById::get)
            .collect(Collectors.toList()));
        if (!canDeclare(play))
            return false;
        declaredCards.add(play);
        return true;
    }

    private boolean canDeclare(Play play) {
        if (status != GameStatus.DRAW)
            return false;
        if (!play.isPlayable(playerHands))
            return false;
        if (play.getCards().stream().map(Card::getValue).distinct().count() != 1)
            return false;
        if (play.getCards().stream().map(Card::getSuit).distinct().count() != 1)
            return false;
        Card card = play.getCards().get(0);
        if (card.getValue() != getCurrentTrump().getValue() && card.getSuit() != Card.Suit.JOKER)
            return false;
        if (declaredCards.isEmpty())
            return true;
        Play lastDeclaredPlay = declaredCards.get(declaredCards.size() - 1);
        if (lastDeclaredPlay.getPlayerId() == play.getPlayerId()) {
            // same player is only allowed to strengthen the declared suit
            return card.getSuit() == lastDeclaredPlay.getSuit()
                    && play.numCards() > lastDeclaredPlay.numCards();
        } else {
            // other players can only override
            if (card.getSuit() == lastDeclaredPlay.getSuit())
                return false;
            if (play.numCards() > lastDeclaredPlay.numCards())
                return true;
            return card.getSuit() == Card.Suit.JOKER
                    && lastDeclaredPlay.getSuit() != Card.Suit.JOKER
                    && play.numCards() == lastDeclaredPlay.numCards();
        }
    }

    public synchronized Play takeKitty() {
        if (status != GameStatus.DRAW_KITTY)
            throw new IllegalStateException();

        // if this is the first round, then the person who declared the trump suit gets the kitty
        if (roundNumber == 0 && !declaredCards.isEmpty())
            declarerPlayerIndex = playerIds.indexOf(declaredCards.get(declaredCards.size() - 1).getPlayerId());

        status = GameStatus.MAKE_KITTY;
        currentPlayerIndex = declarerPlayerIndex;
        String playerId = playerIds.get(currentPlayerIndex);
        List<Card> cards = new ArrayList<>(deck);
        playerHands.get(playerIds.get(currentPlayerIndex)).addAll(cards);
        deck.clear();
        return new Play(playerId, cards);
    }

    public synchronized boolean makeKitty(String playerId, List<Integer> cardIds) {
        Play play = new Play(playerId, cardIds.stream()
            .map(cardsById::get)
            .collect(Collectors.toList()));
        if (status != GameStatus.MAKE_KITTY)
            return false;
        if (play.getPlayerId() != playerIds.get(currentPlayerIndex))
            return false;
        if (play.numCards() != kittySize)
            return false;
        if (!play.isPlayable(playerHands))
            return false;
        status = GameStatus.PLAY;
        kitty = play.getCards();
        currentTrick = new Trick(play.getPlayerId());
        return true;
    }

    /**
     * The specified player makes the given play. Returns whether the play was valid (and the game
     * should continue).
     */
    public synchronized boolean play(Play play) throws InvalidDoesItFlyException {
        if (!canPlay(play))
            return false;

        currentTrick.getPlays().add(play);
        if (currentTrick.getPlays().size() != playerIds.size()) {
            currentPlayerIndex = (currentPlayerIndex + 1) % playerIds.size();
        } else {
            // finish trick
            String winningPlayerId = currentTrick.winningPlayerId(getCurrentTrump());

            pastTricks.add(currentTrick);
            currentPlayerIndex = playerIds.indexOf(winningPlayerId);
            currentTrick = new Trick(winningPlayerId);

            // check for end of round
            if (playerHands.values().stream().allMatch(List::isEmpty)) {
                int roundScore = totalCardScore(pointCards);
                if (!isDeclaringTeam.get(winningPlayerId)) {
                    int bonus = 1 << pastTricks.get(pastTricks.size() - 1).getPlays().get(0).numCards();
                    roundScore += bonus * totalCardScore(kitty);
                }
                boolean doDeclarersWin = roundScore < 80;
                int scoreIncrease = doDeclarersWin ? (115 - roundScore) / 40 : (roundScore - 120) / 40;
                roundEnd(doDeclarersWin, scoreIncrease);
            }
        }
        return true;
    }

    private boolean canPlay(Play play) throws InvalidDoesItFlyException {
        if (status != GameStatus.PLAY)
            return false;
        if (play.getPlayerId() != playerIds.get(currentPlayerIndex))
            return false;
        if (!play.isPlayable(playerHands))
            return false;

        Card trump = getCurrentTrump();
        if (currentTrick.getPlays().isEmpty()) {
            // first play of trick
            List<Component> profile = play.getProfile(trump);
            if (profile.isEmpty())
                return false;
            if (profile.size() == 1)
                return true;

            // check to see if this is a does-it-fly play, and if so, whether it is valid
            for (Component component : profile)
                for (String otherPlayerId : playerIds)
                    if (otherPlayerId != play.getPlayerId()) {
                        List<Card> sameSuitCards = playerHands.get(otherPlayerId).stream()
                            .filter(card -> Cards.grouping(card, trump) == play.getGrouping(trump))
                            .collect(Collectors.toList());
                        for (Component otherComponent : new Play(otherPlayerId, sameSuitCards).getProfile(trump))
                            if (otherComponent.getShape().getWidth() >= component.getShape().getWidth()
                                    && otherComponent.getShape().getHeight() >= component.getShape().getHeight()
                                    && otherComponent.getMinRank() > component.getMinRank()) {
                                throw new InvalidDoesItFlyException();
                            }
                    }
            return true;
        } else {
            Play startingPlay = currentTrick.getPlays().get(0);
            if (play.numCards() != startingPlay.numCards())
                return false;

            List<Card> sameSuitCards = playerHands.get(play.getPlayerId()).stream()
                .filter(card -> Cards.grouping(card, trump) == startingPlay.getGrouping(trump))
                .collect(Collectors.toList());
            for (Component handComponent : new Play(play.getPlayerId(), sameSuitCards).getProfile(trump)) {
                boolean isCapturedByStartingPlay = startingPlay.getProfile(trump).stream().anyMatch(
                    component -> component.getShape().getWidth() >= handComponent.getShape().getWidth()
                            && component.getShape().getHeight() >= handComponent.getShape().getHeight());
                boolean isBetterThanCurrentPlay = play.getProfile(trump).stream().anyMatch(
                    component -> component.getShape().getWidth() < handComponent.getShape().getWidth()
                            && component.getShape().getHeight() < handComponent.getShape().getHeight());
                // player must follow suit; if there's a card in hand better than what's played, the play is invalid
                if (isCapturedByStartingPlay && isBetterThanCurrentPlay && !play.getProfile(trump).contains(handComponent))
                    return false;
            }
        }

        return true;
    }

    public synchronized void forfeitRound(String playerId) {
        boolean doDeclarersWin = isDeclaringTeam.get(playerId);
        roundEnd(doDeclarersWin, doDeclarersWin ? 1 : 0);
    }

    private void roundEnd(boolean doDeclarersWin, int scoreIncrease) {
        roundNumber++;
        do {
            // declarer goes to next person on the winning team
            declarerPlayerIndex = (declarerPlayerIndex + 1) % playerIds.size();
        } while (isDeclaringTeam.get(playerIds.get(declarerPlayerIndex)) != doDeclarersWin);
        for (String pid : playerIds)
            if (isDeclaringTeam.get(pid) == doDeclarersWin) {
                roundWinners.add(pid);
                int newScore = playerRankScores.get(pid).ordinal() + scoreIncrease;
                if (newScore > Card.Value.values().length)
                    playerRankScores.put(pid, Card.Value.BIG_JOKER);
                else
                    playerRankScores.put(pid, Card.Value.values()[newScore]);
                if (newScore > Card.Value.ACE.ordinal())
                    winningPlayerIds.add(pid);
            }
        status = GameStatus.START_ROUND;
    }

    private Card getCurrentTrump() {
        return new Card(
            0,
            playerRankScores.get(playerIds.get(declarerPlayerIndex)),
            declaredCards.isEmpty() ? Card.Suit.JOKER : declaredCards.get(declaredCards.size() - 1).getCards().get(0).getSuit());
    }

    private static int totalCardScore(List<Card> cards) {
        int score = 0;
        for (Card card : cards)
            if (card.getValue() == Card.Value.FIVE)
                score += 5;
            else if (card.getValue() == Card.Value.TEN || card.getValue() == Card.Value.KING)
                score += 10;
        return score;
    }
}
