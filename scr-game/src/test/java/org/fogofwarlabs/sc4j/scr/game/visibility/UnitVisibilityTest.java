package org.fogofwarlabs.sc4j.scr.game.visibility;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerUpgrades;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitMemoryScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
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
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Checks sight ranges, sharing masks and visibility of flag carriers.
 */
@RequiresInstallation
final class UnitVisibilityTest {
    /**
     * The unit record offset of the unit type WORD.
     */
    private static final int UNIT_TYPE_OFFSET = 0x8C;
    /**
     * The unit record offset of the owner BYTE.
     */
    private static final int OWNER_OFFSET = 0x68;
    /**
     * The unit record offset of the primary order BYTE.
     */
    private static final int PRIMARY_ORDER_OFFSET = 0x69;
    /**
     * The unit record offset of the build queue WORD.
     */
    private static final int BUILD_QUEUE_OFFSET = 0xDC;
    /**
     * The unit record offset of the status DWORD.
     */
    private static final int STATUS_OFFSET = 0x140;
    /**
     * The unit record offset of the parasite flags BYTE.
     */
    private static final int PARASITE_FLAGS_OFFSET = 0x1B1;
    /**
     * The unit record offset of the blindness BYTE.
     */
    private static final int BLINDNESS_OFFSET = 0x1B3;
    /**
     * The width and height in tiles of the sight range map.
     */
    private static final int SIGHT_RANGE_MAP_SIZE = 64;
    /**
     * The width and height in tiles of the shared vision maps.
     */
    private static final int MASK_MAP_SIZE = 8;
    /**
     * The tileset index of every map.
     */
    private static final int TILESET_INDEX = 0;
    /**
     * The seed of the game random generator.
     */
    private static final int RANDOM_SEED = 0;
    /**
     * The pool index of the unit under test.
     */
    private static final int UNIT_POOL_INDEX = 1;
    /**
     * The pool index of the carried Uraj crystal.
     */
    private static final int CRYSTAL_POOL_INDEX = 2;
    /**
     * The pool index of the carried Flag.
     */
    private static final int FLAG_POOL_INDEX = 3;
    /**
     * The blindness bytes of the sight range cases.
     */
    private static final int @NotNull [] BLINDNESS_CASES = {0, 255};
    /**
     * The sight upgrade levels of the sight range cases.
     */
    private static final int @NotNull [] UPGRADE_LEVELS = {0, 1};
    /**
     * The player whose sight upgrades the sight range cases set.
     */
    private static final int UPGRADED_PLAYER = 0;
    /**
     * The number of sight range cases.
     */
    private static final int SIGHT_RANGE_CASE_COUNT = 43776;
    /**
     * The shared vision masks of the twelve players.
     */
    private static final int @NotNull [] SHARED_VISION_MASKS = {1, 6, 0x104, 8, 16, 32, 64, 128, 0x10000, 0x20000, 0x40000, 0x80000};
    /**
     * The local comparison mask of the shared vision case.
     */
    private static final int COMPARISON_MASK = 1;
    /**
     * The playable owner of the shared vision case.
     */
    private static final int PLAYABLE_OWNER = 1;
    /**
     * The owner of the shared vision case past the playable players.
     */
    private static final int UNPLAYABLE_OWNER = 8;
    /**
     * The parasite players of the shared vision case: the first and the third player.
     */
    private static final int PARASITE_PLAYERS = 5;
    /**
     * The mask of a playable owner with the parasite players: the parasite players, the owner's shared vision and the parasite players' shared
     * vision.
     */
    private static final int PLAYABLE_OWNER_MASK = 0x107;
    /**
     * The mask of an unplayable owner with the parasite players: the parasite players and their shared vision.
     */
    private static final int UNPLAYABLE_OWNER_MASK = 0x105;
    /**
     * The mask of the eight playable players.
     */
    private static final int EVERY_PLAYER_MASK = 255;
    /**
     * The SHA-256 digest of the sight ranges of every sight range case.
     */
    private static final @NotNull String SIGHT_RANGE_CASES_HASH = "513cf4b35e879bbcf2b5093dccd21097f6a02f3042c23c210db68232d5c06e3b";
    /**
     * The digest algorithm of the sight range digest.
     */
    private static final @NotNull String DIGEST_ALGORITHM = "SHA-256";

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


