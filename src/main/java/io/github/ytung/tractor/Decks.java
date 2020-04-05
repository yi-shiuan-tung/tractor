
package io.github.ytung.tractor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

import com.google.common.collect.ImmutableList;

import io.github.ytung.tractor.api.Card;
import io.github.ytung.tractor.api.Card.Suit;
import io.github.ytung.tractor.api.Card.Value;

public class Decks {

    public static final int SIZE = 54;

    public static Queue<Card> generate(int numDecks) {
        int cardId = 101;
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < numDecks; i++) {
            for (Suit suit : ImmutableList.of(Suit.CLUB, Suit.DIAMOND, Suit.HEART, Suit.SPADE))
                for (Value value : ImmutableList.of(Value.TWO, Value.THREE, Value.FOUR, Value.FIVE, Value.SIX, Value.SEVEN, Value.EIGHT,
                    Value.NINE, Value.TEN, Value.JACK, Value.QUEEN, Value.KING, Value.ACE)) {
                    cards.add(new Card(cardId++, value, suit));
                }
            cards.add(new Card(cardId++, Value.SMALL_JOKER, Suit.JOKER));
            cards.add(new Card(cardId++, Value.BIG_JOKER, Suit.JOKER));
        }
        Collections.shuffle(cards);
        return new ArrayDeque<>(cards);
    }

    private Decks() {
    }
}
