package org.fogofwarlabs.sc4j.scr.client.ui.observer;

import org.fogofwarlabs.sc4j.scr.client.ui.console.ResourceGameScenario;
import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks the resource counting and the supply totals of the replay statistics rows.
 */
final class ObserverStatisticsTest {
    private static final int ZERG = ScrRaceIds.ZERG;
    private static final int TERRAN = ScrRaceIds.TERRAN;
    private static final @NotNull String @NotNull [] NAMES = {"Player"};
    private static final @NotNull String COMPUTER_NAME = "Computer";
    private static final int @NotNull [] NO_APM = {};
    /**
     * The clock of the first update, and the period of one counting tick.
     */
    private static final long FIRST_UPDATE_NANOSECONDS = 1_000_000_000L;
    private static final long TICK_NANOSECONDS = 20_000_000L;

    private static final int MINERALS = 50;
    /**
     * The shown minerals one tick after {@link #MINERALS} became the target of a count of {@code 0}: {@code 50 / 16 + 1}.
     */
    private static final int MINERALS_AFTER_ONE_TICK = 4;
    /**
     * Gas with the sign bit set, a negative 32-bit count.
     */
    private static final int GAS_WITH_SIGN_BIT = -1;

    private static final int SUPPLY_CAP = 400;
    /**
     * A used half-supply DWORD whose low WORD is {@code 2}, one whole unit.
     */
    private static final int USED_SUPPLY_ABOVE_WORD = 0x10002;
    /**
     * A provided half-supply DWORD with the sign bit set, larger than the cap as an unsigned value.
     */
    private static final int PROVIDED_SUPPLY_WITH_SIGN_BIT = 0x80000000;
    private static final int USED_SUPPLY = 4;
    private static final int PROVIDED_SUPPLY = 16;
    /**
     * The totals: Zerg {@code 1} of {@code 200} and Terran {@code 2} of {@code 8} whole units.
     */
    private static final int TOTAL_USED_SUPPLY = 3;
    private static final int TOTAL_AVAILABLE_SUPPLY = 208;

    @Test
    void update_should_count_resources_from_zero_without_showing_a_negative_count() {
        Game game = ResourceGameScenario.createGame(PlayerProgressTestUtils.createUnitAccounting(), TERRAN, MINERALS, GAS_WITH_SIGN_BIT);
        ObserverStatistics statistics = new ObserverStatistics();

        statistics.reset(new ScrGameView(game), NAMES, COMPUTER_NAME);
        statistics.update(FIRST_UPDATE_NANOSECONDS, NO_APM);

        ObserverStatistics.Row row = statistics.getRows().getFirst();

        Assertions.assertEquals(0, row.getMinerals());
        Assertions.assertEquals(0, row.getGas());

        statistics.update(FIRST_UPDATE_NANOSECONDS + TICK_NANOSECONDS, NO_APM);

        Assertions.assertEquals(MINERALS_AFTER_ONE_TICK, row.getMinerals());
        Assertions.assertEquals(0, row.getGas());
    }

    @Test
    void update_should_add_the_shown_supply_words_of_every_race() {
        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();
        Game game = ResourceGameScenario.createGame(accounting, TERRAN, 0, 0);
        ObserverStatistics statistics = new ObserverStatistics();

        ResourceGameScenario.setSupply(accounting, ZERG, USED_SUPPLY_ABOVE_WORD, PROVIDED_SUPPLY_WITH_SIGN_BIT, SUPPLY_CAP);
        ResourceGameScenario.setSupply(accounting, TERRAN, USED_SUPPLY, PROVIDED_SUPPLY, SUPPLY_CAP);

        statistics.reset(new ScrGameView(game), NAMES, COMPUTER_NAME);
        statistics.update(FIRST_UPDATE_NANOSECONDS, NO_APM);

        ObserverStatistics.Row row = statistics.getRows().getFirst();

        Assertions.assertEquals(TOTAL_USED_SUPPLY, row.getSupplyUsed());
        Assertions.assertEquals(TOTAL_AVAILABLE_SUPPLY, row.getSupplyMaximum());
    }
}
