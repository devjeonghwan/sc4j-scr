package org.fogofwarlabs.sc4j.scr.game.visibility;

import org.fogofwarlabs.sc4j.scr.content.ScrOrderIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStorageConstructor;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks cloak fades and the cloaking of nearby units.
 */
@RequiresInstallation
final class UnitCloakingTest {
    /**
     * The instruction budget of every order and script call.
     */
    private static final int INSTRUCTION_LIMIT = 10000;
    /**
     * A unit type past the units table whose type flags have the building bit.
     */
    private static final int OVERRUN_BUILDING_TYPE = 230;
    /**
     * The slot of the first player, who owns the units.
     */
    private static final int FIRST_PLAYER = SelectedUnitScenario.FIRST_PLAYER;
    /**
     * The pool index of the created unit.
     */
    private static final int UNIT_POOL_INDEX = SelectedUnitScenario.UNIT_POOL_INDEX;
    /**
     * The pool index of the cloak target.
     */
    private static final int TARGET_POOL_INDEX = 2;
    /**
     * The horizontal and vertical WORD position of the created units.
     */
    private static final int UNIT_POSITION = 200;
    /**
     * The cloak counter of a unit one source keeps cloaked.
     */
    private static final int SINGLE_CLOAK_COUNT = 1;
    /**
     * The draw function of an image without a draw function.
     */
    private static final int NO_MODIFIER = 0;
    /**
     * The cloaking draw function.
     */
    private static final int CLOAKING_MODIFIER = 2;
    /**
     * The cloaked draw function.
     */
    private static final int CLOAKED_MODIFIER = 3;
    /**
     * The decloaking draw function.
     */
    private static final int DECLOAKING_MODIFIER = 4;
    /**
     * The fade data bits of the modifier data.
     */
    private static final int FADE_DATA_MASK = 0xFFFF;
    /**
     * The retained fade data of the cloaking case: delay {@code 3}, step {@code 5}.
     */
    private static final int CLOAKING_FADE_DATA = 0x0305;
    /**
     * The retained fade data of the decloaking case: delay {@code 2}, step {@code 7}.
     */
    private static final int DECLOAKING_FADE_DATA = 0x0207;
    /**
     * The retained fade data of the cases that keep no fade.
     */
    private static final int NO_FADE_DATA = 0;
    /**
     * The fade data a decloak starts with: delay {@code 3}, step {@code 8}.
     */
    private static final int DECLOAK_FADE_START = 0x0308;
    /**
     * The cloaked status bit, which a fading-in unit keeps.
     */
    private static final int CLOAKED_STATUS = 0x200;
    /**
     * The {@code units.dat} flag of a building type.
     */
    private static final int BUILDING_TYPE_FLAG = 0x1;
    /**
     * The status flag of a unit cloaked by an Arbiter or permanently.
     */
    private static final int PASSIVELY_CLOAKED_STATUS = 0x800;
    /**
     * The image flag that requests a redraw.
     */
    private static final int REDRAW_IMAGE_FLAG = 0x01;
    /**
     * The status flags of the cloak state: requires a detector ({@code 0x100}) and cloaked ({@code 0x200}).
     */
    private static final int CLOAK_STATUS_MASK = 0x300;
    /**
     * The image flag of an image that is not drawn.
     */
    private static final int HIDDEN_IMAGE_FLAG = 0x40;

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
     * Creates a {@link Game} instance on a small installed-tileset map with one completed unit of the given type, owned and selected by the first
     * player ({@link SelectedUnitScenario#createGame}).
     *
     * @param unitType the unit type
     * @return the created {@link Game} instance
     */
    private static @NotNull Game createGame(int unitType) {
        Assertions.assertNotNull(gameData);

        return SelectedUnitScenario.createGame(gameData, unitType);
    }


    @Test
    void update_cloak_nearby_units_should_cloak_a_type_past_units_dat_with_the_building_bit() {
        Game game = createGame(ScrUnitIds.PROTOSS_ARBITER);
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);
        Assertions.assertNotNull(gameData);

        Unit arbiter = pool.getUnit(UNIT_POOL_INDEX);
        Unit target = pool.getUnit(TARGET_POOL_INDEX);

        UnitStorageConstructor.clearBeforeInnerInitialization(target);

        Assertions.assertTrue(game.getUnitInitializer().initialize(target,
                                                                   ScrUnitIds.TERRAN_MARINE,
                                                                   UNIT_POSITION, UNIT_POSITION,
                                                                   FIRST_PLAYER,
                                                                   INSTRUCTION_LIMIT));

        target.setPrimaryOrder(ScrOrderIds.PLAYER_GUARD);

        UnitSpatialIndex spatialIndex = game.getUnitSystem().getUnitSpatialIndex();

        Assertions.assertNotNull(spatialIndex);

        spatialIndex.insert(target, gameData.getUnitTypes());
        target.setUnitId(OVERRUN_BUILDING_TYPE);

        Assertions.assertNotEquals(0, gameData.getUnitTypes().getFlags(target.getUnitId()) & BUILDING_TYPE_FLAG);

        game.getUnitCloaking().updateCloakNearbyUnits(arbiter);

        Assertions.assertEquals(SINGLE_CLOAK_COUNT, target.getCloakCounter());
        Assertions.assertNotEquals(0, target.getStatus() & PASSIVELY_CLOAKED_STATUS);
        Assertions.assertSame(target, game.getUnitCloaking().getFirstCloakedUnit());
    }

    @Test
    void set_sprite_cloak_modifier_should_set_the_modifier_and_fade_for_each_cloak_state() {
        Assertions.assertNotNull(gameData);

        Game game = createGame(ScrUnitIds.PROTOSS_DARK_TEMPLAR);
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Sprite sprite = unitPool.getUnit(UNIT_POOL_INDEX).getSpriteOrNull();

        Assertions.assertNotNull(sprite);

        SpriteImage image = sprite.getMainImage();

        Assertions.assertNotNull(image);

        game.getUnitCloaking().setSpriteCloakModifier(sprite, true, false, false, CLOAKING_FADE_DATA);

        Assertions.assertEquals(CLOAKING_MODIFIER, image.getModifier());
        Assertions.assertEquals(CLOAKING_FADE_DATA, image.getModifierData1() & FADE_DATA_MASK);
        Assertions.assertEquals(gameData.getImages().get("Draw If Cloaked", image.getImageTypeId()) == 0,
                                (image.getFlags() & HIDDEN_IMAGE_FLAG) != 0);

        image.setModifier(NO_MODIFIER);

        game.getUnitCloaking().setSpriteCloakModifier(sprite, false, true, false, DECLOAKING_FADE_DATA);

        Assertions.assertEquals(DECLOAKING_MODIFIER, image.getModifier());
        Assertions.assertEquals(DECLOAKING_FADE_DATA, image.getModifierData1() & FADE_DATA_MASK);

        game.getUnitCloaking().setSpriteCloakModifier(sprite, true, true, false, NO_FADE_DATA);

        Assertions.assertEquals(CLOAKED_MODIFIER, image.getModifier());

        game.getUnitCloaking().setSpriteCloakModifier(sprite, true, true, true, NO_FADE_DATA);

        Assertions.assertEquals(NO_MODIFIER, image.getModifier());
        Assertions.assertNotEquals(0, image.getFlags() & REDRAW_IMAGE_FLAG);
    }

    @Test
    void decloak_unit_should_start_the_fade_in_of_a_fully_cloaked_unit() {
        Game game = createGame(ScrUnitIds.PROTOSS_DARK_TEMPLAR);
        UnitPool unitPool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(unitPool);

        Unit unit = unitPool.getUnit(UNIT_POOL_INDEX);
        Sprite unitSprite = unit.getSpriteOrNull();

        Assertions.assertNotNull(unitSprite);

        SpriteImage image = unitSprite.getMainImage();

        Assertions.assertNotNull(image);

        unit.setStatus(unit.getStatus() | (UnitStatusFlags.REQUIRES_DETECTOR | UnitStatusFlags.CLOAKED));
        image.setModifier(SpriteImage.MODIFIER_CLOAKED);
        image.setFlags(image.getFlags() | SpriteImage.FLAG_HIDDEN);

        game.getUnitCloaking().decloakUnit(unit);

        Assertions.assertEquals(CLOAKED_STATUS, unit.getStatus() & CLOAK_STATUS_MASK);
        Assertions.assertEquals(DECLOAKING_MODIFIER, image.getModifier());
        Assertions.assertEquals(DECLOAK_FADE_START, image.getModifierData1() & FADE_DATA_MASK);
        Assertions.assertEquals(0, image.getFlags() & HIDDEN_IMAGE_FLAG);
    }
}
