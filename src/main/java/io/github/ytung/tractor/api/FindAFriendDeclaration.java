
package io.github.ytung.tractor.api;

import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FindAFriendDeclaration {

    private List<Declaration> declarations;

    @Data
    @NoArgsConstructor
    public static class Declaration {

        /**
         * e.g. 1 for the first Big Joker, 2 for the second Big Joker, etc.
         *
         * 0 refers to the "other" Big Joker, and is only valid 2-deck games when the declarer has
         * one of the Jokers.
         */
        private int ordinal;

        private Card.Value value;

        private Card.Suit suit;
    }
}
