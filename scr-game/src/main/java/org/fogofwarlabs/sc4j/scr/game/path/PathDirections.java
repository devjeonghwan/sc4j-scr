package org.fogofwarlabs.sc4j.scr.game.path;

/**
 * The bits of the direction BYTE of the local path search ({@link PathObstacleCollector} candidates and {@link LocalPathSearch} nodes): the four
 * diagonal quadrants, then the four cardinal directions ({@code CARDINAL_UP << direction} for contour direction {@code 0} up, {@code 1} right,
 * {@code 2} down, {@code 3} left).
 */
final class PathDirections {
    static final int NORTHEAST = 0x01;
    static final int SOUTHEAST = 0x02;
    static final int SOUTHWEST = 0x04;
    static final int NORTHWEST = 0x08;
    static final int CARDINAL_UP = 0x10;
    static final int CARDINAL_RIGHT = 0x20;
    static final int CARDINAL_DOWN = 0x40;
    static final int CARDINAL_LEFT = 0x80;
    static final int ALL = 0xFF;

    private PathDirections() {
    }
}
