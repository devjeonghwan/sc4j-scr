package org.fogofwarlabs.sc4j.scr.game.state;

import org.fogofwarlabs.sc4j.base.exception.IgnoreException;
import org.fogofwarlabs.sc4j.engine.content.modifier.Stat;
import org.fogofwarlabs.sc4j.engine.content.type.ContentType;
import org.fogofwarlabs.sc4j.engine.content.type.UnitType;
import org.fogofwarlabs.sc4j.scr.content.*;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTechnology;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerUpgrades;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitQueries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks that the shared game rules outside the computer player handle a unit of an added unit type as a unit of its base type, with the values
 * of its own type: the upgrade modifiers of the stats, the technologies an ownership transfer grants, the per-type queries of cloaking and weapons,
 * and a whole fight of upgraded copies of the Marine and the Medic.
 */
@RequiresInstallation
final class AddedUnitRulesTest {
    /**
     * The offset of the created units below the human player's town center.
     */
    private static final int UNIT_OFFSET_Y = 96;
    /**
     * The scripted fight: a wave of Marines and Medics of the human player with their upgrades, created below its town center, attack-moves to
     * the computer's town center and the game runs on for a fixed number of frames.
     */
    private static final int WAVE_SIZE = 8;
    private static final int MEDIC_INTERVAL = 4;
    private static final int WAVE_SPACING = 24;
    private static final int FIGHT_FRAMES = 2400;
    /**
     * The maximum energy, sight range and armor the modifiers start from: any value the upgrades change.
     */
    private static final int MAXIMUM_ENERGY = 200 << 8;
    private static final int SIGHT_RANGE = 7;
    private static final int ARMOR = 1 << 8;

    private static @Nullable GameData gameData;
    private static int addedMarine;
    private static int addedMedic;
    private static int addedGhost;
    private static int addedVessel;
    private static int addedZealot;
    private static int addedZergling;
    private static int addedUltralisk;
    private static int addedWraith;
    private static int addedLurker;

    @BeforeAll
    static void loadGameData() throws IOException {
        GameData installedData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());

