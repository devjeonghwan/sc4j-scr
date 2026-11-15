package org.fogofwarlabs.sc4j.scr.game.save.pool;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerUpgrades;
import org.fogofwarlabs.sc4j.scr.game.save.SaveGameUtils;
import org.fogofwarlabs.sc4j.scr.game.save.SaveReferences;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.*;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;

/**
 * Converts a unit to the classic {@code 336}-byte saved record and restores it.
 * <p>
 * Pointers become {@link SaveReferences} values. The kind-dependent union is written in a fixed kind order, a later kind overwriting the DWORDs of an
 * earlier one. The list links, the owner links and the generation belong to the unit pool ({@link UnitPoolSection}). The record keeps three padding
 * bytes (zero unless an EUD map writes them) and the WORD after the order target type. The local event bytes are the saving interface's
 * ({@link Unit#getLocalEventBytes()}), zero in a game that was not loaded.
 */
public final class UnitSaveRecordUtils {
    /**
     * The size of the classic unit.
     */
    public static final int SIZE = 0x150;

    private static final int BYTE_MASK = 0xFF;
    private static final int WORD_MASK = 0xFFFF;

    private static final int HIT_POINTS = 0x08;
    private static final int SPRITE = 0x0C;
    private static final int MOVEMENT_DESTINATION_X = 0x10;
    private static final int MOVEMENT_DESTINATION_Y = 0x12;
    private static final int MOVEMENT_TARGET = 0x14;
    private static final int WAYPOINT_X = 0x18;
    private static final int WAYPOINT_Y = 0x1A;
    private static final int DIRECTION_TARGET_X = 0x1C;
    private static final int DIRECTION_TARGET_Y = 0x1E;
    private static final int MOVEMENT_FLAGS = 0x20;
    private static final int HEADING = 0x21;
    private static final int TURN_RATE = 0x22;
    private static final int TARGET_HEADING = 0x23;
    private static final int FLINGY_TYPE = 0x24;
    private static final int MOVEMENT_CONTROL = 0x27;
    private static final int POSITION_X = 0x28;
    private static final int POSITION_Y = 0x2A;
    private static final int FIXED_POSITION_X = 0x2C;
    private static final int FIXED_POSITION_Y = 0x30;
    private static final int TOP_SPEED = 0x34;
    private static final int CURRENT_SPEED = 0x38;
    private static final int NEXT_SPEED = 0x3C;
    private static final int VELOCITY_X = 0x40;
    private static final int VELOCITY_Y = 0x44;
    private static final int ACCELERATION = 0x48;
    private static final int MOVEMENT_DIRECTION = 0x4A;
    private static final int DESIRED_MOVEMENT_DIRECTION = 0x4B;

    private static final int OWNER = 0x4C;
    private static final int PRIMARY_ORDER = 0x4D;
    private static final int PRIMARY_ORDER_STATE = 0x4E;
    private static final int ORDER_SIGNAL = 0x4F;
    private static final int ORDER_TARGET_TYPE = 0x50;
    private static final int COOLDOWN_TIMERS = 0x54;
    private static final int ORDER_TARGET_X = 0x58;
    private static final int ORDER_TARGET_Y = 0x5A;
    private static final int ORDER_TARGET = 0x5C;

    private static final int SHIELD_POINTS = 0x60;
    private static final int UNIT_TYPE = 0x64;
    private static final int SPARE_WORD = 0x66;
    private static final int SUBUNIT = 0x70;
    private static final int FIRST_ORDER = 0x74;
    private static final int LAST_ORDER = 0x78;
    private static final int AUTOMATIC_TARGET = 0x7C;
    private static final int CONNECTED_UNIT = 0x80;
    private static final int HIGHLIGHTED_ORDER_COUNT = 0x84;
    private static final int PRIMARY_ORDER_COUNTDOWN = 0x85;
    private static final int CLASSIC_UNUSED_BYTE = 0x86;
    private static final int ALERT_MARK = 0x87;
    private static final int PREVIOUS_UNIT_TYPE = 0x88;
    private static final int LOCAL_EVENT_BYTES = 0x8A;
    private static final int CLASSIC_EVENT_BYTES = 0x8C;
    private static final int KILL_COUNT = 0x8F;
    private static final int LAST_ATTACKING_PLAYER = 0x90;
    private static final int VISIBILITY_UPDATE_COUNTDOWN = 0x91;
    private static final int SPELL_TARGET_FLAGS = 0x92;
    private static final int USER_ACTION_FLAGS = 0x93;
    private static final int CURRENT_BUTTON_SET = 0x94;
    private static final int CLOAK_COUNTER = 0x96;
    private static final int MOVEMENT_STATE = 0x97;
    private static final int BUILD_QUEUE = 0x98;
    private static final int BUILD_QUEUE_SECOND = BUILD_QUEUE + Short.BYTES;
    private static final int BUILD_QUEUE_THIRD = BUILD_QUEUE + 2 * Short.BYTES;
    private static final int BUILD_QUEUE_FOURTH = BUILD_QUEUE + 3 * Short.BYTES;
    private static final int BUILD_QUEUE_FIFTH = BUILD_QUEUE + 4 * Short.BYTES;
    private static final int ENERGY = 0xA2;
    private static final int BUILD_QUEUE_INDEX = 0xA4;
    private static final int SECONDARY_ORDER = 0xA6;
    private static final int DAMAGE_OVERLAY_COUNT = 0xA7;
    private static final int CONSTRUCTION_HIT_POINT_INCREMENT = 0xA8;
    private static final int CONSTRUCTION_SHIELD_INCREMENT = 0xAA;
    private static final int REMAINING_BUILD_TIME = 0xAC;
    private static final int PREVIOUS_HIT_POINTS = 0xAE;
    private static final int LOADED_UNITS = 0xB0;

    private static final int UNION_FIRST = 0xC0;
    private static final int UNION_SECOND = 0xC4;
    private static final int UNION_SECOND_HIGH = 0xC6;
    private static final int UNION_THIRD = 0xC8;
    private static final int UNION_THIRD_BYTE_1 = 0xC9;
    private static final int UNION_THIRD_BYTE_2 = 0xCA;
    private static final int UNION_THIRD_BYTE_3 = 0xCB;
    private static final int UNION_FOURTH = 0xCC;
    private static final int UNION_FOURTH_BYTE_1 = 0xCD;
    private static final int UNION_FOURTH_BYTE_2 = 0xCE;
    private static final int UNION_FOURTH_BYTE_3 = 0xCF;
    private static final int UNION_FIFTH = 0xD0;
    private static final int UNION_FIFTH_HIGH = 0xD2;
    private static final int UNION_FIFTH_BYTE_3 = 0xD3;
    private static final int UNION_SIXTH = 0xD4;
    private static final int UNION_SIXTH_HIGH = 0xD6;
    private static final int UNION_SEVENTH = 0xD8;
    private static final int UNION_SEVENTH_BYTE_1 = 0xD9;

