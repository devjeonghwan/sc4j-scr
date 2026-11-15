package org.fogofwarlabs.sc4j.scr.game.map;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkUnit;
import org.fogofwarlabs.sc4j.scr.game.ai.UnitStrengthTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.fogofwarlabs.sc4j.scr.game.unit.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Checks preplaced unit property writes and resource image completion.
 */
@RequiresInstallation
final class PreplacedUnitLoaderUtilsTest {
    /**
     * The SHA-256 digest of the native capture of the preplaced property cases.
     */
    private static final @NotNull String PROPERTY_CAPTURE_HASH = "9cfb3d3d36d72c9641ed9b9cb182ea5ed22ac1b97c8b8c4c4b2ff22556c88213";
    /**
     * The digest algorithm of the capture hashes.
     */
    private static final @NotNull String DIGEST_ALGORITHM = "SHA-256";
    /**
     * The script instruction budget passed to every loader and unit call.
     */
    private static final int INSTRUCTION_LIMIT = 1024;
    /**
     * The width and height of the fixture maps in tiles.
     */
    private static final int MAP_SIZE = 64;
    /**
     * The bytes of one captured unit record.
     */
    private static final int UNIT_CAPTURE_SIZE = 488;
    /**
     * The pattern the property cases fill a unit record with before the case fields.
     */
    private static final int UNIT_FILL_PATTERN = 0xA5A5A5A5;
    /**
     * The neutral owner of the property cases.
     */
    private static final int NEUTRAL_OWNER = 11;
    /**
     * The unit memory offset and size of the sprite reference, which the property cases clear.
     */
    private static final int SPRITE_REFERENCE_OFFSET = 0x18;
    private static final int SPRITE_REFERENCE_SIZE = 8;
    /**
     * The unit memory offsets of the owner byte, the unit type WORD and the status flags DWORD.
     */
    private static final int OWNER_OFFSET = 0x68;
    private static final int UNIT_TYPE_OFFSET = 0x8C;
    private static final int STATUS_OFFSET = 0x140;
    /**
     * The unit memory offset of the resource animation byte of a mineral field.
     */
    private static final int RESOURCE_ANIMATION_OFFSET = 0x12A;
    /**
     * The image and order pool capacity of the resource cases.
     */
    private static final int RESOURCE_POOL_CAPACITY = 16;
    /**
     * The position of the resource cases.
     */
    private static final int RESOURCE_POSITION_X = 320;
    private static final int RESOURCE_POSITION_Y = 640;
    /**
     * The serial number of the {@code UNIT} record of the resource cases.
     */
    private static final int RESOURCE_RECORD_SERIAL = 9999;
    /**
     * The hit point percentage of the {@code UNIT} record of the resource cases.
     */
    private static final int FULL_HIT_POINTS_PERCENT = 100;

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
    void matches_native_preplaced_property_cases() throws IOException, NoSuchAlgorithmException {
        Game game = Game.create(gameData, new GameMap(MAP_SIZE, MAP_SIZE, 0, new int[MAP_SIZE * MAP_SIZE]), new LcgRandom(0));

        Assertions.assertNotNull(gameData);

        game.setUnitStrengthTable(new UnitStrengthTable(gameData.getUnitTypes(), gameData.getWeaponTypes()));

        Sprite sprite = SpriteScenario.createSprite(0, 0, 0, 0, 0);

        sprite.setMainImage(SpriteScenario.createImage(0, 0, 0));

        Assertions.assertEquals(0, gameData.getImages().get("Damage Overlay", 0));

        byte[] captured = GameTestResources.readNativeCapture(GameTestResources.CAPTURE_PREPLACED_PROPERTIES);

        Assertions.assertEquals(PROPERTY_CAPTURE_HASH, HexFormat.of().formatHex(MessageDigest.getInstance(DIGEST_ALGORITHM).digest(captured)));

        int cases = 0;

        for (int unitType : new int[]{176, 177, 178, 188}) {
            for (int validElements : new int[]{0, 2, 4, 8, 16, 30}) {
                for (int percentage : new int[]{0, 1, 100, 255}) {
                    for (long resources : new long[]{0, 249, 250, 499, 500, 749, 750, 65535, 65536, 0xFFFFFFFFL}) {
                        for (int status : new int[]{0, 3}) {
                            for (int stateFlags : new int[]{0, ChkUnit.STATE_INVINCIBLE}) {
                                Unit unit = UnitTestUtils.createUnit(1);

                                for (int offset = 0; offset < UNIT_CAPTURE_SIZE; offset += Integer.BYTES) {
                                    UnitMemoryScenario.of(unit).writeMemoryInteger(offset, UNIT_FILL_PATTERN);
                                }

                                UnitMemoryScenario.of(unit).clearMemoryBytes(SPRITE_REFERENCE_OFFSET, SPRITE_REFERENCE_SIZE);
                                unit.setSprite(sprite);
                                UnitMemoryScenario.of(unit).writeMemoryWord(UNIT_TYPE_OFFSET, unitType);
                                UnitMemoryScenario.of(unit).writeMemoryInteger(STATUS_OFFSET, status);
                                UnitMemoryScenario.of(unit).writeMemoryByte(OWNER_OFFSET, NEUTRAL_OWNER);

                                ChkUnit properties = new ChkUnit(0,
                                                                 0, 0,
                                                                 unitType,
                                                                 0,
                                                                 ChkUnit.STATE_INVINCIBLE, validElements,
                                                                 NEUTRAL_OWNER,
                                                                 percentage, percentage, percentage,
                                                                 resources,
                                                                 0,
                                                                 stateFlags,
                                                                 0);

                                PreplacedUnitLoaderUtils.applyProperties(game, unit, properties, INSTRUCTION_LIMIT);

                                byte[] expected = Arrays.copyOfRange(captured, cases * UNIT_CAPTURE_SIZE, (cases + 1) * UNIT_CAPTURE_SIZE);

                                UnitDiagnosticAssert.assertDomainEquals(expected, unit, "Native property case " + cases);

                                cases++;
                            }
                        }
                    }
                }
            }
        }

        Assertions.assertEquals(3840, cases);
        Assertions.assertEquals(cases * UNIT_CAPTURE_SIZE, captured.length);
    }

