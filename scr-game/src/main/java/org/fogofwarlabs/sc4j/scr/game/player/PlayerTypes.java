package org.fogofwarlabs.sc4j.scr.game.player;

/**
 * Names the player type byte of a player slot ({@link Player#getType()}).
 */
public final class PlayerTypes {
    /**
     * An empty slot.
     */
    public static final int NONE = 0;
    /**
     * A computer player in the game.
     */
    public static final int COMPUTER = 1;
    /**
     * A human player in the game.
     */
    public static final int HUMAN = 2;
    /**
     * A rescuable player that stays passive.
     */
    public static final int RESCUE_PASSIVE = 3;
    /**
     * A rescuable player that acts.
     */
    public static final int RESCUE_ACTIVE = 4;
    /**
     * A lobby slot open to a computer.
     */
    public static final int EITHER_PREFER_COMPUTER = 5;
    /**
     * A lobby slot open to a human.
     */
    public static final int EITHER_PREFER_HUMAN = 6;
    /**
     * A neutral player.
     */
    public static final int NEUTRAL = 7;
    /**
     * A closed lobby slot.
     */
    public static final int CLOSED = 8;
    /**
     * An observer.
     */
    public static final int OBSERVER = 9;
    /**
     * A human player who has left.
     */
    public static final int PLAYER_LEFT = 10;
    /**
     * A computer player that has left.
     */
    public static final int COMPUTER_LEFT = 11;

    private PlayerTypes() {
    }
}