    private static final int STATUS = 0xDC;
    private static final int CARRYING_FLAGS = 0xE0;
    private static final int WIREFRAME_RANDOMIZER = 0xE1;
    private static final int SECONDARY_ORDER_STATE = 0xE2;
    private static final int MOVEMENT_DESTINATION_COUNTDOWN = 0xE3;
    private static final int DETECTION_FLAGS = 0xE4;
    private static final int SECONDARY_ORDER_POSITION = 0xE8;
    private static final int CURRENT_BUILD_UNIT = 0xEC;
    private static final int PREVIOUS_CLOAKED_UNIT = 0xF0;
    private static final int NEXT_CLOAKED_UNIT = 0xF4;
    private static final int KIND_LOW = 0xF8;
    private static final int KIND_HIGH = 0xFC;
    private static final int PATH = 0x100;
    private static final int PATH_RECALCULATION_COUNTDOWN = 0x104;
    private static final int PATH_COLLISION_FLAGS = 0x105;
    private static final int HEALING_STATE = 0x107;
    private static final int TERRAIN_COLLISION_LEFT = 0x108;
    private static final int TERRAIN_COLLISION_TOP = 0x10A;
    private static final int TERRAIN_COLLISION_RIGHT = 0x10C;
    private static final int TERRAIN_COLLISION_BOTTOM = 0x10E;
    private static final int REMAINING_LIFETIME = 0x110;
    private static final int DEFENSIVE_MATRIX_HEALTH = 0x112;
    private static final int DEFENSIVE_MATRIX_TIMER = 0x114;
    private static final int SPEED_INCREASE = 0x115;
    private static final int SPEED_DECREASE = 0x116;
    private static final int LOCKDOWN_TIMER = 0x117;
    private static final int IRRADIATE_TIMER = 0x118;
    private static final int STASIS_TIMER = 0x119;
    private static final int PLAGUE_TIMER = 0x11A;
    private static final int STORM_TIMER = 0x11B;
    private static final int IRRADIATED_BY = 0x11C;
    private static final int IRRADIATE_OWNER = 0x120;
    private static final int PARASITE_FLAGS = 0x121;
    private static final int STATUS_CYCLE_ELAPSED = 0x122;
    private static final int BLINDNESS_STATE = 0x123;
    private static final int MAELSTROM_TIMER = 0x124;
    private static final int ACID_SPORE_COUNT = 0x126;
    private static final int ACID_SPORE_TIMERS = 0x127;
    private static final int NEXT_HIT_NEAR_TARGET = 0x130;
    private static final int LOADED_UNIT_EXTENSION_BITS = 0x132;
    private static final int COMPUTER_RECORD = 0x134;
    private static final int AIR_STRENGTH = 0x138;
    private static final int GROUND_STRENGTH = 0x13A;
    private static final int MINIMUM_HORIZONTAL_EDGE = 0x13C;
    private static final int MAXIMUM_HORIZONTAL_EDGE = 0x140;
    private static final int MINIMUM_VERTICAL_EDGE = 0x144;
    private static final int MAXIMUM_VERTICAL_EDGE = 0x148;
    private static final int REPULSION_FLAGS = 0x14C;
    private static final int REPULSION_DIRECTION = 0x14D;
    private static final int REPULSION_CELL_X = 0x14E;
    private static final int REPULSION_CELL_Y = 0x14F;

    /**
     * The finder mark of a unit the writer took out of the unit finder, kept in its minimum vertical edge index.
     */
    private static final int FINDER_MARK = 1;

