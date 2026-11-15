package org.fogofwarlabs.sc4j.scr.game.combat;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrWeaponIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.game.ai.ComputerTargets;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStorageConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks that weapon and position ranges compare unsigned.
 */
@RequiresInstallation
final class WeaponRangeTest {
    /**
     * The image and order instruction budget of the created units.
     */
    private static final int INSTRUCTION_LIMIT = 10000;
    private static final int UNIT_CAPACITY = 3;
    private static final int SPRITE_CAPACITY = 6;
    private static final int IMAGE_CAPACITY = 32;
    /**
     * The pool indices of the first player's Marine, of the enemy Marine and of the first player's Scout.
     */
    private static final int MARINE_INDEX = 1;
    private static final int ENEMY_INDEX = 2;
    private static final int SCOUT_INDEX = 3;
    /**
     * The horizontal and vertical position of the enemy Marine, far from the units of the first player.
     */
    private static final int ENEMY_POSITION = 900;
    /**
     * The horizontal and vertical position of the first player's Marine, also the vertical position of its Scout.
     */
    private static final int MARINE_POSITION = 100;
    /**
     * The horizontal position of the first player's Scout.
     */
    private static final int SCOUT_POSITION_X = 120;
    /**
     * The minimum range given to the air weapon of the Scout, smaller than {@code -1} as an unsigned DWORD.
     */
    private static final int AIR_MINIMUM_RANGE = 10;
    /**
     * A range that does not reach the enemy Marine.
     */
    private static final int SHORT_RANGE = 32;

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

    private static @NotNull Game createGame() {
        Assertions.assertNotNull(gameData);

        Game game = CombatGameScenario.createGame(gameData);

        CombatGameScenario.initializeWorld(game, UNIT_CAPACITY, SPRITE_CAPACITY, IMAGE_CAPACITY);
        createUnit(game, MARINE_INDEX, ScrUnitIds.TERRAN_MARINE, MARINE_POSITION, MARINE_POSITION, 0);
        createUnit(game, ENEMY_INDEX, ScrUnitIds.TERRAN_MARINE, ENEMY_POSITION, ENEMY_POSITION, 1);
        createUnit(game, SCOUT_INDEX, ScrUnitIds.PROTOSS_SCOUT, SCOUT_POSITION_X, MARINE_POSITION, 0);

        return game;
    }

    private static void createUnit(@NotNull Game game, int poolIndex, int unitType, int positionX, int positionY, int owner) {
        Assertions.assertNotNull(gameData);

        Unit unit = game.getUnitSystem().requireUnitPool().getUnit(poolIndex);

        UnitStorageConstructor.clearBeforeInnerInitialization(unit);

        Assertions.assertTrue(game.getUnitInitializer().initialize(unit, unitType, positionX, positionY, owner, INSTRUCTION_LIMIT));
        unit.setStatus(unit.getStatus() | UnitStatusFlags.COMPLETED);
        unit.setHitPoints(gameData.getUnits().get("Hit Points", unitType));
        unit.requireSprite().setVisibilityFlags(Sprite.VISIBLE_TO_ALL_PLAYERS);
        game.getUnitSystem().requireUnitSpatialIndex().insert(unit, gameData.getUnitTypes());
    }