    @Test
    void matches_native_sight_range_cases() throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
        ByteBuffer result = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);

        Assertions.assertNotNull(gameData);

        GameMap map = new GameMap(SIGHT_RANGE_MAP_SIZE, SIGHT_RANGE_MAP_SIZE, TILESET_INDEX, new int[SIGHT_RANGE_MAP_SIZE * SIGHT_RANGE_MAP_SIZE]);
        Game game = Game.create(gameData, map, new LcgRandom(RANDOM_SEED));
        PlayerUpgrades upgrades = PlayerProgressTestUtils.createPlayerUpgrades(false);

        game.getPlayerTable().setPlayerUpgrades(upgrades);

        Unit unit = UnitTestUtils.createUnit(UNIT_POOL_INDEX);
        int cases = 0;

        for (int unitType = 0; unitType < UnitsDat.UNIT_COUNT; unitType++) {
            UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, unitType);

            for (int status : new int[]{
                    0,
                    UnitStatusFlags.COMPLETED,
                    UnitStatusFlags.GROUNDED_BUILDING,
                    UnitStatusFlags.COMPLETED | UnitStatusFlags.GROUNDED_BUILDING
            }) {
                UnitMemoryScenario.of(unit).writeMemoryInteger(STATUS_OFFSET, status);

                for (int blindness : BLINDNESS_CASES) {
                    UnitMemoryScenario.of(unit).writeMemoryByte(BLINDNESS_OFFSET, blindness);

                    for (boolean ignoreBlindness : new boolean[]{false, true}) {
                        for (int queuedType : new int[]{
                                ScrUnitIds.TERRAN_MARINE,
                                ScrUnitIds.ZERG_LAIR,
                                ScrUnitIds.ZERG_HIVE,
                                ScrUnitIds.ZERG_GREATER_SPIRE,
                                ScrUnitIds.ZERG_SPORE_COLONY,
                                ScrUnitIds.ZERG_SUNKEN_COLONY
                        }) {
                            UnitMemoryScenario.of(unit).writeMemoryWord(BUILD_QUEUE_OFFSET, queuedType);

                            for (int upgradeLevel : UPGRADE_LEVELS) {
                                for (int upgradeIdentifier : new int[]{
                                        ScrUpgradeIds.OCULAR_IMPLANTS,
                                        ScrUpgradeIds.ANTENNAE,
                                        ScrUpgradeIds.APIAL_SENSORS,
                                        ScrUpgradeIds.SENSOR_ARRAY
                                }) {
                                    upgrades.setLevel(UPGRADED_PLAYER, upgradeIdentifier, upgradeLevel);
                                }

                                result.clear();
                                result.putInt(game.getUnitVisibility().calculateSightRange(unit, ignoreBlindness));

                                digest.update(result.array());

                                cases++;
                            }
                        }
                    }
                }
            }
        }

        Assertions.assertEquals(SIGHT_RANGE_CASE_COUNT, cases);
        Assertions.assertEquals(SIGHT_RANGE_CASES_HASH, HexFormat.of().formatHex(digest.digest()));
    }

    @Test
    void calculate_mask_should_combine_owner_and_shared_visibility_without_truncating_dwords() {
        Assertions.assertNotNull(gameData);

        GameMap map = new GameMap(MASK_MAP_SIZE, MASK_MAP_SIZE, TILESET_INDEX, new int[MASK_MAP_SIZE * MASK_MAP_SIZE]);
        Game game = Game.create(gameData, map, new LcgRandom(RANDOM_SEED));
        SightCoverage coverage = new SightCoverage(MASK_MAP_SIZE);

        coverage.setSharedVisionMasks(SHARED_VISION_MASKS);
        game.setSightCoverage(coverage);
        game.getLocalVision().setComparisonMask(COMPARISON_MASK);

        Unit unit = UnitTestUtils.createUnit(UNIT_POOL_INDEX);

        for (int unitType : new int[]{ScrUnitIds.TERRAN_SCV, ScrUnitIds.ZERG_DRONE, ScrUnitIds.PROTOSS_PROBE}) {
            UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, unitType);
            UnitMemoryScenario.of(unit).writeMemoryByte(OWNER_OFFSET, PLAYABLE_OWNER);
            UnitMemoryScenario.of(unit).writeMemoryByte(PARASITE_FLAGS_OFFSET, PARASITE_PLAYERS);

            Assertions.assertEquals(PLAYABLE_OWNER_MASK, game.getUnitVisibility().calculateMask(unit));

            UnitMemoryScenario.of(unit).writeMemoryByte(OWNER_OFFSET, UNPLAYABLE_OWNER);

            Assertions.assertEquals(UNPLAYABLE_OWNER_MASK, game.getUnitVisibility().calculateMask(unit));

            UnitMemoryScenario.of(unit).writeMemoryByte(PARASITE_FLAGS_OFFSET, 0);

            Assertions.assertEquals(0, game.getUnitVisibility().calculateMask(unit));
        }

        UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, ScrUnitIds.POWERUP_FLAG);
        UnitMemoryScenario.of(unit).writeMemoryByte(PRIMARY_ORDER_OFFSET, ScrOrderIds.UNUSED_POWERUP);

        Assertions.assertEquals(EVERY_PLAYER_MASK, game.getUnitVisibility().calculateMask(unit));
    }

    @Test
    void calculate_mask_should_reject_unknown_sharing_and_show_flag_carriers_to_all() {
        Assertions.assertNotNull(gameData);

        GameMap map = new GameMap(MASK_MAP_SIZE, MASK_MAP_SIZE, TILESET_INDEX, new int[MASK_MAP_SIZE * MASK_MAP_SIZE]);
        Game game = Game.create(gameData, map, new LcgRandom(RANDOM_SEED));
        Unit unit = UnitTestUtils.createUnit(UNIT_POOL_INDEX);

        UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, ScrUnitIds.TERRAN_SCV);

        Assertions.assertThrows(UnsupportedOperationException.class, () -> game.getUnitVisibility().calculateMask(unit));

        SightCoverage coverage = new SightCoverage(MASK_MAP_SIZE);

        game.setSightCoverage(coverage);
        coverage.setSharedVisionMasks(new int[PlayerTable.PLAYER_SLOTS]);

        Unit crystal = UnitTestUtils.createUnit(CRYSTAL_POOL_INDEX);

        UnitMemoryScenario.of(crystal).writeMemoryWord(UNIT_TYPE_OFFSET, ScrUnitIds.POWERUP_URAJ_CRYSTAL);
        unit.setCarriedPowerup(crystal);

        Assertions.assertNotEquals(EVERY_PLAYER_MASK, game.getUnitVisibility().calculateMask(unit), "only a carried Flag is seen by everyone");

        Unit flag = UnitTestUtils.createUnit(FLAG_POOL_INDEX);

        UnitMemoryScenario.of(flag).writeMemoryWord(UNIT_TYPE_OFFSET, ScrUnitIds.POWERUP_FLAG);
        unit.setCarriedPowerup(flag);

        Assertions.assertEquals(EVERY_PLAYER_MASK, game.getUnitVisibility().calculateMask(unit), "a worker carrying a Flag is seen by everyone");
    }
}