    /**
     * Writes a unit's fields into its classic record, without the list links.
     *
     * @param unit       the unit
     * @param record     the record, little-endian, at offset {@code 0}
     * @param units      the unit type table, which decides the union kinds
     * @param references the reference conversions of the game
     */
    public static void write(@NotNull Unit unit, @NotNull ByteBuffer record, @NotNull ScrUnitTypeView units, @NotNull SaveReferences references) {
        int type = unit.getUnitId();

        record.putInt(HIT_POINTS, unit.getHitPoints());
        record.putInt(SPRITE, SaveReferences.encodeSprite(unit.getSpriteOrNull()));
        record.putShort(MOVEMENT_DESTINATION_X, (short) unit.getMovementDestinationX());
        record.putShort(MOVEMENT_DESTINATION_Y, (short) unit.getMovementDestinationY());
        record.putInt(MOVEMENT_TARGET, references.encodeUnit(unit.getMovementTargetUnit()));
        record.putShort(WAYPOINT_X, (short) unit.getMovementWaypointX());
        record.putShort(WAYPOINT_Y, (short) unit.getMovementWaypointY());
        record.putShort(DIRECTION_TARGET_X, (short) unit.getDirectionTargetX());
        record.putShort(DIRECTION_TARGET_Y, (short) unit.getDirectionTargetY());
        record.put(MOVEMENT_FLAGS, (byte) unit.getMovementFlags());
        record.put(HEADING, (byte) unit.getHeading());
        record.put(TURN_RATE, (byte) unit.getTurnRate());
        record.put(TARGET_HEADING, (byte) unit.getTargetHeading());
        record.putShort(FLINGY_TYPE, (short) unit.getFlingyType());
        record.put(MOVEMENT_CONTROL, (byte) unit.getMovementControl());
        record.putShort(POSITION_X, (short) unit.getPositionX());
        record.putShort(POSITION_Y, (short) unit.getPositionY());
        record.putInt(FIXED_POSITION_X, unit.getFixedPositionX());
        record.putInt(FIXED_POSITION_Y, unit.getFixedPositionY());
        record.putInt(TOP_SPEED, unit.getTopSpeed());
        record.putInt(CURRENT_SPEED, unit.getCurrentSpeed());
        record.putInt(NEXT_SPEED, unit.getNextSpeed());
        record.putInt(VELOCITY_X, unit.getVelocityX());
        record.putInt(VELOCITY_Y, unit.getVelocityY());
        record.putShort(ACCELERATION, (short) unit.getAcceleration());
        record.put(MOVEMENT_DIRECTION, (byte) unit.getMovementDirection());
        record.put(DESIRED_MOVEMENT_DIRECTION, (byte) unit.getDesiredMovementDirection());

        record.put(OWNER, (byte) unit.getOwnerIndex());
        record.put(PRIMARY_ORDER, (byte) unit.getPrimaryOrder());
        record.put(PRIMARY_ORDER_STATE, (byte) unit.getPrimaryOrderState());
        record.put(ORDER_SIGNAL, (byte) unit.getOrderSignal());
        record.putShort(ORDER_TARGET_TYPE, (short) unit.getOrderTargetUnitType());
        record.putInt(COOLDOWN_TIMERS, unit.getCooldownTimers());
        record.putShort(ORDER_TARGET_X, (short) unit.getOrderTargetX());
        record.putShort(ORDER_TARGET_Y, (short) unit.getOrderTargetY());
        record.putInt(ORDER_TARGET, references.encodeUnit(unit.getOrderTargetUnit()));

        record.putInt(SHIELD_POINTS, unit.getShieldPoints());
        record.putShort(UNIT_TYPE, (short) type);
        record.putShort(SPARE_WORD, (short) unit.getSpareWord());
        record.putInt(SUBUNIT, references.encodeUnit(unit.getSubunit()));
        record.putInt(FIRST_ORDER, references.encodeOrder(unit.getFirstOrder()));
        record.putInt(LAST_ORDER, references.encodeOrder(unit.getLastOrder()));
        record.putInt(AUTOMATIC_TARGET, references.encodeUnit(unit.getAutomaticTargetUnit()));
        record.putInt(CONNECTED_UNIT, references.encodeUnit(unit.getParentUnit()));
        record.put(HIGHLIGHTED_ORDER_COUNT, (byte) unit.getHighlightedOrderCount());
        record.put(PRIMARY_ORDER_COUNTDOWN, (byte) unit.getPrimaryOrderCountdown());
        record.put(CLASSIC_UNUSED_BYTE, (byte) unit.getClassicUnusedByte());
        record.put(ALERT_MARK, (byte) unit.getAlertMark());
        record.putShort(PREVIOUS_UNIT_TYPE, (short) unit.getPreviousUnitType());
        record.putShort(LOCAL_EVENT_BYTES, (short) unit.getLocalEventBytes());
        record.putInt(CLASSIC_EVENT_BYTES, unit.getClassicEventBytes());
        record.put(KILL_COUNT, (byte) unit.getKillCount());
        record.put(LAST_ATTACKING_PLAYER, (byte) unit.getLastAttackingPlayer());
        record.put(VISIBILITY_UPDATE_COUNTDOWN, (byte) unit.getVisibilityUpdateCountdown());
        record.put(SPELL_TARGET_FLAGS, (byte) unit.getSpellTargetFlags());
        record.put(USER_ACTION_FLAGS, (byte) unit.getUserActionFlags());
        record.putShort(CURRENT_BUTTON_SET, (short) unit.getCurrentButtonSet());
        record.put(CLOAK_COUNTER, (byte) unit.getCloakCounter());
        record.put(MOVEMENT_STATE, (byte) unit.getMovementState());
        record.putShort(BUILD_QUEUE, (short) unit.getFirstBuildQueueType());
        record.putShort(BUILD_QUEUE_SECOND, (short) unit.getSecondBuildQueueType());
        record.putShort(BUILD_QUEUE_THIRD, (short) unit.getThirdBuildQueueType());
        record.putShort(BUILD_QUEUE_FOURTH, (short) unit.getFourthBuildQueueType());
        record.putShort(BUILD_QUEUE_FIFTH, (short) unit.getFifthBuildQueueType());
        record.putShort(ENERGY, (short) unit.getEnergy());
        record.put(BUILD_QUEUE_INDEX, (byte) unit.getBuildQueueIndex());
        record.put(SECONDARY_ORDER, (byte) unit.getSecondaryOrder());
        record.put(DAMAGE_OVERLAY_COUNT, (byte) unit.getDamageOverlayCount());
        record.putShort(CONSTRUCTION_HIT_POINT_INCREMENT, (short) unit.getConstructionHitPointIncrement());
        record.putShort(CONSTRUCTION_SHIELD_INCREMENT, (short) unit.getConstructionShieldIncrement());
        record.putShort(REMAINING_BUILD_TIME, (short) unit.getRemainingBuildTime());
        record.putShort(PREVIOUS_HIT_POINTS, (short) unit.getPreviousVisibleHitPoints());

        for (int slot = 0; slot < Unit.LOADED_UNIT_SLOTS; slot++) {
            record.putShort(LOADED_UNITS + slot * Short.BYTES, (short) unit.getLoadedUnitHandle(slot));
        }

        writeUnion(unit, record, units, references);

        record.putInt(STATUS, unit.getStatus());
        record.put(CARRYING_FLAGS, (byte) unit.getCarryingFlags());
        record.put(WIREFRAME_RANDOMIZER, (byte) unit.getWireframeRandomizer());
        record.put(SECONDARY_ORDER_STATE, (byte) unit.getSecondaryOrderState());
        record.put(MOVEMENT_DESTINATION_COUNTDOWN, (byte) unit.getMovementDestinationCountdown());
        record.putInt(DETECTION_FLAGS, unit.getDetectionFlags());
        record.putInt(SECONDARY_ORDER_POSITION, unit.getSecondaryOrderPosition());
        record.putInt(CURRENT_BUILD_UNIT, references.encodeUnit(unit.getCurrentBuildUnit()));

        if (!UnitQueries.isBuilding(units, type)) {
            record.putInt(PREVIOUS_CLOAKED_UNIT, references.encodeUnit(unit.getPreviousCloakedUnit()));
            record.putInt(NEXT_CLOAKED_UNIT, references.encodeUnit(unit.getNextCloakedUnit()));
        }

        if (type == ScrUnitIds.PROTOSS_PYLON) {
            record.putInt(KIND_LOW, references.encodeUnit(unit.getPreviousPsiProvider()));
            record.putInt(KIND_HIGH, references.encodeUnit(unit.getNextPsiProvider()));
        }

        if (units.isRallyProducer(type)) {
            record.putShort(KIND_LOW, (short) unit.getRallyPositionX());
            record.putShort(KIND_LOW + Short.BYTES, (short) unit.getRallyPositionY());
            record.putInt(KIND_HIGH, references.encodeUnit(unit.getRallyTargetUnit()));
        }

        record.putInt(PATH, references.encodePath(unit.getPathIndex()));
        record.put(PATH_RECALCULATION_COUNTDOWN, (byte) unit.getPathRecalculationCountdown());
        record.put(PATH_COLLISION_FLAGS, (byte) unit.getPathCollisionFlags());
        record.put(HEALING_STATE, (byte) unit.getHealingState());
        record.putShort(TERRAIN_COLLISION_LEFT, (short) unit.getTerrainCollisionLeft());
        record.putShort(TERRAIN_COLLISION_TOP, (short) unit.getTerrainCollisionTop());
        record.putShort(TERRAIN_COLLISION_RIGHT, (short) unit.getTerrainCollisionRight());
        record.putShort(TERRAIN_COLLISION_BOTTOM, (short) unit.getTerrainCollisionBottom());
        record.putShort(REMAINING_LIFETIME, (short) unit.getRemainingLifetime());
        record.putShort(DEFENSIVE_MATRIX_HEALTH, (short) unit.getDefensiveMatrixHealth());
        record.put(DEFENSIVE_MATRIX_TIMER, (byte) unit.getDefensiveMatrixTimer());
        record.put(SPEED_INCREASE, (byte) unit.getSpeedIncreaseByte());
        record.put(SPEED_DECREASE, (byte) unit.getSpeedDecreaseByte());
        record.put(LOCKDOWN_TIMER, (byte) unit.getLockdownTimer());
        record.put(IRRADIATE_TIMER, (byte) unit.getIrradiateTimer());
        record.put(STASIS_TIMER, (byte) unit.getStasisTimer());
        record.put(PLAGUE_TIMER, (byte) unit.getPlagueTimer());
        record.put(STORM_TIMER, (byte) unit.getStormTimer());
        record.putInt(IRRADIATED_BY, references.encodeUnit(unit.getIrradiatedBy()));
        record.put(IRRADIATE_OWNER, (byte) unit.getIrradiateOwner());
        record.put(PARASITE_FLAGS, (byte) unit.getParasiteFlags());
        record.put(STATUS_CYCLE_ELAPSED, (byte) unit.getStatusCycleElapsed());
        record.put(BLINDNESS_STATE, (byte) unit.getBlindnessState());
        record.put(MAELSTROM_TIMER, (byte) unit.getMaelstromTimer());
        record.put(ACID_SPORE_COUNT, (byte) unit.getAcidSporeCount());

        for (int index = 0; index < Unit.ACID_SPORE_SLOTS; index++) {
            record.put(ACID_SPORE_TIMERS + index, (byte) unit.getAcidSporeTimer(index));
        }

        record.putShort(NEXT_HIT_NEAR_TARGET, (short) unit.getNextHitNearTargetIndex());
        record.putShort(LOADED_UNIT_EXTENSION_BITS, (short) unit.getLoadedUnitHandleExtensionBits());
        record.putInt(COMPUTER_RECORD, references.encodeComputerRecord(unit.getComputerRecord()));
        record.putShort(AIR_STRENGTH, (short) unit.getAirStrength());
        record.putShort(GROUND_STRENGTH, (short) unit.getGroundStrength());
        record.putInt(MINIMUM_HORIZONTAL_EDGE, unit.getMinimumHorizontalEdgeIndex());
        record.putInt(MAXIMUM_HORIZONTAL_EDGE, unit.getMaximumHorizontalEdgeIndex());
        record.putInt(MINIMUM_VERTICAL_EDGE, unit.getMinimumVerticalEdgeIndex());
        record.putInt(MAXIMUM_VERTICAL_EDGE, unit.getMaximumVerticalEdgeIndex());
        record.put(REPULSION_FLAGS, (byte) unit.getRepulsionFlags());
        record.put(REPULSION_DIRECTION, (byte) unit.getRepulsionDirection());
        record.put(REPULSION_CELL_X, (byte) unit.getRepulsionCellX());
        record.put(REPULSION_CELL_Y, (byte) unit.getRepulsionCellY());
    }

