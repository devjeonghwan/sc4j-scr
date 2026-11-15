package org.fogofwarlabs.sc4j.scr.game.frame;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.unit.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Tests power refresh, active-list redistribution and loaded handles.
 */
@RequiresInstallation
final class UnitPhaseTest {
    private static final int MAP_SIDE_TILES = 8;
    private static final int PLAYER_TYPE_LOOKUP_COUNT = 16;
    private static final int DISPLAY_REFRESH_CONTROL_COUNT = 6;

    private static final int POWER_OUTPUT_BYTES = 32;
    private static final int COUNTER_OUTPUT_BYTES = 2 * Integer.BYTES;

    private static final int GENERATION_ADVANCES = 16;

    /**
     * The number of captured power refresh cases.
     */
    private static final int POWER_CASE_COUNT = 512;
    /**
     * The classic and extended unit capacities of the test unit pools.
     */
    private static final int CLASSIC_CAPACITY = 1700;
    private static final int EXTENDED_CAPACITY = 1750;

    /**
     * The case index bits of the captured power refresh cases: a completed building (the bit is also the completed status bit), a disabled building,
     * an extended owner, a rescue owner type and a cleared refresh gate.
     */
    private static final int COMPLETED_CASE_BIT = 1;
    private static final int DISABLED_CASE_BIT = 2;
    private static final int EXTENDED_OWNER_CASE_BIT = 4;
    private static final int RESCUE_OWNER_CASE_BIT = 8;
    private static final int CLEARED_REFRESH_CASE_BIT = 16;
    /**
     * The status of a completed and disabled building.
     */
    private static final int COMPLETED_DISABLED_STATUS = UnitStatusFlags.COMPLETED | UnitStatusFlags.DISABLED;
    /**
     * The owners of the power refresh cases, a player slot and the first extended owner, and the owner type index of the extended owner.
     */
    private static final int PLAYER_OWNER = 3;
    private static final int EXTENDED_OWNER = 128;
    private static final int EXTENDED_OWNER_TYPE_INDEX = 12;
    /**
     * The building power refresh gate without and with a pending refresh.
     */
    private static final int NO_POWER_REFRESH = 0;
    private static final int POWER_REFRESH_PENDING = 1;
    /**
     * The factors and offsets that derive the initial display refresh controls of a power refresh case from its index.
     */
    private static final int SECOND_CONTROL_FACTOR = 19;
    private static final int FOURTH_CONTROL_FACTOR = 7;
    private static final int FIFTH_CONTROL_OFFSET = 1;
    private static final int SIXTH_CONTROL_OFFSET = 2;
    /**
     * The mask of the byte the display refresh controls of a power refresh case take from its index.
     */
    private static final int BYTE_MASK = 0xFF;
    /**
     * The indices of the second to sixth display refresh controls.
     */
    private static final int SECOND_CONTROL_INDEX = 1;
    private static final int THIRD_CONTROL_INDEX = 2;
    private static final int FOURTH_CONTROL_INDEX = 3;
    private static final int FIFTH_CONTROL_INDEX = 4;
    private static final int SIXTH_CONTROL_INDEX = 5;

    /**
     * The unit types of the created units: a Nexus, a Dropship and an SCV.
     */
    private static final int NEXUS_TYPE = 154;
    private static final int DROPSHIP_TYPE = 11;
    private static final int SCV_TYPE = 7;
    /**
     * The position of the sprites of the redistribution cases.
     */
    private static final int SPRITE_POSITION = 64;
    /**
     * The primary order and the order countdowns of the units of the redistribution cases.
     */
    private static final int NOTHING_ORDER = 23;
    private static final int PRIMARY_ORDER_COUNTDOWN = 0xA5;
    private static final int VISIBILITY_UPDATE_COUNTDOWN = 0xBB;
    /**
     * The order and the order state of the loaded unit without an order.
     */
    private static final int CLEARED_ORDER = 0;
    private static final int CLEARED_ORDER_STATE = 1;
    /**
     * The periods and position bits of the redistribution cases: every third unit can attack and every fourth unit is a Dropship, units with position
     * bit {@code 1} fly and units with position bit {@code 4} are hallucinations.
     */
    private static final int ATTACKING_UNIT_PERIOD = 3;
    private static final int DROPSHIP_PERIOD = 4;
    private static final int FLYING_POSITION_BIT = 1;
    private static final int HALLUCINATION_POSITION_BIT = 4;
    /**
     * The status of the loaded units of the redistribution cases.
     */
    private static final int LOADED_UNIT_STATUS = UnitStatusFlags.CANNOT_ATTACK | UnitStatusFlags.COMPLETED;
    /**
     * The free generations given in turn to the slots of the loaded units before their allocation.
     */
    private static final int @NotNull [] LOADED_FREE_GENERATIONS = {16, 8, 30, 0};

