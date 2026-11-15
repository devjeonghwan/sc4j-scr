package org.fogofwarlabs.sc4j.scr.client.ui.console;

import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Checks the counting, the 32-bit display and the supply WORD rules of the resource counters.
 */
final class ResourceBarTest {
    private static final int TERRAN = ScrRaceIds.TERRAN;
    private static final int TERRAN_SUPPLY_ICON = 5;
    private static final int MINERAL_ICON = 0;
    private static final int GAS_ICON = 2;
    /**
     * A player slot without a player record.
     */
    private static final int EMPTY_PLAYER = 1;

    private static final int MINERALS = 50;
    /**
     * Resources with the sign bit set: minerals {@code 4294967295} and gas {@code 2147483648} as unsigned DWORDs.
     */
    private static final int MINERALS_WITH_ALL_BITS = -1;
    private static final int GAS_WITH_SIGN_BIT = Integer.MIN_VALUE;
    /**
     * The shown minerals one tick after {@link #MINERALS} became the target of a count of {@code 0}: {@code 50 / 16 + 1}.
     */
    private static final int MINERALS_AFTER_ONE_TICK = 4;
    /**
     * Enough ticks to count from {@code 0} to any 32-bit target.
     */
    private static final int COUNTING_TICKS = 1000;
    /**
     * The ticks counted before the shown player changes.
     */
    private static final int TICKS_BEFORE_PLAYER_CHANGE = 3;

    private static final int SUPPLY_CAP = 400;
    /**
     * A half-supply DWORD whose low WORD is {@code 0}.
     */
    private static final int SUPPLY_WORD_OVERFLOW = 0x10000;
    /**
     * A used half-supply DWORD whose low WORD is {@code 2}, one whole unit.
     */
    private static final int USED_SUPPLY_ABOVE_WORD = 0x10002;
    private static final int PROVIDED_SUPPLY = 16;
    /**
     * A provided half-supply DWORD with the sign bit set, larger than the cap as an unsigned value.
     */
    private static final int PROVIDED_SUPPLY_WITH_SIGN_BIT = 0x80000000;
    /**
     * Used and available half-supply counters that show {@code 3/2}, a shortage.
     */
    private static final int SHORTAGE_USED_SUPPLY = 5;
    private static final int SHORTAGE_PROVIDED_SUPPLY = 4;
    /**
     * Used and available half-supply counters that both show {@code 2}, although the used counter is larger.
     */
    private static final int ROUNDED_USED_SUPPLY = 4;
    private static final int ROUNDED_PROVIDED_SUPPLY = 3;

    /**
     * Returns the text of the counter with an icon, failing when no counter has it.
     *
     * @param counters the counters
     * @param icon     the {@code icons} frame
     * @return the counter's text with color codes
     */
    private static @NotNull String getText(@NotNull List<ResourceBar.Counter> counters, int icon) {
        for (ResourceBar.Counter counter : counters) {
            if (counter.getIcon() == icon) {
                return counter.getText();
            }
        }

        return Assertions.fail("No counter with icon " + icon);
    }

    /**
     * Builds the counters of a Terran player with the given half-supply counters and no resources.
     *
     * @param used     the used half-supply DWORD
     * @param provided the provided half-supply DWORD
     * @param cap      the half-supply cap DWORD
     * @return the counters
     */
    private static @NotNull List<ResourceBar.Counter> readSupplyCounters(int used, int provided, int cap) {
        UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();
        Game game = ResourceGameScenario.createGame(accounting, TERRAN, 0, 0);

        ResourceGameScenario.setSupply(accounting, TERRAN, used, provided, cap);

        return new ResourceBar().update(new ScrGameView(game), ResourceGameScenario.PLAYER, TERRAN);
    }


