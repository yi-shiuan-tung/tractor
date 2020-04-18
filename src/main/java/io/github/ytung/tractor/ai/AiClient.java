package io.github.ytung.tractor.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
import io.github.ytung.tractor.api.Play;
import io.github.ytung.tractor.api.Trick;
import lombok.Data;

@Data
public class AiClient {

    private final String myPlayerId;

    public void processMessage(Game game, OutgoingMessage message, Consumer<IncomingMessage> send) {
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
            request.setConfirmDoesItFly(true);
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

    private List<Integer> play(Game game) {
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
        Collections.sort(startingProfile,
            Comparator.comparing(component -> -component.getShape().getWidth() * component.getShape().getHeight()));

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
                        trumpCardIds.removeAll(component.getCardIds());
                        myCardIds.addAll(component.getCardIds());
                        break;
                    }
            if (myCardIds.size() >= startingCardIds.size())
                return myCardIds.subList(0, startingCardIds.size());
        }

        // Can I beat it in the same suit?
        List<Component> profile = game.getProfile(sameSuitCards);
        Collections.sort(profile,
            Comparator.comparing(component -> -component.getShape().getWidth() * component.getShape().getHeight()));

        if (!sameSuitCards.isEmpty()) {
            List<Integer> sameSuitCardsCopy = new ArrayList<>(sameSuitCards);
            List<Integer> myCardIds = new ArrayList<>();
            for (Component startingComponent : startingProfile)
                for (Component component : profile)
                    if (sameSuitCardsCopy.containsAll(component.getCardIds())
                            && component.getShape().getWidth() >= startingComponent.getShape().getWidth()
                            && component.getShape().getHeight() >= startingComponent.getShape().getHeight()
                            && component.getMaxRank() > startingComponent.getMaxRank()) {
                        sameSuitCardsCopy.removeAll(component.getCardIds());
                        myCardIds.addAll(component.getCardIds());
                        break;
                    }
            if (myCardIds.size() >= startingCardIds.size())
                return myCardIds.subList(0, startingCardIds.size());
        }

        // play some random cards
        List<Integer> myCardIds = new ArrayList<>();
        if (!sameSuitCards.isEmpty()) {
            List<Integer> sameSuitCardsCopy = new ArrayList<>(sameSuitCards);
            for (Component startingComponent : startingProfile) {
                int startingWidth = startingComponent.getShape().getWidth();
                for (Component component : profile)
                    if (sameSuitCardsCopy.containsAll(component.getCardIds())
                            && component.getShape().getWidth() <= startingWidth) {
                        sameSuitCardsCopy.removeAll(component.getCardIds());
                        myCardIds.addAll(component.getCardIds());
                        break;
                    }
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
