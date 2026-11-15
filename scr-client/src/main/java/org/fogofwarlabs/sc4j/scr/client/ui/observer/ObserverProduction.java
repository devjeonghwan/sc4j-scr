package org.fogofwarlabs.sc4j.scr.client.ui.observer;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.client.ui.status.StatusPanel;
import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.content.ScrTechIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.type.ScrTechTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUpgradeTypeView;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitQueries;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collects units in production and current research and upgrades for a player.
 * Unit entries merge by type and retain the smallest remaining frame count; research and upgrade entries retain unit-pool order.
 * Icon names are resolved from the installed observer page action tables.
 */
public final class ObserverProduction {
    private static final int PATTERN_VALUE_GROUP = 2;

    private static final int UNIT_KIND = 0;
    private static final int RESEARCH_KIND = 1;
    private static final int UPGRADE_KIND = 2;

    private static final int MERGED_TYPE = 0;
    private static final int MERGED_COUNT = 1;
    private static final int MERGED_REMAINING = 2;
    private static final int MERGED_TOTAL = 3;

    private static final @NotNull Pattern ENTRY = Pattern.compile("(\\d+):\\{\\s*name:\"[^\"]*\"(?:,actionId:\"(\\d+)\")?");
    private static final @NotNull Pattern ACTION = Pattern.compile("(\\d+):\\{\\s*id:\\d+,x:\\d+,y:\\d+,icon:\"([^\"]+)\"");

    /**
     * One production entry of a player.
     */
    public static final class Item {
        private final @Nullable String icon;
        private final int kind;
        private final int type;
        private final int count;
        private final float progress;
        private final int remaining;

        /**
         * Creates an {@link Item} instance.
         *
         * @param icon      the page image name, for example {@code cmdicons.0.png}, or {@code null} when the page has none
         * @param kind      {@code 0} unit, {@code 1} research, {@code 2} upgrade
         * @param type      the unit, research or upgrade type
         * @param count     the merged unit count
         * @param progress  the progress fraction {@code (total - floor(remaining)) / total}
         * @param remaining the remaining frames, for sorting
         */
        public Item(@Nullable String icon, int kind, int type, int count, float progress, int remaining) {
            this.icon      = icon;
            this.kind      = kind;
            this.type      = type;
            this.count     = count;
            this.progress  = progress;
            this.remaining = remaining;
        }

        /**
         * @return the page image name, or {@code null} when absent
         */
        public @Nullable String getIcon() {
            return this.icon;
        }

        /**
         * @return the entry kind
         */
        public int getKind() {
            return this.kind;
        }

        /**
         * @return the unit, research or upgrade type
         */
        public int getType() {
            return this.type;
        }

        /**
         * @return the merged unit count
         */
        public int getCount() {
            return this.count;
        }

        /**
         * @return the progress fraction
         */
        public float getProgress() {
            return this.progress;
        }

        /**
         * @return the remaining frames
         */
        public int getRemaining() {
            return this.remaining;
        }
    }

    /**
     * Creates an {@link ObserverProduction} instance from the page's icon tables.
     *
     * @param storage the installed game storage
     * @return the created {@link ObserverProduction} instance
     * @throws IOException if the page script cannot be read
     */
    public static @NotNull ObserverProduction load(@NotNull CascStorage storage) throws IOException {
        String script = new String(storage.readFile("webui/dist/ObserverPanel2/ObserverPanel2.js"), StandardCharsets.UTF_8);
        ObserverProduction production = new ObserverProduction();
        Matcher actions = ACTION.matcher(readSection(script, "Actions={"));

        while (actions.find()) {
            production.actionIcons.put(Integer.parseInt(actions.group(1)), actions.group(PATTERN_VALUE_GROUP));
        }

        readEntries(readSection(script, "UnitList={"), production.unitActions);
        readEntries(readSection(script, "Research={"), production.researchActions);
        readEntries(readSection(script, "Upgrades={"), production.upgradeActions);

        return production;
    }

    private static @NotNull String readSection(@NotNull String script, @NotNull String start) throws IOException {
        int begin = script.indexOf(start);

        if (begin < 0) {
            throw Exceptions.newIOException("ObserverPanel2.js has no {0}", start);
        }

        int depth = 0;

        for (int index = begin + start.length() - 1; index < script.length(); index++) {
            char character = script.charAt(index);

            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return script.substring(begin, index + 1);
            }
        }