    /**
     * Writes the kind-dependent union in a fixed order: building, worker, Carrier or Reaver, Interceptor or Scarab and
     * Vulture for the first four DWORDs; Nuclear Silo, Nydus Canal, nuke caster, Pylon, powerup, resource, worker and
     * Hatchery for the last three.
     *
     * @param unit       the unit
     * @param record     the record
     * @param units      the unit type table
     * @param references the reference conversions of the game
     */
    private static void writeUnion(@NotNull Unit unit,
                                   @NotNull ByteBuffer record,
                                   @NotNull ScrUnitTypeView units,
                                   @NotNull SaveReferences references) {
        int type = unit.getUnitId();

        if (UnitQueries.isGroundedBuilding(unit) || UnitQueries.isBuilding(units, type)) {
            record.putInt(UNION_FIRST, references.encodeUnit(unit.getAddon()));
            record.putShort(UNION_SECOND, (short) unit.getAddonBuildType());
            record.putShort(UNION_SECOND_HIGH, (short) unit.getUpgradeResearchTime());
            record.put(UNION_THIRD, (byte) unit.getResearchType());
            record.put(UNION_THIRD_BYTE_1, (byte) unit.getUpgradeType());
            record.put(UNION_THIRD_BYTE_2, (byte) unit.getLarvaSpawnCountdown());
            record.put(UNION_THIRD_BYTE_3, (byte) unit.getLandingByte());
            record.put(UNION_FOURTH, (byte) unit.getCreepTimer());
            record.put(UNION_FOURTH_BYTE_1, (byte) unit.getUpgradeLevel());
            record.putShort(UNION_FOURTH_BYTE_2, (short) 0);
        }

        if (isWorker(units, type)) {
            record.putInt(UNION_FIRST, references.encodeUnit(unit.getCarriedPowerup()));
            record.putShort(UNION_SECOND, (short) unit.getTargetResourceX());
            record.putShort(UNION_SECOND_HIGH, (short) unit.getTargetResourceY());
            record.putInt(UNION_THIRD, references.encodeUnit(unit.getTargetResourceUnit()));
            record.putShort(UNION_FOURTH, (short) unit.getRepairTimer());
            record.put(UNION_FOURTH_BYTE_2, (byte) unit.getGatheringResourceByte());
            record.put(UNION_FOURTH_BYTE_3, (byte) unit.getCarriedResourceAmount());
        }

        if (units.hasHangar(type)) {
            record.putInt(UNION_FIRST, references.encodeUnit(unit.getFirstHangarUnit(false)));
            record.putInt(UNION_SECOND, references.encodeUnit(unit.getFirstHangarUnit(true)));
            record.put(UNION_THIRD, (byte) unit.getHangarCount(false));
            record.put(UNION_THIRD_BYTE_1, (byte) unit.getHangarCount(true));
            record.put(UNION_THIRD_BYTE_2, (byte) unit.getHangarSpareByte());
            record.put(UNION_THIRD_BYTE_3, (byte) 0);
        }

        if (isFighter(type)) {
            record.putInt(UNION_FIRST, references.encodeUnit(unit.getHangarParent()));
            record.putInt(UNION_SECOND, references.encodeUnit(unit.getPreviousHangarUnit()));
            record.putInt(UNION_THIRD, references.encodeUnit(unit.getNextHangarUnit()));
            record.put(UNION_FOURTH, (byte) unit.getHangarOutsideByte());
            record.putShort(UNION_FOURTH_BYTE_1, (short) 0);
            record.put(UNION_FOURTH_BYTE_3, (byte) 0);
        }

        if (units.isVulture(type)) {
            record.put(UNION_FIRST, (byte) unit.getSpiderMineCount());
        }

        if (type == ScrUnitIds.TERRAN_NUCLEAR_SILO) {
            record.putInt(UNION_FIFTH, references.encodeUnit(unit.getSiloNuke()));
            record.putInt(UNION_SIXTH, unit.getSiloReadyDword());
        }

        if (type == ScrUnitIds.ZERG_NYDUS_CANAL) {
            record.putInt(UNION_FIFTH, references.encodeUnit(unit.getNydusExit()));
        }

        if (units.isGhost(type)) {
            record.putInt(UNION_FIFTH, references.encodeThingy(unit.getNukeDot()));
        }

        if (type == ScrUnitIds.PROTOSS_PYLON) {
            record.putInt(UNION_FIFTH, SaveReferences.encodeSprite(unit.getPsiFieldSprite()));
        }

        if ((units.getFlags(type) & UnitTypeFlags.POWERUP) != 0) {
            record.putShort(UNION_FIFTH, (short) unit.getResources());
            record.putShort(UNION_FIFTH_HIGH, (short) unit.getSecondLarvaSideCount());
            record.putInt(UNION_SIXTH, references.encodeUnit(unit.getPowerupCarrier()));
        }

        if ((units.getFlags(type) & UnitTypeFlags.RESOURCE_CONTAINER) != 0) {
            record.putShort(UNION_FIFTH, (short) unit.getResources());
            record.put(UNION_FIFTH_HIGH, (byte) unit.getResourceAnimation());
            record.put(UNION_FIFTH_BYTE_3, (byte) unit.getResourceBeingGatheredByte());
            record.putInt(UNION_SIXTH, references.encodeUnit(unit.getFirstGatheringWorker()));
            record.put(UNION_SEVENTH, (byte) unit.getResourceAreaIndex());
            record.put(UNION_SEVENTH_BYTE_1, (byte) unit.getResourceClaimByte());
            record.putShort(UNION_SEVENTH + Short.BYTES, (short) 0);
        }

        if (isWorker(units, type)) {
            record.putInt(UNION_FIFTH, references.encodeUnit(unit.getGatherTarget()));
            record.putInt(UNION_SIXTH, references.encodeUnit(unit.getPreviousGatheringWorker()));
            record.putInt(UNION_SEVENTH, references.encodeUnit(unit.getNextGatheringWorker()));
        }

        if (isHatchery(type)) {
            record.putShort(UNION_FIFTH, (short) unit.getResources());
            record.putShort(UNION_FIFTH_HIGH, (short) unit.getSecondLarvaSideCount());
            record.putShort(UNION_SIXTH, (short) unit.getThirdLarvaSideCount());
            record.putShort(UNION_SIXTH_HIGH, (short) unit.getFourthLarvaSideCount());
        }
    }

