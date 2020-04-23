package io.github.ytung.tractor.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.common.collect.Maps;

import io.github.ytung.tractor.Cards;
import io.github.ytung.tractor.Cards.Grouping;
import io.github.ytung.tractor.Component;
import io.github.ytung.tractor.Game;
import io.github.ytung.tractor.api.Card;
import io.github.ytung.tractor.api.GameStatus;
import io.github.ytung.tractor.api.IncomingMessage;
import io.github.ytung.tractor.api.IncomingMessage.DeclareRequest;
import io.github.ytung.tractor.api.IncomingMessage.MakeKittyRequest;
import io.github.ytung.tractor.api.IncomingMessage.PlayRequest;
import io.github.ytung.tractor.api.OutgoingMessage;
import io.github.ytung.tractor.api.OutgoingMessage.InvalidAction;
import io.github.ytung.tractor.api.Play;
import io.github.ytung.tractor.api.Trick;
import lombok.Data;

@Data
public class AiClient {

    private static final double EPS = 1e-6;

    private final String myPlayerId;

    public void processMessage(Game game, OutgoingMessage message, Consumer<IncomingMessage> send) {
        if (message instanceof InvalidAction) {
            throw new IllegalStateException(((InvalidAction) message).getMessage());
        }

        if (game.getStatus() == GameStatus.DRAW) {
            List<Integer> declaredCardIds = declare(game);
            if (declaredCardIds != null) {
                DeclareRequest request = new DeclareRequest();
                request.setCardIds(new ArrayList<>(declaredCardIds));
                send.accept(request);
            }
        }

        if (game.getStatus() == GameStatus.MAKE_KITTY
                && game.getCurrentPlayerIndex() != -1
                && game.getPlayerIds().get(game.getCurrentPlayerIndex()).equals(myPlayerId)
                && game.getKitty().isEmpty()) {
            MakeKittyRequest request = new MakeKittyRequest();
            request.setCardIds(new ArrayList<>(makeKitty(game)));
            send.accept(request);
        }

        if (game.getStatus() == GameStatus.PLAY
                && game.getCurrentPlayerIndex() != -1
                && game.getPlayerIds().get(game.getCurrentPlayerIndex()).equals(myPlayerId)) {
            PlayRequest request = new PlayRequest();
            request.setCardIds(new ArrayList<>(play(game)));
            request.setConfirmSpecialPlay(true);
            send.accept(request);
        }
    }

    private List<Integer> declare(Game game) {
        Map<Integer, Card> cardsById = game.getCardsById();
        List<Play> declaredCards = game.getDeclaredCards();
        Card trump = game.getCurrentTrump();
        List<Integer> myHand = game.getPlayerHands().get(myPlayerId);

        if (!declaredCards.isEmpty())
            return null;

        Map<Grouping, List<Integer>> myHandByGrouping = Maps.toMap(Arrays.asList(Grouping.values()), grouping -> myHand.stream()
            .filter(cardId -> Cards.grouping(cardsById.get(cardId), trump) == grouping)
            .collect(Collectors.toList()));

        for (int cardId : myHand) {
            Card card = cardsById.get(cardId);
            if (card.getValue() == trump.getValue()) {
                Grouping grouping = Cards.grouping(card, null);
                if (myHandByGrouping.containsKey(grouping) && myHandByGrouping.get(grouping).size() >= 5)
                    return Arrays.asList(cardId);
            }
        }
        return null;
    }

    private List<Integer> makeKitty(Game game) {
        Map<Integer, Card> cardsById = game.getCardsById();
        Card trump = game.getCurrentTrump();
        int kittySize = game.getKittySize();
        List<Integer> myHand = game.getPlayerHands().get(myPlayerId);

        List<Card> myCards = myHand.stream().map(cardsById::get).collect(Collectors.toList());
        return myHand.stream()
                .sorted(Comparator.comparing(cardId -> {
                    Card card = cardsById.get(cardId);
                    return Cards.rank(card, trump)
                            + Collections.frequency(myCards, card) * 5
                            + (Cards.grouping(card, trump) == Grouping.TRUMP ? 100 : 0);
                }))
                .limit(kittySize)
                .collect(Collectors.toList());
    }

