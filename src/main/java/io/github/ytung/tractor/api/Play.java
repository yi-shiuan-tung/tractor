package io.github.ytung.tractor.api;

import java.util.List;

import lombok.Data;

/**
 * One or more cards played by a single player in a trick.
 */
@Data
public class Play {

    private final String playerId;
    private final List<Integer> cardIds;
}
