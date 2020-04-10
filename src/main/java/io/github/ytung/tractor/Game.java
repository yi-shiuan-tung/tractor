package io.github.ytung.tractor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
import com.google.common.collect.Maps;
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
    private Map<String, Integer> currentRoundScores = new HashMap<>();

    public synchronized void addPlayer(String playerId) {
        if (status != GameStatus.START_ROUND)
            return;
        if (playerIds.contains(playerId))
            return;

        playerIds.add(playerId);
        playerRankScores.put(playerId, Card.Value.TWO);
    }

    public synchronized void removePlayer(String playerId) {
        if (!playerIds.contains(playerId))
            return;

        playerIds.remove(playerId);
        playerRankScores.remove(playerId);
    }

    public synchronized void setPlayerOrder(List<String> newPlayerIds) {
        if (!new HashSet<>(playerIds).equals(new HashSet<>(newPlayerIds)))
            throw new IllegalStateException();

        String currentPlayerId = playerIds.get(currentPlayerIndex);
        String declarerPlayerId = playerIds.get(declarerPlayerIndex);
        playerIds = newPlayerIds;
        currentPlayerIndex = playerIds.indexOf(currentPlayerId);
        declarerPlayerIndex = playerIds.indexOf(declarerPlayerId);
    }

    public synchronized void setNumDecks(int numDecks) {
        if (status != GameStatus.START_ROUND)
            throw new IllegalStateException();
        if (numDecks <= 0 || numDecks > 10)
            throw new IllegalStateException();

        this.numDecks = numDecks;
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
        currentRoundScores = new HashMap<>(Maps.toMap(playerIds, playerId -> 0));

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
        sortCards(playerHands.get(playerId));
        currentPlayerIndex = (currentPlayerIndex + 1) % playerIds.size();
        if (deck.size() <= getKittySize())
            status = GameStatus.DRAW_KITTY;
        return new Play(playerId, Collections.singletonList(cardId));
    }

    public synchronized void declare(String playerId, List<Integer> cardIds) throws InvalidDeclareException {
        Play play = new Play(playerId, cardIds);
        verifyCanDeclare(play);
        declaredCards.add(play);
        playerHands.forEach((otherPlayerId, otherCardIds) -> sortCards(otherCardIds));

        // if this is the first round, then the person who declares is the declarer
        if (roundNumber == 0)
            declarerPlayerIndex = playerIds.indexOf(declaredCards.get(declaredCards.size() - 1).getPlayerId());
    }

    private void verifyCanDeclare(Play play) throws InvalidDeclareException {
        if (status != GameStatus.DRAW && status != GameStatus.DRAW_KITTY)
            throw new InvalidDeclareException("You can no longer declare.");
        if (play.getCardIds().isEmpty())
            throw new InvalidDeclareException("You must declare at least one card.");
        if (!isPlayable(play))
            throw new InvalidDeclareException("You do not have that card.");
        if (play.getCardIds().stream().map(cardId -> cardsById.get(cardId).getValue()).distinct().count() != 1)
            throw new InvalidDeclareException("All declared cards must be the same.");
        if (play.getCardIds().stream().map(cardId -> cardsById.get(cardId).getSuit()).distinct().count() != 1)
            throw new InvalidDeclareException("All declared cards must be the same.");
        Card card = cardsById.get(play.getCardIds().get(0));
        if (card.getValue() != getCurrentTrump().getValue() && card.getSuit() != Card.Suit.JOKER)
            throw new InvalidDeclareException("You can only declare the current trump value.");

        if (declaredCards.isEmpty())
            return;

        Play lastDeclaredPlay = declaredCards.get(declaredCards.size() - 1);
        Suit lastDeclaredSuit = cardsById.get(lastDeclaredPlay.getCardIds().get(0)).getSuit();
        if (lastDeclaredPlay.getPlayerId().equals(play.getPlayerId())) {
            // same player is only allowed to strengthen the declared suit
            if (card.getSuit() != lastDeclaredSuit)
                throw new InvalidDeclareException("You can only strengthen your declare.");
            if (play.getCardIds().size() <= lastDeclaredPlay.getCardIds().size())
                throw new InvalidDeclareException("You can only strengthen your declare.");
        } else {
            // other players can only override
            if (card.getSuit() == lastDeclaredSuit)
                throw new InvalidDeclareException("You may not re-declare the current suit.");
            if (play.getCardIds().size() < lastDeclaredPlay.getCardIds().size())
                throw new InvalidDeclareException("You must declare more cards than the last declare.");
            if (play.getCardIds().size() > lastDeclaredPlay.getCardIds().size())
                return;
            if (play.getCardIds().size() == 1 || card.getSuit() != Card.Suit.JOKER || lastDeclaredSuit == Card.Suit.JOKER)
                throw new InvalidDeclareException("You must declare more cards than the last declare.");
        }
    }

    public synchronized Play takeKitty() {
        if (status != GameStatus.DRAW_KITTY)
            return null;

        status = GameStatus.MAKE_KITTY;
        currentPlayerIndex = declarerPlayerIndex;
        String playerId = playerIds.get(currentPlayerIndex);
        List<Integer> cardIds = new ArrayList<>(deck);
        playerHands.get(playerIds.get(currentPlayerIndex)).addAll(cardIds);
        sortCards(playerHands.get(playerIds.get(currentPlayerIndex)));
        deck.clear();
        return new Play(playerId, cardIds);
    }

    public synchronized void makeKitty(String playerId, List<Integer> cardIds) throws InvalidKittyException {
        Play play = new Play(playerId, cardIds);
        if (status != GameStatus.MAKE_KITTY)
            throw new InvalidKittyException("You cannot make kitty now");
        if (!play.getPlayerId().equals(playerIds.get(currentPlayerIndex)))
            throw new InvalidKittyException("You cannot make kitty");
        if (play.getCardIds().size() != getKittySize())
            throw new InvalidKittyException("The kitty has to have " + getKittySize() + " cards");
        if (!isPlayable(play))
            throw new InvalidKittyException("Unknown error");
        status = GameStatus.PLAY;
        kitty = play.getCardIds();
        playerHands.get(playerId).removeAll(cardIds);
        currentTrick = new Trick(play.getPlayerId());
    }

    /**
     * The specified player makes the given play. Returns whether the current trick is finished.
     */
    public synchronized boolean play(String playerId, List<Integer> cardIds) throws InvalidPlayException {
        sortCards(cardIds);
        Play play = new Play(playerId, cardIds);
        verifyCanPlay(play);

        playerHands.get(playerId).removeAll(cardIds);
        currentTrick.getPlays().add(play);

        if (currentTrick.getPlays().size() == playerIds.size()) {
            currentPlayerIndex = -1;
            return true;
        } else {
            currentPlayerIndex = (currentPlayerIndex + 1) % playerIds.size();
            return false;
        }
    }

    public synchronized void finishTrick() {
        if (currentTrick.getPlays().size() != playerIds.size())
            throw new IllegalStateException();

        // finish trick
        String winningPlayerId = winningPlayerId(currentTrick);
        for (Play play : currentTrick.getPlays())
            currentRoundScores.put(winningPlayerId, currentRoundScores.get(winningPlayerId) + totalCardScore(play.getCardIds()));

        pastTricks.add(currentTrick);
        currentPlayerIndex = playerIds.indexOf(winningPlayerId);
        currentTrick = new Trick(winningPlayerId);

        // check for end of round
        if (playerHands.values().stream().allMatch(List::isEmpty)) {
            if (!isDeclaringTeam.get(winningPlayerId)) {
                int bonus = 1 << pastTricks.get(pastTricks.size() - 1).getPlays().get(0).getCardIds().size();
                currentRoundScores.put(winningPlayerId, currentRoundScores.get(winningPlayerId) + bonus * totalCardScore(kitty));
            }
            int roundScore = 0;
            for (String playerId : playerIds)
                if (!isDeclaringTeam.get(playerId))
                    roundScore += currentRoundScores.get(playerId);
            boolean doDeclarersWin = roundScore < 80;
            int scoreIncrease = doDeclarersWin ? (115 - roundScore) / 40 : (roundScore - 120) / 40;
            roundEnd(doDeclarersWin, scoreIncrease);
        }
    }

    private void verifyCanPlay(Play play) throws InvalidPlayException {
        if (status != GameStatus.PLAY)
            throw new InvalidPlayException("You cannot make a play now.");
        if (!play.getPlayerId().equals(playerIds.get(currentPlayerIndex)))
            throw new InvalidPlayException("It is not your turn.");
        if (play.getCardIds().isEmpty())
            throw new InvalidPlayException("You must play at least one card.");
        if (!isPlayable(play))
            throw new InvalidPlayException("You do not have that card.");

        Card trump = getCurrentTrump();
        if (currentTrick.getPlays().isEmpty()) {
            // first play of trick
            List<Component> profile = getProfile(play.getCardIds());
            if (profile.isEmpty())
                throw new InvalidPlayException("You must play cards in only one suit.");
            if (profile.size() == 1)
                return;

            // check to see if this is a does-it-fly play, and if so, whether it is valid
            for (Component component : profile)
                for (String otherPlayerId : playerIds)
                    if (!otherPlayerId.equals(play.getPlayerId())) {
                        List<Integer> sameSuitCardIds = playerHands.get(otherPlayerId).stream()
                            .filter(cardId -> Cards.grouping(cardsById.get(cardId), trump) == getGrouping(play.getCardIds()))
                            .collect(Collectors.toList());
                        for (Component otherComponent : getProfile(sameSuitCardIds))
                            if (otherComponent.getShape().getWidth() >= component.getShape().getWidth()
                                    && otherComponent.getShape().getHeight() >= component.getShape().getHeight()
                                    && otherComponent.getMinRank() > component.getMinRank()) {
                                throw new InvalidPlayException("That play does not fly.");
                            }
                    }
        } else {
            Play startingPlay = currentTrick.getPlays().get(0);
            if (play.getCardIds().size() != startingPlay.getCardIds().size())
                throw new InvalidPlayException("You must play the same number of cards.");

            Grouping startingGrouping = getGrouping(startingPlay.getCardIds());
            List<Integer> sameSuitCards = playerHands.get(play.getPlayerId()).stream()
                .filter(cardId -> Cards.grouping(cardsById.get(cardId), trump) == startingGrouping)
                .collect(Collectors.toList());

            if (!sameSuitCards.isEmpty()
                    && sameSuitCards.stream().anyMatch(cardId -> !play.getCardIds().contains(cardId))
                    && play.getCardIds().stream().anyMatch(cardId -> Cards.grouping(cardsById.get(cardId), trump) != startingGrouping)) {
                throw new InvalidPlayException("You must follow suit.");
            }

            for (Component handComponent : getProfile(sameSuitCards)) {
                Shape handShape = handComponent.getShape();
                boolean isCapturedByStartingPlay = getProfile(startingPlay.getCardIds()).stream()
                    .map(Component::getShape)
                    .anyMatch(shape -> shape.getWidth() >= handShape.getWidth() && shape.getHeight() >= handShape.getHeight());
                boolean isBetterThanCurrentPlay = getProfile(play.getCardIds()).stream()
                    .map(Component::getShape)
                    .anyMatch(shape -> shape.getWidth() <= handShape.getWidth()
                            && shape.getHeight() <= handShape.getHeight()
                            && shape.getWidth() + shape.getHeight() < handShape.getWidth() + handShape.getHeight());
                if (isCapturedByStartingPlay && isBetterThanCurrentPlay && !getProfile(play.getCardIds()).contains(handComponent))
                    throw new InvalidPlayException("You must play pairs before singles, etc.");
            }
        }
    }

    public synchronized void forfeitRound(String playerId) {
        boolean doDeclarersWin = !isDeclaringTeam.get(playerId);
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

    public Card getCurrentTrump() {
        return new Card(
            playerRankScores.get(playerIds.get(declarerPlayerIndex)),
            declaredCards.isEmpty()
                    ? Card.Suit.JOKER
                    : cardsById.get(declaredCards.get(declaredCards.size() - 1).getCardIds().get(0)).getSuit());
    }

    public int getKittySize() {
        int kittySize = numDecks * Decks.SIZE % playerIds.size();
        while (kittySize < 5)
            kittySize += playerIds.size();
        return kittySize;
    }

    private void sortCards(List<Integer> hand) {
        Card trump = getCurrentTrump();
        Collections.sort(hand, Comparator.comparing(cardId -> {
            Card card = cardsById.get(cardId);
            Grouping grouping = Cards.grouping(card, trump);
            return grouping.ordinal() * 1000 + Cards.rank(card, trump) * 10 + card.getSuit().ordinal();
        }));
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
        List<Card> cards = cardIds.stream()
                .map(cardsById::get)
                .collect(Collectors.toList());
        List<Component> profile = cards.stream()
            .distinct()
            .map(card -> {
                return new Component(
                    new Shape(Collections.frequency(cards, card), 1),
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