    /**
     * Reads a unit's fields from its classic record ({@link #write}) into a cleared pool slot.
     * The kinds of the read unit type fill their union fields.
     *
     * @param unit       the unit, whose slot the unit pool cleared
     * @param record     the record, little-endian, at offset {@code 0}
     * @param units      the unit type table, which decides the union kinds
     * @param upgrades   the players' upgrade levels, read before the units, or {@code null}
     * @param references the reference conversions of the game
     */
    public static void read(@NotNull Unit unit,
                            @NotNull ByteBuffer record,
                            @NotNull ScrUnitTypeView units,
                            @Nullable PlayerUpgrades upgrades,
                            @NotNull SaveReferences references) {
        int type = record.getShort(UNIT_TYPE) & WORD_MASK;

        unit.setHitPoints(record.getInt(HIT_POINTS));
        unit.setSprite(references.decodeSprite(record.getInt(SPRITE)));
        unit.setMovementDestinationX(record.getShort(MOVEMENT_DESTINATION_X));
        unit.setMovementDestinationY(record.getShort(MOVEMENT_DESTINATION_Y));
        unit.setMovementTargetUnit(references.decodeUnit(record.getInt(MOVEMENT_TARGET)));
        unit.setMovementWaypointX(record.getShort(WAYPOINT_X));
        unit.setMovementWaypointY(record.getShort(WAYPOINT_Y));
        unit.setDirectionTargetX(record.getShort(DIRECTION_TARGET_X));
        unit.setDirectionTargetY(record.getShort(DIRECTION_TARGET_Y));
        unit.setMovementFlags(record.get(MOVEMENT_FLAGS) & BYTE_MASK);
        unit.setHeading(record.get(HEADING) & BYTE_MASK);
        unit.setTurnRate(record.get(TURN_RATE) & BYTE_MASK);
        unit.setTargetHeading(record.get(TARGET_HEADING) & BYTE_MASK);
        unit.setFlingyType(record.getShort(FLINGY_TYPE) & WORD_MASK);
        unit.setMovementControl(record.get(MOVEMENT_CONTROL) & BYTE_MASK);
        unit.setPositionX(record.getShort(POSITION_X));
        unit.setPositionY(record.getShort(POSITION_Y));
        unit.setFixedPositionX(record.getInt(FIXED_POSITION_X));
        unit.setFixedPositionY(record.getInt(FIXED_POSITION_Y));
        unit.setTopSpeed(record.getInt(TOP_SPEED));
        unit.setCurrentSpeed(record.getInt(CURRENT_SPEED));
        unit.setNextSpeed(record.getInt(NEXT_SPEED));
        unit.setVelocityX(record.getInt(VELOCITY_X));
        unit.setVelocityY(record.getInt(VELOCITY_Y));
        unit.setAcceleration(record.getShort(ACCELERATION) & WORD_MASK);
        unit.setMovementDirection(record.get(MOVEMENT_DIRECTION) & BYTE_MASK);
        unit.setDesiredMovementDirection(record.get(DESIRED_MOVEMENT_DIRECTION) & BYTE_MASK);

        unit.setOwnerIndex(record.get(OWNER) & BYTE_MASK);
        unit.setPrimaryOrder(record.get(PRIMARY_ORDER) & BYTE_MASK);
        unit.setPrimaryOrderState(record.get(PRIMARY_ORDER_STATE) & BYTE_MASK);
        unit.setOrderSignal(record.get(ORDER_SIGNAL) & BYTE_MASK);
        unit.setOrderTargetUnitType(record.getShort(ORDER_TARGET_TYPE) & WORD_MASK);
        unit.setCooldownTimers(record.getInt(COOLDOWN_TIMERS));
        unit.setOrderTargetX(record.getShort(ORDER_TARGET_X));
        unit.setOrderTargetY(record.getShort(ORDER_TARGET_Y));
        unit.setOrderTargetUnit(references.decodeUnit(record.getInt(ORDER_TARGET)));

        unit.setShieldPoints(record.getInt(SHIELD_POINTS));
        unit.setUnitId(type);
        unit.setSpareWord(record.getShort(SPARE_WORD) & WORD_MASK);
        unit.setSubunit(references.decodeUnit(record.getInt(SUBUNIT)));
        unit.setFirstOrder(references.decodeOrder(record.getInt(FIRST_ORDER)));
        unit.setLastOrder(references.decodeOrder(record.getInt(LAST_ORDER)));
        unit.setAutomaticTargetUnit(references.decodeUnit(record.getInt(AUTOMATIC_TARGET)));
        unit.setParentUnit(references.decodeUnit(record.getInt(CONNECTED_UNIT)));
        unit.setHighlightedOrderCount(record.get(HIGHLIGHTED_ORDER_COUNT) & BYTE_MASK);
        unit.setPrimaryOrderCountdown(record.get(PRIMARY_ORDER_COUNTDOWN) & BYTE_MASK);
        unit.setClassicUnusedByte(record.get(CLASSIC_UNUSED_BYTE) & BYTE_MASK);
        unit.setAlertMark(record.get(ALERT_MARK) & BYTE_MASK);
        unit.setPreviousUnitType(record.getShort(PREVIOUS_UNIT_TYPE) & WORD_MASK);
        unit.setLocalEventBytes(record.getShort(LOCAL_EVENT_BYTES));
        unit.setClassicEventBytes(record.getInt(CLASSIC_EVENT_BYTES));
        unit.setKillCount(record.get(KILL_COUNT) & BYTE_MASK);
        unit.setLastAttackingPlayer(record.get(LAST_ATTACKING_PLAYER) & BYTE_MASK);
        unit.setVisibilityUpdateCountdown(record.get(VISIBILITY_UPDATE_COUNTDOWN) & BYTE_MASK);
        unit.setSpellTargetFlags(record.get(SPELL_TARGET_FLAGS) & BYTE_MASK);
        unit.setUserActionFlags(record.get(USER_ACTION_FLAGS) & BYTE_MASK);
        unit.setCurrentButtonSet(record.getShort(CURRENT_BUTTON_SET) & WORD_MASK);
        unit.setCloakCounter(record.get(CLOAK_COUNTER) & BYTE_MASK);
        unit.setMovementState(record.get(MOVEMENT_STATE) & BYTE_MASK);
        unit.setFirstBuildQueueType(record.getShort(BUILD_QUEUE) & WORD_MASK);
        unit.setSecondBuildQueueType(record.getShort(BUILD_QUEUE_SECOND) & WORD_MASK);
        unit.setThirdBuildQueueType(record.getShort(BUILD_QUEUE_THIRD) & WORD_MASK);
        unit.setFourthBuildQueueType(record.getShort(BUILD_QUEUE_FOURTH) & WORD_MASK);
        unit.setFifthBuildQueueType(record.getShort(BUILD_QUEUE_FIFTH) & WORD_MASK);
        unit.setEnergy(record.getShort(ENERGY) & WORD_MASK);
        unit.setBuildQueueIndex(record.get(BUILD_QUEUE_INDEX) & BYTE_MASK);
        unit.setSecondaryOrderByte(record.get(SECONDARY_ORDER) & BYTE_MASK);
        unit.setDamageOverlayCount(record.get(DAMAGE_OVERLAY_COUNT) & BYTE_MASK);
        unit.setConstructionHitPointIncrement(record.getShort(CONSTRUCTION_HIT_POINT_INCREMENT) & WORD_MASK);
        unit.setConstructionShieldIncrement(record.getShort(CONSTRUCTION_SHIELD_INCREMENT) & WORD_MASK);
        unit.setRemainingBuildTime(record.getShort(REMAINING_BUILD_TIME) & WORD_MASK);
        unit.setPreviousVisibleHitPoints(record.getShort(PREVIOUS_HIT_POINTS) & WORD_MASK);

        for (int slot = 0; slot < Unit.LOADED_UNIT_SLOTS; slot++) {
            unit.setLoadedUnitHandle(slot, record.getShort(LOADED_UNITS + slot * Short.BYTES) & WORD_MASK);
        }

        readUnion(unit, type, record, units, upgrades, references);

        unit.setStatus(record.getInt(STATUS));
        unit.setCarryingFlags(record.get(CARRYING_FLAGS) & BYTE_MASK);
        unit.setWireframeRandomizer(record.get(WIREFRAME_RANDOMIZER) & BYTE_MASK);
        unit.setSecondaryOrderState(record.get(SECONDARY_ORDER_STATE) & BYTE_MASK);
        unit.setMovementDestinationCountdown(record.get(MOVEMENT_DESTINATION_COUNTDOWN) & BYTE_MASK);
        unit.setDetectionFlags(record.getInt(DETECTION_FLAGS));
        unit.setSecondaryOrderPosition(record.getInt(SECONDARY_ORDER_POSITION));
        unit.setCurrentBuildUnit(references.decodeUnit(record.getInt(CURRENT_BUILD_UNIT)));

        if (!UnitQueries.isBuilding(units, type)) {
            unit.setPreviousCloakedUnit(references.decodeUnit(record.getInt(PREVIOUS_CLOAKED_UNIT)));
            unit.setNextCloakedUnit(references.decodeUnit(record.getInt(NEXT_CLOAKED_UNIT)));
        }

        if (type == ScrUnitIds.PROTOSS_PYLON) {
            unit.setPreviousPsiProvider(references.decodeUnit(record.getInt(KIND_LOW)));
            unit.setNextPsiProvider(references.decodeUnit(record.getInt(KIND_HIGH)));
        }

        if (units.isRallyProducer(type)) {
            unit.setRallyPositionX(record.getShort(KIND_LOW));
            unit.setRallyPositionY(record.getShort(KIND_LOW + Short.BYTES));
            unit.setRallyTargetUnit(references.decodeUnit(record.getInt(KIND_HIGH)));
        }

        unit.setPathIndex(references.decodePath(record.getInt(PATH)));
        unit.setPathRecalculationCountdown(record.get(PATH_RECALCULATION_COUNTDOWN) & BYTE_MASK);
        unit.setPathCollisionFlags(record.get(PATH_COLLISION_FLAGS) & BYTE_MASK);
        unit.setHealingState(record.get(HEALING_STATE) & BYTE_MASK);
        unit.setTerrainCollisionLeft(record.getShort(TERRAIN_COLLISION_LEFT));
        unit.setTerrainCollisionTop(record.getShort(TERRAIN_COLLISION_TOP));
        unit.setTerrainCollisionRight(record.getShort(TERRAIN_COLLISION_RIGHT));
        unit.setTerrainCollisionBottom(record.getShort(TERRAIN_COLLISION_BOTTOM));
        unit.setRemainingLifetime(record.getShort(REMAINING_LIFETIME) & WORD_MASK);
        unit.setDefensiveMatrixHealth(record.getShort(DEFENSIVE_MATRIX_HEALTH));
        unit.setDefensiveMatrixTimer(record.get(DEFENSIVE_MATRIX_TIMER) & BYTE_MASK);
        unit.setSpeedIncreaseByte(record.get(SPEED_INCREASE) & BYTE_MASK);
        unit.setSpeedDecreaseByte(record.get(SPEED_DECREASE) & BYTE_MASK);
        unit.setLockdownTimer(record.get(LOCKDOWN_TIMER) & BYTE_MASK);
        unit.setIrradiateTimer(record.get(IRRADIATE_TIMER) & BYTE_MASK);
        unit.setStasisTimer(record.get(STASIS_TIMER) & BYTE_MASK);
        unit.setPlagueTimer(record.get(PLAGUE_TIMER) & BYTE_MASK);
        unit.setStormTimer(record.get(STORM_TIMER) & BYTE_MASK);
        unit.setIrradiatedBy(references.decodeUnit(record.getInt(IRRADIATED_BY)));
        unit.setIrradiateOwner(record.get(IRRADIATE_OWNER) & BYTE_MASK);
        unit.setParasiteFlags(record.get(PARASITE_FLAGS) & BYTE_MASK);
        unit.setStatusCycleElapsed(record.get(STATUS_CYCLE_ELAPSED) & BYTE_MASK);
        unit.setBlindnessState(record.get(BLINDNESS_STATE) & BYTE_MASK);
        unit.setMaelstromTimer(record.get(MAELSTROM_TIMER) & BYTE_MASK);
        unit.setAcidSporeCount(record.get(ACID_SPORE_COUNT) & BYTE_MASK);

        for (int index = 0; index < Unit.ACID_SPORE_SLOTS; index++) {
            unit.setAcidSporeTimer(index, record.get(ACID_SPORE_TIMERS + index) & BYTE_MASK);
        }

        unit.setNextHitNearTargetIndex(record.getShort(NEXT_HIT_NEAR_TARGET) & WORD_MASK);
        unit.setLoadedUnitHandleExtensionBits(record.getShort(LOADED_UNIT_EXTENSION_BITS) & WORD_MASK);
        unit.setComputerRecord(references.decodeComputerRecord(record.getInt(COMPUTER_RECORD)));
        unit.setAirStrength(record.getShort(AIR_STRENGTH) & WORD_MASK);
        unit.setGroundStrength(record.getShort(GROUND_STRENGTH) & WORD_MASK);
        unit.setMinimumHorizontalEdgeIndex(record.getInt(MINIMUM_HORIZONTAL_EDGE));
        unit.setMaximumHorizontalEdgeIndex(record.getInt(MAXIMUM_HORIZONTAL_EDGE));
        unit.setMinimumVerticalEdgeIndex(record.getInt(MINIMUM_VERTICAL_EDGE));
        unit.setMaximumVerticalEdgeIndex(record.getInt(MAXIMUM_VERTICAL_EDGE));
        unit.setRepulsionFlags(record.get(REPULSION_FLAGS) & BYTE_MASK);
        unit.setRepulsionDirection(record.get(REPULSION_DIRECTION) & BYTE_MASK);
        unit.setRepulsionCellX(record.get(REPULSION_CELL_X) & BYTE_MASK);
        unit.setRepulsionCellY(record.get(REPULSION_CELL_Y) & BYTE_MASK);
    }