    /**
     * The offsets of the unit fields in a unit capture.
     */
    private static final int OWNER_OFFSET = 0x68;
    private static final int PRIMARY_ORDER_OFFSET = 0x69;
    private static final int PRIMARY_ORDER_STATE_OFFSET = 0x6A;
    private static final int UNIT_TYPE_OFFSET = 0x8C;
    private static final int PRIMARY_ORDER_COUNTDOWN_OFFSET = 0xC9;
    private static final int VISIBILITY_UPDATE_COUNTDOWN_OFFSET = 0xD5;
    private static final int GENERATION_OFFSET = 0xE9;
    private static final int LOADED_UNIT_HANDLES_OFFSET = 0xF4;
    private static final int STATUS_OFFSET = 0x140;
    private static final int LOADED_HANDLE_EXTENSION_BITS_OFFSET = 0x1C2;

    /**
     * The loaded unit positions of the captured transports that hold no handle, a handle of a stale generation, a unit without a sprite and a unit
     * without an order.
     */
    private static final int EMPTY_LOADED_POSITION = 4;
    private static final int STALE_LOADED_POSITION = 5;
    private static final int SPRITELESS_LOADED_POSITION = 6;
    private static final int NO_ORDER_LOADED_POSITION = 7;

    /**
     * The shift of the handle bits above the loaded unit handle WORD.
     */
    private static final int HANDLE_EXTENSION_SHIFT = 16;
    /**
     * Loaded unit handle extension bits with every bit set.
     */
    private static final int ALL_EXTENSION_BITS = 0xFFFF;

    /**
     * The expected statuses: a completed grounded building, the same building disabled, a completed passenger that cannot attack, the passenger
     * after its restriction flags are cleared and the transport after its restriction flags are cleared.
     */
    private static final int COMPLETED_BUILDING_STATUS = 0x3;
    private static final int DISABLED_BUILDING_STATUS = 0x403;
    private static final int RESTRICTED_PASSENGER_STATUS = 0x8001;
    private static final int CLEARED_PASSENGER_STATUS = 0x1;
    private static final int CLEARED_TRANSPORT_STATUS = 0x3;
    /**
     * The generation of the passenger slot after its recycling and reallocation.
     */
    private static final int REALLOCATED_PASSENGER_GENERATION = 18;

    /**
     * The digest algorithm of the case outputs and the expected digests of the power refresh and the order redistribution cases.
     */
    private static final @NotNull String DIGEST_ALGORITHM = "SHA-256";
    private static final @NotNull String POWER_REFRESH_DIGEST = "dc27428fb23b3b360d3dd3d5a13703b073f38c9a5c881c245d57bda8e72568ee";
    private static final @NotNull String ORDER_REDISTRIBUTION_DIGEST = "c189a587d83c0680ca17f65e4979e9a02255a06b3e18ec11aa3df2db8b9f7d08";

    private static @Nullable GameData gameData;

    @BeforeAll
    static void loadGameData() throws IOException {
        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
    }

    @AfterAll
    static void closeGameData() {
        if (gameData != null) {
            gameData.close();
        }
    }

    /**
     * Creates a {@link Game} instance of the test map with a unit pool of the given capacity.
     *
     * @param unitCapacity the unit pool capacity
     * @return the created {@link Game} instance
     */
    private static @NotNull Game createGame(int unitCapacity) {
        Game game = Game.create(gameData, new GameMap(MAP_SIDE_TILES, MAP_SIDE_TILES, 0, new int[MAP_SIDE_TILES * MAP_SIDE_TILES]), new LcgRandom(0));

        game.getUnitSystem().initializeUnitPool(unitCapacity);

        return game;
    }


