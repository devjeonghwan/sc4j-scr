package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks attack-group insertion, release and expiration.
 */
final class ComputerAttackGroupTest {
    @Test
    void add_attack_units_should_append_after_used_entries_up_to_sixty_three() {
        ComputerPlayer player = new ComputerPlayer(0, 0, 0);

        player.addAttackUnits(2, ScrUnitIds.ZERG_ZERGLING);
        player.addAttackUnits(1, ScrUnitIds.ZERG_HYDRALISK);

        short[] types = player.getAttackTypes();

        Assertions.assertEquals(38, types[0]);
        Assertions.assertEquals(38, types[1]);
        Assertions.assertEquals(39, types[2]);
        Assertions.assertEquals(0, types[3]);

        player.addAttackUnits(ComputerPlayer.ATTACK_GROUP_LIMIT, ScrUnitIds.ZERG_ZERGLING);

        Assertions.assertEquals(0, types[3], "a count of 63 or more is ignored");

        player.addAttackUnits(62, ScrUnitIds.ZERG_MUTALISK);

        Assertions.assertEquals(44, types[3]);
        Assertions.assertEquals(44, types[62]);
        Assertions.assertEquals(0, types[63], "entry 63 stays the terminator");

        player.addAttackUnits(1, ScrUnitIds.ZERG_MUTALISK);

        Assertions.assertEquals(0, types[63]);
    }

    @Test
    void release_attack_unit_should_release_first_match_counting_siege_mode_as_tank_mode() {
        ComputerPlayer player = new ComputerPlayer(0, 0, 0);

        player.addAttackUnits(2, ScrUnitIds.TERRAN_SIEGE_TANK_TANK_MODE);
        player.releaseAttackUnit(ScrUnitIds.TERRAN_SIEGE_TANK_SIEGE_MODE);

        short[] types = player.getAttackTypes();

        Assertions.assertEquals(0xE5, types[0]);
        Assertions.assertEquals(6, types[1]);

        player.releaseAttackUnit(ScrUnitIds.TERRAN_SCV);

        Assertions.assertEquals(6, types[1], "an absent type changes nothing");
    }

    @Test
    void clear_attack_should_clear_group_and_keep_clock_unless_requested() {
        ComputerPlayer player = new ComputerPlayer(0, 0, 0);

        player.addAttackUnits(3, 0);
        player.setAttackRegion(41, false, 100);

        Assertions.assertEquals(42, player.getAttackRegion());

        player.clearAttack(false);

        Assertions.assertEquals(0, player.getAttackTypes()[0]);
        Assertions.assertEquals(0, player.getAttackRegion());
        Assertions.assertFalse(player.isAttackExpired(219));
        Assertions.assertTrue(player.isAttackExpired(220), "the clock kept its start of 100");

        player.setAttackRegion(3, false, 300);
        player.clearAttack(true);

        Assertions.assertFalse(player.isAttackExpired(100000), "a cleared clock never expires");
    }

    @Test
    void set_attack_region_should_start_clock_once_unless_forced_and_expire_in_120_ticks() {
        ComputerPlayer player = new ComputerPlayer(0, 0, 0);

        player.setAttackRegion(5, false, 1000);
        player.setAttackRegion(6, false, 1100);

        Assertions.assertFalse(player.isAttackExpired(1119));
        Assertions.assertTrue(player.isAttackExpired(1120));
        Assertions.assertFalse(player.isAttackExpired(5000), "expiry clears the clock");

        player.setAttackRegion(6, false, 2000);
        player.setAttackRegion(6, true, 2050);

        Assertions.assertFalse(player.isAttackExpired(2169));
        Assertions.assertTrue(player.isAttackExpired(2170));
    }

    @Test
    void is_attack_expired_should_use_longer_limit_for_campaign_player() {
        ComputerPlayer player = new ComputerPlayer(0, 0, 0);

        player.setFlags(player.getFlags() | ComputerPlayer.FLAG_CAMPAIGN);
        player.setAttackRegion(1, true, 0x10000);

        Assertions.assertFalse(player.isAttackExpired(0x10000 + 0xB3));
        Assertions.assertTrue(player.isAttackExpired(0x10000 + 0xB4));
    }

    @Test
    void set_attack_delay_should_make_clock_expire_after_delay() {
        ComputerPlayer player = new ComputerPlayer(0, 0, 0);

        player.setAttackDelay(0, 500);

        Assertions.assertFalse(player.isAttackExpired(459), "an unset clock starts 160 ticks back");
        Assertions.assertTrue(player.isAttackExpired(460));

        player.setAttackRegion(1, true, 1000);
        player.setAttackDelay(0, 2000);

        Assertions.assertTrue(player.isAttackExpired(1120), "a running clock keeps its start without an explicit delay");

        player.setAttackDelay(50, 3000);

        Assertions.assertFalse(player.isAttackExpired(2989));
        Assertions.assertTrue(player.isAttackExpired(2990));
    }
}