    /**
     * Reads the kind-dependent union in the order of {@link #writeUnion}.
     *
     * @param unit       the unit
     * @param type       the read unit type
     * @param record     the record
     * @param units      the unit type table
     * @param upgrades   the players' upgrade levels, or {@code null}
     * @param references the reference conversions of the game
     */
    private static void readUnion(@NotNull Unit unit,
                                  int type,
                                  @NotNull ByteBuffer record,
                                  @NotNull ScrUnitTypeView units,
                                  @Nullable PlayerUpgrades upgrades,
                                  @NotNull SaveReferences references) {
        if ((record.getInt(STATUS) & UnitStatusFlags.GROUNDED_BUILDING) != 0 || UnitQueries.isBuilding(units, type)) {
            unit.setAddon(references.decodeUnit(record.getInt(UNION_FIRST)));
            unit.setAddonBuildType(record.getShort(UNION_SECOND) & WORD_MASK);
            unit.setUpgradeResearchTime(record.getShort(UNION_SECOND_HIGH) & WORD_MASK);
            unit.setResearchType(record.get(UNION_THIRD) & BYTE_MASK);
            unit.setUpgradeType(record.get(UNION_THIRD_BYTE_1) & BYTE_MASK);
            unit.setLarvaSpawnCountdown(record.get(UNION_THIRD_BYTE_2) & BYTE_MASK);
            unit.setLandingByte(record.get(UNION_THIRD_BYTE_3) & BYTE_MASK);
            unit.setCreepTimer(record.get(UNION_FOURTH) & BYTE_MASK);
            unit.setUpgradeLevel(record.get(UNION_FOURTH_BYTE_1) & BYTE_MASK);

            // An upgrade in progress with saved level zero takes the owner's level plus one.
            if (unit.getUpgradeType() != ScrUpgradeIds.NONE && unit.getUpgradeLevel() == 0 && upgrades != null) {
                unit.setUpgradeLevel(upgrades.getLevel(unit.getOwnerIndex(), unit.getUpgradeType()) + 1);
            }
        }

        if (isWorker(units, type)) {
            unit.setCarriedPowerup(references.decodeUnit(record.getInt(UNION_FIRST)));
            unit.setTargetResource(references.decodeUnit(record.getInt(UNION_THIRD)),
                                   record.getShort(UNION_SECOND) & WORD_MASK, record.getShort(UNION_SECOND_HIGH) & WORD_MASK);
            unit.setRepairTimer(record.getShort(UNION_FOURTH) & WORD_MASK);
            unit.setGatheringResourceByte(record.get(UNION_FOURTH_BYTE_2) & BYTE_MASK);
            unit.setCarriedResourceAmount(record.get(UNION_FOURTH_BYTE_3) & BYTE_MASK);
        }

        if (units.hasHangar(type)) {
            unit.setFirstHangarUnit(false, references.decodeUnit(record.getInt(UNION_FIRST)));
            unit.setFirstHangarUnit(true, references.decodeUnit(record.getInt(UNION_SECOND)));
            unit.setHangarCount(false, record.get(UNION_THIRD) & BYTE_MASK);
            unit.setHangarCount(true, record.get(UNION_THIRD_BYTE_1) & BYTE_MASK);
            unit.setHangarSpareByte(record.get(UNION_THIRD_BYTE_2) & BYTE_MASK);
        }

        if (isFighter(type)) {
            unit.setHangarParent(references.decodeUnit(record.getInt(UNION_FIRST)));
            unit.setPreviousHangarUnit(references.decodeUnit(record.getInt(UNION_SECOND)));
            unit.setNextHangarUnit(references.decodeUnit(record.getInt(UNION_THIRD)));
            unit.setHangarOutsideByte(record.get(UNION_FOURTH) & BYTE_MASK);
        }

        if (units.isVulture(type)) {
            unit.setSpiderMineCount(record.get(UNION_FIRST) & BYTE_MASK);
        }

        if (type == ScrUnitIds.TERRAN_NUCLEAR_SILO) {
            unit.setSiloNuke(references.decodeUnit(record.getInt(UNION_FIFTH)));
            unit.setSiloReadyDword(record.getInt(UNION_SIXTH));
        }

        if (type == ScrUnitIds.ZERG_NYDUS_CANAL) {
            unit.setNydusExit(references.decodeUnit(record.getInt(UNION_FIFTH)));
        }

        if (units.isGhost(type)) {
            unit.setNukeDot(references.decodeThingy(record.getInt(UNION_FIFTH)));
        }

        if (type == ScrUnitIds.PROTOSS_PYLON) {
            unit.setPsiFieldSprite(references.decodeSprite(record.getInt(UNION_FIFTH)));
        }

        if ((units.getFlags(type) & UnitTypeFlags.POWERUP) != 0) {
            unit.setResources(record.getShort(UNION_FIFTH) & WORD_MASK);
            unit.setSecondLarvaSideCount(record.getShort(UNION_FIFTH_HIGH) & WORD_MASK);
            unit.setPowerupCarrier(references.decodeUnit(record.getInt(UNION_SIXTH)));
        }

        if ((units.getFlags(type) & UnitTypeFlags.RESOURCE_CONTAINER) != 0) {
            unit.setResources(record.getShort(UNION_FIFTH) & WORD_MASK);
            unit.setResourceAnimation(record.get(UNION_FIFTH_HIGH) & BYTE_MASK);
            unit.setResourceBeingGatheredByte(record.get(UNION_FIFTH_BYTE_3) & BYTE_MASK);
            unit.setFirstGatheringWorker(references.decodeUnit(record.getInt(UNION_SIXTH)));
            unit.setResourceAreaIndex(record.get(UNION_SEVENTH) & BYTE_MASK);
            unit.setResourceClaimByte(record.get(UNION_SEVENTH_BYTE_1) & BYTE_MASK);
        }

        if (isWorker(units, type)) {
            unit.setGatherTarget(references.decodeUnit(record.getInt(UNION_FIFTH)));
            unit.setPreviousGatheringWorker(references.decodeUnit(record.getInt(UNION_SIXTH)));
            unit.setNextGatheringWorker(references.decodeUnit(record.getInt(UNION_SEVENTH)));
        }

        if (isHatchery(type)) {
            unit.setResources(record.getShort(UNION_FIFTH) & WORD_MASK);
            unit.setSecondLarvaSideCount(record.getShort(UNION_FIFTH_HIGH) & WORD_MASK);
            unit.setThirdLarvaSideCount(record.getShort(UNION_SIXTH) & WORD_MASK);
            unit.setFourthLarvaSideCount(record.getShort(UNION_SIXTH_HIGH) & WORD_MASK);
        }
    }