    @Test
    void maximum_range_of_minus_one_reaches_every_target() {
        Game game = createGame();
        Unit marine = game.getUnitSystem().requireUnitPool().getUnit(MARINE_INDEX);
        Unit enemy = game.getUnitSystem().requireUnitPool().getUnit(ENEMY_INDEX);

        marine.setOrderTargetUnit(enemy);
        marine.setDirectionTargetX(marine.getPositionX());
        marine.setDirectionTargetY(marine.getPositionY());

        int range = game.requireGameData().getWeapons().get("Maximum Range", ScrWeaponIds.GAUSS_RIFLE);

        try {
            Assertions.assertFalse(game.getUnitTargeting().isInRange(marine, enemy, range), "the far target is outside the installed range");
            Assertions.assertFalse(game.getUnitAttack().canFireWeapon(marine, ScrWeaponIds.GAUSS_RIFLE));

            game.requireGameData().getWeapons().set("Maximum Range", ScrWeaponIds.GAUSS_RIFLE, -1);

            Assertions.assertTrue(game.getUnitTargeting().isInRange(marine, enemy, -1));
            Assertions.assertTrue(game.getUnitTargeting().isInWeaponMovementRange(marine, enemy));
            Assertions.assertFalse(game.getUnitTargeting().isOutOfMaximumRange(marine, enemy));
            Assertions.assertTrue(game.getUnitAttack().canFireWeapon(marine, ScrWeaponIds.GAUSS_RIFLE));

            marine.setOrderTargetUnit(marine);

            Assertions.assertTrue(game.getUnitAttack().canFireWeapon(marine, ScrWeaponIds.GAUSS_RIFLE),
                                  "a unit ordered to attack itself fires at distance 0");
        } finally {
            game.requireGameData().getWeapons().set("Maximum Range", ScrWeaponIds.GAUSS_RIFLE, range);
        }
    }

    @Test
    void acquisition_takes_unsigned_smaller_minimum_range_and_compares_both_ranges_unsigned() {
        Game game = createGame();
        Unit scout = game.getUnitSystem().requireUnitPool().getUnit(SCOUT_INDEX);
        Unit enemy = game.getUnitSystem().requireUnitPool().getUnit(ENEMY_INDEX);
        DatTable weapons = game.requireGameData().getWeapons();
        int groundWeaponIdentifier = game.requireGameData().getUnits().get("Ground Weapon", ScrUnitIds.PROTOSS_SCOUT);
        int airWeaponIdentifier = game.requireGameData().getUnits().get("Air Weapon", ScrUnitIds.PROTOSS_SCOUT);

        Assertions.assertNotEquals(groundWeaponIdentifier, airWeaponIdentifier);

        int groundMinimum = weapons.get("Minimum Range", groundWeaponIdentifier);
        int airMinimum = weapons.get("Minimum Range", airWeaponIdentifier);

        try {
            // -1 compares as the larger DWORD, so 10 is the minimum range.
            weapons.set("Minimum Range", groundWeaponIdentifier, -1);
            weapons.set("Minimum Range", airWeaponIdentifier, AIR_MINIMUM_RANGE);

            Assertions.assertEquals(AIR_MINIMUM_RANGE, game.getUnitTargeting().getAcquisitionMinimumRange(scout));

            weapons.set("Minimum Range", airWeaponIdentifier, -1);

            Assertions.assertEquals(-1, game.getUnitTargeting().getAcquisitionMinimumRange(scout));

            // A minimum range of -1 excludes every target.
            Assertions.assertFalse(game.getUnitTargeting().isWithinAcquisitionDistance(scout, enemy, -1, Integer.MAX_VALUE));
            Assertions.assertTrue(game.getUnitTargeting().isWithinAcquisitionDistance(scout, enemy, 0, -1),
                                  "an acquisition distance of -1 reaches every target");
            Assertions.assertFalse(game.getUnitTargeting().isWithinAcquisitionDistance(scout, enemy, 0, SHORT_RANGE));
        } finally {
            weapons.set("Minimum Range", groundWeaponIdentifier, groundMinimum);
            weapons.set("Minimum Range", airWeaponIdentifier, airMinimum);
        }
    }

    @Test
    void position_range_compares_unsigned() {
        Game game = createGame();
        Unit marine = game.getUnitSystem().requireUnitPool().getUnit(MARINE_INDEX);

        Assertions.assertTrue(ComputerTargets.isNear(marine, -1, ENEMY_POSITION, ENEMY_POSITION));
        Assertions.assertFalse(ComputerTargets.isNear(marine, SHORT_RANGE, ENEMY_POSITION, ENEMY_POSITION));
        Assertions.assertTrue(ComputerTargets.isNear(marine, 0, marine.getPositionX(), marine.getPositionY()));
    }
}