    @Test
    void matches_native_power_refresh_cases() throws Exception {
        MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        ByteBuffer output = ByteBuffer.allocate(POWER_OUTPUT_BYTES).order(ByteOrder.LITTLE_ENDIAN);

        for (int caseIndex = 0; caseIndex < POWER_CASE_COUNT; caseIndex++) {
            Game game = createGame(UnitPool.CLASSIC_UNIT_CAPACITY);
            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            Unit unit = pool.getUnit(UnitPoolScenario.allocate(pool));
            int status = UnitStatusFlags.GROUNDED_BUILDING | (caseIndex & COMPLETED_CASE_BIT) |
                         ((caseIndex & DISABLED_CASE_BIT) != 0 ? UnitStatusFlags.DISABLED : 0);
            int owner = (caseIndex & EXTENDED_OWNER_CASE_BIT) != 0 ? EXTENDED_OWNER : PLAYER_OWNER;
            int ownerType = (status & COMPLETED_DISABLED_STATUS) == COMPLETED_DISABLED_STATUS || (caseIndex & RESCUE_OWNER_CASE_BIT) != 0
                            ? PlayerTypes.RESCUE_PASSIVE
                            : PlayerTypes.HUMAN;

            UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, NEXUS_TYPE);
            UnitMemoryScenario.of(unit).writeMemoryByte(OWNER_OFFSET, owner);
            UnitMemoryScenario.of(unit).writeMemoryInteger(STATUS_OFFSET, status);
            pool.insertCreatedUnit(unit);

            int[] playerTypes = new int[PLAYER_TYPE_LOOKUP_COUNT];

            playerTypes[owner == EXTENDED_OWNER ? EXTENDED_OWNER_TYPE_INDEX : PLAYER_OWNER] = ownerType;

            UnitPhase.Context context = new UnitPhase.Context(UnitPhase.INITIAL_PRIMARY_REDISTRIBUTION_TIMER,
                                                              UnitPhase.INITIAL_SECONDARY_REDISTRIBUTION_TIMER);
            long[] initialControls = {0,
                                      caseIndex * SECOND_CONTROL_FACTOR,
                                      caseIndex & BYTE_MASK,
                                      (caseIndex * FOURTH_CONTROL_FACTOR) & BYTE_MASK,
                                      caseIndex + FIFTH_CONTROL_OFFSET,
                                      caseIndex + SIXTH_CONTROL_OFFSET};

            context.getDisplayState().initializeDisplayRefresh(initialControls);
            game.setBuildingPowerRefresh((caseIndex & CLEARED_REFRESH_CASE_BIT) != 0 ? NO_POWER_REFRESH : POWER_REFRESH_PENDING);
            game.getUnitPhase().refreshBuildingPower(context, playerTypes);

            long[] controls = context.getDisplayState().getDisplayRefreshState();

            Assertions.assertNotNull(controls);

            output.clear();
            output.putInt(UnitMemoryScenario.of(unit).readMemoryInteger(STATUS_OFFSET));

            Integer buildingPowerRefresh = game.getBuildingPowerRefresh();

            Assertions.assertNotNull(buildingPowerRefresh);

            output.putInt(buildingPowerRefresh);
            output.putInt((int) controls[SECOND_CONTROL_INDEX]);
            output.putInt((int) (controls[THIRD_CONTROL_INDEX] | controls[FOURTH_CONTROL_INDEX] << Byte.SIZE));
            output.putLong(controls[FIFTH_CONTROL_INDEX]);
            output.putLong(controls[SIXTH_CONTROL_INDEX]);
            digest.update(output.array());
        }