    /**
     * Restores what a saved game keeps implicitly once every saved unit is read: the head of the cloaked-unit list and each unit's membership in it,
     * and each resource's last waiting worker, the end of the gatherer chain from its first one.
     *
     * @param game             the game
     * @param firstCloakedUnit the head of the cloaked-unit list, or {@code null}
     * @throws IllegalStateException if the game has no unit pool
     */
    public static void restoreImplicitLinks(@NotNull Game game, @Nullable Unit firstCloakedUnit) {
        UnitPool pool = SaveGameUtils.require(game.getUnitSystem().getUnitPool(), "unit pool");

        game.getUnitCloaking().setFirstCloakedUnit(firstCloakedUnit);

        for (int poolIndex = 1; poolIndex <= pool.getCapacity(); poolIndex++) {
            Unit unit = pool.getUnit(poolIndex);

            if (!pool.isAllocated(poolIndex)) {
                continue;
            }

            unit.setCloakedLinked(unit == firstCloakedUnit || unit.getPreviousCloakedUnit() != null);

            Unit last = unit.getFirstGatheringWorker();

            for (int step = 0; last != null && last.getNextGatheringWorker() != null && step < pool.getCapacity(); step++) {
                last = last.getNextGatheringWorker();
            }

            unit.setLastGatheringWorker(last);
        }
    }

