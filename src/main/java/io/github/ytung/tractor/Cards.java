
package io.github.ytung.tractor;

import io.github.ytung.tractor.api.Card;
import io.github.ytung.tractor.api.Card.Suit;

public class Cards {

    public enum Grouping {
        CLUB, DIAMOND, HEART, SPADE, TRUMP
    }

    /**
     * Get the suit of a card, or TRUMP if the card is a trump in this round.
     */
    public static Grouping grouping(Card card, Card trump) {
        if (trump != null && (card.getValue() == trump.getValue() || card.getSuit() == Suit.JOKER || card.getSuit() == trump.getSuit()))
            return Grouping.TRUMP;
        else
            return Grouping.valueOf(card.getSuit().name());
    }

    /**
     * Returns a rank for the card such that consecutive cards (for counting tractors) have
     * consecutive ranks. The 12 normal cards are 0-11, the normal trump value is 12, the big trump
     * value is 13, the small joker is 14, and the big joker is 15.
     */
    public static int rank(Card card, Card trump) {
        if (card.getSuit() == Card.Suit.JOKER)
            return card.getValue().ordinal() + 1;
        else if (card.getValue() == trump.getValue())
            return Card.Value.SMALL_JOKER.ordinal() - (card.getSuit() == trump.getSuit() ? 0 : 1);
        else if (card.getValue().ordinal() > trump.getValue().ordinal())
            return card.getValue().ordinal() - 1;
        else
            return card.getValue().ordinal();
    }

    private Cards() {
    }
}