    @Test
    void preplaced_resources_get_amount_hit_points_and_quantity_animation() throws IOException {
        for (int unitType : new int[]{ScrUnitIds.RESOURCE_MINERAL_FIELD, ScrUnitIds.RESOURCE_MINERAL_FIELD_TYPE_2,
                                      ScrUnitIds.RESOURCE_MINERAL_FIELD_TYPE_3, ScrUnitIds.RESOURCE_VESPENE_GEYSER}) {
            for (int resources : new int[]{196, 318, 593, 1500, 5000}) {
                Game game = Game.create(gameData,
                                        new GameMap(MAP_SIZE, MAP_SIZE, 0, new int[MAP_SIZE * MAP_SIZE], new int[MAP_SIZE * MAP_SIZE]),
                                        new LcgRandom(0));

                Assertions.assertNotNull(gameData);

                game.getUnitSystem().initializeUnitPool(1);
                game.getSpriteSystem().initializeSpritePool(1);
                game.getSpriteSystem().initializeImagePool(RESOURCE_POOL_CAPACITY);
                game.initializeOrderPool(RESOURCE_POOL_CAPACITY);
                game.initializeExecutionScope();
                game.initializeUnitSpatialIndex();

                UnitPool registryPool = game.getUnitSystem().getUnitPool();

                Assertions.assertNotNull(registryPool);

                game.getUnitSystem().setUnitRegistry(new UnitRegistry(registryPool));
                game.setFoliage(gameData.createFoliage(MAP_SIZE, 0));

                UnitAccounting accounting = PlayerProgressTestUtils.createUnitAccounting();

                accounting.initializeForGame(null);
                game.getPlayerTable().setUnitAccounting(accounting);
                game.setUnitStrengthTable(new UnitStrengthTable(gameData.getUnitTypes(), gameData.getWeaponTypes()));

                Unit unit = PreplacedUnitLoaderUtils.create(new UnitCreationContext(false, true, true, false, true, 0), game,
                                                            unitType,
                                                            RESOURCE_POSITION_X, RESOURCE_POSITION_Y,
                                                            PlayerTable.NEUTRAL_PLAYER_ID, 0,
                                                            true,
                                                            INSTRUCTION_LIMIT);

                Assertions.assertNotNull(unit);

                PreplacedUnitLoaderUtils.applyProperties(game,
                                                         unit,
                                                         new ChkUnit(RESOURCE_RECORD_SERIAL,
                                                                     RESOURCE_POSITION_X, RESOURCE_POSITION_Y,
                                                                     unitType,
                                                                     0,
                                                                     ChkUnit.STATE_INVINCIBLE,
                                                                     ChkUnit.ELEMENT_HIT_POINTS | ChkUnit.ELEMENT_RESOURCE_AMOUNT,
                                                                     PlayerTable.NEUTRAL_PLAYER_ID,
                                                                     FULL_HIT_POINTS_PERCENT, 0, 0,
                                                                     resources,
                                                                     0,
                                                                     0,
                                                                     0),
                                                         INSTRUCTION_LIMIT);

                Assertions.assertEquals(resources, unit.getResources());
                Assertions.assertEquals(gameData.getUnits().get("Hit Points", unitType), unit.getHitPoints());

                if (unitType != ScrUnitIds.RESOURCE_VESPENE_GEYSER) {
                    Assertions.assertEquals(resources >= 750 ? 20 : resources >= 500 ? 15 : resources >= 250 ? 14 : 13,
                                            UnitMemoryScenario.of(unit).readMemoryUnsignedByte(RESOURCE_ANIMATION_OFFSET));
                }

                Assertions.assertEquals(1, registryPool.getGeneration(1));
                Assertions.assertEquals(0x0B0B, registryPool.getCreationTag(1));
                Assertions.assertFalse(game.hasInitializedWorldState());
            }
        }
    }
}
