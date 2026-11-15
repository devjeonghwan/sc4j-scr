package org.fogofwarlabs.sc4j.scr.content.table;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.registry.TableKind;
import org.fogofwarlabs.sc4j.engine.entity.column.ColumnKey;
import org.fogofwarlabs.sc4j.engine.entity.column.ColumnReset;
import org.fogofwarlabs.sc4j.engine.entity.column.IntColumn;
import org.fogofwarlabs.sc4j.scr.data.format.dat.*;
import org.fogofwarlabs.sc4j.scr.data.type.ScrSpriteTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Names the nine DAT tables of the SC:R content and where the engine content stores each of their fields: a field is
 * held either by an engine column of the engine type table of its kind or by one SC:R extension column of that table,
 * never by both. A field whose native value carries more than its engine column (a flag word, a race byte, a boolean
 * byte) keeps the native value in its extension column; the engine column then holds the value derived from it.
 * <p>
 * Element {@code e} of a field is the value of type {@code e + getFirstType(field)}: most fields start at type
 * {@code 0}, the infestation and add-on fields of units.dat and the selection fields of sprites.dat start later. The
 * extension columns of a table are registered in the field order of its DAT file.
 */
public enum ScrDatKind {
    /**
     * {@code units.dat}, the unit type table.
     */
    UNITS(TableKind.UNIT, UnitsDat.createSchema(), UnitsDat.UNIT_COUNT,
          Set.of(UnitsDat.FLINGY_TYPE, UnitsDat.HIT_POINTS, UnitsDat.COMPUTER_IDLE_ORDER, UnitsDat.HUMAN_IDLE_ORDER,
                 UnitsDat.GROUND_WEAPON, UnitsDat.AIR_WEAPON,
                 UnitsDat.TARGET_ACQUISITION_RANGE, UnitsDat.SIGHT_RANGE, UnitsDat.ARMOR_UPGRADE, UnitsDat.ARMOR,
                 UnitsDat.PLACEMENT_WIDTH, UnitsDat.PLACEMENT_HEIGHT,
                 UnitsDat.DIMENSION_LEFT, UnitsDat.DIMENSION_UP, UnitsDat.DIMENSION_RIGHT, UnitsDat.DIMENSION_DOWN,
                 UnitsDat.MINERAL_COST, UnitsDat.GAS_COST, UnitsDat.BUILD_TIME,
                 UnitsDat.SUPPLY_PROVIDED, UnitsDat.SUPPLY_REQUIRED, UnitsDat.SPACE_REQUIRED, UnitsDat.SPACE_PROVIDED),
          Map.of(UnitsDat.INFESTED_TYPE, ScrUnitTypeView.FIRST_INFESTATION_TYPE,
                 UnitsDat.ADDON_HORIZONTAL_OFFSET, ScrUnitTypeView.FIRST_INFESTATION_TYPE,
                 UnitsDat.ADDON_VERTICAL_OFFSET, ScrUnitTypeView.FIRST_INFESTATION_TYPE)),
    /**
     * {@code weapons.dat}, the weapon type table.
     */
    WEAPONS(TableKind.WEAPON, WeaponsDat.createSchema(), WeaponsDat.WEAPON_COUNT,
            Set.of(WeaponsDat.FLINGY_TYPE, WeaponsDat.MINIMUM_RANGE, WeaponsDat.MAXIMUM_RANGE, WeaponsDat.DAMAGE_UPGRADE,
                   WeaponsDat.DAMAGE_TYPE, WeaponsDat.INNER_SPLASH_RADIUS, WeaponsDat.MEDIUM_SPLASH_RADIUS, WeaponsDat.OUTER_SPLASH_RADIUS,
                   WeaponsDat.DAMAGE_AMOUNT, WeaponsDat.DAMAGE_BONUS, WeaponsDat.COOLDOWN, WeaponsDat.DAMAGE_FACTOR),
            Map.of()),
    /**
     * {@code flingy.dat}, the flingy type table.
     */
    FLINGY(TableKind.MOTION_PROFILE, FlingyDat.createSchema(), FlingyDat.FLINGY_COUNT,
           Set.of(FlingyDat.SPRITE, FlingyDat.TOP_SPEED, FlingyDat.ACCELERATION, FlingyDat.HALT_DISTANCE, FlingyDat.TURN_RATE),
           Map.of()),
    /**
     * {@code sprites.dat}, the sprite type table.
     */
    SPRITES(TableKind.SPRITE, SpritesDat.createSchema(), SpritesDat.SPRITE_COUNT,
            Set.of(SpritesDat.IMAGE, SpritesDat.SELECTION_CIRCLE),
            Map.of(SpritesDat.HEALTH_BAR_WIDTH, ScrSpriteTypeView.FIRST_SELECTABLE_SPRITE,
                   SpritesDat.SELECTION_CIRCLE, ScrSpriteTypeView.FIRST_SELECTABLE_SPRITE,
                   SpritesDat.SELECTION_CIRCLE_OFFSET, ScrSpriteTypeView.FIRST_SELECTABLE_SPRITE)),
    /**
     * {@code images.dat}, the image type table.
     */
    IMAGES(TableKind.IMAGE, ImagesDat.createSchema(), ImagesDat.IMAGE_COUNT,
           Set.of(ImagesDat.ISCRIPT),
           Map.of()),
    /**
     * {@code upgrades.dat}, the upgrade type table.
     */
    UPGRADES(TableKind.UPGRADE, UpgradesDat.createSchema(), UpgradesDat.UPGRADE_COUNT,
             Set.of(UpgradesDat.MINERAL_COST, UpgradesDat.MINERAL_COST_FACTOR, UpgradesDat.GAS_COST, UpgradesDat.GAS_COST_FACTOR,
                    UpgradesDat.RESEARCH_TIME, UpgradesDat.RESEARCH_TIME_FACTOR, UpgradesDat.MAXIMUM_LEVEL),
             Map.of()),
    /**
     * {@code techdata.dat}, the tech type table.
     */
    TECHDATA(TableKind.TECH, TechdataDat.createSchema(), TechdataDat.TECH_COUNT,
             Set.of(TechdataDat.MINERAL_COST, TechdataDat.GAS_COST, TechdataDat.RESEARCH_TIME, TechdataDat.ENERGY_COST),
             Map.of()),
    /**
     * {@code orders.dat}, the order type table.
     */
    ORDERS(TableKind.ORDER, OrdersDat.createSchema(), OrdersDat.ORDER_COUNT,
           Set.of(OrdersDat.WEAPON, OrdersDat.TECHNOLOGY),
           Map.of()),
    /**
     * {@code sfxdata.dat}, the sound type table.
     */
    SOUNDS(TableKind.SOUND, SfxdataDat.createSchema(), SfxdataDat.SOUND_COUNT,
           Set.of(SfxdataDat.PRIORITY, SfxdataDat.VOLUME),
           Map.of());

