
package io.github.ytung.tractor.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.Maps;

import io.github.ytung.tractor.Cards;
import io.github.ytung.tractor.Cards.Grouping;
import io.github.ytung.tractor.Component;
import io.github.ytung.tractor.Game;
import io.github.ytung.tractor.api.Card;
import io.github.ytung.tractor.api.Play;
import io.github.ytung.tractor.api.Trick;

public class SimpleAiClient implements AiClient {

    @Override
    public List<Integer> declare(String myPlayerId, Game game) {
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

    @Override
    public List<Integer> makeKitty(String myPlayerId, Game game) {
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

    @Override
    public Collection<Integer> play(String myPlayerId, Game game) {
        Map<Integer, Card> cardsById = game.getCardsById();
        Trick currentTrick = game.getCurrentTrick();
        Card trump = game.getCurrentTrump();
        List<Integer> myHand = game.getPlayerHands().get(myPlayerId);

        Map<Grouping, List<Integer>> myHandByGrouping = Maps.toMap(Arrays.asList(Grouping.values()), grouping -> myHand.stream()
            .filter(cardId -> Cards.grouping(cardsById.get(cardId), trump) == grouping)
            .collect(Collectors.toList()));

        if (currentTrick.getPlays().isEmpty()) {
            // Do I have aces?
            for (int cardId : myHand) {
                Card card = cardsById.get(cardId);
                if (card.getValue() == Card.Value.ACE && Cards.grouping(card, trump) != Grouping.TRUMP)
                    return Arrays.asList(cardId);
            }

            // Do I have a pair?
            int bestPairRank = -1;
            List<Integer> bestPair = null;
            for (List<Integer> sameSuitCards : myHandByGrouping.values())
                for (Component component : game.getProfile(sameSuitCards))
                    if (component.getShape().getWidth() >= 2) {
                        int rank = component.getMaxRank();
                        if (rank > bestPairRank) {
                            bestPairRank = rank;
                            bestPair = new ArrayList<>(component.getCardIds());
                        }
                    }
            if (bestPair != null)
                return bestPair;

            // Play low trump
            List<Integer> myTrumpCards = myHandByGrouping.get(Grouping.TRUMP);
            if (!myTrumpCards.isEmpty())
                return Arrays.asList(myTrumpCards.get(0));

            // Play whatever
            return Arrays.asList(myHand.get(0));
        }

        Play startingPlay = currentTrick.getPlays().get(0);
        List<Integer> startingCardIds = startingPlay.getCardIds();
        List<Component> startingProfile = game.getProfile(startingPlay.getCardIds());
        Grouping startingGrouping = game.getGrouping(startingPlay.getCardIds());
        Collections.sort(
            startingProfile,
            Comparator.<Component, Integer> comparing(component -> component.getShape().getWidth())
                .thenComparing(component -> component.getShape().getHeight())
                .reversed());

        List<Integer> sameSuitCards = myHandByGrouping.get(startingGrouping);

        // Can I beat it with trump?
        if (sameSuitCards.isEmpty()) {
            List<Integer> trumpCardIds = new ArrayList<>(myHandByGrouping.get(Grouping.TRUMP));
            List<Integer> myCardIds = new ArrayList<>();
            List<Component> profile = game.getProfile(trumpCardIds);
            for (Component startingComponent : startingProfile)
                for (Component component : profile)
                    if (trumpCardIds.containsAll(component.getCardIds())
                            && component.getShape().getWidth() >= startingComponent.getShape().getWidth()
                            && component.getShape().getHeight() >= startingComponent.getShape().getHeight()
                            && component.getMaxRank() > startingComponent.getMaxRank()) {
                        List<Integer> cardIds = component.getCardIds().stream()
                            .sorted(Comparator.comparing(cardId -> Cards.rank(cardsById.get(cardId), trump)))
                            .collect(Collectors.toList());
                        List<Integer> prunedCardIds = new ArrayList<>();
                        for (int i = 0; i < startingComponent.getShape().getWidth(); i++)
                            for (int j = 0; j < startingComponent.getShape().getHeight(); j++)
                                prunedCardIds.add(cardIds.get(j * startingComponent.getShape().getWidth() + i));
                        trumpCardIds.removeAll(prunedCardIds);
                        myCardIds.addAll(prunedCardIds);
                        break;
                    }
            if (myCardIds.size() >= startingCardIds.size())
                return myCardIds;
        }

        // Can I beat it in the same suit?
        List<Component> profile = game.getProfile(sameSuitCards);
        Collections.sort(
            profile,
            Comparator.<Component, Integer> comparing(component -> component.getShape().getWidth())
                .thenComparing(component -> component.getShape().getHeight())
                .reversed());

        if (!sameSuitCards.isEmpty()) {
            List<Integer> sameSuitCardsCopy = new ArrayList<>(sameSuitCards);
            List<Integer> myCardIds = new ArrayList<>();
            for (Component startingComponent : startingProfile)
                for (Component component : profile)
                    if (sameSuitCardsCopy.containsAll(component.getCardIds())
                            && component.getShape().getWidth() >= startingComponent.getShape().getWidth()
                            && component.getShape().getHeight() >= startingComponent.getShape().getHeight()
                            && component.getMaxRank() > startingComponent.getMaxRank()) {
                        List<Integer> cardIds = component.getCardIds().stream()
                            .sorted(Comparator.comparing(cardId -> Cards.rank(cardsById.get(cardId), trump)))
                            .collect(Collectors.toList());
                        List<Integer> prunedCardIds = new ArrayList<>();
                        for (int i = 0; i < startingComponent.getShape().getWidth(); i++)
                            for (int j = 0; j < startingComponent.getShape().getHeight(); j++)
                                prunedCardIds.add(cardIds.get(j * startingComponent.getShape().getWidth() + i));
                        sameSuitCardsCopy.removeAll(prunedCardIds);
                        myCardIds.addAll(prunedCardIds);
                        break;
                    }
            if (myCardIds.size() >= startingCardIds.size())
                return myCardIds;
        }

        // play some random cards
        List<Integer> myCardIds = new ArrayList<>();
        if (!sameSuitCards.isEmpty()) {
            List<Integer> sameSuitCardsCopy = new ArrayList<>(sameSuitCards);
            for (Component startingComponent : startingProfile)
                for (Component component : profile)
                    if (sameSuitCardsCopy.containsAll(component.getCardIds())
                            && component.getShape().getWidth() <= startingComponent.getShape().getWidth()) {
                        List<Integer> cardIds = component.getCardIds().stream()
                            .sorted(Comparator.comparing(cardId -> Cards.rank(cardsById.get(cardId), trump)))
                            .collect(Collectors.toList());
                        sameSuitCardsCopy.removeAll(cardIds);
                        myCardIds.addAll(cardIds);
                    }

            while (myCardIds.size() < startingCardIds.size() && !sameSuitCardsCopy.isEmpty())
                myCardIds.add(sameSuitCardsCopy.remove(0));
        }

        for (int index = 0; myCardIds.size() < startingCardIds.size(); index++) {
            int cardId = myHand.get(index);
            if (!myCardIds.contains(cardId))
                myCardIds.add(cardId);
        }
        return myCardIds.subList(0, startingCardIds.size());
    }
}
