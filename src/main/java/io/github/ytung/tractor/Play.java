package io.github.ytung.tractor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Iterables;

import io.github.ytung.tractor.Cards.Grouping;
import io.github.ytung.tractor.api.Card;
import lombok.Data;

/**
 * One or more cards played by a single player in a trick.
 */
@Data
public class Play {

    private final String playerId;
    private final List<Card> cards;

    public int numCards() {
        return cards.size();
    }

    public Card.Suit getSuit() {
        return cards.get(0).getSuit();
    }

    public Grouping getGrouping(Card trump) {
        Set<Grouping> groupings = cards.stream()
            .map(card -> Cards.grouping(card, trump))
            .collect(Collectors.toSet());
        return groupings.size() == 1 ? Iterables.getOnlyElement(groupings) : null;
    }

    public boolean isPlayable(Map<String, List<Card>> playerHands) {
        List<Card> hand = new ArrayList<>(playerHands.get(playerId));
        for (Card card : cards)
            if (!hand.remove(card))
                return false;
        return true;
    }

    public List<Component> getProfile(Card trump) {
        if (getGrouping(trump) == null)
            return new ArrayList<>();

        List<Component> profile = cards.stream()
            .distinct()
            .map(card -> new Component(
                new Shape(Collections.frequency(cards, card), 1),
                Cards.rank(card, trump),
                Cards.rank(card, trump)))
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
}
