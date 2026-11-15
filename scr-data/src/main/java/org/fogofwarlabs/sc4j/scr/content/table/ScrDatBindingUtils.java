package org.fogofwarlabs.sc4j.scr.content.table;

import org.fogofwarlabs.sc4j.engine.content.table.*;
import org.fogofwarlabs.sc4j.engine.entity.column.ColumnTable;
import org.fogofwarlabs.sc4j.engine.entity.column.IntColumn;
import org.fogofwarlabs.sc4j.engine.game.ControllerKind;
import org.fogofwarlabs.sc4j.scr.content.ScrNativeIds;
import org.fogofwarlabs.sc4j.scr.content.ScrResources;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitTraits;
import org.fogofwarlabs.sc4j.scr.content.ScrUnits;
import org.fogofwarlabs.sc4j.scr.data.format.dat.*;
import org.fogofwarlabs.sc4j.scr.data.type.DatColumn;
import org.fogofwarlabs.sc4j.scr.data.type.ScrSpriteTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * Creates the DAT tables of a game over its engine content: tables with the fields and native memory layout of the
 * installed DAT tables whose columns read and write the engine and extension columns of the content
 * ({@link ScrDatKind}), so the content is the only store of the game's values.
 * <p>
 * A column keeps the native value of its field: a type reference converts the engine's
 * {@link org.fogofwarlabs.sc4j.engine.content.type.ContentType#NO_INDEX} to and from the native "none" entry
 * ({@link ScrNativeIds}) and passes every other value through, a cost field reads and writes one resource kind of the
 * cost column, and a field whose engine column is derived ({@link ScrDerivedValueUtils}) keeps its native value in its
 * extension column and writes the derived engine value with it. The DAT table narrows every write to the field's
 * width and sign before the column stores it, as for the installed tables.
 */
public final class ScrDatBindingUtils {
    /**
     * Creates the DAT table of one kind over a content.
     *
     * @param kind    the DAT table kind
     * @param content the engine content of the game
     * @param layout  a DAT table of the kind, whose fields and native memory layout the created table has
     * @return the created {@link DatTable} instance; its values are the content's
     */
    public static @NotNull DatTable bind(@NotNull ScrDatKind kind, @NotNull GameContent content, @NotNull DatTable layout) {
        return layout.createOver(field -> createColumn(kind, content, field, layout.getCount(field)));
    }

    /**
     * Creates the trait columns of a game's unit type view over a content: each reads and writes the content's extension column of the trait
     * column ({@link ScrUnitTraits#getKey}).
     *
     * @param content the engine content of the game, registered by the SC:R importer
     * @return the created column source for {@link ScrUnitTypeView}
     */
    public static @NotNull Function<ScrUnitTypeView.TraitColumn, @NotNull DatColumn> bindTraitColumns(@NotNull GameContent content) {
        ColumnTable extension = content.getUnitTypes().getExtensionColumns();

        return column -> new ScrExtensionColumn(extension.get(ScrUnitTraits.getKey(column)), 0, ScrUnitTraits.TYPE_COUNT);
    }

    /**
     * Creates the added columns of one DAT kind over a content: for each field, the column of the entries added after the native ones (unit types
     * {@link org.fogofwarlabs.sc4j.scr.content.ScrAddedUnitTypes}, images, sprites and flingy entries
     * {@link org.fogofwarlabs.sc4j.scr.content.ScrAddedGraphics}), whose element {@code n} reads and writes the content's entry
     * {@link ScrDatKind#getTypeCount()}{@code  + n} as the field's column does. A kind without added entries has empty columns.
     *
     * @param kind    the DAT table kind
     * @param content the engine content of the game, registered by the SC:R importer with its added entries after the native ones
     * @return the created column source by field name, one element per added entry
     */
    public static @NotNull Function<String, @NotNull DatColumn> bindAddedColumns(@NotNull ScrDatKind kind, @NotNull GameContent content) {
        int count = getTable(kind, content).getCount() - kind.getTypeCount();

        return field -> new ScrOffsetColumn(createColumn(kind, content, field, count), kind.getTypeCount() - kind.getFirstType(field), count);
    }

    /**
     * Creates the added trait columns of a game's unit type view over a content: each reads and writes the content's extension column of the
     * trait column from unit type {@link ScrUnits#COUNT} on.
     *
     * @param content the engine content of the game, registered by the SC:R importer with its added unit types after the native ones
     * @return the created column source for {@link ScrUnitTypeView}, one element per added type
     */
    public static @NotNull Function<ScrUnitTypeView.TraitColumn, @NotNull DatColumn> bindAddedTraitColumns(@NotNull GameContent content) {
        ColumnTable extension = content.getUnitTypes().getExtensionColumns();
        int count = content.getUnitTypes().getCount() - ScrUnits.COUNT;

        return column -> new ScrExtensionColumn(extension.get(ScrUnitTraits.getKey(column)), ScrUnits.COUNT, count);
    }

    private static @NotNull TypeTable getTable(@NotNull ScrDatKind kind, @NotNull GameContent content) {
        return switch (kind) {
            case UNITS -> content.getUnitTypes();
            case WEAPONS -> content.getWeaponTypes();
            case FLINGY -> content.getMotionProfiles();
            case SPRITES -> content.getSpriteTypes();
            case IMAGES -> content.getImageTypes();
            case UPGRADES -> content.getUpgradeTypes();
            case TECHDATA -> content.getTechTypes();
            case ORDERS -> content.getOrderTypes();
            case SOUNDS -> content.getSoundTypes();
        };
    }

    private static @NotNull DatColumn createColumn(@NotNull ScrDatKind kind, @NotNull GameContent content, @NotNull String field, int length) {
        return switch (kind) {
            case UNITS -> createUnitColumn(kind, content.getUnitTypes(), field, length);
            case WEAPONS -> createWeaponColumn(kind, content.getWeaponTypes(), field, length);
            case FLINGY -> createFlingyColumn(kind, content.getMotionProfiles(), field, length);
            case SPRITES -> createSpriteColumn(kind, content.getSpriteTypes(), field, length);
            case IMAGES -> createImageColumn(kind, content.getImageTypes(), field, length);
            case UPGRADES -> createUpgradeColumn(kind, content.getUpgradeTypes(), field, length);
            case TECHDATA -> createTechColumn(kind, content.getTechTypes(), field, length);
            case ORDERS -> createOrderColumn(kind, content.getOrderTypes(), field, length);
            case SOUNDS -> createSoundColumn(kind, content.getSoundTypes(), field, length);
        };
    }

    private static @NotNull DatColumn createExtensionColumn(@NotNull ScrDatKind kind, @NotNull TypeTable table, @NotNull String field, int length) {
        return new ScrExtensionColumn(table.getExtensionColumns().get(kind.getExtensionKey(field)), kind.getFirstType(field), length);
    }

    private static @NotNull DatColumn createUnitColumn(@NotNull ScrDatKind kind, @NotNull UnitTypeTable units, @NotNull String field, int length) {
        int minerals = ScrResources.MINERALS.getIndex();
        int gas = ScrResources.GAS.getIndex();
        ColumnTable extension = units.getExtensionColumns();

        return switch (field) {
            case UnitsDat.FLINGY_TYPE -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return units.getMotionProfileIndex(element);
                }

                @Override
                public void set(int element, int value) {
                    units.setMotionProfileIndex(element, value);
                }
            };
            case UnitsDat.HIT_POINTS -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return units.getHitPoints(element);
                }

                @Override
                public void set(int element, int value) {
                    units.setHitPoints(element, value);
                }
            };
            case UnitsDat.GROUND_WEAPON -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return ScrNativeIds.toNativeWeapon(units.getGroundWeaponIndex(element));
                }

                @Override
                public void set(int element, int value) {
                    units.setGroundWeaponIndex(element, ScrNativeIds.fromNativeWeapon(value));
                }
            };
            case UnitsDat.AIR_WEAPON -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return ScrNativeIds.toNativeWeapon(units.getAirWeaponIndex(element));
                }

                @Override
                public void set(int element, int value) {
                    units.setAirWeaponIndex(element, ScrNativeIds.fromNativeWeapon(value));
                }
            };
            case UnitsDat.COMPUTER_IDLE_ORDER -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return ScrNativeIds.toNativeOrder(units.getIdleOrderIndex(element, ControllerKind.COMPUTER));
                }

                @Override
                public void set(int element, int value) {
                    units.setIdleOrderIndex(element, ControllerKind.COMPUTER, ScrNativeIds.fromNativeOrder(value));
                }
            };
            case UnitsDat.HUMAN_IDLE_ORDER -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return ScrNativeIds.toNativeOrder(units.getIdleOrderIndex(element, ControllerKind.HUMAN));
                }

                @Override
                public void set(int element, int value) {
                    units.setIdleOrderIndex(element, ControllerKind.HUMAN, ScrNativeIds.fromNativeOrder(value));
                }
            };
            case UnitsDat.TARGET_ACQUISITION_RANGE -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return units.getTargetAcquisitionRange(element);
                }

                @Override
                public void set(int element, int value) {
                    units.setTargetAcquisitionRange(element, value);
                }
            };
            case UnitsDat.SIGHT_RANGE -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return units.getSightRange(element);
                }

                @Override
                public void set(int element, int value) {
                    units.setSightRange(element, value);
                }
            };
            case UnitsDat.ARMOR_UPGRADE -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return ScrNativeIds.toNativeUpgrade(units.getArmorUpgradeIndex(element));
                }

                @Override
                public void set(int element, int value) {
                    units.setArmorUpgradeIndex(element, ScrNativeIds.fromNativeUpgrade(value));
                }
            };
            case UnitsDat.ARMOR -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return units.getArmor(element);
                }

                @Override
                public void set(int element, int value) {
                    units.setArmor(element, value);
                }
            };
            case UnitsDat.PLACEMENT_WIDTH -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return units.getPlacementWidth(element);
                }

                @Override
                public void set(int element, int value) {
                    units.setPlacementWidth(element, value);
                }
            };
            case UnitsDat.PLACEMENT_HEIGHT -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return units.getPlacementHeight(element);
                }

                @Override
                public void set(int element, int value) {
                    units.setPlacementHeight(element, value);
                }
            };
            case UnitsDat.DIMENSION_LEFT -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return units.getCollisionLeft(element);
                }

                @Override
                public void set(int element, int value) {
                    units.setCollisionLeft(element, value);
                }
            };
            case UnitsDat.DIMENSION_UP -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return units.getCollisionUp(element);
                }

                @Override
                public void set(int element, int value) {
                    units.setCollisionUp(element, value);
                }
            };
            case UnitsDat.DIMENSION_RIGHT -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return units.getCollisionRight(element);
                }

                @Override
                public void set(int element, int value) {
                    units.setCollisionRight(element, value);
                }
            };
            case UnitsDat.DIMENSION_DOWN -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return units.getCollisionDown(element);
                }

                @Override
                public void set(int element, int value) {
                    units.setCollisionDown(element, value);
                }
            };
            case UnitsDat.MINERAL_COST -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return units.getCost(element, minerals);
                }

                @Override
                public void set(int element, int value) {
                    units.setCost(element, minerals, value);
                }
            };
            case UnitsDat.GAS_COST -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return units.getCost(element, gas);
                }

                @Override
                public void set(int element, int value) {
                    units.setCost(element, gas, value);
                }
            };
            case UnitsDat.BUILD_TIME -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return units.getBuildTime(element);
                }

                @Override
                public void set(int element, int value) {
                    units.setBuildTime(element, value);
                }
            };
            case UnitsDat.SUPPLY_PROVIDED -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return units.getSupplyProvided(element);
                }

                @Override
                public void set(int element, int value) {
                    units.setSupplyProvided(element, value);
                }
            };
            case UnitsDat.SUPPLY_REQUIRED -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return units.getSupplyRequired(element);
                }

                @Override
                public void set(int element, int value) {
                    units.setSupplyRequired(element, value);
                }
            };
            case UnitsDat.SPACE_REQUIRED -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return units.getSpaceRequired(element);
                }

                @Override
                public void set(int element, int value) {
                    units.setSpaceRequired(element, value);
                }
            };
            case UnitsDat.SPACE_PROVIDED -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return units.getSpaceProvided(element);
                }

                @Override
                public void set(int element, int value) {
                    units.setSpaceProvided(element, value);
                }
            };
            case UnitsDat.FLAGS -> {
                IntColumn nativeValues = extension.get(kind.getExtensionKey(field));

                yield new DatColumn(length) {
                    @Override
                    public int get(int element) {
                        return nativeValues.get(element);
                    }

                    @Override
                    public void set(int element, int value) {
                        nativeValues.set(element, value);
                        units.setTraits(element, ScrDerivedValueUtils.getUnitTraits(value));
                    }
                };
            }
            case UnitsDat.GROUP_FLAGS -> {
                IntColumn nativeValues = extension.get(kind.getExtensionKey(field));

                yield new DatColumn(length) {
                    @Override
                    public int get(int element) {
                        return nativeValues.get(element);
                    }

                    @Override
                    public void set(int element, int value) {
                        nativeValues.set(element, value);
                        units.setFactionIndex(element, ScrDerivedValueUtils.getUnitRaceIndex(value));
                    }
                };
            }
            default -> createExtensionColumn(kind, units, field, length);
        };
    }

    private static @NotNull DatColumn createWeaponColumn(@NotNull ScrDatKind kind, @NotNull WeaponTypeTable weapons,
                                                         @NotNull String field, int length) {
        ColumnTable extension = weapons.getExtensionColumns();

        return switch (field) {
            case WeaponsDat.FLINGY_TYPE -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return weapons.getProjectileIndex(element);
                }

                @Override
                public void set(int element, int value) {
                    weapons.setProjectileIndex(element, value);
                }
            };
            case WeaponsDat.MINIMUM_RANGE -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return weapons.getMinimumRange(element);
                }

                @Override
                public void set(int element, int value) {
                    weapons.setMinimumRange(element, value);
                }
            };
            case WeaponsDat.MAXIMUM_RANGE -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return weapons.getMaximumRange(element);
                }

                @Override
                public void set(int element, int value) {
                    weapons.setMaximumRange(element, value);
                }
            };
            case WeaponsDat.DAMAGE_UPGRADE -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return ScrNativeIds.toNativeUpgrade(weapons.getUpgradeIndex(element));
                }

                @Override
                public void set(int element, int value) {
                    weapons.setUpgradeIndex(element, ScrNativeIds.fromNativeUpgrade(value));
                }
            };
            case WeaponsDat.DAMAGE_TYPE -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return weapons.getDamageKind(element);
                }

                @Override
                public void set(int element, int value) {
                    weapons.setDamageKind(element, value);
                }
            };
            case WeaponsDat.INNER_SPLASH_RADIUS -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return weapons.getSplashInnerRadius(element);
                }

                @Override
                public void set(int element, int value) {
                    weapons.setSplashInnerRadius(element, value);
                }
            };
            case WeaponsDat.MEDIUM_SPLASH_RADIUS -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return weapons.getSplashMiddleRadius(element);
                }

                @Override
                public void set(int element, int value) {
                    weapons.setSplashMiddleRadius(element, value);
                }
            };
            case WeaponsDat.OUTER_SPLASH_RADIUS -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return weapons.getSplashOuterRadius(element);
                }

                @Override
                public void set(int element, int value) {
                    weapons.setSplashOuterRadius(element, value);
                }
            };
            case WeaponsDat.DAMAGE_AMOUNT -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return weapons.getDamage(element);
                }

                @Override
                public void set(int element, int value) {
                    weapons.setDamage(element, value);
                }
            };
            case WeaponsDat.DAMAGE_BONUS -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return weapons.getDamageBonus(element);
                }

                @Override
                public void set(int element, int value) {
                    weapons.setDamageBonus(element, value);
                }
            };
            case WeaponsDat.COOLDOWN -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return weapons.getCooldown(element);
                }

                @Override
                public void set(int element, int value) {
                    weapons.setCooldown(element, value);
                }
            };
            case WeaponsDat.DAMAGE_FACTOR -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return weapons.getDamageFactor(element);
                }

                @Override
                public void set(int element, int value) {
                    weapons.setDamageFactor(element, value);
                }
            };
            case WeaponsDat.TARGET_FLAGS -> {
                IntColumn nativeValues = extension.get(kind.getExtensionKey(field));

                yield new DatColumn(length) {
                    @Override
                    public int get(int element) {
                        return nativeValues.get(element);
                    }

                    @Override
                    public void set(int element, int value) {
                        nativeValues.set(element, value);
                        weapons.setTargets(element, ScrDerivedValueUtils.getWeaponTargets(value));
                    }
                };
            }
            default -> createExtensionColumn(kind, weapons, field, length);
        };
    }

    private static @NotNull DatColumn createFlingyColumn(@NotNull ScrDatKind kind, @NotNull MotionProfileTable flingies,
                                                         @NotNull String field, int length) {
        return switch (field) {
            case FlingyDat.SPRITE -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return flingies.getSpriteIndex(element);
                }

                @Override
                public void set(int element, int value) {
                    flingies.setSpriteIndex(element, value);
                }
            };
            case FlingyDat.TOP_SPEED -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return flingies.getTopSpeed(element);
                }

                @Override
                public void set(int element, int value) {
                    flingies.setTopSpeed(element, value);
                }
            };
            case FlingyDat.ACCELERATION -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return flingies.getAcceleration(element);
                }

                @Override
                public void set(int element, int value) {
                    flingies.setAcceleration(element, value);
                }
            };
            case FlingyDat.HALT_DISTANCE -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return flingies.getStoppingDistance(element);
                }

                @Override
                public void set(int element, int value) {
                    flingies.setStoppingDistance(element, value);
                }
            };
            case FlingyDat.TURN_RATE -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return flingies.getTurnRate(element);
                }

                @Override
                public void set(int element, int value) {
                    flingies.setTurnRate(element, value);
                }
            };
            default -> createExtensionColumn(kind, flingies, field, length);
        };
    }

    private static @NotNull DatColumn createSpriteColumn(@NotNull ScrDatKind kind, @NotNull SpriteTypeTable sprites,
                                                         @NotNull String field, int length) {
        return switch (field) {
            case SpritesDat.IMAGE -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return sprites.getImageIndex(element);
                }

                @Override
                public void set(int element, int value) {
                    sprites.setImageIndex(element, value);
                }
            };
            case SpritesDat.SELECTION_CIRCLE -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return sprites.getSelectionSize(element + ScrSpriteTypeView.FIRST_SELECTABLE_SPRITE);
                }

                @Override
                public void set(int element, int value) {
                    sprites.setSelectionSize(element + ScrSpriteTypeView.FIRST_SELECTABLE_SPRITE, value);
                }
            };
            default -> createExtensionColumn(kind, sprites, field, length);
        };
    }

    private static @NotNull DatColumn createImageColumn(@NotNull ScrDatKind kind, @NotNull ImageTypeTable images, @NotNull String field, int length) {
        ColumnTable extension = images.getExtensionColumns();

        return switch (field) {
            case ImagesDat.ISCRIPT -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return images.getAnimationSetIndex(element);
                }

                @Override
                public void set(int element, int value) {
                    images.setAnimationSetIndex(element, value);
                }
            };
            case ImagesDat.GRAPHIC_TURNS -> {
                IntColumn nativeValues = extension.get(kind.getExtensionKey(field));

                yield new DatColumn(length) {
                    @Override
                    public int get(int element) {
                        return nativeValues.get(element);
                    }

                    @Override
                    public void set(int element, int value) {
                        nativeValues.set(element, value);
                        images.setTurning(element, ScrDerivedValueUtils.isSet(value));
                    }
                };
            }
            case ImagesDat.CLICKABLE -> {
                IntColumn nativeValues = extension.get(kind.getExtensionKey(field));

                yield new DatColumn(length) {
                    @Override
                    public int get(int element) {
                        return nativeValues.get(element);
                    }

                    @Override
                    public void set(int element, int value) {
                        nativeValues.set(element, value);
                        images.setClickable(element, ScrDerivedValueUtils.isSet(value));
                    }
                };
            }
            default -> createExtensionColumn(kind, images, field, length);
        };
    }

    private static @NotNull DatColumn createUpgradeColumn(@NotNull ScrDatKind kind, @NotNull UpgradeTypeTable upgrades,
                                                          @NotNull String field, int length) {
        int minerals = ScrResources.MINERALS.getIndex();
        int gas = ScrResources.GAS.getIndex();
        ColumnTable extension = upgrades.getExtensionColumns();

        return switch (field) {
            case UpgradesDat.MINERAL_COST -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return upgrades.getBaseCost(element, minerals);
                }

                @Override
                public void set(int element, int value) {
                    upgrades.setBaseCost(element, minerals, value);
                }
            };
            case UpgradesDat.MINERAL_COST_FACTOR -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return upgrades.getCostFactor(element, minerals);
                }

                @Override
                public void set(int element, int value) {
                    upgrades.setCostFactor(element, minerals, value);
                }
            };
            case UpgradesDat.GAS_COST -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return upgrades.getBaseCost(element, gas);
                }

                @Override
                public void set(int element, int value) {
                    upgrades.setBaseCost(element, gas, value);
                }
            };
            case UpgradesDat.GAS_COST_FACTOR -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return upgrades.getCostFactor(element, gas);
                }

                @Override
                public void set(int element, int value) {
                    upgrades.setCostFactor(element, gas, value);
                }
            };
            case UpgradesDat.RESEARCH_TIME -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return upgrades.getBaseTime(element);
                }

                @Override
                public void set(int element, int value) {
                    upgrades.setBaseTime(element, value);
                }
            };
            case UpgradesDat.RESEARCH_TIME_FACTOR -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return upgrades.getTimeFactor(element);
                }

                @Override
                public void set(int element, int value) {
                    upgrades.setTimeFactor(element, value);
                }
            };
            case UpgradesDat.MAXIMUM_LEVEL -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return upgrades.getMaximumLevel(element);
                }

                @Override
                public void set(int element, int value) {
                    upgrades.setMaximumLevel(element, value);
                }
            };
            case UpgradesDat.RACE -> {
                IntColumn nativeValues = extension.get(kind.getExtensionKey(field));

                yield new DatColumn(length) {
                    @Override
                    public int get(int element) {
                        return nativeValues.get(element);
                    }

                    @Override
                    public void set(int element, int value) {
                        nativeValues.set(element, value);
                        upgrades.setFactionIndex(element, ScrDerivedValueUtils.getRaceIndex(value));
                    }
                };
            }
            default -> createExtensionColumn(kind, upgrades, field, length);
        };
    }

    private static @NotNull DatColumn createTechColumn(@NotNull ScrDatKind kind, @NotNull TechTypeTable techs, @NotNull String field, int length) {
        int minerals = ScrResources.MINERALS.getIndex();
        int gas = ScrResources.GAS.getIndex();
        ColumnTable extension = techs.getExtensionColumns();

        return switch (field) {
            case TechdataDat.MINERAL_COST -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return techs.getCost(element, minerals);
                }

                @Override
                public void set(int element, int value) {
                    techs.setCost(element, minerals, value);
                }
            };
            case TechdataDat.GAS_COST -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return techs.getCost(element, gas);
                }

                @Override
                public void set(int element, int value) {
                    techs.setCost(element, gas, value);
                }
            };
            case TechdataDat.RESEARCH_TIME -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return techs.getResearchTime(element);
                }

                @Override
                public void set(int element, int value) {
                    techs.setResearchTime(element, value);
                }
            };
            case TechdataDat.ENERGY_COST -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return techs.getEnergyCost(element);
                }

                @Override
                public void set(int element, int value) {
                    techs.setEnergyCost(element, value);
                }
            };
            case TechdataDat.RACE -> {
                IntColumn nativeValues = extension.get(kind.getExtensionKey(field));

                yield new DatColumn(length) {
                    @Override
                    public int get(int element) {
                        return nativeValues.get(element);
                    }

                    @Override
                    public void set(int element, int value) {
                        nativeValues.set(element, value);
                        techs.setFactionIndex(element, ScrDerivedValueUtils.getRaceIndex(value));
                    }
                };
            }
            default -> createExtensionColumn(kind, techs, field, length);
        };
    }

    private static @NotNull DatColumn createOrderColumn(@NotNull ScrDatKind kind, @NotNull OrderTypeTable orders, @NotNull String field, int length) {
        ColumnTable extension = orders.getExtensionColumns();

        return switch (field) {
            case OrdersDat.WEAPON -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return ScrNativeIds.toNativeWeapon(orders.getWeaponIndex(element));
                }

                @Override
                public void set(int element, int value) {
                    orders.setWeaponIndex(element, ScrNativeIds.fromNativeWeapon(value));
                }
            };
            case OrdersDat.TECHNOLOGY -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return ScrNativeIds.toNativeTech(orders.getTechIndex(element));
                }

                @Override
                public void set(int element, int value) {
                    orders.setTechIndex(element, ScrNativeIds.fromNativeTech(value));
                }
            };
            case OrdersDat.CAN_BE_INTERRUPTED -> {
                IntColumn nativeValues = extension.get(kind.getExtensionKey(field));

                yield new DatColumn(length) {
                    @Override
                    public int get(int element) {
                        return nativeValues.get(element);
                    }

                    @Override
                    public void set(int element, int value) {
                        nativeValues.set(element, value);
                        orders.setInterruptible(element, ScrDerivedValueUtils.isSet(value));
                    }
                };
            }
            case OrdersDat.CAN_BE_QUEUED -> {
                IntColumn nativeValues = extension.get(kind.getExtensionKey(field));

                yield new DatColumn(length) {
                    @Override
                    public int get(int element) {
                        return nativeValues.get(element);
                    }

                    @Override
                    public void set(int element, int value) {
                        nativeValues.set(element, value);
                        orders.setQueueable(element, ScrDerivedValueUtils.isSet(value));
                    }
                };
            }
            default -> createExtensionColumn(kind, orders, field, length);
        };
    }

    private static @NotNull DatColumn createSoundColumn(@NotNull ScrDatKind kind, @NotNull SoundTypeTable sounds, @NotNull String field, int length) {
        return switch (field) {
            case SfxdataDat.PRIORITY -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return sounds.getPriority(element);
                }

                @Override
                public void set(int element, int value) {
                    sounds.setPriority(element, value);
                }
            };
            case SfxdataDat.VOLUME -> new DatColumn(length) {
                @Override
                public int get(int element) {
                    return sounds.getVolume(element);
                }

                @Override
                public void set(int element, int value) {
                    sounds.setVolume(element, value);
                }
            };
            default -> createExtensionColumn(kind, sounds, field, length);
        };
    }


    private ScrDatBindingUtils() {
    }
}