        gameData       = installedData;
        addedMarine    = addType(installedData, "Marine copy", ScrUnits.TERRAN_MARINE);
        addedMedic     = addType(installedData, "Medic copy", ScrUnits.TERRAN_MEDIC);
        addedGhost     = addType(installedData, "Ghost copy", ScrUnits.TERRAN_GHOST);
        addedVessel    = addType(installedData, "Science Vessel copy", ScrUnits.TERRAN_SCIENCE_VESSEL);
        addedZealot    = addType(installedData, "Zealot copy", ScrUnits.PROTOSS_ZEALOT);
        addedZergling  = addType(installedData, "Zergling copy", ScrUnits.ZERG_ZERGLING);
        addedUltralisk = addType(installedData, "Ultralisk copy", ScrUnits.ZERG_ULTRALISK);
        addedWraith    = addType(installedData, "Wraith copy", ScrUnits.TERRAN_WRAITH);
        addedLurker    = addType(installedData, "Lurker copy", ScrUnits.ZERG_LURKER);
    }

    @AfterAll
    static void closeGameData() {
        GameData installedData = gameData;

        gameData = null;

        if (installedData != null) {
            IgnoreException.execute(
                    installedData::close
            );
        }
    }


    private static int addType(@NotNull GameData installedData, @NotNull String name, @NotNull UnitType baseType) {
        return installedData.getAddedUnitTypes().add(new ScrAddedUnitType.Builder(new UnitType(name), name, baseType,
                                                                                  installedData.getUnits()).build());
    }

    private static @NotNull ReplayRuntime start() throws IOException {
        Assertions.assertNotNull(gameData);

        return RunningGameScenario.start(gameData);
    }

    private static @NotNull Unit findTownCenter(@NotNull Game game, int owner) {
        UnitPool pool = game.getUnitSystem().requireUnitPool();
        ScrUnitTypeView units = game.requireGameData().getUnitTypes();

        for (int index = pool.getFirstOwnerIndex(owner); index != 0; index = pool.getNextOwnerIndex(index)) {
            Unit unit = pool.getUnit(index);

            if (!unit.isDestroyed() && (units.getFlags(unit.getUnitId()) & UnitTypeFlags.RESOURCE_DEPOT) != 0) {
                return unit;
            }
        }

        return Assertions.fail("The player has a town center.");
    }

    private static @NotNull Unit createBelowTownCenter(@NotNull Game game, int unitType, int owner) {
        Unit center = findTownCenter(game, RunningGameScenario.HUMAN);

        return RunningGameScenario.createUnit(game, unitType, center.getPositionX(), center.getPositionY() + UNIT_OFFSET_Y, owner);
    }

    private static void assertSameModifier(@NotNull Game game, @NotNull Stat stat, int nativeType, int addedType, int weapon, int value) {
        int nativeValue = game.applyModifiers(stat, RunningGameScenario.HUMAN, nativeType, weapon, value);

        Assertions.assertNotEquals(value, nativeValue, stat + " of " + nativeType);
        Assertions.assertEquals(nativeValue, game.applyModifiers(stat, RunningGameScenario.HUMAN, addedType, weapon, value),
                                stat + " of " + addedType);
    }

    /**
     * Plays the scripted fight with Marines and Medics of two types and returns, for every frame, the units of every player without their types:
     * pool index, owner, position, hit points, energy, primary order and order target. The rules treat both types the same exactly when the lists
     * are equal.
     *
     * @param marineType the unit type of the Marines
     * @param medicType  the unit type of the Medics
     * @return the units of every player after each frame
     * @throws IOException if the game cannot be started
     */
    private static @NotNull List<String> playFight(int marineType, int medicType) throws IOException {
        ReplayRuntime runtime = start();
        Game game = runtime.getGame();
        Unit humanCenter = findTownCenter(game, RunningGameScenario.HUMAN);
        Unit computerCenter = findTownCenter(game, RunningGameScenario.COMPUTER);
        List<String> frames = new ArrayList<>();

        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            PlayerUpgrades upgrades = game.getPlayerTable().requirePlayerUpgrades();

            upgrades.setLevel(RunningGameScenario.HUMAN, ScrUpgradeIds.U_238_SHELLS, 1);
            upgrades.setLevel(RunningGameScenario.HUMAN, ScrUpgradeIds.CADUCEUS_REACTOR, 1);
            game.getPlayerTable().requirePlayerTechnology().setResearched(RunningGameScenario.HUMAN, ScrTechIds.STIM_PACKS, true);

            for (int index = 0; index < WAVE_SIZE; index++) {
                int unitType = index % MEDIC_INTERVAL == MEDIC_INTERVAL - 1 ? medicType : marineType;
                Unit unit = RunningGameScenario.createUnit(game, unitType, humanCenter.getPositionX() + index * WAVE_SPACING,
                                                           humanCenter.getPositionY() + UNIT_OFFSET_Y, RunningGameScenario.HUMAN);

                game.getOrders().issue(unit, ScrOrderIds.ATTACK_MOVE, null, computerCenter.getPositionX(), computerCenter.getPositionY(),
                                       ScrUnitIds.NONE, false, RunningGameScenario.INSTRUCTION_LIMIT);
            }
        });

        UnitPool pool = game.getUnitSystem().requireUnitPool();

        for (int frame = 0; frame < FIGHT_FRAMES; frame++) {
            Assertions.assertTrue(runtime.step(0, game.getCurrentFrame() * runtime.getFrameDuration()));
            Assertions.assertNull(runtime.getFailure());

            StringBuilder units = new StringBuilder();

            for (int owner = 0; owner < PlayerTable.PLAYER_SLOTS; owner++) {
                for (int index = pool.getFirstOwnerIndex(owner); index != 0; index = pool.getNextOwnerIndex(index)) {
                    Unit unit = pool.getUnit(index);
                    Unit target = unit.getOrderTargetUnit();

                    units.append(index).append(':').append(owner).append(':').append(unit.getPositionX()).append(',').append(unit.getPositionY())
                         .append(':').append(unit.getHitPoints()).append(':').append(unit.getEnergy()).append(':').append(unit.getPrimaryOrder())
                         .append(':').append(target == null ? 0 : target.getIndex()).append(' ');
                }
            }

            frames.add(units.toString());
        }

        return frames;
    }


    @Test
    void get_base_type_should_return_the_base_type_of_an_added_type_and_every_other_type_itself() throws IOException {
        Game game = start().getGame();
        ScrUnitTypeView units = game.requireGameData().getUnitTypes();

        Assertions.assertEquals(ScrUnitIds.TERRAN_MARINE, game.getBaseType(addedMarine));
        Assertions.assertEquals(ScrUnitIds.ZERG_LURKER, units.getBaseType(addedLurker));
        Assertions.assertEquals(ScrUnitIds.TERRAN_GOLIATH, game.getBaseType(ScrUnitIds.TERRAN_GOLIATH));
        Assertions.assertEquals(ScrUnitIds.TERRAN_GOLIATH, units.getBaseType(ScrUnitIds.TERRAN_GOLIATH));
        Assertions.assertEquals(ScrUnitIds.NONE, units.getBaseType(ScrUnitIds.NONE));
        Assertions.assertEquals(addedLurker + 1, game.getBaseType(addedLurker + 1));
        Assertions.assertEquals(addedLurker + 1, units.getBaseType(addedLurker + 1));
    }

    @Test
    void stat_modifiers_apply_the_upgrades_of_the_base_type_to_an_added_type() throws IOException {
        ReplayRuntime runtime = start();
        Game game = runtime.getGame();
        ScrUnitTypeView units = game.requireGameData().getUnitTypes();
        int gaussRifle = units.getGroundWeapon(ScrUnitIds.TERRAN_MARINE);

        Assertions.assertEquals(units.getGroundWeapon(ScrUnitIds.TERRAN_MARINE), units.getGroundWeapon(addedMarine));

        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            PlayerUpgrades upgrades = game.getPlayerTable().requirePlayerUpgrades();

            for (int upgrade : new int[]{ScrUpgradeIds.U_238_SHELLS, ScrUpgradeIds.OCULAR_IMPLANTS, ScrUpgradeIds.TITAN_REACTOR,
                                         ScrUpgradeIds.LEG_ENHANCEMENTS, ScrUpgradeIds.ADRENAL_GLANDS, ScrUpgradeIds.CHITINOUS_PLATING}) {
                upgrades.setLevel(RunningGameScenario.HUMAN, upgrade, 1);
            }
        });

        int range = game.requireGameData().getWeaponTypes().getMaximumRange(gaussRifle);

        assertSameModifier(game, Stat.WEAPON_RANGE, ScrUnitIds.TERRAN_MARINE, addedMarine, gaussRifle, range);
        assertSameModifier(game, Stat.TARGET_ACQUISITION_RANGE, ScrUnitIds.TERRAN_MARINE, addedMarine, ContentType.NO_INDEX,
                           units.getTargetAcquisitionRange(ScrUnitIds.TERRAN_MARINE));
        assertSameModifier(game, Stat.SIGHT_RANGE, ScrUnitIds.TERRAN_GHOST, addedGhost, ContentType.NO_INDEX, SIGHT_RANGE);
        assertSameModifier(game, Stat.MAXIMUM_ENERGY, ScrUnitIds.TERRAN_SCIENCE_VESSEL, addedVessel, ContentType.NO_INDEX, MAXIMUM_ENERGY);
        assertSameModifier(game, Stat.TOP_SPEED, ScrUnitIds.PROTOSS_ZEALOT, addedZealot, ContentType.NO_INDEX, 0);
        assertSameModifier(game, Stat.WEAPON_COOLDOWN, ScrUnitIds.ZERG_ZERGLING, addedZergling, ContentType.NO_INDEX, 0);
        assertSameModifier(game, Stat.ARMOR, ScrUnitIds.ZERG_ULTRALISK, addedUltralisk, ContentType.NO_INDEX, ARMOR);
        Assertions.assertEquals(range, game.applyModifiers(Stat.WEAPON_RANGE, RunningGameScenario.HUMAN, addedMedic, gaussRifle, range),
                                "A type without the upgrade keeps the value.");
    }

    @Test
    void ownership_transfer_grants_the_technologies_of_the_base_type_of_an_added_type() throws IOException {
        ReplayRuntime runtime = start();
        Game game = runtime.getGame();
        PlayerTechnology technology = game.getPlayerTable().requirePlayerTechnology();

        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            technology.setResearched(RunningGameScenario.COMPUTER, ScrTechIds.STIM_PACKS, false);

            Unit unit = createBelowTownCenter(game, addedMarine, RunningGameScenario.HUMAN);

            Assertions.assertTrue(game.getUnitOwnership().giveUnit(unit, RunningGameScenario.COMPUTER, RunningGameScenario.INSTRUCTION_LIMIT));
        });

        Assertions.assertTrue(technology.isResearched(RunningGameScenario.COMPUTER, ScrTechIds.STIM_PACKS));
    }

    @Test
    void unit_queries_apply_the_rules_of_the_base_type_to_an_added_type() throws IOException {
        ReplayRuntime runtime = start();
        Game game = runtime.getGame();
        ScrUnitTypeView units = game.requireGameData().getUnitTypes();
        Unit[] created = new Unit[2];

        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            created[0] = createBelowTownCenter(game, addedWraith, RunningGameScenario.HUMAN);
            created[1] = createBelowTownCenter(game, addedLurker, RunningGameScenario.HUMAN);
        });

        Assertions.assertEquals(ScrTechIds.CLOAKING_FIELD, UnitQueries.getCloakTechnology(units, created[0]));
        Assertions.assertEquals(ScrWeaponIds.NONE, game.getUnitTargeting().getGroundWeapon(created[1]), "An unburrowed Lurker copy has no weapon.");
    }

    @Test
    void upgraded_copies_of_the_marine_and_the_medic_fight_as_marines_and_medics() throws IOException {
        List<String> natives = playFight(ScrUnitIds.TERRAN_MARINE, ScrUnitIds.TERRAN_MEDIC);
        List<String> copies = playFight(addedMarine, addedMedic);

        Assertions.assertEquals(FIGHT_FRAMES, copies.size());

        for (int frame = 0; frame < FIGHT_FRAMES; frame++) {
            Assertions.assertEquals(natives.get(frame), copies.get(frame), "Frame " + frame);
        }

        Assertions.assertNotEquals(natives.getFirst(), natives.getLast());
    }
}
