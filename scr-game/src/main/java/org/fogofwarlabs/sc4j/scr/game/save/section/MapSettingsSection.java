package org.fogofwarlabs.sc4j.scr.game.save.section;

import org.fogofwarlabs.sc4j.scr.data.format.dat.*;
import org.fogofwarlabs.sc4j.scr.game.save.SaveFileReader;
import org.fogofwarlabs.sc4j.scr.game.save.SaveFileWriter;
import org.fogofwarlabs.sc4j.scr.game.save.SaveGameContext;
import org.fogofwarlabs.sc4j.scr.game.save.SaveSection;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Writes and restores nineteen DAT columns replaced by a map's UNIx, UPGx and TECx settings, in this order:
 * <ul>
 *     <li>units.dat (228 entries): hit points (DWORD), shield amount, armor (BYTE), build time, mineral cost, vespene
 *     cost and map string (WORDs);</li>
 *     <li>weapons.dat (130 entries): damage amount and damage bonus (WORDs);</li>
 *     <li>upgrades.dat (61 entries): mineral cost and factor, vespene cost and factor, time and factor (WORDs);</li>
 *     <li>techdata.dat (44 entries): mineral cost, vespene cost, time and energy (WORDs).</li>
 * </ul>
 */
public final class MapSettingsSection implements SaveSection {
    private static final int BYTE_MASK = 0xFF;
    private static final int WORD_MASK = 0xFFFF;

    /**
     * One saved column: its table, column name, entry count and entry width.
     */
    private enum Column {
        HIT_POINTS(Table.UNITS, UnitsDat.HIT_POINTS, UnitsDat.UNIT_COUNT, Integer.BYTES),
        SHIELD_AMOUNT(Table.UNITS, UnitsDat.SHIELD_AMOUNT, UnitsDat.UNIT_COUNT, Short.BYTES),
        ARMOR(Table.UNITS, UnitsDat.ARMOR, UnitsDat.UNIT_COUNT, Byte.BYTES),
        BUILD_TIME(Table.UNITS, UnitsDat.BUILD_TIME, UnitsDat.UNIT_COUNT, Short.BYTES),
        MINERAL_COST(Table.UNITS, UnitsDat.MINERAL_COST, UnitsDat.UNIT_COUNT, Short.BYTES),
        VESPENE_COST(Table.UNITS, UnitsDat.GAS_COST, UnitsDat.UNIT_COUNT, Short.BYTES),
        MAP_STRING(Table.UNITS, UnitsDat.MAP_STRING_INDEX, UnitsDat.UNIT_COUNT, Short.BYTES),
        DAMAGE_AMOUNT(Table.WEAPONS, WeaponsDat.DAMAGE_AMOUNT, WeaponsDat.WEAPON_COUNT, Short.BYTES),
        DAMAGE_BONUS(Table.WEAPONS, WeaponsDat.DAMAGE_BONUS, WeaponsDat.WEAPON_COUNT, Short.BYTES),
        UPGRADE_MINERAL_COST(Table.UPGRADES, UpgradesDat.MINERAL_COST, UpgradesDat.UPGRADE_COUNT, Short.BYTES),
        UPGRADE_MINERAL_FACTOR(Table.UPGRADES, UpgradesDat.MINERAL_COST_FACTOR, UpgradesDat.UPGRADE_COUNT, Short.BYTES),
        UPGRADE_VESPENE_COST(Table.UPGRADES, UpgradesDat.GAS_COST, UpgradesDat.UPGRADE_COUNT, Short.BYTES),
        UPGRADE_VESPENE_FACTOR(Table.UPGRADES, UpgradesDat.GAS_COST_FACTOR, UpgradesDat.UPGRADE_COUNT, Short.BYTES),
        UPGRADE_TIME(Table.UPGRADES, UpgradesDat.RESEARCH_TIME, UpgradesDat.UPGRADE_COUNT, Short.BYTES),
        UPGRADE_TIME_FACTOR(Table.UPGRADES, UpgradesDat.RESEARCH_TIME_FACTOR, UpgradesDat.UPGRADE_COUNT, Short.BYTES),
        TECHNOLOGY_MINERAL_COST(Table.TECHNOLOGIES, TechdataDat.MINERAL_COST, TechdataDat.TECH_COUNT, Short.BYTES),
        TECHNOLOGY_VESPENE_COST(Table.TECHNOLOGIES, TechdataDat.GAS_COST, TechdataDat.TECH_COUNT, Short.BYTES),
        TECHNOLOGY_TIME(Table.TECHNOLOGIES, TechdataDat.RESEARCH_TIME, TechdataDat.TECH_COUNT, Short.BYTES),
        TECHNOLOGY_ENERGY(Table.TECHNOLOGIES, TechdataDat.ENERGY_COST, TechdataDat.TECH_COUNT, Short.BYTES);

        private final @NotNull Table table;
        private final @NotNull String name;
        private final int count;
        private final int width;

        Column(@NotNull Table table, @NotNull String name, int count, int width) {
            this.table = table;
            this.name  = name;
            this.count = count;
            this.width = width;
        }
    }

    /**
     * The DAT tables of the saved columns.
     */
    private enum Table {
        UNITS,
        WEAPONS,
        UPGRADES,
        TECHNOLOGIES
    }

    private static @NotNull DatTable getTable(@NotNull GameData gameData, @NotNull Table table) {
        return switch (table) {
            case UNITS -> gameData.getUnits();
            case WEAPONS -> gameData.getWeapons();
            case UPGRADES -> gameData.getUpgrades();
            case TECHNOLOGIES -> gameData.getTechdata();
        };
    }


    /**
     * Creates a {@link MapSettingsSection} instance.
     */
    public MapSettingsSection() {
    }

    /**
     * Writes the part from the saved game.
     *
     * @param context the saved game
     * @param output  the file output
     * @throws NullPointerException if the world was built without game data
     */
    @Override
    public void write(@NotNull SaveGameContext context, @NotNull SaveFileWriter output) {
        GameData gameData = context.getGame().requireGameData();

        for (Column column : Column.values()) {
            DatTable table = getTable(gameData, column.table);
            ByteBuffer values = ByteBuffer.allocate(column.count * column.width).order(ByteOrder.LITTLE_ENDIAN);

            for (int entry = 0; entry < column.count; entry++) {
                int value = table.get(column.name, entry);

                switch (column.width) {
                    case Integer.BYTES -> values.putInt(value);
                    case Short.BYTES -> values.putShort((short) value);
                    default -> values.put((byte) value);
                }
            }

            output.writeSection(values.array());
        }
    }

    /**
     * Reads the part into the restored game.
     *
     * @param context the restored game
     * @param input   the file input
     * @throws NullPointerException if the world was built without game data
     */
    @Override
    public void read(@NotNull SaveGameContext context, @NotNull SaveFileReader input) {
        GameData gameData = context.getGame().requireGameData();

        for (Column column : Column.values()) {
            DatTable table = getTable(gameData, column.table);
            ByteBuffer values = ByteBuffer.wrap(input.readSection(column.count * column.width)).order(ByteOrder.LITTLE_ENDIAN);

            for (int entry = 0; entry < column.count; entry++) {
                int value = switch (column.width) {
                    case Integer.BYTES -> values.getInt();
                    case Short.BYTES -> values.getShort() & WORD_MASK;
                    default -> values.get() & BYTE_MASK;
                };

                // The game started from the same map holds the map's settings already; only a changed value is stored.
                if (table.get(column.name, entry) != value) {
                    table.set(column.name, entry, value);
                }
            }
        }
    }
}
