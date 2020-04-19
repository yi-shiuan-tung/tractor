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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.common.collect.Streams;

import io.github.ytung.tractor.Cards.Grouping;
import io.github.ytung.tractor.api.Card;
import io.github.ytung.tractor.api.Card.Suit;
import io.github.ytung.tractor.api.FindAFriendDeclaration;
import io.github.ytung.tractor.api.FindAFriendDeclaration.Declaration;
import io.github.ytung.tractor.api.GameStatus;
import io.github.ytung.tractor.api.Play;
import io.github.ytung.tractor.api.Trick;
import lombok.Data;

@Data
public class Game {

    private List<String> playerIds = new ArrayList<>();

    // game configuration
    private int numDecks = 2;
    private boolean findAFriend = false;

    // constant over each round
    private int roundNumber = 0;
    private int starterPlayerIndex = 0;
    private Map<String, Card.Value> playerRankScores = new HashMap<>();
    private boolean doDeclarersWin;
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
    private FindAFriendDeclaration findAFriendDeclaration;
    private List<Trick> pastTricks;
    private Trick currentTrick;
    private Map<String, Integer> currentRoundScores = new HashMap<>();

    // internal state for convenience
    private Multimap<Card, Integer> findAFriendDeclarationCounters;

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

        if (playerIds.size() < 4)
            findAFriend = false;
    }

    public synchronized void setPlayerOrder(List<String> newPlayerIds) {
        if (status != GameStatus.START_ROUND)
            throw new IllegalStateException();
        if (!new HashSet<>(playerIds).equals(new HashSet<>(newPlayerIds)))
            throw new IllegalStateException();

        String currentPlayerId = playerIds.get(currentPlayerIndex);
        String starterPlayerId = playerIds.get(starterPlayerIndex);
        playerIds = newPlayerIds;
        currentPlayerIndex = playerIds.indexOf(currentPlayerId);
        starterPlayerIndex = playerIds.indexOf(starterPlayerId);
    }

    public synchronized void updatePlayerScore(String playerId, boolean increment) {
        if (status != GameStatus.START_ROUND)
            throw new IllegalStateException();

        updatePlayerScore(playerId, increment ? 1 : -1);
    }

    public synchronized void setNumDecks(int numDecks) {
        if (status != GameStatus.START_ROUND)
            throw new IllegalStateException();
        if (numDecks <= 0 || numDecks > 10)
            throw new IllegalStateException();

        this.numDecks = numDecks;
    }

    public synchronized void setFindAFriend(boolean findAFriend) {
        if (status != GameStatus.START_ROUND)
            throw new IllegalStateException();
        if (findAFriend && playerIds.size() < 4)
            throw new IllegalStateException();

        this.findAFriend = findAFriend;
    }

    public synchronized void startRound() {
        if (status != GameStatus.START_ROUND)
            throw new IllegalStateException();

        status = GameStatus.DRAW;
        currentPlayerIndex = starterPlayerIndex;
        setIsDeclaringTeam();
        cardsById = Decks.getCardsById(numDecks);
        deck = Decks.shuffle(cardsById);
        playerHands = new HashMap<>();
        declaredCards = new ArrayList<>();
        kitty = new ArrayList<>();
        findAFriendDeclaration = null;
        pastTricks = new ArrayList<>();
        currentTrick = null;
        currentRoundScores = new HashMap<>(Maps.toMap(playerIds, playerId -> 0));

        for (String playerId : playerIds)
            playerHands.put(playerId, new ArrayList<>());
        findAFriendDeclarationCounters = ArrayListMultimap.create();
    }

    /**
     * The next player draws a card from the deck.
     */
    public synchronized Play draw() {
        if (status == GameStatus.DRAW_KITTY)
            return null;
        if (status != GameStatus.DRAW)
            throw new IllegalStateException();

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

        // if this is the first round, then the person who declares is the starter
        if (roundNumber == 0) {
            starterPlayerIndex = playerIds.indexOf(playerId);
            setIsDeclaringTeam();
        }
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
        if (card.getSuit() == Card.Suit.JOKER && play.getCardIds().size() == 1)
            throw new InvalidDeclareException("You cannot declare a single joker.");

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

    private void setIsDeclaringTeam() {
        isDeclaringTeam = IntStream.range(0, playerIds.size())
            .boxed()
            .collect(
                Collectors.toMap(i -> playerIds.get(i), i -> findAFriend ? i == starterPlayerIndex : (i + starterPlayerIndex) % 2 == 0));
    }

    public synchronized Play takeKitty() {
        if (status != GameStatus.DRAW_KITTY)
            return null;

        status = GameStatus.MAKE_KITTY;
        currentPlayerIndex = starterPlayerIndex;
        String playerId = playerIds.get(currentPlayerIndex);
        List<Integer> cardIds = new ArrayList<>(deck);
        playerHands.get(playerIds.get(currentPlayerIndex)).addAll(cardIds);
        sortCards(playerHands.get(playerIds.get(currentPlayerIndex)));
        deck.clear();
        return new Play(playerId, cardIds);
    }

    public synchronized void makeFindAFriendDeclaration(String playerId, FindAFriendDeclaration declarations)
            throws InvalidFindAFriendDeclarationException {
        if (!findAFriend)
            throw new InvalidFindAFriendDeclarationException("The game is not in find a friend mode.");
        if (status != GameStatus.MAKE_KITTY)
            throw new InvalidFindAFriendDeclarationException("You cannot declare a friend now.");
        if (!playerId.equals(playerIds.get(currentPlayerIndex)))
            throw new InvalidFindAFriendDeclarationException("Only the starter can declare a friend.");
        if (findAFriendDeclaration != null)
            throw new InvalidFindAFriendDeclarationException("You've already declared.");

        // check for valid declaration
        if (declarations.getDeclarations().size() != playerIds.size() / 2 - 1)
            throw new InvalidFindAFriendDeclarationException("Invalid number of declarations.");
        for (Declaration declaration : declarations.getDeclarations()) {
            Card card = new Card(declaration.getValue(), declaration.getSuit());
            if (declaration.getOrdinal() > numDecks)
                throw new InvalidFindAFriendDeclarationException("Invalid ordinal.");
            if (declaration.getOrdinal() < 0)
                throw new InvalidFindAFriendDeclarationException("Invalid ordinal.");
            if (!cardsById.containsValue(card))
                throw new InvalidFindAFriendDeclarationException("Invalid card.");

            if (declaration.getOrdinal() == 0) {
                if (numDecks != 2)
                    throw new InvalidFindAFriendDeclarationException("You can only declare OTHER with 2 decks.");

                long numCards = playerHands.get(playerId).stream()
                        .filter(cardId -> cardsById.get(cardId).equals(card))
                        .count();
                if (numCards != 1)
                    throw new InvalidFindAFriendDeclarationException("You need the card to declare OTHER.");
            }
        }

        if (!kitty.isEmpty())
            status = GameStatus.PLAY;

        findAFriendDeclaration = declarations;

        for (Declaration declaration : declarations.getDeclarations()) {
            Card card = new Card(declaration.getValue(), declaration.getSuit());
            findAFriendDeclarationCounters.put(card, declaration.getOrdinal());
        }
    }

    public synchronized void makeKitty(String playerId, List<Integer> cardIds) throws InvalidKittyException {
        sortCards(cardIds);
        Play play = new Play(playerId, cardIds);
        if (status != GameStatus.MAKE_KITTY || !kitty.isEmpty())
            throw new InvalidKittyException("You cannot make kitty now");
        if (!play.getPlayerId().equals(playerIds.get(currentPlayerIndex)))
            throw new InvalidKittyException("You cannot make kitty");
        if (play.getCardIds().size() != getKittySize())
            throw new InvalidKittyException("The kitty has to have " + getKittySize() + " cards");
        if (!isPlayable(play))
            throw new InvalidKittyException("Unknown error");
        if (!findAFriend || findAFriendDeclaration != null)
            status = GameStatus.PLAY;
        kitty = play.getCardIds();
        playerHands.get(playerId).removeAll(cardIds);
        currentTrick = new Trick(play.getPlayerId());
    }

    /**
     * The specified player makes the given play.
     *
     * If confirmDoesItFly is true, then the penalty will be paid if the play does not fly. If
     * false, then the play will always fail if it is a possible does-it-fly play, regardless of
     * whether the play is valid or not.
     */
    public synchronized PlayResult play(String playerId, List<Integer> cardIds, boolean confirmDoesItFly)
            throws InvalidPlayException, DoesNotFlyException {
        sortCards(cardIds);
        Play play = new Play(playerId, cardIds);

        try {
            verifyCanPlay(play, confirmDoesItFly);
        } catch (DoesNotFlyException e) {
            if (confirmDoesItFly)
                forfeitRound(playerId);
            throw e;
        }

        playerHands.get(playerId).removeAll(cardIds);
        currentTrick.getPlays().add(play);
        currentTrick.setWinningPlayerId(winningPlayerId(currentTrick));

        // test for find a friend
        boolean didFriendJoin = false;
        for (Map.Entry<Card, Integer> entry : new ArrayList<>(findAFriendDeclarationCounters.entries())) {
            Card card = entry.getKey();
            int counter = entry.getValue();
            int count = (int) cardIds.stream().map(cardsById::get).filter(otherCard -> card.equals(otherCard)).count();
            if (count == 0)
                continue;

            // ignore OTHER declaration if the starter played the card
            if (counter == 0 && playerIds.get(starterPlayerIndex).equals(playerId))
                continue;

            findAFriendDeclarationCounters.remove(card, counter);
            if (counter - count <= 0) {
                didFriendJoin = true;
                continue;
            }
            findAFriendDeclarationCounters.put(card, counter - count);
        }
        if (didFriendJoin)
            isDeclaringTeam.put(playerId, true);

        if (currentTrick.getPlays().size() == playerIds.size()) {
            currentPlayerIndex = -1;
            return new PlayResult(true, didFriendJoin);
        } else {
            currentPlayerIndex = (currentPlayerIndex + 1) % playerIds.size();
            return new PlayResult(false, didFriendJoin);
        }
    }

    public synchronized void finishTrick() {
        if (currentTrick.getPlays().size() != playerIds.size())
            throw new IllegalStateException();

        // finish trick
        String winningPlayerId = currentTrick.getWinningPlayerId();
        for (Play play : currentTrick.getPlays())
            currentRoundScores.put(winningPlayerId, currentRoundScores.get(winningPlayerId) + totalCardScore(play.getCardIds()));

        pastTricks.add(currentTrick);
        currentPlayerIndex = playerIds.indexOf(winningPlayerId);
        currentTrick = new Trick(winningPlayerId);

        // check for end of round
        if (playerHands.values().stream().allMatch(List::isEmpty)) {
            if (!isDeclaringTeam.get(winningPlayerId)) {
                int bonus = 2 * pastTricks.get(pastTricks.size() - 1).getPlays().get(0).getCardIds().size();
                currentRoundScores.put(winningPlayerId, currentRoundScores.get(winningPlayerId) + bonus * totalCardScore(kitty));
            }
            int roundScore = 0;
            for (String playerId : playerIds)
                if (!isDeclaringTeam.get(playerId))
                    roundScore += currentRoundScores.get(playerId);
            boolean doDeclarersWin = roundScore < 40 * numDecks;
            int scoreIncrease = doDeclarersWin
                    ? (roundScore == 0 ? 3 : 2 - roundScore / (20 * numDecks))
                    : roundScore / (20 * numDecks) - 2;
            roundEnd(doDeclarersWin, scoreIncrease);
        }
    }

    private void verifyCanPlay(Play play, boolean confirmDoesItFly) throws InvalidPlayException, DoesNotFlyException {
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

            if (!confirmDoesItFly)
                throw new DoesNotFlyException();

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
                                throw new DoesNotFlyException();
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
                    .anyMatch(shape -> shape.getWidth() >= handShape.getWidth());
                boolean inPlay = getProfile(play.getCardIds()).contains(handComponent);
                // Suppose the starting player played pairs. If you have any pairs (isCapturedByStartingPlay), but you didn't play it
                // (!inPlay), then look at how many cards you played are worse than it (numFreeCardsInPlay). If there are at least as many
                // worse cards (2), then those cards could have been replaced with the pair. This logic extends for any set of n cards.
                if (isCapturedByStartingPlay && !inPlay) {
                    int numFreeCardsInPlay = getProfile(play.getCardIds()).stream()
                        .map(Component::getShape)
                        .filter(shape -> shape.getWidth() < handShape.getWidth())
                        .mapToInt(shape -> shape.getWidth() * shape.getHeight())
                        .sum();
                    if (numFreeCardsInPlay >= handShape.getWidth())
                        throw new InvalidPlayException("You must play pairs before singles, etc.");
                }
            }
        }
    }

    public synchronized void takeBack(String playerId) {
        List<Play> plays = currentTrick.getPlays();
        if (plays.isEmpty())
            throw new IllegalStateException();

        Play lastPlay = plays.get(plays.size() - 1);
        if (!lastPlay.getPlayerId().equals(playerId))
            throw new IllegalStateException();

        currentTrick.getPlays().remove(plays.size() - 1);
        currentTrick.setWinningPlayerId(winningPlayerId(currentTrick));
        playerHands.get(playerId).addAll(lastPlay.getCardIds());
        sortCards(playerHands.get(playerId));
        currentPlayerIndex = playerIds.indexOf(playerId);
    }

    public synchronized void forfeitRound(String playerId) {
        boolean doDeclarersWin = !isDeclaringTeam.get(playerId);
        roundEnd(doDeclarersWin, doDeclarersWin ? 1 : 0);
    }

    private void roundEnd(boolean doDeclarersWin, int scoreIncrease) {
        roundNumber++;
        this.doDeclarersWin = doDeclarersWin;
        int prevStarterPlayerIndex = starterPlayerIndex;
        do {
            // starter goes to next person on the winning team
            starterPlayerIndex = (starterPlayerIndex + 1) % playerIds.size();
        } while (starterPlayerIndex != prevStarterPlayerIndex
                && isDeclaringTeam.get(playerIds.get(starterPlayerIndex)) != doDeclarersWin);
        winningPlayerIds.clear();
        for (String playerId : playerIds)
            if (isDeclaringTeam.get(playerId) == doDeclarersWin) {
                updatePlayerScore(playerId, scoreIncrease);
                winningPlayerIds.add(playerId);
            }
        status = GameStatus.START_ROUND;
    }

    private void updatePlayerScore(String playerId, int scoreIncrease) {
        int newScore = playerRankScores.get(playerId).ordinal() + scoreIncrease;
        if (newScore > Card.Value.ACE.ordinal())
            playerRankScores.put(playerId, Card.Value.ACE);
        else if (newScore < Card.Value.TWO.ordinal())
            playerRankScores.put(playerId, Card.Value.TWO);
        else
            playerRankScores.put(playerId, Card.Value.values()[newScore]);
    }

    public Card getCurrentTrump() {
        return new Card(
            playerRankScores.get(playerIds.get(starterPlayerIndex)),
            declaredCards == null || declaredCards.isEmpty()
                    ? Card.Suit.JOKER
                    : cardsById.get(declaredCards.get(declaredCards.size() - 1).getCardIds().get(0)).getSuit());
    }

    public int getKittySize() {
        if (playerIds.isEmpty())
            return 0;
        int kittySize = numDecks * Decks.SIZE % playerIds.size();
        while (kittySize < 5)
            kittySize += playerIds.size();
        return kittySize;
    }

    public Map<Integer, Card> getPublicCards() {
        Map<Integer, Card> publicCards = new HashMap<>();
        if (declaredCards != null)
            for (Play play : declaredCards)
                for (int cardId : play.getCardIds())
                    publicCards.put(cardId, cardsById.get(cardId));
        if (pastTricks != null)
            for (Trick trick : pastTricks)
                for (Play play : trick.getPlays())
                    for (int cardId : play.getCardIds())
                        publicCards.put(cardId, cardsById.get(cardId));
        if (currentTrick != null)
            for (Play play : currentTrick.getPlays())
                for (int cardId : play.getCardIds())
                    publicCards.put(cardId, cardsById.get(cardId));
        return publicCards;
    }

    public Map<Integer, Card> getPrivateCards(String playerId) {
        Map<Integer, Card> privateCards = new HashMap<>();
        if (playerHands != null && playerHands.containsKey(playerId))
            for (int cardId : playerHands.get(playerId))
                privateCards.put(cardId, cardsById.get(cardId));
        if (kitty != null)
            for (int cardId : kitty)
                privateCards.put(cardId, cardsById.get(cardId));
        return privateCards;
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

    public Grouping getGrouping(List<Integer> cardIds) {
        Set<Grouping> groupings = cardIds.stream()
            .map(cardsById::get)
            .map(card -> Cards.grouping(card, getCurrentTrump()))
            .collect(Collectors.toSet());
        return groupings.size() == 1 ? Iterables.getOnlyElement(groupings) : null;
    }

    public List<Component> getProfile(List<Integer> cardIds) {
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
                    Cards.rank(card, trump),
                    cardIds.stream().filter(cardId -> cardsById.get(cardId).equals(card)).collect(Collectors.toSet()));
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
                        component1.maxRank,
                        Streams.concat(component1.cardIds.stream(), component2.cardIds.stream()).collect(Collectors.toSet())));
                    profile.remove(j);
                    return true;
                }
            }
        return false;
    }

    private String winningPlayerId(Trick trick) {
        String winningPlayerId = trick.getStartPlayerId();
        List<Play> plays = trick.getPlays();
        if (!plays.isEmpty()) {
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
        }
        return winningPlayerId;
    }

    private static Multiset<Shape> getShapes(List<Component> profile) {
        return HashMultiset.create(profile.stream().map(Component::getShape).collect(Collectors.toList()));
    }
}