        throw Exceptions.newIOException("Unterminated {0}", start);
    }

    private static void readEntries(@NotNull String section, @NotNull Map<Integer, Integer> actions) {
        Matcher entries = ENTRY.matcher(section);

        while (entries.find()) {
            if (entries.group(PATTERN_VALUE_GROUP) != null) {
                actions.put(Integer.parseInt(entries.group(1)), Integer.parseInt(entries.group(PATTERN_VALUE_GROUP)));
            }
        }
    }

    private static float calculateProgress(int total, int remaining) {
        return total <= 0 ? 0 : Math.clamp((total - remaining) / (float) total, 0, 1);
    }

    private final @NotNull Map<Integer, String> actionIcons;
    private final @NotNull Map<Integer, Integer> unitActions;
    private final @NotNull Map<Integer, Integer> researchActions;
    private final @NotNull Map<Integer, Integer> upgradeActions;

    /**
     * Creates an {@link ObserverProduction} instance without icon tables.
     */
    public ObserverProduction() {
        this.actionIcons     = new HashMap<>();
        this.unitActions     = new HashMap<>();
        this.researchActions = new HashMap<>();
        this.upgradeActions  = new HashMap<>();
    }


    /**
     * Collects a player's production.
     *
     * @param view   the game view
     * @param player the player slot
     * @return the items in page order: units by type then time remaining, then research and upgrades
     * @throws NullPointerException if the world was built without game data
     */
    public @NotNull List<Item> collect(@NotNull ScrGameView view, int player) {
        UnitPool pool = view.getUnitSystem().getUnitPool();
        List<Item> items = new ArrayList<>();

        if (pool == null) {
            return items;
        }

        ScrUnitTypeView units = view.requireGameData().getUnitTypes();
        ScrTechTypeView technologies = view.requireGameData().getTechTypes();
        ScrUpgradeTypeView upgrades = view.requireGameData().getUpgradeTypes();
        Map<Integer, int[]> merged = new HashMap<>();
        List<int[]> order = new ArrayList<>();
        List<Item> research = new ArrayList<>();

        for (int index = 1; index <= pool.getCapacity(); index++) {
            if (!pool.isAllocated(index)) {
                continue;
            }

            Unit unit = pool.getUnit(index);

            if (unit.getOwnerIndex() != player) {
                continue;
            }

            int type = unit.getUnitId();
            int queuedType = UnitQueries.getQueuedType(unit);
            boolean completed = (unit.getStatus() & UnitStatusFlags.COMPLETED) != 0;

            if (units.isEgg(type) || UnitQueries.isBuildingMorph(units, unit)) {
                type = queuedType;
            } else if (completed) {
                type = -1;
            }

            if (type >= 0 && units.hasEntry(type)) {
                int total = units.getBuildTime(type);
                int remaining = unit.getRemainingBuildTime();
                int[] entry = merged.get(type);

                if (entry == null) {
                    entry = new int[]{type, 0, remaining, total};

                    merged.put(type, entry);
                    order.add(entry);
                }

                entry[MERGED_COUNT]++;
                entry[MERGED_REMAINING] = Math.min(entry[MERGED_REMAINING], remaining);
            }

            // Only completed buildings research; their upgrade/research timer runs while one is in progress.
            boolean researcher = (unit.getStatus() & (UnitStatusFlags.COMPLETED | UnitStatusFlags.GROUNDED_BUILDING)) ==
                                 (UnitStatusFlags.COMPLETED | UnitStatusFlags.GROUNDED_BUILDING) && unit.getUpgradeResearchTime() > 0;
            int technology = unit.getResearchType();

            if (researcher && UnitQueries.isResearching(unit) && technology >= 0 && technology < ScrTechIds.NONE) {
                int total = technologies.getResearchTime(technology);

                research.add(new Item(this.getIcon(this.researchActions, technology), RESEARCH_KIND, technology, 1,
                                      calculateProgress(total, unit.getUpgradeResearchTime()), unit.getUpgradeResearchTime()));
            }

            int upgrade = unit.getUpgradeType();

            if (researcher && UnitQueries.isUpgrading(unit) && upgrade >= 0 && upgrade < ScrUpgradeIds.NONE) {
                int level = StatusPanel.getUpgradeLevel(view, player, upgrade);
                int total = upgrades.getResearchTime(upgrade) + upgrades.getResearchTimeFactor(upgrade) * level;

                research.add(new Item(this.getIcon(this.upgradeActions, upgrade), UPGRADE_KIND, upgrade, 1,
                                      calculateProgress(total, unit.getUpgradeResearchTime()), unit.getUpgradeResearchTime()));
            }
        }

        order.sort(Comparator.<int[]>comparingInt(entry -> entry[MERGED_TYPE]).thenComparingInt(entry -> entry[MERGED_REMAINING]));

        for (int[] entry : order) {
            // An added unit type shows the icon of its wireframe type.
            int iconType = view.requireGameData().getWireframeType(entry[MERGED_TYPE]);

            items.add(new Item(this.getIcon(this.unitActions, iconType), UNIT_KIND, entry[MERGED_TYPE], entry[MERGED_COUNT],
                               calculateProgress(entry[MERGED_TOTAL], entry[MERGED_REMAINING]), entry[MERGED_REMAINING]));
        }

        items.addAll(research);

        return items;
    }

    private @Nullable String getIcon(@NotNull Map<Integer, Integer> actions, int type) {
        Integer action = actions.get(type);

        return action == null ? null : this.actionIcons.get(action);
    }
}
