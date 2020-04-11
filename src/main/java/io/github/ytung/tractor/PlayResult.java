package io.github.ytung.tractor;

import lombok.Data;

/**
 * An object encapsulating what happened from a single play.
 */
@Data
public class PlayResult {

    private final boolean isTrickComplete;
    private final boolean didFriendJoin;
}
