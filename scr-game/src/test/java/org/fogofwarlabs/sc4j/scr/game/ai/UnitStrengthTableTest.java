package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitMemoryScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
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
 * Checks strength tables, health scaling and excluded unit types.
 * <p>
 * The layout of the strength tables is the contract: the ground table follows the air table, and computer players read the air strength of the unit
 * type after the last unit type from the first ground strength, a value that replays and LAN games reproduce only with the same layout. The captured
 * cases write their units by unit record offset, because the captures hold the unit records in the game's record layout.
 */
@RequiresInstallation
final class UnitStrengthTableTest {
    /**
     * The SHA-256 digest of the strength tables: the strength of every unit type in every direction, little-endian.
     */
    private static final @NotNull String STRENGTH_TABLE_SHA256 = "fa567531d8751e72dae4e1ac6cab36b1728de71d906683495c385bac456b6e45";
    /**
     * The SHA-256 digest of the calculated strengths of every case, little-endian.
     */
    private static final @NotNull String CALCULATED_STRENGTH_SHA256 = "961e9d5f7b72661fe67d78d52d05b4da5b1a8134045399eb8c84bce1a527250d";
    private static final @NotNull String DIGEST_ALGORITHM = "SHA-256";
    /**
     * The calculated strengths: every case of every unit type except the Bunker, in every direction.
     */
    private static final int CALCULATED_STRENGTH_COUNT = 10896;
    private static final int @NotNull [] STATUS_CASES = {0, UnitStatusFlags.HALLUCINATION};
    private static final int @NotNull [] HIT_POINT_CASES = {0, 256, 128000, -1};
    private static final int @NotNull [] SHIELD_POINT_CASES = {0, 256, 0x10000};

    private static final int UNIT_RECORD_SIZE = 488;
    /**
     * The BYTE pattern that fills every unit record of a case before its fields are written.
     */
    private static final int UNIT_RECORD_FILL = 0xA5A5A5A5;
    private static final int HIT_POINTS_OFFSET = 0x10;
    private static final int SHIELD_POINTS_OFFSET = 0x88;
    private static final int UNIT_TYPE_OFFSET = 0x8C;
    private static final int STATUS_OFFSET = 0x140;

    /**
     * A bit above the unit type WORD, which the unit does not keep.
     */
    private static final int UNIT_TYPE_HIGH_BIT = 0x10000;
    private static final int WORD_MASK = 0xFFFF;
    private static final int HIT_POINTS = 123;
    private static final int SHIELD_POINTS = 456;

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
    void matches_native_strength_table_and_health_scaling_cases() throws NoSuchAlgorithmException {
        Assertions.assertNotNull(gameData);

        UnitStrengthTable strength = new UnitStrengthTable(gameData.getUnitTypes(), gameData.getWeaponTypes());
        ByteBuffer table = ByteBuffer.allocate(UnitStrengthTable.DIRECTION_COUNT * UnitsDat.UNIT_COUNT * Integer.BYTES)
                                     .order(ByteOrder.LITTLE_ENDIAN);

        for (int direction = 0; direction < UnitStrengthTable.DIRECTION_COUNT; direction++) {
            for (int unitType = 0; unitType < UnitsDat.UNIT_COUNT; unitType++) {
                table.putInt(strength.getStrength(unitType, direction));
            }
        }

        MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);

        Assertions.assertEquals(STRENGTH_TABLE_SHA256, HexFormat.of().formatHex(digest.digest(table.array())));

        ByteBuffer value = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        int caseCount = 0;

        for (int unitType = 0; unitType < UnitsDat.UNIT_COUNT; unitType++) {
            if (unitType == ScrUnitIds.TERRAN_BUNKER) {
                continue;
            }

            for (int status : STATUS_CASES) {
                for (int hitPoints : HIT_POINT_CASES) {
                    for (int shields : SHIELD_POINT_CASES) {
                        Unit unit = UnitTestUtils.createUnit(1);

                        for (int offset = 0; offset < UNIT_RECORD_SIZE; offset += Integer.BYTES) {
                            UnitMemoryScenario.of(unit).writeMemoryInteger(offset, UNIT_RECORD_FILL);
                        }

                        UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, unitType);
                        UnitMemoryScenario.of(unit).writeMemoryInteger(STATUS_OFFSET, status);
                        UnitMemoryScenario.of(unit).writeMemoryInteger(HIT_POINTS_OFFSET, hitPoints);
                        UnitMemoryScenario.of(unit).writeMemoryInteger(SHIELD_POINTS_OFFSET, shields);

                        for (int direction = 0; direction < UnitStrengthTable.DIRECTION_COUNT; direction++) {
                            value.clear();

                            Assertions.assertNotNull(gameData);

                            value.putInt(strength.calculate(unit, direction, gameData.getUnitTypes()));
                            digest.update(value.array());

                            caseCount++;
                        }
                    }
                }
            }
        }

        Assertions.assertEquals(CALCULATED_STRENGTH_COUNT, caseCount);
        Assertions.assertEquals(CALCULATED_STRENGTH_SHA256, HexFormat.of().formatHex(digest.digest()));
    }

    @Test
    void calculate_should_return_zero_for_excluded_types_before_reading_upgrades() {
        Assertions.assertNotNull(gameData);

        UnitStrengthTable strength = new UnitStrengthTable(gameData.getUnitTypes(), gameData.getWeaponTypes());

        for (int unitType : new int[]{ScrUnitIds.ZERG_LARVA, ScrUnitIds.ZERG_EGG, ScrUnitIds.ZERG_COCOON,
                                      ScrUnitIds.ZERG_LURKER_EGG, UNIT_TYPE_HIGH_BIT | ScrUnitIds.ZERG_LARVA}) {
            Unit unit = new UnitPool(1).getUnit(1);

            unit.setUnitId(unitType);
            unit.setHitPoints(HIT_POINTS);
            unit.setShieldPoints(SHIELD_POINTS);

            Assertions.assertEquals(0, strength.calculate(unit, UnitStrengthTable.AIR, gameData.getUnitTypes()));
            Assertions.assertEquals(unitType & WORD_MASK, unit.getUnitId());
        }
    }

    @Test
    void get_strength_should_read_the_ground_table_past_the_air_table_and_zero_past_the_ground_table() {
        Assertions.assertNotNull(gameData);

        UnitStrengthTable strength = new UnitStrengthTable(gameData.getUnitTypes(), gameData.getWeaponTypes());
        int firstGroundStrength = strength.getStrength(ScrUnitIds.TERRAN_MARINE, UnitStrengthTable.GROUND);

        Assertions.assertNotEquals(0, firstGroundStrength, "The Marine has a ground strength.");
        Assertions.assertEquals(firstGroundStrength, strength.getStrength(ScrUnitIds.NONE, UnitStrengthTable.AIR));
        Assertions.assertEquals(0, strength.getStrength(ScrUnitIds.NONE, UnitStrengthTable.GROUND));
    }
}