    private Collection<Integer> play(Game game) {
        List<String> playerIds = game.getPlayerIds();
        int numDecks = game.getNumDecks();
        Map<Integer, Card> cardsById = game.getCardsById();
        Trick currentTrick = game.getCurrentTrick();
        Card trump = game.getCurrentTrump();

        Map<ProbKey, Double> probTable = new HashMap<>();
        for (String playerId : playerIds)
            for (int cardId : cardsById.keySet())
                for (int numExisting = 0; numExisting < numDecks; numExisting++)
                    probTable.put(new ProbKey(playerId, cardId, numExisting), 1.);

        // compute the various probabilities of each player having which cards
        for (Trick trick : game.getAllTricks()) {
            List<Play> plays = trick.getPlays();
            if (plays.isEmpty())
                continue;

            List<Integer> startingCardIds = plays.get(0).getCardIds();
            Grouping startingGrouping = game.getGrouping(startingCardIds);

            for (Play play : plays) {
                List<Integer> cardIds = play.getCardIds();
                Grouping grouping = game.getGrouping(cardIds);

                // these cards are definitely no longer in hand
                for (int cardId : cardIds)
                    for (String playerId : playerIds)
                        for (int numExisting = 0; numExisting < numDecks; numExisting++)
                            probTable.put(new ProbKey(playerId, cardId, numExisting), 0.);

                // if the player isn't following suit, then they definitely don't have any more
                if (grouping != startingGrouping)
                    for (int cardId : cardsById.keySet())
                        if (Cards.grouping(cardsById.get(cardId), trump) == startingGrouping)
                            probTable.put(new ProbKey(play.getPlayerId(), cardId, 0), 0.);
            }
        }

        // the cards that are in my hand are definitely in my hand by tautology
        for (int cardId : game.getPlayerHands().get(myPlayerId))
            for (int numExisting = 0; numExisting < numDecks; numExisting++)
                probTable.put(new ProbKey(myPlayerId, cardId, numExisting), 1000000.);

        // normalize the probability table
        for (int cardId : cardsById.keySet())
            IntStream.range(0, numDecks).forEach(numExisting -> {
                double totalProb = playerIds.stream()
                    .mapToDouble(playerId -> probTable.get(new ProbKey(playerId, cardId, numExisting)) + EPS)
                    .sum();
                for (String playerId : playerIds)
                    probTable.compute(new ProbKey(playerId, cardId, numExisting), (key, prob) -> (prob + EPS) / totalProb);
            });

        // Find the play that gives me the highest expected score
        // This only looks at the short-term of the current trick, and has no long-term goal
        double bestScore = Double.NEGATIVE_INFINITY;
        Collection<Integer> bestPlay = null;
        System.out.println();
        for (Collection<Integer> myPlay : getCandidatePlays(game)) {
            Trick currentTrickWithMyPlay = new Trick(currentTrick.getStartPlayerId());
            currentTrickWithMyPlay.getPlays().addAll(currentTrick.getPlays());
            currentTrickWithMyPlay.getPlays().add(new Play(myPlayerId, new ArrayList<>(myPlay)));
            currentTrickWithMyPlay.setWinningPlayerId(game.winningPlayerId(currentTrickWithMyPlay));

            double score = score(game, currentTrickWithMyPlay, probTable);
            if (score > bestScore) {
                bestScore = score;
                bestPlay = myPlay;
            }
            System.out.println(myPlay + " " + myPlay.stream().map(cardsById::get).collect(Collectors.toList()) + " " + score);
        }
        return bestPlay;
    }

