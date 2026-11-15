package org.fogofwarlabs.sc4j.scr.client.ui.observer;

import org.fogofwarlabs.sc4j.scr.client.ui.console.ResourceBar;
import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects replay statistics for human and computer player slots in slot order.
 * <p>
 * Mineral and gas displays start at {@code 0} for a new game and approach the current values at the counting tick rate
 * ({@link ResourceBar#calculateShownValue}), never showing a negative count. Supply totals add the shown supply of each
 * race ({@link ResourceBar#calculateShownSupply}). Worker counts follow the player race, and APM comes from {@link ApmTracker}.
 */
public final class ObserverStatistics {
    private static final int MAXIMUM_CATCHUP_TICKS = 50;
    private static final long TICK_NANOSECONDS = 20_000_000L;
    private static final int @NotNull [] WORKERS = {ScrUnitIds.ZERG_DRONE, ScrUnitIds.TERRAN_SCV, ScrUnitIds.PROTOSS_PROBE};

    /**
     * One player row.
     */
    public static final class Row {
        private final int player;
        private final @NotNull String name;
        private final int race;
        private final int color;

        private int minerals;
        private int gas;
        private int workers;
        private int supplyUsed;
        private int supplyMaximum;
        private int apm;

        /**
         * Creates a {@link Row} instance with no shown value.
         *
         * @param player the player slot
         * @param name   the shown name
         * @param race   the race
         * @param color  the player's color slot
         */
        Row(int player, @NotNull String name, int race, int color) {
            this.player = player;
            this.name   = name;
            this.race   = race;
            this.color  = color;

            this.minerals      = 0;
            this.gas           = 0;
            this.workers       = 0;
            this.supplyUsed    = 0;
            this.supplyMaximum = 0;
            this.apm           = 0;
        }

        /**
         * @return the player slot
         */
        public int getPlayer() {
            return this.player;
        }

        /**
         * @return the shown name
         */
        public @NotNull String getName() {
            return this.name;
        }

        /**
         * @return the race: {@code 0} Zerg, {@code 1} Terran, {@code 2} Protoss
         */
        public int getRace() {
            return this.race;
        }

        /**
         * @return the player's color slot
         */
        public int getColor() {
            return this.color;
        }

        /**
         * @return the shown minerals
         */
        public int getMinerals() {
            return this.minerals;
        }

        /**
         * @return the shown gas
         */
        public int getGas() {
            return this.gas;
        }

        /**
         * @return the completed workers of the player's race
         */
        public int getWorkers() {
            return this.workers;
        }

        /**
         * @return the used supply summed over the races
         */
        public int getSupplyUsed() {
            return this.supplyUsed;
        }

        /**
         * @return the available supply summed over the races
         */
        public int getSupplyMaximum() {
            return this.supplyMaximum;
        }

        /**
         * @return the actions per minute
         */
        public int getApm() {
            return this.apm;
        }
    }

    private final @NotNull List<Row> rows;
    private @Nullable ScrGameView view;
    private long lastTick;

    /**
     * Creates an {@link ObserverStatistics} instance without rows.
     */
    public ObserverStatistics() {
        this.rows     = new ArrayList<>();
        this.view     = null;
        this.lastTick = 0;
    }

    /**
     * @return the game view the rows belong to, or {@code null} before {@link #reset}
     */
    public @Nullable ScrGameView getView() {
        return this.view;
    }

    /**
     * @return the rows in slot order
     */
    public @NotNull List<Row> getRows() {
        return this.rows;
    }


    /**
     * Rebuilds the rows for a game.
     *
     * @param view         the game view
     * @param names        the player names by slot (from the replay header), {@code null} entries for none
     * @param computerName the name shown for computer players ({@code gluAll slotCOMPUTER})
     */
    public void reset(@NotNull ScrGameView view, @Nullable String @NotNull [] names, @NotNull String computerName) {
        this.rows.clear();

        this.view = view;

        for (int player = 0; player < PlayerTable.PLAYABLE_PLAYER_SLOTS; player++) {
            Player record = view.getPlayerTable().getPlayer(player);

            if (record == null || record.getType() != PlayerTypes.COMPUTER && record.getType() != PlayerTypes.HUMAN) {
                continue;
            }

            String name = record.getType() == PlayerTypes.COMPUTER ? computerName
                                                                   : names.length > player && names[player] != null ? names[player]
                                                                                                                    : "";

            this.rows.add(new Row(player, name, record.getRace(), record.getColor()));
        }

        this.lastTick = 0;
    }

    /**
     * Updates the values.
     *
     * @param now         a monotonic clock in nanoseconds
     * @param apmOfPlayer the APM of each player slot
     */
    public void update(long now, int @NotNull [] apmOfPlayer) {
        ScrGameView view = this.view;

        if (view == null) {
            return;
        }

        UnitAccounting accounting = view.getPlayerTable().getUnitAccounting();
        int ticks = this.lastTick == 0 ? 0 : (int) Math.min(MAXIMUM_CATCHUP_TICKS, (now - this.lastTick) / TICK_NANOSECONDS);

        if (this.lastTick == 0 || ticks > 0) {
            this.lastTick = this.lastTick == 0 ? now : this.lastTick + ticks * TICK_NANOSECONDS;
        }

        for (Row row : this.rows) {
            Player record = view.getPlayerTable().getPlayer(row.player);
            int minerals = record == null ? 0 : record.getMinerals();
            int gas = record == null ? 0 : record.getGas();

            for (int tick = 0; tick < ticks; tick++) {
                row.minerals = Math.max(0, ResourceBar.calculateShownValue(row.minerals, minerals));
                row.gas      = Math.max(0, ResourceBar.calculateShownValue(row.gas, gas));
            }

            int used = 0;
            int available = 0;

            for (int race = 0; race < ScrRaceIds.PLAYABLE_COUNT && accounting != null; race++) {
                used += ResourceBar.calculateShownSupply(accounting.getSupplyUsed(race * PlayerTable.PLAYER_SLOTS + row.player));
                available += ResourceBar.calculateShownSupply(accounting.getSupplyAvailable(race * PlayerTable.PLAYER_SLOTS + row.player));
            }

            row.supplyUsed    = used;
            row.supplyMaximum = available;
            row.workers       = accounting == null || row.race < 0 || row.race > ScrRaceIds.PLAYABLE_COUNT - 1
                                ? 0
                                : accounting.getCompletedTypeCount(WORKERS[row.race], row.player);
            row.apm           = row.player < apmOfPlayer.length ? apmOfPlayer[row.player] : 0;
        }
    }
}
