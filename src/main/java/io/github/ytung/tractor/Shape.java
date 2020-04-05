package io.github.ytung.tractor;

import lombok.Data;

/**
 * The shape of a set of cards. The height is the number of distinct cards, and the width is the
 * number of copies of each card. For example, a bulldozer 4-4-5-5-6-6 has width 2 and height 3.
 * A single pair K-K has width 2 and height 1.
 */
@Data
public final class Shape {

    final int width;
    final int height;
}
