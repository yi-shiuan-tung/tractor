
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
         * e.g. 1 for the first of card X, 2 for the second of card X, etc.
         *
         * 0 refers to the "other" card X, and is only valid 2-deck games when the starter has one
         * of the card X.
         */
        private int ordinal;

        private Card.Value value;

        private Card.Suit suit;
    }
}
