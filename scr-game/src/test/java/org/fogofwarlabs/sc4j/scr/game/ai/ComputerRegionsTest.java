package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks packed region strengths, limits and decay.
 */
final class ComputerRegionsTest {
    @Test
    void update_should_raise_required_strength_from_air_and_ground_enemy_words_then_decay() {
        ComputerRegions regions = new ComputerRegions(Game.create(null, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0)), 4);

        regions.update(0, 2, 400 | 600 << 16, 0, 0);

        Assertions.assertEquals(300, regions.getRequiredStrength(0, 2, true));
        Assertions.assertEquals(600, regions.getRequiredStrength(0, 2, false));

        regions.update(0, 2, 0, 0, 0);

        Assertions.assertEquals(0, regions.getRequiredStrength(0, 2, true));
        Assertions.assertEquals(300, regions.getRequiredStrength(0, 2, false));
        Assertions.assertEquals(0, regions.getRequiredStrength(1, 2, false));
    }

    @Test
    void update_should_truncate_strength_word_and_apply_cap_before_decay() {
        ComputerRegions regions = new ComputerRegions(Game.create(null, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0)), 2);

        regions.update(0, 1, 65535, 0, 0);

        Assertions.assertEquals(32466, regions.getRequiredStrength(0, 1, true));

        regions.update(0, 1, 65535, 0, 250);

        Assertions.assertEquals(0, regions.getRequiredStrength(0, 1, true));
    }
}
