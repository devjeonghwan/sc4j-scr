package org.fogofwarlabs.sc4j.scr.game.state;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayGameVersion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks version boundaries for producer mismatch, queued orders and detonation timing.
 */
final class GameRulesVersionTest {
    private static final int OLD_BUILD = 0x1fd8;
    private static final int BOUNDARY_BUILD = 0x1fd9;
    private static final int CURRENT_BUILD = 0x1fda;
    private static final int LAST_BUILD_WITHOUT_PRODUCER_MISMATCH = 0x18c5;
    private static final int FIRST_PRODUCER_MISMATCH_BUILD = 0x18c6;
    private static final int RELAXED_CAPACITY_KIND = 5;
    private static final int OTHER_KIND = 4;
    private static final int STRICT_CAPACITY_FLAG = 4;

    private static final int FREE_HIGHLIGHTED_ORDERS = 7;
    private static final int FULL_HIGHLIGHTED_ORDERS = 8;
    private static final int LEGACY_HIGHLIGHTED_ORDERS = 1934;

    private static final int DIE_ORDER = ScrOrderIds.DIE;
    private static final int MOVE_ORDER = ScrOrderIds.MOVE;
    private static final int BURROWING_ORDER = ScrOrderIds.BURROWING;
    private static final int RESET_HARVEST_COLLISION_ORDER = ScrOrderIds.RESET_HARVEST_COLLISION;

    @Test
    void new_game_should_use_running_build_and_strict_capacity_rules() {
        GameRulesVersion version = GameRulesVersion.newGame();

        Assertions.assertFalse(version.isReplay());
        Assertions.assertEquals(13515, version.getBuild());
        Assertions.assertTrue(version.appliesProducerMismatch());
        Assertions.assertTrue(version.allowsOrderAtCapacity(DIE_ORDER));
        Assertions.assertTrue(version.allowsOrderAtCapacity(RESET_HARVEST_COLLISION_ORDER));
        Assertions.assertFalse(version.allowsOrderAtCapacity(MOVE_ORDER));
    }

    @Test
    void replays_without_version_section_skip_producer_mismatch_and_keep_strict_capacity() {
        GameRulesVersion version = GameRulesVersion.forReplay(ReplayGameVersion.absent(), false);

        Assertions.assertTrue(version.isReplay());
        Assertions.assertEquals(0, version.getBuild());
        Assertions.assertFalse(version.appliesProducerMismatch());
        Assertions.assertFalse(version.allowsOrderAtCapacity(MOVE_ORDER));
        Assertions.assertTrue(version.allowsOrderAtCapacity(RESET_HARVEST_COLLISION_ORDER));
    }

    @Test
    void old_build_kind_five_replays_relax_capacity_exception_unless_flag_four_is_set() {
        GameRulesVersion old = GameRulesVersion.forReplay(ReplayGameVersion.of(OLD_BUILD, RELAXED_CAPACITY_KIND, 0), false);

        Assertions.assertTrue(old.allowsOrderAtCapacity(MOVE_ORDER));
        Assertions.assertTrue(old.appliesProducerMismatch());

        GameRulesVersion boundary = GameRulesVersion.forReplay(ReplayGameVersion.of(BOUNDARY_BUILD, RELAXED_CAPACITY_KIND, 0), false);

        Assertions.assertTrue(boundary.allowsOrderAtCapacity(DIE_ORDER));
        Assertions.assertFalse(boundary.allowsOrderAtCapacity(RESET_HARVEST_COLLISION_ORDER));
        Assertions.assertFalse(boundary.allowsOrderAtCapacity(MOVE_ORDER));

        GameRulesVersion current = GameRulesVersion.forReplay(ReplayGameVersion.of(CURRENT_BUILD, RELAXED_CAPACITY_KIND, 0), false);

        Assertions.assertFalse(current.allowsOrderAtCapacity(MOVE_ORDER));
        Assertions.assertTrue(current.allowsOrderAtCapacity(RESET_HARVEST_COLLISION_ORDER));

        GameRulesVersion flagged = GameRulesVersion.forReplay(ReplayGameVersion.of(OLD_BUILD, RELAXED_CAPACITY_KIND, STRICT_CAPACITY_FLAG), false);

        Assertions.assertFalse(flagged.allowsOrderAtCapacity(MOVE_ORDER));

        GameRulesVersion otherKind = GameRulesVersion.forReplay(ReplayGameVersion.of(OLD_BUILD, OTHER_KIND, 0), false);

        Assertions.assertFalse(otherKind.allowsOrderAtCapacity(MOVE_ORDER));

        GameRulesVersion earlyBuild = GameRulesVersion.forReplay(ReplayGameVersion.of(LAST_BUILD_WITHOUT_PRODUCER_MISMATCH, 0, 0), false);

        Assertions.assertFalse(earlyBuild.appliesProducerMismatch());
        Assertions.assertTrue(GameRulesVersion.forReplay(ReplayGameVersion.of(FIRST_PRODUCER_MISMATCH_BUILD, 0, 0), false).appliesProducerMismatch());
    }

    @Test
    void appends_order_should_test_capacity_for_new_games_and_remastered_replays_only() {
        GameRulesVersion newGame = GameRulesVersion.newGame();

        Assertions.assertTrue(newGame.appendsOrder(false, FREE_HIGHLIGHTED_ORDERS, MOVE_ORDER));
        Assertions.assertFalse(newGame.appendsOrder(false, FULL_HIGHLIGHTED_ORDERS, MOVE_ORDER));
        Assertions.assertFalse(newGame.appendsOrder(true, 0, MOVE_ORDER));
        Assertions.assertTrue(newGame.appendsOrder(true, FULL_HIGHLIGHTED_ORDERS, DIE_ORDER));
        Assertions.assertTrue(newGame.appendsOrder(true, FULL_HIGHLIGHTED_ORDERS, RESET_HARVEST_COLLISION_ORDER));

        GameRulesVersion remasteredReplay = GameRulesVersion.forReplay(ReplayGameVersion.absent(), false);

        Assertions.assertFalse(remasteredReplay.isLegacyReplay());
        Assertions.assertFalse(remasteredReplay.appendsOrder(false, FULL_HIGHLIGHTED_ORDERS, BURROWING_ORDER));

        GameRulesVersion olderRemasteredReplay = GameRulesVersion.forReplay(ReplayGameVersion.of(OLD_BUILD, RELAXED_CAPACITY_KIND, 0), false);

        Assertions.assertTrue(olderRemasteredReplay.appendsOrder(true, FULL_HIGHLIGHTED_ORDERS, BURROWING_ORDER));

        GameRulesVersion legacy = GameRulesVersion.forReplay(ReplayGameVersion.absent(), true);

        Assertions.assertTrue(legacy.isLegacyReplay());
        Assertions.assertTrue(legacy.appendsOrder(true, LEGACY_HIGHLIGHTED_ORDERS, BURROWING_ORDER));
        Assertions.assertFalse(legacy.appliesProducerMismatch());
    }

    @Test
    void detonates_on_arrival_should_hold_outside_legacy_replays() {
        Assertions.assertTrue(GameRulesVersion.newGame().detonatesOnArrival());
        Assertions.assertTrue(GameRulesVersion.forReplay(ReplayGameVersion.absent(), false).detonatesOnArrival());
        Assertions.assertFalse(GameRulesVersion.forReplay(ReplayGameVersion.absent(), true).detonatesOnArrival());
    }
}