    /**
     * Returns a list of all possible plays that the current AI player can make. The plays are not
     * sorted; a downstream processor will determine which play is the best. This list is also not
     * exhaustive; often, various other combinations of losing plays are not considered.
     */
    private List<Collection<Integer>> getCandidatePlays(Game game) {
        Map<Integer, Card> cardsById = game.getCardsById();
        Card trump = game.getCurrentTrump();
        Map<Grouping, List<Integer>> myHandByGrouping = Maps.toMap(
            Arrays.asList(Grouping.values()),
            grouping -> game.getPlayerHands().get(myPlayerId).stream()
                .filter(cardId -> Cards.grouping(cardsById.get(cardId), trump) == grouping)
                .collect(Collectors.toList()));
        Trick currentTrick = game.getCurrentTrick();

        List<Collection<Integer>> candidatePlays = new ArrayList<>();
        if (currentTrick.getPlays().isEmpty()) {
            // If I lead, I can lead with any component
            for (Grouping grouping : myHandByGrouping.keySet()) {
                List<Component> profile = game.getProfile(myHandByGrouping.get(grouping));
                for (Component component : profile)
                    candidatePlays.add(component.getCardIds());
            }
        } else {
            List<Integer> startingCardIds = currentTrick.getPlays().get(0).getCardIds();
            Grouping startingGrouping = game.getGrouping(startingCardIds);

            String winningPlayerId = currentTrick.getWinningPlayerId();
            Play winningPlay = currentTrick.getPlays().stream()
                .filter(play -> play.getPlayerId().equals(winningPlayerId))
                .findFirst()
                .get();
            List<Integer> winningCardIds = winningPlay.getCardIds();
            Grouping winningGrouping = game.getGrouping(winningCardIds);
            List<Component> winningProfile = game.getProfile(winningCardIds);
            // Optimization: ensure I try to beat the largest component first, etc.
            // Otherwise, I might use up a card in my pair to beat a single, and then not be able to beat the pair
            Collections.sort(
                winningProfile,
                Comparator.<Component, Integer> comparing(component -> component.getShape().getWidth())
                    .thenComparing(component -> component.getShape().getHeight())
                    .reversed());

            if (myHandByGrouping.get(startingGrouping).isEmpty()) {
                // If I'm out of the current suit, find the ways I can beat the play with trump
                findWinningCandidatePlays(
                    game,
                    winningProfile,
                    myHandByGrouping.get(Grouping.TRUMP),
                    winningGrouping == Grouping.TRUMP,
                    candidatePlays);
            } else if (winningGrouping == startingGrouping) {
                // Find the ways I can beat the play in the same suit
                findWinningCandidatePlays(
                    game,
                    winningProfile,
                    myHandByGrouping.get(winningGrouping),
                    true,
                    candidatePlays);
            }

            // If I can't beat it, consider two possibilities:
            // either give as many points as possible, or avoid giving as many points as possible
            // We assume that one of these is the optimal; i.e. we never give only *some* points
            for (boolean playPoints : Arrays.asList(true, false))
                candidatePlays.add(findLosingCandidatePlay(
                    game,
                    startingCardIds,
                    startingGrouping,
                    myHandByGrouping,
                    playPoints));
        }
        return candidatePlays;
    }

    private void findWinningCandidatePlays(
            Game game,
            List<Component> winningProfile,
            List<Integer> myCardIds,
            boolean areMyCardIdsInSameSuit,
            List<Collection<Integer>> candidatePlays) {
        List<Component> myProfile = game.getProfile(myCardIds);
        Collections.sort(
            myProfile,
            Comparator.<Component, Integer> comparing(component -> component.getShape().getWidth())
                .thenComparing(component -> component.getShape().getHeight()));
        findWinningCandidatePlays(
            game.getCardsById(),
            game.getCurrentTrump(),
            winningProfile,
            new ArrayList<>(myCardIds),
            myProfile,
            areMyCardIdsInSameSuit,
            new ArrayList<>(),
            candidatePlays);
    }

    private void findWinningCandidatePlays(
            Map<Integer, Card> cardsById,
            Card trump,
            List<Component> winningProfile,
            List<Integer> myCardIds,
            List<Component> myProfile,
            boolean areMyCardIdsInSameSuit,
            List<Collection<Integer>> myComponents,
            List<Collection<Integer>> candidatePlays) {
        // Avoid pathological cases (mainly from special plays) where we have too many candidates
        if (candidatePlays.size() >= 100)
            return;

        // Recursion base case
        if (myComponents.size() == winningProfile.size()) {
            candidatePlays.add(myComponents.stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList()));
            return;
        }