    @Test
    void update_should_count_minerals_from_zero_after_the_bar_is_created() {
        Game game = ResourceGameScenario.createGame(PlayerProgressTestUtils.createUnitAccounting(), TERRAN, MINERALS, 0);
        ResourceBar bar = new ResourceBar();

        Assertions.assertEquals("0", getText(bar.update(new ScrGameView(game), ResourceGameScenario.PLAYER, TERRAN), MINERAL_ICON));

        bar.tick();

        List<ResourceBar.Counter> counters = bar.update(new ScrGameView(game), ResourceGameScenario.PLAYER, TERRAN);

        Assertions.assertEquals(Integer.toString(MINERALS_AFTER_ONE_TICK), getText(counters, MINERAL_ICON));
    }

    @Test
    void update_should_keep_the_shown_counts_when_the_player_changes() {
        Game game = ResourceGameScenario.createGame(PlayerProgressTestUtils.createUnitAccounting(), TERRAN, MINERALS, 0);
        ResourceBar bar = new ResourceBar();

        bar.update(new ScrGameView(game), ResourceGameScenario.PLAYER, TERRAN);

        for (int tick = 0; tick < TICKS_BEFORE_PLAYER_CHANGE; tick++) {
            bar.tick();
        }

        String shown = getText(bar.update(new ScrGameView(game), ResourceGameScenario.PLAYER, TERRAN), MINERAL_ICON);

        Assertions.assertNotEquals("0", shown);
        Assertions.assertEquals(shown, getText(bar.update(new ScrGameView(game), EMPTY_PLAYER, TERRAN), MINERAL_ICON));
    }

    @Test
    void tick_should_show_resources_with_the_sign_bit_as_negative_counts() {
        Game game = ResourceGameScenario.createGame(PlayerProgressTestUtils.createUnitAccounting(), TERRAN, MINERALS_WITH_ALL_BITS, GAS_WITH_SIGN_BIT);
        ResourceBar bar = new ResourceBar();

        bar.update(new ScrGameView(game), ResourceGameScenario.PLAYER, TERRAN);
        bar.tick();

        Assertions.assertEquals("-1", getText(bar.update(new ScrGameView(game), ResourceGameScenario.PLAYER, TERRAN), MINERAL_ICON));

        for (int tick = 0; tick < COUNTING_TICKS; tick++) {
            bar.tick();
        }

        Assertions.assertEquals("-2147483648", getText(bar.update(new ScrGameView(game), ResourceGameScenario.PLAYER, TERRAN), GAS_ICON));
    }

    @Test
    void update_should_hide_a_supply_race_whose_available_word_is_zero() {
        List<ResourceBar.Counter> counters = readSupplyCounters(0, SUPPLY_WORD_OVERFLOW, SUPPLY_WORD_OVERFLOW);

        Assertions.assertEquals(List.of(GAS_ICON, MINERAL_ICON), counters.stream().map(ResourceBar.Counter::getIcon).toList());
    }

    @Test
    void update_should_show_the_low_supply_word_in_whole_units() {
        List<ResourceBar.Counter> counters = readSupplyCounters(USED_SUPPLY_ABOVE_WORD, PROVIDED_SUPPLY, SUPPLY_CAP);

        Assertions.assertEquals("\u00071\u0001/8", getText(counters, TERRAN_SUPPLY_ICON));
    }

    @Test
    void update_should_take_the_unsigned_minimum_of_provided_supply_and_cap() {
        Assertions.assertEquals("\u00070\u0001/200", getText(readSupplyCounters(0, PROVIDED_SUPPLY_WITH_SIGN_BIT, SUPPLY_CAP), TERRAN_SUPPLY_ICON));
    }

    @Test
    void update_should_use_the_shortage_color_when_shown_used_supply_exceeds_shown_available_supply() {
        String shortage = getText(readSupplyCounters(SHORTAGE_USED_SUPPLY, SHORTAGE_PROVIDED_SUPPLY, SUPPLY_CAP), TERRAN_SUPPLY_ICON);
        String rounded = getText(readSupplyCounters(ROUNDED_USED_SUPPLY, ROUNDED_PROVIDED_SUPPLY, SUPPLY_CAP), TERRAN_SUPPLY_ICON);

        Assertions.assertEquals("\u00063\u0001/2", shortage);
        Assertions.assertEquals("\u00072\u0001/2", rounded);
    }
}
