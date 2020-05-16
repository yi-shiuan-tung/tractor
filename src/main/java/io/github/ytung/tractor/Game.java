package io.github.ytung.tractor;

import java.util.ArrayList;
import java.util.Collection;
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

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Streams;
import com.google.common.collect.TreeMultiset;

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
    private Set<String> winningPlayerIds = new HashSet<>();

    // round state
    private GameStatus status = GameStatus.START_ROUND;
    private int currentPlayerIndex;
    private Map<String, Boolean> isDeclaringTeam;
    private Queue<Integer> deck;
    private Map<Integer, Card> cardsById;
    private Map<String, List<Integer>> playerHands;
    private List<Play> declaredCards;
    private List<Integer> exposedBottomCards = new ArrayList<>();
    private List<Integer> kitty;
    private FindAFriendDeclaration findAFriendDeclaration;
    private List<Trick> pastTricks;
    private Trick currentTrick;
    private Map<String, Integer> currentRoundScores = new HashMap<>();
    private Map<String, Integer> currentRoundPenalties = new HashMap<>();

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
        exposedBottomCards = new ArrayList<>();
        kitty = new ArrayList<>();
        findAFriendDeclaration = null;
        pastTricks = new ArrayList<>();
        currentTrick = null;
        currentRoundScores = new HashMap<>(Maps.toMap(playerIds, playerId -> 0));
        currentRoundPenalties = new HashMap<>(Maps.toMap(playerIds, playerId -> 0));

        for (String playerId : playerIds)
            playerHands.put(playerId, new ArrayList<>());
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

    public synchronized void exposeBottomCards() {
        if (status != GameStatus.DRAW_KITTY)
            throw new IllegalStateException();
        if (!declaredCards.isEmpty())
            throw new IllegalStateException();

        // draw from deck until we find a trump, or take the suit of the highest value card
        status = GameStatus.EXPOSE_BOTTOM_CARDS;
        for (int cardId : deck) {
            exposedBottomCards.add(cardId);
            if (getCurrentTrump().getSuit() != Card.Suit.JOKER) {
                playerHands.forEach((otherPlayerId, otherCardIds) -> sortCards(otherCardIds));
                return;
            }
        }
    }

    public synchronized Play takeKitty() {
        if (status != GameStatus.DRAW_KITTY && status != GameStatus.EXPOSE_BOTTOM_CARDS)
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
        status = findAFriend ? GameStatus.DECLARE_FRIEND : GameStatus.PLAY;
        kitty = play.getCardIds();
        playerHands.get(playerId).removeAll(cardIds);
        currentTrick = new Trick(play.getPlayerId());
    }

    public synchronized void makeFindAFriendDeclaration(String playerId, FindAFriendDeclaration declarations)
            throws InvalidFindAFriendDeclarationException {
        if (status != GameStatus.DECLARE_FRIEND)
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
            if (declaration.isSatisfied())
                throw new InvalidFindAFriendDeclarationException("Unknown error");
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

        status = GameStatus.PLAY;
        findAFriendDeclaration = declarations;
    }

    /**
     * The specified player makes the given play.
     *
     * If confirmSpecialPlay is true, then the penalty will be paid if the special play is invalid. If
     * false, then the play will always fail, regardless of whether the special play is valid or not.
     */
    public synchronized PlayResult play(String playerId, List<Integer> cardIds, boolean confirmSpecialPlay)
            throws InvalidPlayException, ConfirmSpecialPlayException {
        sortCards(cardIds);
        Play play = new Play(playerId, cardIds);
        verifyCanPlay(new Play(playerId, cardIds));

        // check to see if this is a special play, and if so, whether it is valid
        Component badComponent = null;
        if (currentTrick.getPlays().isEmpty()) {
            List<Component> profile = getProfile(play.getCardIds());

            if (profile.size() > 1) {
                if (!confirmSpecialPlay)
                    throw new ConfirmSpecialPlayException();

                Card trump = getCurrentTrump();
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
                                    badComponent = component;
                                }
                        }
            }
        }
        if (badComponent != null) {
            currentRoundPenalties.compute(playerId, (key, penalty) -> penalty + 10);
            cardIds = new ArrayList<>(badComponent.getCardIds());
            sortCards(cardIds);
        }

        playerHands.get(playerId).removeAll(cardIds);
        currentTrick.getPlays().add(new Play(playerId, cardIds)); // might be different from the initial play
        currentTrick.setWinningPlayerId(winningPlayerId(currentTrick));

        boolean didFriendJoin = updateFindAFriendDeclaration();

        if (currentTrick.getPlays().size() == playerIds.size()) {
            currentPlayerIndex = -1;
            return new PlayResult(true, didFriendJoin, badComponent != null);
        } else {
            currentPlayerIndex = (currentPlayerIndex + 1) % playerIds.size();
            return new PlayResult(false, didFriendJoin, badComponent != null);
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

    /**
     * Returns whether a friend joined
     */
    private boolean updateFindAFriendDeclaration() {
        if (!findAFriend)
            return false;

        long numSatisfiedDeclarations = findAFriendDeclaration.getDeclarations().stream()
                .filter(Declaration::isSatisfied)
                .count();

        for (int i = 0; i < playerIds.size(); i++)
            if (i != starterPlayerIndex)
                isDeclaringTeam.put(playerIds.get(i), false);

        for (Declaration declaration : findAFriendDeclaration.getDeclarations())
            updateFindAFriendDeclaration(declaration);

        return findAFriendDeclaration.getDeclarations().stream()
                .filter(Declaration::isSatisfied)
                .count() != numSatisfiedDeclarations;
    }

    private void updateFindAFriendDeclaration(Declaration declaration) {
        declaration.setSatisfied(false);
        int numPlayed = 0;
        for (Trick trick : getAllTricks())
            for (Play play : trick.getPlays()) {
                for (int cardId : play.getCardIds()) {
                    Card card = cardsById.get(cardId);
                    if (declaration.getValue() == card.getValue() && declaration.getSuit() == card.getSuit()
                            && (declaration.getOrdinal() > 0 || !playerIds.get(starterPlayerIndex).equals(play.getPlayerId()))) {
                        numPlayed++;
                    }
                }
                if (numPlayed >= Math.max(declaration.getOrdinal(), 1)) {
                    isDeclaringTeam.put(play.getPlayerId(), true);
                    declaration.setSatisfied(true);
                    return;
                }
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
            for (String playerId : playerIds) {
                if (isDeclaringTeam.get(playerId)) {
                    roundScore += currentRoundPenalties.get(playerId);
                } else {
                    roundScore += currentRoundScores.get(playerId);
                    roundScore -= currentRoundPenalties.get(playerId);
                }
            }
            boolean doDeclarersWin = roundScore < 40 * numDecks;
            int scoreIncrease = doDeclarersWin
                    ? (roundScore == 0 ? 3 : 2 - roundScore / (20 * numDecks))
                    : roundScore / (20 * numDecks) - 2;
            finishRound(doDeclarersWin, scoreIncrease);

            currentPlayerIndex = -1;
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
        updateFindAFriendDeclaration();
    }

    public synchronized void forfeitRound(String playerId) {
        boolean doDeclarersWin = !isDeclaringTeam.get(playerId);
        finishRound(doDeclarersWin, doDeclarersWin ? 1 : 0);
    }

    private void finishRound(boolean doDeclarersWin, int scoreIncrease) {
        roundNumber++;
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
        if (playerIds.isEmpty())
            return null;

        Card.Value trumpValue = playerRankScores.get(playerIds.get(starterPlayerIndex));

        if (declaredCards != null && !declaredCards.isEmpty())
            return new Card(trumpValue, cardsById.get(declaredCards.get(declaredCards.size() - 1).getCardIds().get(0)).getSuit());

        for (int cardId : exposedBottomCards) {
            Card card = cardsById.get(cardId);
            if (card.getValue() == trumpValue)
                return new Card(trumpValue, card.getSuit());
        }

        if (exposedBottomCards.size() == getKittySize()) {
            Card highestCard = null;
            for (int cardId : exposedBottomCards) {
                Card card = cardsById.get(cardId);
                if (card.getSuit() == Card.Suit.JOKER)
                    continue;
                if (highestCard == null || card.getValue().ordinal() > highestCard.getValue().ordinal())
                    highestCard = card;
            }
            if (highestCard != null)
                return new Card(trumpValue, highestCard.getSuit());
        }

        return new Card(trumpValue, Card.Suit.JOKER);
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
        for (Trick trick : getAllTricks())
            for (Play play : trick.getPlays())
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

    public List<Trick> getAllTricks() {
        List<Trick> allTricks = new ArrayList<>();
        if (pastTricks != null)
            allTricks.addAll(pastTricks);
        if (currentTrick != null)
            allTricks.add(currentTrick);
        return allTricks;
    }

    private void sortCards(List<Integer> hand) {
        Card trump = getCurrentTrump();
        Collections.sort(hand, Comparator.comparing(cardId -> {
            Card card = cardsById.get(cardId);
            Grouping grouping = Cards.grouping(card, trump);
            return grouping.ordinal() * 1000 + Cards.rank(card, trump) * 10 + card.getSuit().ordinal();
        }));
    }

    public int totalCardScore(Collection<Integer> cardIds) {
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

    public Grouping getGrouping(Collection<Integer> cardIds) {
        Set<Grouping> groupings = cardIds.stream()
            .map(cardsById::get)
            .map(card -> Cards.grouping(card, getCurrentTrump()))
            .collect(Collectors.toSet());
        return groupings.size() == 1 ? Iterables.getOnlyElement(groupings) : null;
    }

    public List<Component> getProfile(Collection<Integer> cardIds) {
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

    public String winningPlayerId(Trick trick) {
        String winningPlayerId = trick.getStartPlayerId();
        List<Play> plays = trick.getPlays();
        if (!plays.isEmpty()) {
            List<Component> bestProfile = getProfile(plays.get(0).getCardIds());
            Grouping bestGrouping = getGrouping(plays.get(0).getCardIds());
            for (int i = 1; i < plays.size(); i++) {
                Play play = plays.get(i);
                List<Component> profile = getProfile(play.getCardIds());
                Grouping grouping = getGrouping(play.getCardIds());
                if (hasCoveringShape(profile, bestProfile)) {
                    if ((grouping == Grouping.TRUMP && bestGrouping != Grouping.TRUMP)
                            || (grouping == bestGrouping && rank(profile) > rank(bestProfile))) {
                        winningPlayerId = play.getPlayerId();
                        bestProfile = profile;
                        bestGrouping = grouping;
                    }
                }
            }
        }
        return winningPlayerId;
    }

    /**
     * Returns whether my play "covers" the other play. For example, if myPlay is a single pair and
     * otherPlay is two singles, then the pair covers the singles. This method is used to check the
     * first requirement of beating a play in Tractor: whether your play has the same "shape".
     */
    private static boolean hasCoveringShape(List<Component> myPlay, List<Component> otherPlay) {
        TreeMultiset<Shape> myShapes = TreeMultiset.create(Comparator.comparing(shape -> shape.getWidth() * shape.getHeight()));
        for (Component component : myPlay)
            myShapes.add(component.getShape());
        List<Shape> otherShapes = otherPlay.stream()
            .map(Component::getShape)
            .sorted(Comparator.<Shape, Integer>comparing(shape -> shape.getWidth() * shape.getHeight()).reversed())
            .collect(Collectors.toList());

        for (Shape otherShape : otherShapes) {
            // For each shape in the other play, find a component of my play that "covers" it (has at least that width
            // and height), and then remove the relevant cards. This is a greedy algorithm that isn't guaranteed to be
            // correct, but works for all practical cases if we try to match our smallest components with the other
            // play's largest components.
            boolean found = false;
            for (Shape myShape : myShapes) {
                if (myShape.getWidth() >= otherShape.getWidth() && myShape.getHeight() >= otherShape.getHeight()) {
                    myShapes.remove(myShape);
                    if (myShape.getHeight() > otherShape.getHeight())
                        myShapes.add(new Shape(myShape.getWidth(), myShape.getHeight() - otherShape.getHeight()));
                    if (myShape.getWidth() > otherShape.getWidth())
                        myShapes.add(new Shape(myShape.getWidth() - otherShape.getHeight(), otherShape.getHeight()));
                    found = true;
                    break;
                }
            }
            if (!found)
                return false;
        }
        return myShapes.isEmpty();
    }

    private static int rank(List<Component> profile) {
        return profile.stream().mapToInt(component -> component.getCardIds().size() * 1000 + component.getMaxRank()).max().orElse(0);
    }
}
