package io.github.ytung.tractor.ai;

import java.util.Collection;

import io.github.ytung.tractor.Game;

public interface AiClient {

    /**
     * Given a game in the draw phase where you have some cards in hand, return the cardIds that you
     * wish to declare, or return null if you do not wish to declare (yet).
     */
    Collection<Integer> declare(String myPlayerId, Game game);

    /**
     * Given a game in the make kitty phase and you are about to make the kitty, return the cardIds
     * for the kitty you wish to make.
     */
    Collection<Integer> makeKitty(String myPlayerId, Game game);

    /**
     * Given a game in the play phase and you are about to play, return the cardIds that you wish to
     * play.
     */
    Collection<Integer> play(String myPlayerId, Game game);
}