        // For the current component to process, try each of my components that beats it, and continue the recursion
        Component winningComponent = winningProfile.get(myComponents.size());
        for (Component myComponent : myProfile)
            if (myCardIds.containsAll(myComponent.getCardIds())
                    && myComponent.getShape().getWidth() >= winningComponent.getShape().getWidth()
                    && myComponent.getShape().getHeight() >= winningComponent.getShape().getHeight()
                    && (!areMyCardIdsInSameSuit || myComponent.getMaxRank() > winningComponent.getMaxRank())) {
                // prune down to correct number of cards in correct shape
                List<Integer> cardIds = myComponent.getCardIds().stream()
                    .sorted(Comparator.comparing(cardId -> Cards.rank(cardsById.get(cardId), trump)))
                    .collect(Collectors.toList());
                List<Integer> prunedCardIds = new ArrayList<>();
                for (int i = 0; i < winningComponent.getShape().getWidth(); i++)
                    for (int j = 0; j < winningComponent.getShape().getHeight(); j++)
                        prunedCardIds.add(cardIds.get(j * winningComponent.getShape().getWidth() + i));

                myCardIds.removeAll(prunedCardIds);
                myComponents.add(prunedCardIds);
                findWinningCandidatePlays(
                    cardsById,
                    trump,
                    winningProfile,
                    myCardIds,
                    myProfile,
                    areMyCardIdsInSameSuit,
                    myComponents,
                    candidatePlays);
                myCardIds.addAll(prunedCardIds);
                myComponents.remove(myComponents.size() - 1);
            }
    }

    private List<Integer> findLosingCandidatePlay(
            Game game,
            List<Integer> startingCardIds,
            Grouping startingGrouping,
            Map<Grouping, List<Integer>> myHandByGrouping,
            boolean playPoints) {
        List<Component> startingProfile = game.getProfile(startingCardIds);
        int maxWidth = startingProfile.stream().mapToInt(component -> component.getShape().getWidth()).max().orElse(0);

        return myHandByGrouping.values().stream()
            .flatMap(cardIds -> game.getProfile(cardIds).stream())
            .sorted(Comparator.comparing(component -> {
                // Scoring function that first prioritizes cards that you *must* play (e.g. same suit, smaller width)
                // Then prioritizes lower cards and components with smaller width since you can't win anyway
                Grouping grouping = game.getGrouping(component.getCardIds());

                int score = (playPoints ? -100 : 100) * game.totalCardScore(component.getCardIds())
                        + component.getMaxRank();
                if (grouping == startingGrouping) {
                    if (component.getShape().getWidth() <= maxWidth) {
                        score -= component.getShape().getWidth() * 1_000_000;
                    } else {
                        score += 100_000_000;
                        score += component.getShape().getWidth() * 1_000_000;
                    }
                } else {
                    score += 1_000_000_000;
                    score += component.getShape().getWidth() * 1_000_000;
                    if (grouping == Grouping.TRUMP)
                        score += 100_000;
                }
                return score;
            }))
            .flatMap(component -> component.getCardIds().stream())
            .limit(startingCardIds.size())
            .collect(Collectors.toList());
    }

    /**
     * Scoring function for a particular play
     */
    private double score(Game game, Trick currentTrickWithMyPlay, Map<ProbKey, Double> probTable) {
        List<String> playerIds = game.getPlayerIds();
        Map<String, Boolean> isDeclaringTeam = game.getIsDeclaringTeam();
        Map<Integer, Card> cardsById = game.getCardsById();
        Card trump = game.getCurrentTrump();

        int startingPlayerIndex = playerIds.indexOf(currentTrickWithMyPlay.getStartPlayerId());
        List<Integer> startingPlay = currentTrickWithMyPlay.getPlays().get(0).getCardIds();
        List<Component> startingComponents = game.getProfile(startingPlay);

        List<String> remainingPlayerIds = new ArrayList<>();
        for (int i = currentTrickWithMyPlay.getPlays().size(); i < playerIds.size(); i++)
            remainingPlayerIds.add(playerIds.get((startingPlayerIndex + i) % playerIds.size()));

        Map<String, Double> winningProbabilities = new HashMap<>(Maps.toMap(playerIds, key -> 0.));

        // Only compute probabilities if a single type of card is played.
        // (Otherwise, it's too complicated - just assume the currently winning player wins.)
        if (startingComponents.size() == 1 && startingComponents.get(0).getShape().getHeight() == 1) {
            Card startingCard = cardsById.get(startingPlay.get(0));
            Grouping startingGrouping = Cards.grouping(startingCard, trump);
            int startingWidth = startingPlay.size();

            // Compute the probability that each player is out of the suit
            Map<String, Double> outOfSuitProbabilities = new HashMap<>(Maps.toMap(playerIds, key -> 1.));
            for (String playerId : playerIds)
                cardsById.forEach((cardId, card) -> {
                    if (Cards.grouping(card, trump) == startingGrouping)
                        outOfSuitProbabilities.compute(
                            playerId,
                            (key, prob) -> prob * (1 - probTable.get(new ProbKey(playerId, cardId, 0))));
                });

            // Starting player has the kitty, so always give them at least a 30% chance of being out of a suit
            if (startingGrouping != Grouping.TRUMP)
                outOfSuitProbabilities.compute(playerIds.get(game.getStarterPlayerIndex()), (key, prob) -> Math.max(prob, 0.3));

            // Go through every possible card that can beat the starting card,
            // starting from trumps (highest to lowest) than cards in the same suit (highest to lowest).
            List<Integer> sortedCardIds = cardsById.keySet().stream()
                    .sorted(Comparator.comparing(cardId -> {
                        Card card = cardsById.get(cardId);
                        Grouping grouping = Cards.grouping(card, trump);
                        int score = Cards.rank(card, trump);
                        if (grouping == Grouping.TRUMP)
                            score += 1000;
                        else if (grouping == startingGrouping)
                            score += 100;
                        return score;
                    }).reversed())
                    .collect(Collectors.toList());

            // Now go through these possible cards
            // For each card and player, process the probability that the player has the card(s) (and therefore can win)
            // Then subtract that probability, and continue for the next card and player
            double remainingProbability = 1;
            for (int cardId : sortedCardIds) {
                Card card = cardsById.get(cardId);
                Grouping grouping = Cards.grouping(card, trump);

                // Stop once I get to this card; all later cards are smaller
                if (card.equals(startingCard))
                    break;

                for (String playerId : remainingPlayerIds) {
                    double probHaveCards = 1;
                    for (int numExisting = 0; numExisting < startingWidth; numExisting++)
                        probHaveCards *= probTable.get(new ProbKey(playerId, cardId, numExisting));

                    double probCanPlayCards = grouping == Grouping.TRUMP && startingGrouping != Grouping.TRUMP
                            ? outOfSuitProbabilities.get(playerId)
                            : 1;
                    double probCanBeat = remainingProbability * probHaveCards * probCanPlayCards;

                    winningProbabilities.compute(playerId, (key, prob) -> prob + probCanBeat);
                    remainingProbability -= probCanBeat;
                }
            }
        }

        // The leftover win probability goes to the currently winning player
        String winningPlayerId = currentTrickWithMyPlay.getWinningPlayerId();
        double totalProb = winningProbabilities.values().stream().mapToDouble(prob -> prob).sum();
        winningProbabilities.compute(winningPlayerId, (key, prob) -> prob + 1 - totalProb);
        System.out.println(playerIds.stream().map(winningProbabilities::get).collect(Collectors.toList()));

        double totalExpectedScore = 0;
        for (String playerId : playerIds) {
            double expectedScore = 0;
            for (Play play : currentTrickWithMyPlay.getPlays())
                expectedScore += game.totalCardScore(play.getCardIds());

            // As a heuristic, assume that members of the winning team will add an average of 5 points per card
            for (String remainingPlayerId : remainingPlayerIds)
                if (isDeclaringTeam.get(remainingPlayerId).equals(isDeclaringTeam.get(winningPlayerId)))
                    expectedScore += 5 * startingPlay.size();

            expectedScore *= winningProbabilities.get(playerId);

            if (isDeclaringTeam.get(playerId).equals(isDeclaringTeam.get(myPlayerId)))
                totalExpectedScore += expectedScore;
            else
                totalExpectedScore -= expectedScore;
        }
        return totalExpectedScore;
    }

    /**
     * The key for the probability of playerId having cardId, given that the player already has
     * numExisting copies of the same card (value + suit).
     */
    @Data
    private static class ProbKey {

        final String playerId;
        final int cardId;
        final int numExisting;
    }
}
