package tractorio.github.ytung.tractor;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

import io.github.ytung.tractor.Game;
import io.github.ytung.tractor.api.Card;
import io.github.ytung.tractor.api.Card.Suit;
import io.github.ytung.tractor.api.Card.Value;
import io.github.ytung.tractor.api.Play;
import io.github.ytung.tractor.api.Trick;

class GameTests {

    @Test
    void testWinningPlayerId_trumpPair_beatsTwoSingles() {
        Game game = spy(new Game());

        game.setCardsById(ImmutableMap.<Integer, Card>builder()
            .put(1, new Card(Value.ACE, Suit.CLUB))
            .put(2, new Card(Value.KING, Suit.CLUB))
            .put(3, new Card(Value.TWO, Suit.SPADE))
            .put(4, new Card(Value.TWO, Suit.SPADE))
            .build());
        when(game.getCurrentTrump()).thenReturn(new Card(Value.TWO, Suit.SPADE));

        Trick trick = new Trick("p1");
        trick.getPlays().add(new Play("p1", asList(1, 2)));
        trick.getPlays().add(new Play("p2", asList(3, 4)));

        // p2 trumps the special play with a pair
        assertThat(game.winningPlayerId(trick)).isEqualTo("p2");
    }

    @Test
    void testWinningPlayerId_smallerPair_doesNotBeatTopSingles() {
        Game game = spy(new Game());

        game.setCardsById(ImmutableMap.<Integer, Card>builder()
                .put(1, new Card(Value.ACE, Suit.HEART))
                .put(2, new Card(Value.KING, Suit.HEART))
                .put(3, new Card(Value.SIX, Suit.HEART))
                .put(4, new Card(Value.SIX, Suit.HEART))
                .build());
        when(game.getCurrentTrump()).thenReturn(new Card(Value.EIGHT, Suit.SPADE));

        Trick trick = new Trick("p1");
        trick.getPlays().add(new Play("p1", asList(1, 2)));
        trick.getPlays().add(new Play("p2", asList(3, 4)));

        // p2 may have a pair, but that doesn't beat the rank of the starting play
        assertThat(game.winningPlayerId(trick)).isEqualTo("p1");
    }

    @Test
    void testWinningPlayerId_mustBeatHighestCard() {
        Game game = spy(new Game());

        game.setCardsById(ImmutableMap.<Integer, Card>builder()
                .put(1, new Card(Value.KING, Suit.CLUB))
                .put(2, new Card(Value.ACE, Suit.CLUB))
                .put(3, new Card(Value.THREE, Suit.SPADE))
                .put(4, new Card(Value.BIG_JOKER, Suit.JOKER))
                .put(5, new Card(Value.FOUR, Suit.SPADE))
                .put(6, new Card(Value.FIVE, Suit.SPADE))
                .build());
        when(game.getCurrentTrump()).thenReturn(new Card(Value.TWO, Suit.SPADE));

        Trick trick = new Trick("p1");
        trick.getPlays().add(new Play("p1", asList(1, 2)));
        trick.getPlays().add(new Play("p2", asList(3, 4)));
        trick.getPlays().add(new Play("p3", asList(5, 6)));

        // p2 should win with the highest trump
        assertThat(game.winningPlayerId(trick)).isEqualTo("p2");
    }
}
