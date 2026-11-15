package org.fogofwarlabs.sc4j.scr.game.unit;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteScenario;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.UnitTestUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Compares unit maintenance state against captured timer results.
 */
@RequiresInstallation
final class UnitTimersTest {
    /**
     * A unit type past units.dat whose flags, read past the units.dat columns, have the spellcaster bit.
     */
    private static final int PAST_UNITS_DAT_SPELLCASTER_TYPE = 351;
    /**
     * The energy of the cloaked unit, with eight fraction bits.
     */
    private static final int CLOAKED_ENERGY = 50 << 8;

    private static final int MAP_DIMENSION = 8;
    private static final int MAP_TILE_COUNT = MAP_DIMENSION * MAP_DIMENSION;
    private static final int ORDER_CAPACITY = 8;
    /**
     * The image offsets of the unit fields that the timer cases write and read.
     */
    private static final int HIT_POINTS_OFFSET = 0x10;
    private static final int COOLDOWN_TIMERS_OFFSET = 0x70;
    private static final int SHIELD_POINTS_OFFSET = 0x88;
    private static final int PRIMARY_ORDER_COUNTDOWN_OFFSET = 0xC9;
    private static final int MOVEMENT_DESTINATION_COUNTDOWN_OFFSET = 0x147;
    private static final int HEALING_STATE_OFFSET = 0x193;
    private static final int REMAINING_LIFETIME_OFFSET = 0x19C;
    private static final int STATUS_CYCLE_ELAPSED_OFFSET = 0x1B2;
    /**
     * The timer update cases of each unit type and the case index bits that choose the image modifier, the sprite flags and the status.
     */
    private static final int TIMER_CASE_COUNT = 256;
    private static final int STATUS_CASE_BIT = 1;
    private static final int HIDDEN_SPRITE_CASE_BIT = 2;
    private static final int FLAGGED_SPRITE_CASE_BIT = 4;
    private static final int MODIFIER_CASE_BIT = 16;
    /**
     * The image modifiers, the image flags, the sprite flags and the status that the timer cases choose from.
     */
    private static final int FIRST_IMAGE_MODIFIER = 10;
    private static final int SECOND_IMAGE_MODIFIER = 11;
    private static final int IMAGE_FLAGS = 0x2000;
    private static final int FLAGGED_SPRITE_FLAG = 0x8;
    private static final int SPRITE_HIDDEN_FLAG = 0x20;
    private static final int COMPLETED_BUILDING_STATUS = 0x3;
    /**
     * The shield fraction bits and the period, the center and the step of the shield points around the maximum shields.
     */
    private static final int SHIELD_FRACTION_BITS = 8;
    private static final int SHIELD_CASE_PERIOD = 5;
    private static final int SHIELD_CASE_CENTER = 2;
    private static final int SHIELD_CASE_STEP = 5;
    /**
     * The multipliers of the case index for the second, third and fourth cooldown byte.
     */
    private static final int SECOND_COOLDOWN_MULTIPLIER = 3;
    private static final int THIRD_COOLDOWN_MULTIPLIER = 5;
    private static final int FOURTH_COOLDOWN_MULTIPLIER = 7;
    /**
     * The primary order countdown and the healing state that every timer case starts with.
     */
    private static final int PRIMARY_ORDER_COUNTDOWN = 0xA5;
    private static final int HEALING_STATE = 0x7E;
    /**
     * The period of the cases without a lifetime, and the smallest lifetime and the lifetime period of the others.
     */
    private static final int NO_LIFETIME_PERIOD = 3;
    private static final int MINIMUM_LIFETIME = 2;
    private static final int LIFETIME_PERIOD = 8;
    private static final @NotNull String DIGEST_ALGORITHM = "SHA-256";
    private static final @NotNull String TIMER_CASES_DIGEST = "4c0534eac8254eaf087454b4b0838df3add66c35f3bbd7efd32f2144fe8ca522";

