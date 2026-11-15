package org.fogofwarlabs.sc4j.scr.game.test;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.golden.GoldenDigestUtils;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.recording.RecorderStateUtils;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Digests the observable game state with 64-bit FNV-1a.
 * <p>
 * The observable state is what the native recording comparison reads ({@link RecorderStateUtils}): the RNG state, total and source counters, every
 * player's resource and supply values, the unit count, every live unit slot with its recorded fields, and the visible and hidden unit list order. It
 * does not depend on how the Java state is organized, so it stays comparable across structural changes.
 */
public final class StateDigestCheck {
    private static final int MISSING = -1;

    /**
     * Computes the digest of the observable state.
     *
     * @param game the game state
     * @return the digest
     */
    public static long digestObservableState(@NotNull Game game) {
        LcgRandom random = game.getRandom();
        UnitPool pool = game.getUnitSystem().getUnitPool();
        long digest = GoldenDigestUtils.OFFSET_BASIS;

        digest = GoldenDigestUtils.mix(digest, game.getCurrentFrame());
        digest = GoldenDigestUtils.mix(digest, random.getState());
        digest = GoldenDigestUtils.mix(digest, random.getTotalRandomCounts());

        for (int source = 0; source < LcgRandom.SOURCE_COUNT; source++) {
            digest = GoldenDigestUtils.mix(digest, random.getCallCount(source));
        }

        for (int player = 0; player < PlayerTable.PLAYABLE_PLAYER_SLOTS; player++) {
            for (int value : RecorderStateUtils.getPlayerValues(game, player)) {
                digest = GoldenDigestUtils.mix(digest, value);
            }
        }

        if (pool == null) {
            return GoldenDigestUtils.mix(digest, MISSING);
        }

        digest = GoldenDigestUtils.mix(digest, pool.getAllocatedCount());

        for (int index = 1; index <= pool.getCapacity(); index++) {
            if (!RecorderStateUtils.isLive(pool, index)) {
                continue;
            }

            digest = GoldenDigestUtils.mix(digest, index);

            for (int value : RecorderStateUtils.getUnitValues(pool, index)) {
                digest = GoldenDigestUtils.mix(digest, value);
            }
        }

        List<Integer> visibleUnits = RecorderStateUtils.getUnitList(pool, true);

        digest = GoldenDigestUtils.mix(digest, visibleUnits.size());

        for (int index : visibleUnits) {
            digest = GoldenDigestUtils.mix(digest, index);
        }

        List<Integer> hiddenUnits = RecorderStateUtils.getUnitList(pool, false);

        digest = GoldenDigestUtils.mix(digest, hiddenUnits.size());

        for (int index : hiddenUnits) {
            digest = GoldenDigestUtils.mix(digest, index);
        }

        return digest;
    }


    /**
     * Creates a {@link StateDigestCheck} instance.
     */
    private StateDigestCheck() {
    }
}
