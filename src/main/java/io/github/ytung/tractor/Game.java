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

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;

import io.github.ytung.tractor.Cards.Grouping;
import io.github.ytung.tractor.api.Card;
import io.github.ytung.tractor.api.Card.Suit;
import io.github.ytung.tractor.api.GameStatus;
import io.github.ytung.tractor.api.Play;
import io.github.ytung.tractor.api.Trick;
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
    private Queue<Integer> deck;
    private Map<Integer, Card> cardsById;
    private Map<String, List<Integer>> playerHands;
    private List<Play> declaredCards;
    private List<Integer> kitty;
    private List<Trick> pastTricks;
    private Trick currentTrick;

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
            throw new IllegalStateException();

        status = GameStatus.DRAW;
        currentPlayerIndex = declarerPlayerIndex;
        isDeclaringTeam = IntStream.range(0, playerIds.size())
            .boxed()
            .collect(Collectors.toMap(i -> playerIds.get(i), i -> (i + declarerPlayerIndex) % 2 == 0));
        cardsById = Decks.getCardsById(numDecks);
        deck = Decks.shuffle(cardsById);
        playerHands = new HashMap<>();
        declaredCards = new ArrayList<>();
        kitty = new ArrayList<>();
        pastTricks = new ArrayList<>();
        currentTrick = null;

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
        int cardId = deck.poll();
        playerHands.get(playerId).add(cardId);
        currentPlayerIndex = (currentPlayerIndex + 1) % playerIds.size();
        if (deck.size() <= kittySize)
            status = GameStatus.DRAW_KITTY;
        return new Play(playerId, Collections.singletonList(cardId));
    }

    public synchronized void declare(String playerId, List<Integer> cardIds) {
        Play play = new Play(playerId, cardIds);
        if (!canDeclare(play))
            throw new IllegalStateException();
        declaredCards.add(play);
    }

    private boolean canDeclare(Play play) {
        if (status != GameStatus.DRAW)
            return false;
        if (!isPlayable(play))
            return false;
        if (play.getCardIds().stream().map(cardId -> cardsById.get(cardId).getValue()).distinct().count() != 1)
            return false;
        if (play.getCardIds().stream().map(cardId -> cardsById.get(cardId).getSuit()).distinct().count() != 1)
            return false;
        Card card = cardsById.get(play.getCardIds().get(0));
        if (card.getValue() != getCurrentTrump().getValue() && card.getSuit() != Card.Suit.JOKER)
            return false;
        if (declaredCards.isEmpty())
            return true;
        Play lastDeclaredPlay = declaredCards.get(declaredCards.size() - 1);
        Suit lastDeclaredSuit = cardsById.get(lastDeclaredPlay.getCardIds().get(0)).getSuit();
        if (lastDeclaredPlay.getPlayerId() == play.getPlayerId()) {
            // same player is only allowed to strengthen the declared suit
            return card.getSuit() == lastDeclaredSuit
                    && play.getCardIds().size() > lastDeclaredPlay.getCardIds().size();
        } else {
            // other players can only override
            if (card.getSuit() == lastDeclaredSuit)
                return false;
            if (play.getCardIds().size() > lastDeclaredPlay.getCardIds().size())
                return true;
            return card.getSuit() == Card.Suit.JOKER
                    && lastDeclaredSuit != Card.Suit.JOKER
                    && play.getCardIds().size() == lastDeclaredPlay.getCardIds().size();
        }
    }

    public synchronized Play takeKitty() {
        if (status != GameStatus.DRAW_KITTY)
            return null;

        // if this is the first round, then the person who declared the trump suit gets the kitty
        if (roundNumber == 0 && !declaredCards.isEmpty())
            declarerPlayerIndex = playerIds.indexOf(declaredCards.get(declaredCards.size() - 1).getPlayerId());

        status = GameStatus.MAKE_KITTY;
        currentPlayerIndex = declarerPlayerIndex;
        String playerId = playerIds.get(currentPlayerIndex);
        List<Integer> cardIds = new ArrayList<>(deck);
        playerHands.get(playerIds.get(currentPlayerIndex)).addAll(cardIds);
        deck.clear();
        return new Play(playerId, cardIds);
    }

    public synchronized boolean makeKitty(String playerId, List<Integer> cardIds) {
        Play play = new Play(playerId, cardIds);
        if (status != GameStatus.MAKE_KITTY)
            return false;
        if (play.getPlayerId() != playerIds.get(currentPlayerIndex))
            return false;
        if (play.getCardIds().size() != kittySize)
            return false;
        if (!isPlayable(play))
            return false;
        status = GameStatus.PLAY;
        kitty = play.getCardIds();
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
            String winningPlayerId = winningPlayerId(currentTrick);

            pastTricks.add(currentTrick);
            currentPlayerIndex = playerIds.indexOf(winningPlayerId);
            currentTrick = new Trick(winningPlayerId);

            // check for end of round
            if (playerHands.values().stream().allMatch(List::isEmpty)) {
                int roundScore = 0;
                for (Trick trick : pastTricks)
                    if (!isDeclaringTeam.get(winningPlayerId(trick)))
                        for (Play trickPlay : trick.getPlays())
                            roundScore += totalCardScore(trickPlay.getCardIds());
                if (!isDeclaringTeam.get(winningPlayerId)) {
                    int bonus = 1 << pastTricks.get(pastTricks.size() - 1).getPlays().get(0).getCardIds().size();
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
        if (!isPlayable(play))
            return false;

        Card trump = getCurrentTrump();
        if (currentTrick.getPlays().isEmpty()) {
            // first play of trick
            List<Component> profile = getProfile(play.getCardIds());
            if (profile.isEmpty())
                return false;
            if (profile.size() == 1)
                return true;

            // check to see if this is a does-it-fly play, and if so, whether it is valid
            for (Component component : profile)
                for (String otherPlayerId : playerIds)
                    if (otherPlayerId != play.getPlayerId()) {
                        List<Integer> sameSuitCardIds = playerHands.get(otherPlayerId).stream()
                            .filter(cardId -> Cards.grouping(cardsById.get(cardId), trump) == getGrouping(play.getCardIds()))
                            .collect(Collectors.toList());
                        for (Component otherComponent : getProfile(sameSuitCardIds))
                            if (otherComponent.getShape().getWidth() >= component.getShape().getWidth()
                                    && otherComponent.getShape().getHeight() >= component.getShape().getHeight()
                                    && otherComponent.getMinRank() > component.getMinRank()) {
                                throw new InvalidDoesItFlyException();
                            }
                    }
            return true;
        } else {
            Play startingPlay = currentTrick.getPlays().get(0);
            if (play.getCardIds().size() != startingPlay.getCardIds().size())
                return false;

            List<Integer> sameSuitCards = playerHands.get(play.getPlayerId()).stream()
                .filter(cardId -> Cards.grouping(cardsById.get(cardId), trump) == getGrouping(startingPlay.getCardIds()))
                .collect(Collectors.toList());
            for (Component handComponent : getProfile(sameSuitCards)) {
                boolean isCapturedByStartingPlay = getProfile(startingPlay.getCardIds()).stream().anyMatch(
                    component -> component.getShape().getWidth() >= handComponent.getShape().getWidth()
                            && component.getShape().getHeight() >= handComponent.getShape().getHeight());
                boolean isBetterThanCurrentPlay = getProfile(play.getCardIds()).stream().anyMatch(
                    component -> component.getShape().getWidth() < handComponent.getShape().getWidth()
                            && component.getShape().getHeight() < handComponent.getShape().getHeight());
                // player must follow suit; if there's a card in hand better than what's played, the play is invalid
                if (isCapturedByStartingPlay && isBetterThanCurrentPlay && !getProfile(play.getCardIds()).contains(handComponent))
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
        for (String playerId : playerIds)
            if (isDeclaringTeam.get(playerId) == doDeclarersWin) {
                int newScore = playerRankScores.get(playerId).ordinal() + scoreIncrease;
                if (newScore > Card.Value.values().length)
                    playerRankScores.put(playerId, Card.Value.BIG_JOKER);
                else
                    playerRankScores.put(playerId, Card.Value.values()[newScore]);
                if (newScore > Card.Value.ACE.ordinal())
                    winningPlayerIds.add(playerId);
            }
        status = GameStatus.START_ROUND;
    }

    private Card getCurrentTrump() {
        return new Card(
            0,
            playerRankScores.get(playerIds.get(declarerPlayerIndex)),
            declaredCards.isEmpty()
                    ? Card.Suit.JOKER
                    : cardsById.get(declaredCards.get(declaredCards.size() - 1).getCardIds().get(0)).getSuit());
    }

    private int totalCardScore(List<Integer> cardIds) {
        int score = 0;
        for (int cardId : cardIds) {
            Card card = cardsById.get(cardId);
            if (card.getValue() == Card.Value.FIVE)
                score += 5;
            else if (card.getValue() == Card.Value.TEN || card.getValue() == Card.Value.KING)
                score += 10;
        }
        return score;
    }

    private boolean isPlayable(Play play) {
        List<Integer> hand = new ArrayList<>(playerHands.get(play.getPlayerId()));
        for (Integer card : play.getCardIds())
            if (!hand.remove(card))
                return false;
        return true;
    }

    private Grouping getGrouping(List<Integer> cardIds) {
        Set<Grouping> groupings = cardIds.stream()
            .map(cardsById::get)
            .map(card -> Cards.grouping(card, getCurrentTrump()))
            .collect(Collectors.toSet());
        return groupings.size() == 1 ? Iterables.getOnlyElement(groupings) : null;
    }

    private List<Component> getProfile(List<Integer> cardIds) {
        if (getGrouping(cardIds) == null)
            return new ArrayList<>();

        Card trump = getCurrentTrump();
        List<Card> cards = cardIds.stream().map(cardsById::get).collect(Collectors.toList());
        List<Component> profile = cards.stream()
            .distinct()
            .map(card -> {
                int width = 0;
                for (Card otherCard : cards)
                    if (card.getValue() == otherCard.getValue() && card.getSuit() == otherCard.getSuit())
                        width++;
                return new Component(
                    new Shape(width, 1),
                    Cards.rank(card, trump),
                    Cards.rank(card, trump));
            })
            .collect(Collectors.toList());

        while (combineConsecutiveComponents(profile));

        return profile;
    }

    private static boolean combineConsecutiveComponents(List<Component> profile) {
        for (int i = 0; i < profile.size(); i++)
            for (int j = 0; j < profile.size(); j++) {
                Component component1 = profile.get(i);
                Component component2 = profile.get(j);
                if (component1.shape.width == component2.shape.width
                        && component1.shape.width >= 2
                        && component1.minRank - component2.maxRank == 1) {
                    profile.set(i, new Component(
                        new Shape(component1.shape.width, component1.shape.height + component2.shape.height),
                        component2.minRank,
                        component1.maxRank));
                    profile.remove(j);
                    return true;
                }
            }
        return false;
    }

    private String winningPlayerId(Trick trick) {
        String winningPlayerId = trick.getStartPlayerId();
        List<Play> plays = trick.getPlays();
        List<Component> bestProfile = getProfile(plays.get(0).getCardIds());
        Grouping bestGrouping = getGrouping(plays.get(0).getCardIds());
        for (int i = 1; i < plays.size(); i++) {
            Play play = plays.get(i);
            List<Component> profile = getProfile(play.getCardIds());
            Grouping grouping = getGrouping(play.getCardIds());
            if (getShapes(profile).equals(getShapes(bestProfile))) {
                if (grouping == Grouping.TRUMP && bestGrouping != Grouping.TRUMP
                        || grouping == bestGrouping && profile.get(0).getMaxRank() > bestProfile.get(0).getMaxRank()) {
                    winningPlayerId = play.getPlayerId();
                    bestProfile = profile;
                    bestGrouping = grouping;
                }
            }
        }
        return winningPlayerId;
    }

    private static Multiset<Shape> getShapes(List<Component> profile) {
        return HashMultiset.create(profile.stream().map(Component::getShape).collect(Collectors.toList()));
    }
}
