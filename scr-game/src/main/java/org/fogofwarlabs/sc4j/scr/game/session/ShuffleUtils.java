package org.fogofwarlabs.sc4j.scr.game.session;

import org.fogofwarlabs.sc4j.scr.game.state.RuntimeRandom;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Orders the player lists of the game's dialogs (the waiting-for-players list, the replay vision, alliance and messaging
 * dialogs) by the game seed: the game's C runtime generator ({@link org.fogofwarlabs.sc4j.scr.game.state.Game#getRuntimeRandom})
 * is reseeded with the game seed and draws one remaining entry at a time. The generator keeps the state the draws leave,
 * so a shuffle changes what its other users draw next; the game's random generator does not advance.
 */
public final class ShuffleUtils {
    /**
     * Shuffles the entries: the generator is reseeded with the seed, then each draw takes the remaining entry at the drawn
     * value modulo the remaining count.
     *
     * @param <T>           the entry type
     * @param entries       the entries in slot order, unchanged
     * @param seed          the game seed
     * @param runtimeRandom the game's C runtime generator, reseeded and advanced once per entry
     * @return the shuffled entries
     */
    public static <T> @NotNull List<T> shuffle(@NotNull List<T> entries, int seed, @NotNull RuntimeRandom runtimeRandom) {
        List<T> remaining = new ArrayList<>(entries);
        List<T> shuffled = new ArrayList<>(entries.size());

        runtimeRandom.seed(seed);

        for (int count = remaining.size(); count >= 1; count--) {
            int value = runtimeRandom.draw();

            shuffled.add(remaining.remove(value % count));
        }

        return shuffled;
    }

    private ShuffleUtils() {
    }
}