    /**
     * The lifetime test: the built animation, the cooldown and the countdown before the update, the last lifetime frame, the cooldown
     * after the update and the order of a dying unit.
     */
    private static final int BUILT_ANIMATION = 16;
    private static final int EXPIRING_COOLDOWN = 4;
    private static final int EXPIRING_COUNTDOWN = 2;
    private static final int LAST_LIFETIME = 1;
    private static final int UPDATED_COOLDOWN = 3;
    private static final int DIE_ORDER = 0;
    /**
     * The spriteless failure test: the unit type WORD, the cooldown bytes before and after the cooldown update, the shield points and the
     * status cycle.
     */
    private static final int LAST_TYPE_WORD = 0xFFFF;
    private static final int COOLDOWN_BYTES = 0x03020100;
    private static final int UPDATED_COOLDOWN_BYTES = 0x02010000;
    private static final int SHIELD_POINTS = 1234;
    private static final int STATUS_CYCLE_ELAPSED = 7;

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
    void matches_native_timer_update_cases() throws Exception {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));
        MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);

        for (int unitType : new int[]{
                ScrUnitIds.ZERG_LARVA,
                ScrUnitIds.ZERG_DRONE,
                ScrUnitIds.ZERG_OVERLORD,
                ScrUnitIds.PROTOSS_PROBE,
                ScrUnitIds.ZERG_HATCHERY,
                ScrUnitIds.PROTOSS_NEXUS,
                ScrUnitIds.RESOURCE_MINERAL_FIELD,
                ScrUnitIds.RESOURCE_MINERAL_FIELD_TYPE_2,
                ScrUnitIds.RESOURCE_MINERAL_FIELD_TYPE_3,
                ScrUnitIds.RESOURCE_VESPENE_GEYSER,
                ScrUnitIds.TERRAN_COMMAND_CENTER,
                ScrUnitIds.TERRAN_SCV,
                ScrUnitIds.ZERG_ZERGLING,
                ScrUnitIds.HERO_DEVOURING_ONE
        }) {
            for (int caseIndex = 0; caseIndex < TIMER_CASE_COUNT; caseIndex++) {
                Unit unit = UnitTestUtils.createUnit(1);
                Sprite sprite = SpriteScenario.createSprite(0, 0, 0, 0);
                SpriteImage image = SpriteScenario.createImage(0, 0, 0);

                image.setModifier((caseIndex & MODIFIER_CASE_BIT) != 0 ? SECOND_IMAGE_MODIFIER : FIRST_IMAGE_MODIFIER);
                image.setFlags(IMAGE_FLAGS);
                sprite.insertTailImage(image);
                sprite.setFlags(((caseIndex & FLAGGED_SPRITE_CASE_BIT) != 0 ? FLAGGED_SPRITE_FLAG : 0) |
                                ((caseIndex & HIDDEN_SPRITE_CASE_BIT) != 0 ? SPRITE_HIDDEN_FLAG : 0));
                unit.setSprite(sprite);
                UnitMemoryScenario.of(unit).writeMemoryWord(UnitMemoryScenario.UNIT_TYPE_OFFSET, unitType);
                UnitMemoryScenario.of(unit).writeMemoryInteger(UnitMemoryScenario.STATUS_OFFSET,
                                                               (caseIndex & STATUS_CASE_BIT) != 0 ? COMPLETED_BUILDING_STATUS : 0);

                Assertions.assertNotNull(gameData);

                UnitMemoryScenario.of(unit).writeMemoryInteger(HIT_POINTS_OFFSET, gameData.getUnits().get("Hit Points", unitType));

                int maximumShields = gameData.getUnits().get("Shield Amount", unitType) << SHIELD_FRACTION_BITS;
                int shieldPoints = maximumShields + (caseIndex % SHIELD_CASE_PERIOD - SHIELD_CASE_CENTER) * SHIELD_CASE_STEP;

                UnitMemoryScenario.of(unit).writeMemoryInteger(SHIELD_POINTS_OFFSET, shieldPoints);
                UnitMemoryScenario.of(unit).writeMemoryByte(COOLDOWN_TIMERS_OFFSET, caseIndex);
                UnitMemoryScenario.of(unit).writeMemoryByte(COOLDOWN_TIMERS_OFFSET + 1, caseIndex * SECOND_COOLDOWN_MULTIPLIER);
                UnitMemoryScenario.of(unit).writeMemoryByte(COOLDOWN_TIMERS_OFFSET + 2, caseIndex * THIRD_COOLDOWN_MULTIPLIER);
                UnitMemoryScenario.of(unit).writeMemoryByte(COOLDOWN_TIMERS_OFFSET + 3, caseIndex * FOURTH_COOLDOWN_MULTIPLIER);
                UnitMemoryScenario.of(unit).writeMemoryByte(PRIMARY_ORDER_COUNTDOWN_OFFSET, PRIMARY_ORDER_COUNTDOWN);
                UnitMemoryScenario.of(unit).writeMemoryByte(HEALING_STATE_OFFSET, HEALING_STATE);
                UnitMemoryScenario.of(unit).writeMemoryByte(STATUS_CYCLE_ELAPSED_OFFSET, caseIndex);
                UnitMemoryScenario.of(unit).writeMemoryByte(MOVEMENT_DESTINATION_COUNTDOWN_OFFSET, caseIndex);
                UnitMemoryScenario.of(unit).writeMemoryWord(REMAINING_LIFETIME_OFFSET,
                                                            caseIndex % NO_LIFETIME_PERIOD == 0 ? 0 : MINIMUM_LIFETIME + caseIndex % LIFETIME_PERIOD);

                game.getUnitTimers().update(unit);

                digest.update(UnitMemoryScenario.of(unit).exportMemoryBytes());
                digest.update((byte) image.getFlags());
                digest.update((byte) (image.getFlags() >>> Byte.SIZE));
            }
        }

        Assertions.assertEquals(TIMER_CASES_DIGEST, HexFormat.of().formatHex(digest.digest()));
    }

    @Test
    void update_should_kill_a_unit_whose_lifetime_expires_after_earlier_timer_writes() {
        Game game = Game.create(gameData, new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_TILE_COUNT]), new LcgRandom(0));

        game.initializeOrderPool(ORDER_CAPACITY);

        Unit unit = UnitTestUtils.createUnit(1);
        Sprite sprite = SpriteScenario.createSprite(0, 0, 0, 0);

        unit.setSprite(sprite);

        SpriteImage main = SpriteScenario.createImage(game, 0, 0, 0);

        main.setAnimationType(BUILT_ANIMATION);
        sprite.insertHeadImage(main);
        sprite.setMainImage(main);
        UnitMemoryScenario.of(unit).writeMemoryWord(UnitMemoryScenario.UNIT_TYPE_OFFSET, ScrUnitIds.RESOURCE_MINERAL_FIELD);
        UnitMemoryScenario.of(unit).writeMemoryInteger(UnitMemoryScenario.STATUS_OFFSET, COMPLETED_BUILDING_STATUS);
        UnitMemoryScenario.of(unit).writeMemoryByte(COOLDOWN_TIMERS_OFFSET, EXPIRING_COOLDOWN);
        UnitMemoryScenario.of(unit).writeMemoryByte(MOVEMENT_DESTINATION_COUNTDOWN_OFFSET, EXPIRING_COUNTDOWN);
        UnitMemoryScenario.of(unit).writeMemoryWord(REMAINING_LIFETIME_OFFSET, LAST_LIFETIME);
        unit.setPrimaryOrder(ScrOrderIds.NOTHING);

        // Expiration enters Die before the move-target timer is reset.
        game.getUnitTimers().update(unit);

        Assertions.assertEquals(DIE_ORDER, unit.getPrimaryOrder());
        Assertions.assertEquals(UPDATED_COOLDOWN, UnitMemoryScenario.of(unit).readMemoryUnsignedByte(COOLDOWN_TIMERS_OFFSET));
        Assertions.assertEquals(0, UnitMemoryScenario.of(unit).readMemoryUnsignedByte(MOVEMENT_DESTINATION_COUNTDOWN_OFFSET));
        Assertions.assertEquals(0, UnitMemoryScenario.of(unit).readMemoryUnsignedWord(REMAINING_LIFETIME_OFFSET));
    }

    @Test
    void update_should_fail_without_a_sprite_after_cooldowns_and_before_shield_writes() {
        Game game = Game.create(gameData, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));
        Unit unit = UnitTestUtils.createUnit(1);

        unit.setUnitId(LAST_TYPE_WORD);
        unit.setCooldownTimers(COOLDOWN_BYTES);
        unit.setShieldPoints(SHIELD_POINTS);
        UnitMemoryScenario.of(unit).writeMemoryByte(HEALING_STATE_OFFSET, HEALING_STATE);
        unit.setStatusCycleElapsed(STATUS_CYCLE_ELAPSED);

        UnsupportedOperationException failure = Assertions.assertThrows(UnsupportedOperationException.class, () -> game.getUnitTimers().update(unit));

        Assertions.assertEquals("Unit timer updates require the retained sprite.", failure.getMessage());
        Assertions.assertEquals(UPDATED_COOLDOWN_BYTES, unit.getCooldownTimers());
        Assertions.assertEquals(SHIELD_POINTS, unit.getShieldPoints());
        Assertions.assertEquals(HEALING_STATE, UnitMemoryScenario.of(unit).readMemoryUnsignedByte(HEALING_STATE_OFFSET));
        Assertions.assertEquals(STATUS_CYCLE_ELAPSED, unit.getStatusCycleElapsed());
    }

    @Test
    void update_should_charge_no_cloak_energy_to_a_cloaked_spellcaster_whose_type_is_past_units_dat() {
        Game game = Game.create(gameData, new GameMap(1, 1, 0, new int[1]), new LcgRandom(0));
        Unit unit = UnitTestUtils.createUnit(1);

        unit.setSprite(SpriteScenario.createSprite(0, 0, 0, 0));
        unit.setUnitId(PAST_UNITS_DAT_SPELLCASTER_TYPE);
        unit.setStatus(UnitStatusFlags.COMPLETED | UnitStatusFlags.CLOAKED);
        unit.setEnergy(CLOAKED_ENERGY);

        game.getUnitTimers().update(unit);

        Assertions.assertEquals(CLOAKED_ENERGY, unit.getEnergy());
    }
}