    /**
     * The prefix of the extension column names: {@code scr.<kind>.<field>}.
     */
    private static final @NotNull String COLUMN_NAME_PREFIX = "scr.";

    private final @NotNull TableKind tableKind;
    private final @NotNull List<@NotNull String> fields;
    private final int typeCount;
    private final @NotNull Map<String, Integer> firstTypes;
    private final @NotNull Map<String, @NotNull ColumnKey<IntColumn>> extensionKeys;

    ScrDatKind(@NotNull TableKind tableKind, @NotNull DatSchema schema, int typeCount, @NotNull Set<String> engineFields,
               @NotNull Map<String, Integer> firstTypes) {
        Map<String, ColumnKey<IntColumn>> keys = new LinkedHashMap<>();
        List<String> names = new ArrayList<>();

        for (DatSchema.Field field : schema.getFields()) {
            names.add(field.getName());

            if (!engineFields.contains(field.getName())) {
                keys.put(field.getName(), ColumnKey.ofInt(COLUMN_NAME_PREFIX + this.name().toLowerCase(Locale.ROOT) + "." + field.getName(), 0,
                                                          ColumnReset.KEEP));
            }
        }

        this.tableKind     = tableKind;
        this.fields        = List.copyOf(names);
        this.typeCount     = typeCount;
        this.firstTypes    = firstTypes;
        this.extensionKeys = keys;
    }

    /**
     * @return the engine type table that holds the fields of this DAT table
     */
    public @NotNull TableKind getTableKind() {
        return this.tableKind;
    }

    /**
     * @return every field of this DAT table, in DAT field order
     */
    public @NotNull List<@NotNull String> getFields() {
        return this.fields;
    }

    /**
     * @return the number of types of the table: the registered types of the kind
     */
    public int getTypeCount() {
        return this.typeCount;
    }

    /**
     * @param fieldName a field of this DAT table
     * @return the type of the first element of the field
     */
    public int getFirstType(@NotNull String fieldName) {
        return this.firstTypes.getOrDefault(fieldName, 0);
    }

    /**
     * @return the fields held by extension columns, in DAT field order: the order their columns are registered in
     */
    public @NotNull List<@NotNull String> getExtensionFields() {
        return new ArrayList<>(this.extensionKeys.keySet());
    }

    /**
     * Returns the key of the extension column of a field.
     *
     * @param fieldName a field of this DAT table held by an extension column
     * @return the column key, the same instance for every content
     * @throws IllegalArgumentException if an engine column holds the field or the table has no such field
     */
    public @NotNull ColumnKey<IntColumn> getExtensionKey(@NotNull String fieldName) {
        ColumnKey<IntColumn> key = this.extensionKeys.get(fieldName);

        if (key == null) {
            throw Exceptions.newIllegalArgumentException("The field {0} of {1} has no extension column.", fieldName, this);
        }

        return key;
    }
}
