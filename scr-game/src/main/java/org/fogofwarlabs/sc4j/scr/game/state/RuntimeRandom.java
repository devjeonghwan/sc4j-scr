package org.fogofwarlabs.sc4j.scr.game.state;

/**
 * Holds the one C runtime random generator ({@code srand}/{@code rand}) that a game and its interface share, distinct from
 * the simulation generator {@link org.fogofwarlabs.sc4j.scr.core.LcgRandom}.
 * <p>
 * The start-up draws, the creep tile variants, the player-list orders, the cheat input and the unit responses all draw
 * from and reseed this one state in the order they run, so a reseed or a draw by one of them changes what the next one
 * draws. The state is local to this client: it is not synchronized and replays do not record it.
 */
public final class RuntimeRandom {
    /**
     * The state of a generator that nothing has seeded yet.
     */
    public static final int DEFAULT_SEED = 1;

    private static final int HASH_MULTIPLIER = 31;

    private int state;
    private int drawCount;

    /**
     * Creates a {@link RuntimeRandom} instance seeded as {@link #seed} does.
     *
     * @param seed the seed
     */
    public RuntimeRandom(int seed) {
        this.state     = seed;
        this.drawCount = 0;
    }

    /**
     * @return the state the last seed or draw left
     */
    public int getState() {
        return this.state;
    }

    /**
     * @return the number of draws since this generator was created; a reseed keeps it
     */
    public int getDrawCount() {
        return this.drawCount;
    }

    /**
     * Seeds the generator: the next draw advances from {@code seed}.
     *
     * @param seed the seed
     */
    public void seed(int seed) {
        this.state = seed;
    }

    /**
     * Draws the next value, advancing the state once ({@link RuntimeRandomUtils#advance}).
     *
     * @return the drawn value, {@code 0..0x7FFF}
     */
    public int draw() {
        this.state = RuntimeRandomUtils.advance(this.state);
        this.drawCount++;

        return RuntimeRandomUtils.getValue(this.state);
    }

    /**
     * Folds the state and the draw count into a state hash.
     *
     * @param hash the preceding state hash
     * @return the hash including the state and the draw count
     */
    public int computeStateHash(int hash) {
        hash = HASH_MULTIPLIER * hash + this.state;
        hash = HASH_MULTIPLIER * hash + this.drawCount;

        return hash;
    }
}