        Assertions.assertEquals(POWER_REFRESH_DIGEST, HexFormat.of().formatHex(digest.digest()));
    }

    @Test
    void refresh_building_power_should_clear_refresh_flag_before_updating_display() {
        Game game = createGame(UnitPool.CLASSIC_UNIT_CAPACITY);
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit nexus = pool.getUnit(UnitPoolScenario.allocate(pool));

        UnitMemoryScenario.of(nexus).writeMemoryWord(UNIT_TYPE_OFFSET, ScrUnitIds.PROTOSS_NEXUS);
        UnitMemoryScenario.of(nexus).writeMemoryByte(OWNER_OFFSET, PLAYER_OWNER);
        UnitMemoryScenario.of(nexus).writeMemoryInteger(STATUS_OFFSET, UnitStatusFlags.COMPLETED | UnitStatusFlags.GROUNDED_BUILDING);
        pool.insertCreatedUnit(nexus);

        UnitPhase.Context context = new UnitPhase.Context(UnitPhase.INITIAL_PRIMARY_REDISTRIBUTION_TIMER,
                                                          UnitPhase.INITIAL_SECONDARY_REDISTRIBUTION_TIMER);
        int[] playerTypes = new int[PLAYER_TYPE_LOOKUP_COUNT];

        playerTypes[PLAYER_OWNER] = PlayerTypes.HUMAN;

        game.setBuildingPowerRefresh(POWER_REFRESH_PENDING);

        Assertions.assertThrows(UnsupportedOperationException.class, () -> game.getUnitPhase().refreshBuildingPower(context, playerTypes));
        Assertions.assertEquals(NO_POWER_REFRESH, game.getBuildingPowerRefresh());
        Assertions.assertEquals(COMPLETED_BUILDING_STATUS, UnitMemoryScenario.of(nexus).readMemoryInteger(STATUS_OFFSET));

        context.getDisplayState().initializeDisplayRefresh(new long[]{0, 5, 7, 9, 11, 13});

        game.setBuildingPowerRefresh(POWER_REFRESH_PENDING);
        game.getUnitPhase().refreshBuildingPower(context, playerTypes);

        Assertions.assertArrayEquals(new long[]{0, 1, 1, 1, 0, 0}, context.getDisplayState().getDisplayRefreshState());
        Assertions.assertEquals(NO_POWER_REFRESH, game.getBuildingPowerRefresh());

        context.getDisplayState().initializeDisplayRefresh(new long[]{1, 5, 7, 9, 11, 13});

        game.setBuildingPowerRefresh(POWER_REFRESH_PENDING);

        Assertions.assertThrows(UnsupportedOperationException.class, () -> game.getUnitPhase().refreshBuildingPower(context, playerTypes));
        Assertions.assertEquals(NO_POWER_REFRESH, game.getBuildingPowerRefresh());
        Assertions.assertArrayEquals(new long[]{1, 5, 7, 9, 11, 13}, context.getDisplayState().getDisplayRefreshState());
    }

    @Test
    void refresh_building_power_should_skip_rescue_owners_and_throw_before_power_effects() {
        Game game = createGame(UnitPool.CLASSIC_UNIT_CAPACITY);
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        Unit building = pool.getUnit(UnitPoolScenario.allocate(pool));

        UnitMemoryScenario.of(building).writeMemoryWord(UNIT_TYPE_OFFSET, ScrUnitIds.PROTOSS_GATEWAY);
        UnitMemoryScenario.of(building).writeMemoryByte(OWNER_OFFSET, EXTENDED_OWNER);
        UnitMemoryScenario.of(building).writeMemoryInteger(STATUS_OFFSET,
                                                           UnitStatusFlags.COMPLETED | UnitStatusFlags.GROUNDED_BUILDING |
                                                           UnitStatusFlags.DISABLED);
        pool.insertCreatedUnit(building);

        UnitPhase.Context context = new UnitPhase.Context(UnitPhase.INITIAL_PRIMARY_REDISTRIBUTION_TIMER,
                                                          UnitPhase.INITIAL_SECONDARY_REDISTRIBUTION_TIMER);

        context.getDisplayState().initializeDisplayRefresh(new long[DISPLAY_REFRESH_CONTROL_COUNT]);

        int[] playerTypes = new int[PLAYER_TYPE_LOOKUP_COUNT];

        playerTypes[EXTENDED_OWNER_TYPE_INDEX] = PlayerTypes.RESCUE_PASSIVE;

        game.setBuildingPowerRefresh(POWER_REFRESH_PENDING);
        game.getUnitPhase().refreshBuildingPower(context, playerTypes);

        Assertions.assertEquals(DISABLED_BUILDING_STATUS, UnitMemoryScenario.of(building).readMemoryInteger(STATUS_OFFSET));

        playerTypes[EXTENDED_OWNER_TYPE_INDEX] = PlayerTypes.HUMAN;
        game.setBuildingPowerRefresh(POWER_REFRESH_PENDING);

        Assertions.assertThrows(UnsupportedOperationException.class, () -> game.getUnitPhase().refreshBuildingPower(context, playerTypes));
        Assertions.assertEquals(POWER_REFRESH_PENDING, game.getBuildingPowerRefresh());
        Assertions.assertEquals(DISABLED_BUILDING_STATUS, UnitMemoryScenario.of(building).readMemoryInteger(STATUS_OFFSET));
    }

    @Test
    void matches_native_order_redistribution_and_transport_handle_cases() throws Exception {
        MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        ByteBuffer counters = ByteBuffer.allocate(COUNTER_OUTPUT_BYTES).order(ByteOrder.LITTLE_ENDIAN);

        for (int capacity : new int[]{CLASSIC_CAPACITY, EXTENDED_CAPACITY}) {
            for (int count : new int[]{0, 1, 8, 31, 64}) {
                for (int[] timers : new int[][]{{0, 0}, {1, 1}, {10, 150}, {2, 150}, {Integer.MIN_VALUE, Integer.MAX_VALUE}, {150, 300}}) {
                    Game game = createGame(capacity);
                    UnitPool pool = game.getUnitSystem().getUnitPool();

                    Assertions.assertNotNull(pool);

                    Unit[] units = new Unit[count + Unit.LOADED_UNIT_SLOTS];

                    for (int position = 0; position < units.length; position++) {
                        if (position >= count) {
                            UnitPoolScenario.setFreeGeneration(pool, pool.getFirstFreeIndex(),
                                                               LOADED_FREE_GENERATIONS[(position - count) % LOADED_FREE_GENERATIONS.length]);
                        }

                        units[position] = pool.getUnit(UnitPoolScenario.allocate(pool));

                        Unit unit = units[position];

                        unit.setSprite(SpriteScenario.createSprite(0, SPRITE_POSITION, SPRITE_POSITION, 0));
                        UnitMemoryScenario.of(unit).writeMemoryByte(PRIMARY_ORDER_OFFSET, NOTHING_ORDER);
                        UnitMemoryScenario.of(unit).writeMemoryByte(PRIMARY_ORDER_COUNTDOWN_OFFSET, PRIMARY_ORDER_COUNTDOWN);
                        UnitMemoryScenario.of(unit).writeMemoryByte(VISIBILITY_UPDATE_COUNTDOWN_OFFSET, VISIBILITY_UPDATE_COUNTDOWN);

                        if (position < count) {
                            int status = UnitStatusFlags.COMPLETED | UnitStatusFlags.GROUNDED_BUILDING |
                                         (position % ATTACKING_UNIT_PERIOD != 0 ? UnitStatusFlags.CANNOT_ATTACK : 0) |
                                         ((position & FLYING_POSITION_BIT) != 0 ? UnitStatusFlags.FLYING : 0) |
                                         ((position & HALLUCINATION_POSITION_BIT) != 0 ? UnitStatusFlags.HALLUCINATION : 0);

                            UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, position % DROPSHIP_PERIOD == 0 ? DROPSHIP_TYPE : SCV_TYPE);
                            UnitMemoryScenario.of(unit).writeMemoryInteger(STATUS_OFFSET, status);
                            pool.insertCreatedUnit(unit);
                        } else {
                            int loadedPosition = position - count;

                            UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, SCV_TYPE);
                            UnitMemoryScenario.of(unit).writeMemoryInteger(STATUS_OFFSET, LOADED_UNIT_STATUS);

                            if (loadedPosition == SPRITELESS_LOADED_POSITION) {
                                unit.setSprite(null);
                            }

                            if (loadedPosition == NO_ORDER_LOADED_POSITION) {
                                UnitMemoryScenario.of(unit).writeMemoryByte(PRIMARY_ORDER_OFFSET, CLEARED_ORDER);
                                UnitMemoryScenario.of(unit).writeMemoryByte(PRIMARY_ORDER_STATE_OFFSET, CLEARED_ORDER_STATE);
                            }
                        }
                    }

                    for (int position = 0; position < count; position++) {
                        Unit unit = units[position];

                        unit.setSubunit(units[count]);

                        int extraBits = 0;

                        for (int loadedPosition = 0; loadedPosition < Unit.LOADED_UNIT_SLOTS; loadedPosition++) {
                            Unit loadedUnit = units[count + loadedPosition];
                            int generation = pool.getGeneration(loadedUnit.getIndex());
                            int generationOffset = loadedPosition == STALE_LOADED_POSITION ? 1 : 0;
                            int handle = loadedPosition == EMPTY_LOADED_POSITION
                                         ? 0
                                         : UnitHandle.encode(loadedUnit.getIndex(), generation + generationOffset, capacity);

                            UnitMemoryScenario.of(unit).writeMemoryWord(LOADED_UNIT_HANDLES_OFFSET + loadedPosition * Short.BYTES, handle);
                            extraBits |= ((handle >>> HANDLE_EXTENSION_SHIFT) & Unit.LOADED_HANDLE_EXTENSION_MASK) <<
                                         (loadedPosition * Unit.LOADED_HANDLE_EXTENSION_BITS);
                        }

                        UnitMemoryScenario.of(unit).writeMemoryWord(LOADED_HANDLE_EXTENSION_BITS_OFFSET, extraBits);
                    }

                    UnitPhase.Context context = new UnitPhase.Context(timers[0], timers[1]);

                    game.getUnitPhase().redistributeOrders(context);
                    game.getUnitPhase().clearRestrictionFlags();
                    counters.clear();
                    counters.putInt(context.getPrimaryRedistributionTimer());
                    counters.putInt(context.getSecondaryRedistributionTimer());
                    digest.update(counters.array());

                    for (Unit unit : units) {
                        byte[] capture = UnitMemoryScenario.of(unit).exportMemoryBytes();

                        capture[GENERATION_OFFSET] = (byte) pool.getGeneration(unit.getIndex());
                        digest.update(capture);
                    }
                }
            }
        }

        Assertions.assertEquals(ORDER_REDISTRIBUTION_DIGEST, HexFormat.of().formatHex(digest.digest()));
    }

    @Test
    void clear_restriction_flags_should_resolve_loaded_handles_by_capacity_and_generation() {
        for (int capacity : new int[]{CLASSIC_CAPACITY, EXTENDED_CAPACITY}) {
            Game game = Game.create(gameData, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));

            game.getUnitSystem().initializeUnitPool(capacity);

            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            Unit transport = pool.getUnit(UnitPoolScenario.allocate(pool));

            transport.setUnitId(ScrUnitIds.TERRAN_DROPSHIP);
            transport.setStatus(UnitStatusFlags.CANNOT_ATTACK | UnitStatusFlags.GROUNDED_BUILDING | UnitStatusFlags.COMPLETED);
            pool.insertCreatedUnit(transport);

            for (int generation = 0; generation < GENERATION_ADVANCES; generation++) {
                pool.advanceGeneration(pool.getUnit(pool.getFirstFreeIndex()));
            }

            Unit passenger = pool.getUnit(UnitPoolScenario.allocate(pool));

            passenger.setSprite(SpriteScenario.createSprite(0, 0, 0, 0));
            passenger.setPrimaryOrder(ScrOrderIds.NOTHING);
            passenger.setStatus(UnitStatusFlags.CANNOT_ATTACK | UnitStatusFlags.COMPLETED);

            int handle = UnitHandle.encode(passenger.getIndex(), pool.getGeneration(passenger.getIndex()), capacity);

            transport.setLoadedUnitHandle(0, handle);
            transport.setLoadedUnitHandleExtensionBits(capacity > CLASSIC_CAPACITY ? 0 : ALL_EXTENSION_BITS);

            game.getUnitPhase().clearRestrictionFlags();

            Assertions.assertEquals(capacity > CLASSIC_CAPACITY ? RESTRICTED_PASSENGER_STATUS : CLEARED_PASSENGER_STATUS, passenger.getStatus());

            passenger.setStatus(UnitStatusFlags.CANNOT_ATTACK | UnitStatusFlags.COMPLETED);
            transport.setLoadedUnitHandleExtensionBits((handle >>> HANDLE_EXTENSION_SHIFT) & Unit.LOADED_HANDLE_EXTENSION_MASK);

            game.getUnitPhase().clearRestrictionFlags();

            Assertions.assertEquals(CLEARED_PASSENGER_STATUS, passenger.getStatus());

            passenger.setStatus(UnitStatusFlags.CANNOT_ATTACK | UnitStatusFlags.COMPLETED);
            pool.recycleFinalized(passenger.getIndex());
            UnitPoolScenario.allocate(pool);

            Assertions.assertSame(passenger, pool.getUnit(UnitPoolScenario.allocate(pool)));
            Assertions.assertEquals(REALLOCATED_PASSENGER_GENERATION, pool.getGeneration(passenger.getIndex()));

            game.getUnitPhase().clearRestrictionFlags();

            Assertions.assertEquals(RESTRICTED_PASSENGER_STATUS, passenger.getStatus());
            Assertions.assertEquals(CLEARED_TRANSPORT_STATUS, transport.getStatus());
        }
    }
}
