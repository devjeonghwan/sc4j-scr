package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks the budgets, the spell countdown, the nuke rate gate and the stored strength limit of a computer player.
 */
final class ComputerPlayerTest {
    @Test
    void credit_budgets_should_raise_the_budgets_at_once() {
        ComputerPlayer player = new ComputerPlayer(0, 98, 0);

        player.beginUpdate(98, 0);
        player.creditBudgets(8, 0);

        Assertions.assertEquals(106, player.getMineralBudget());

        player.creditBudgets(0, 8);

        Assertions.assertEquals(8, player.getGasBudget());
    }

    @Test
    void begin_update_should_count_the_spell_countdown_down() {
        ComputerPlayer player = new ComputerPlayer(0, 0, 0);

        Assertions.assertEquals(0, player.getSpellCountdown());

        player.setSpellCountdown(1);

        Assertions.assertEquals(1, player.getSpellCountdown());

        player.beginUpdate(0, 0);

        Assertions.assertEquals(0, player.getSpellCountdown(), "Each player update counts it down.");
    }

    @RequiresInstallation
    @Test
    void is_nuke_ready_should_gate_by_rate_in_minutes_since_last_nuke() {
        ComputerPlayer player = new ComputerPlayer(0, 0, 0);

        Assertions.assertTrue(player.isNukeReady(0), "a fresh record has rate zero and clock zero");

        player.setNukeRate(5);

        Assertions.assertFalse(player.isNukeReady(299));
        Assertions.assertTrue(player.isNukeReady(300));

        player.setLastNukeClock(1000 + 1);

        Assertions.assertFalse(player.isNukeReady(1300));
        Assertions.assertTrue(player.isNukeReady(1301));
    }

    @Test
    void set_strength_limit_should_store_word_that_caps_required_strength() {
        ComputerPlayer player = new ComputerPlayer(0, 0, 0);

        Assertions.assertEquals(0, player.getStrengthLimit(), "a fresh record has no limit");

        player.setStrengthLimit(0x10000 | 700);

        Assertions.assertEquals(700, player.getStrengthLimit(), "the strength limit stores a WORD");

        ComputerRegions regions = new ComputerRegions(Game.create(null, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0)), 2);

        regions.update(0, 1, 800 << 16, 0, player.getStrengthLimit());

        // 800 * 1.5 = 1200 is capped to 700 before the non-campaign decay of 300.
        Assertions.assertEquals(400, regions.getRequiredStrength(0, 1, false));
    }
}
