package org.fogofwarlabs.sc4j.scr.client.ui.console;

import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds resource counters from right to left: available race supplies, gas, then minerals.
 * <p>
 * Mineral and gas counts are signed 32-bit values that start at {@code 0} when the bar is created and approach their current targets on each counting
 * tick ({@link #calculateShownValue}). Supply shows the low WORD of the used and available half-supply counters rounded up to whole units
 * ({@link #calculateShownSupply}), skips a race whose available WORD is {@code 0}, and changes its color code when the shown used supply exceeds the
 * shown available supply.
 */
public final class ResourceBar {
    /**
     * The period of the counting timer in milliseconds.
     */
    public static final int TICK_INTERVAL = 20;

    /**
     * The minerals icon frame of the resource icon sheet.
     */
    static final int MINERAL_ICON = 0;
    /**
     * The gas icon frame of the Zerg in the resource icon sheet; Terran and Protoss follow.
     */
    static final int FIRST_GAS_ICON = 1;

    private static final int SMOOTHING_DIVISOR = 16;
    /**
     * The low WORD of a half-supply counter, the part the supply counters show.
     */
    private static final int SUPPLY_WORD_MASK = 0xFFFF;
    private static final int SHORTAGE_COLOR = 6;
    private static final int AVAILABLE_COLOR = 7;
    private static final int FIRST_SUPPLY_ICON = 4;
    /**
     * The {@code statres} control of the first counter; the others follow leftwards.
     */
    private static final int FIRST_COUNTER_CONTROL = 1;
    /**
     * The text between the used and the available supply: the default color code, then the slash.
     */
    private static final @NotNull String SUPPLY_SEPARATOR = "\u0001/";

    /**
     * One shown counter.
     */
    public static final class Counter {
        private final int control;
        private final int icon;
        private final @NotNull String text;

        /**
         * Creates a {@link Counter} instance.
         *
         * @param control the {@code statres} control id
         * @param icon    the {@code icons} frame
         * @param text    the text with color codes
         */
        Counter(int control, int icon, @NotNull String text) {
            this.control = control;
            this.icon    = icon;
            this.text    = text;
        }

        /**
         * @return the {@code statres} control id
         */
        public int getControlIdentifier() {
            return this.control;
        }

        /**
         * @return the {@code icons} frame: {@code 0} minerals, {@code 1..3} gas, {@code 4..6} supply (Zerg, Terran, Protoss)
         */
        public int getIcon() {
            return this.icon;
        }

        /**
         * @return the text with color codes
         */
        public @NotNull String getText() {
            return this.text;
        }
    }


    /**
     * Moves a shown resource count one counting tick toward its target.
     * <p>
     * The difference wraps as a 32-bit value. A non-zero difference moves the count by the difference divided by {@code 16}, truncated toward zero,
     * plus one step in the direction of the difference.
     *
     * @param shown  the shown count
     * @param target the target count
     * @return the shown count after the tick
     */
    public static int calculateShownValue(int shown, int target) {
        int delta = target - shown;

        if (delta == 0) {
            return shown;
        }

        return shown + delta / SMOOTHING_DIVISOR + Integer.signum(delta);
    }

    /**
     * Converts a half-supply counter to its shown supply: the low WORD of the counter, rounded up to whole units.
     *
     * @param halfSupply the half-supply DWORD
     * @return the shown supply
     */
    public static int calculateShownSupply(int halfSupply) {
        return ((halfSupply & SUPPLY_WORD_MASK) + 1) >> 1;
    }


    private int targetMinerals;
    private int targetGas;

    private int shownMinerals;
    private int shownGas;

    /**
     * Creates a {@link ResourceBar} instance whose shown minerals and gas start at {@code 0}.
     */
    public ResourceBar() {
        this.targetMinerals = 0;
        this.targetGas      = 0;

        this.shownMinerals = 0;
        this.shownGas      = 0;
    }

    /**
     * Advances the shown mineral and gas counts by one interface-timer tick, at most once per pump. Each count approaches its last target
     * as {@link #calculateShownValue} does.
     */
    public void tick() {
        this.shownMinerals = calculateShownValue(this.shownMinerals, this.targetMinerals);
        this.shownGas      = calculateShownValue(this.shownGas, this.targetGas);
    }

    /**
     * Reads the player's resources as the counting targets and builds the counters.
     * <p>
     * A change of the player keeps the shown counts, so they count toward the new player's resources.
     *
     * @param view   the game view
     * @param player the player whose resources are shown
     * @param race   the console race: {@code 0} Zerg, {@code 1} Terran, {@code 2} Protoss
     * @return the counters from control {@code 1} leftwards
     */
    public @NotNull List<Counter> update(@NotNull ScrGameView view, int player, int race) {
        Player record = player >= 0 && player < PlayerTable.PLAYER_SLOTS ? view.getPlayerTable().getPlayer(player) : null;
        UnitAccounting accounting = view.getPlayerTable().getUnitAccounting();
        List<Counter> counters = new ArrayList<>();
        int control = FIRST_COUNTER_CONTROL;

        this.targetMinerals = record == null ? 0 : record.getMinerals();
        this.targetGas      = record == null ? 0 : record.getGas();

        for (int supplyRace = 0; supplyRace < ScrRaceIds.PLAYABLE_COUNT && accounting != null && player >= 0 &&
                                 player < PlayerTable.PLAYER_SLOTS; supplyRace++) {
            int counterIndex = supplyRace * PlayerTable.PLAYER_SLOTS + player;
            int available = accounting.getSupplyAvailable(counterIndex);

            if ((available & SUPPLY_WORD_MASK) == 0) {
                continue;
            }

            int shownUsed = calculateShownSupply(accounting.getSupplyUsed(counterIndex));
            int shownAvailable = calculateShownSupply(available);
            char color = shownAvailable < shownUsed ? (char) SHORTAGE_COLOR : (char) AVAILABLE_COLOR;
            String text = color + Integer.toString(shownUsed) + SUPPLY_SEPARATOR + shownAvailable;

            counters.add(new Counter(control++, FIRST_SUPPLY_ICON + supplyRace, text));
        }

        counters.add(new Counter(control++, FIRST_GAS_ICON + Math.clamp(race, 0, ScrRaceIds.PLAYABLE_COUNT - 1), Integer.toString(this.shownGas)));
        counters.add(new Counter(control, MINERAL_ICON, Integer.toString(this.shownMinerals)));

        return counters;
    }
}