    /**
     * Takes a unit with finder edges out of the unit finder before record conversion. The removal clears the edges (all four indices
     * become {@code -1}), frees a grounded building's footprint and unregisters a flying unit's cell; the writer then marks the unit with
     * {@link #FINDER_MARK} in its minimum vertical edge index, the value the saved record keeps.
     *
     * @param game the game
     * @param unit the saved unit
     * @return {@code true} if the unit had finder edges and was taken out, {@code false} otherwise
     * @throws NullPointerException if the world was built without game data
     */
    public static boolean leaveUnitFinder(@NotNull Game game, @NotNull Unit unit) {
        UnitSpatialIndex spatialIndex = game.getUnitSystem().getUnitSpatialIndex();
        Sprite sprite = unit.getSpriteOrNull();

        if (spatialIndex == null || unit.getMinimumHorizontalEdgeIndex() < 0) {
            return false;
        }

        spatialIndex.remove(unit);

        if (UnitQueries.isGroundedBuilding(unit) && sprite != null) {
            game.getUnitPositioning().setFootprintOccupied(unit.getUnitId(), (short) sprite.getX(), (short) sprite.getY(), false);
        }

        if ((unit.getStatus() & UnitStatusFlags.FLYING) != 0) {
            spatialIndex.unregisterFlyingUnit(unit, game.requireGameData().getUnitTypes());
        }

        unit.setMinimumVerticalEdgeIndex(FINDER_MARK);

        return true;
    }

    /**
     * Puts a unit marked by {@link #leaveUnitFinder} (or read with the mark) back into the unit finder.
     * The mark becomes {@code -1} before the unit is registered again.
     *
     * @param game the game
     * @param unit the saved unit
     */
    public static void enterUnitFinder(@NotNull Game game, @NotNull Unit unit) {
        UnitCreationContext creation = game.getUnitSystem().getUnitCreationContext();

        if (unit.getMinimumVerticalEdgeIndex() != FINDER_MARK) {
            return;
        }

        unit.setMinimumVerticalEdgeIndex(-1);
        game.getUnitStorageConstructor().registerSpatially(unit, creation != null && creation.isFoliageEnabled());
    }

    private static boolean isWorker(@NotNull ScrUnitTypeView units, int type) {
        return type < ScrUnitIds.NONE && (units.getFlags(type) & UnitTypeFlags.WORKER) != 0;
    }

    /**
     * Tells a hangar unit type: Interceptor or Scarab.
     *
     * @param type the unit type
     * @return {@code true} if the type is launched from a hangar, {@code false} otherwise
     */
    private static boolean isFighter(int type) {
        return type == ScrUnitIds.PROTOSS_INTERCEPTOR || type == ScrUnitIds.PROTOSS_SCARAB;
    }

    /**
     * Tells a Hatchery type: Hatchery, Lair or Hive.
     *
     * @param type the unit type
     * @return {@code true} if the type spawns Larvae, {@code false} otherwise
     */
    private static boolean isHatchery(int type) {
        return type >= ScrUnitIds.ZERG_HATCHERY && type <= ScrUnitIds.ZERG_HIVE;
    }


    private UnitSaveRecordUtils() {
    }
}
