
package io.github.ytung.tractor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import com.google.common.collect.ImmutableList;

import io.github.ytung.tractor.api.Card;
import io.github.ytung.tractor.api.Card.Suit;
import io.github.ytung.tractor.api.Card.Value;

public class Decks {

    public static final int SIZE = 54;

    public static Map<Integer, Card> getCardsById(int numDecks) {
        int cardId = 101;
        Map<Integer, Card> cards = new HashMap<>();
        for (int i = 0; i < numDecks; i++) {
            for (Suit suit : ImmutableList.of(Suit.CLUB, Suit.DIAMOND, Suit.HEART, Suit.SPADE))
                for (Value value : ImmutableList.of(Value.TWO, Value.THREE, Value.FOUR, Value.FIVE, Value.SIX, Value.SEVEN, Value.EIGHT,
                    Value.NINE, Value.TEN, Value.JACK, Value.QUEEN, Value.KING, Value.ACE)) {
                    cards.put(cardId, new Card(cardId, value, suit));
                    cardId++;
                }

            cards.put(cardId, new Card(cardId, Value.SMALL_JOKER, Suit.JOKER));
            cardId++;

            cards.put(cardId, new Card(cardId, Value.BIG_JOKER, Suit.JOKER));
        }
        return cards;
    }

    public static Queue<Integer> shuffle(Map<Integer, Card> cardsById) {
        List<Integer> cardIds = new ArrayList<>(cardsById.keySet());
        Collections.shuffle(cardIds);
        return new ArrayDeque<>(cardIds);
    }

    private Decks() {
    }
}
