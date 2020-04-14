package io.github.ytung.tractor;

import java.util.List;

import lombok.Data;

/**
 * A single component of a play. Almost all plays have a single component, but does-it-fly plays can
 * have more than one component. For example, A-6-6-5-5 has two components, {shape: [1, 1], minRank:
 * 'A', maxRank: 'A'} and {shape: [2, 2], minRank: '5', maxRank: '6'}. {@link Cards#rank} is used to
 * compute the rank.
 */
@Data
public final class Component {

    final Shape shape;
    final int minRank;
    final int maxRank;
    final List<Integer> cardIds;
}
