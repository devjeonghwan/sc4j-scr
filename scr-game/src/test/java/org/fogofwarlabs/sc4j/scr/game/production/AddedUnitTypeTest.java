package org.fogofwarlabs.sc4j.scr.game.production;

import org.fogofwarlabs.sc4j.base.exception.IgnoreException;
import org.fogofwarlabs.sc4j.engine.content.type.UnitType;
import org.fogofwarlabs.sc4j.scr.content.*;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.UnitsDat;
import org.fogofwarlabs.sc4j.scr.data.format.replay.*;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.ai.UnitStrengthTable;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTechnology;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerUnitAvailability;
import org.fogofwarlabs.sc4j.scr.game.player.UnitAccounting;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitCreationContext;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks a unit type added after the native {@code units.dat} entries: a copy of the Terran Marine trained at a Command Center, read through
 * the game's tables, trained from a written and parsed replay that plays back with equal state hashes, fighting, and refused by the native-only
 * paths.
 */
@RequiresInstallation
final class AddedUnitTypeTest {
    /**
     * The values the test registration changes: free, without supply and quickly trained, so the replay's player always trains it.
     */
    private static final int ADDED_MINERAL_COST = 0;
    private static final int ADDED_SUPPLY_REQUIRED = 0;
    private static final int ADDED_BUILD_TIME = 30;
    /**
     * The fraction bits of fixed-point hit points.
     */
    private static final int HIT_POINT_FRACTION_BITS = 8;
    /**
     * The hit points of the added type, with {@link #HIT_POINT_FRACTION_BITS} fraction bits: more than the Marine's.
     */
    private static final int ADDED_HIT_POINTS = 60 << HIT_POINT_FRACTION_BITS;
    private static final @NotNull String ADDED_NAME = "Test Trooper";

    /**
     * The frame at which the injected commands select the Command Center and train the added type, and the last frame the playbacks run to.
     */
    private static final int TRAIN_FRAME = 400;
    private static final int LAST_FRAME = 1600;

    /**
     * The offset of the fighting units (the added unit and an enemy Goliath) below the human player's Command Center, and their distance.
     */
    private static final int FIGHT_OFFSET_Y = 96;
    private static final int FIGHT_DISTANCE = 32;
    private static final int FIGHT_FRAMES = 240;
    private static final int CREATION_TAG_OWNER_SHIFT = 8;
    /**
     * The computer game: added units of the computer below its town center, and the human player's waves of added units and Marines that
     * attack-move to the computer's town center, with Stim Packs researched by both players.
     */
    private static final int ARMY_SIZE = 6;
    private static final int ARMY_SPACING = 24;
    private static final int WAVE_COUNT = 4;
    private static final int WAVE_FRAMES = 900;

    private static @Nullable GameData gameData;
    private static int addedId;

    @BeforeAll
    static void loadGameData() throws IOException {
        GameData installedData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());
        ScrAddedUnitType trooper = new ScrAddedUnitType.Builder(new UnitType(ADDED_NAME), ADDED_NAME, ScrUnits.TERRAN_MARINE,
                                                                installedData.getUnits())
                .setRequirementType(ScrUnits.TERRAN_SCV)
                .setValue(UnitsDat.MINERAL_COST, ADDED_MINERAL_COST)
                .setValue(UnitsDat.SUPPLY_REQUIRED, ADDED_SUPPLY_REQUIRED)
                .setValue(UnitsDat.BUILD_TIME, ADDED_BUILD_TIME)
                .setValue(UnitsDat.HIT_POINTS, ADDED_HIT_POINTS)
                .build();

        gameData = installedData;
        addedId  = installedData.getAddedUnitTypes().add(trooper);
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


    private static @NotNull GameData requireGameData() {
        Assertions.assertNotNull(gameData);

        return gameData;
    }

    private static @NotNull ReplayFile readReplay() throws IOException {
        return ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_TERRAN_VS_COMPUTER)));
    }

    /**
     * Runs a replay to a frame and returns the state hash of every frame.
     *
     * @param replay    the replay
     * @param lastFrame the last frame
     * @param runtimes  the list that receives the runtime, stopped at the last frame
     * @return the state hashes, in frame order
     */
    private static @NotNull List<Long> runReplay(@NotNull ReplayFile replay, int lastFrame, @NotNull List<ReplayRuntime> runtimes) throws IOException {
        ReplayRuntime runtime = RunningGameScenario.createRuntime(replay, requireGameData());
        List<Long> hashes = new ArrayList<>();

        runtime.start(0, 0);

        Game game = runtime.getGame();

        while (game.getCurrentFrame() < lastFrame) {
            Assertions.assertTrue(runtime.step(0, game.getCurrentFrame() * runtime.getFrameDuration()));

            hashes.add(game.computeStateHash());
        }

        runtimes.add(runtime);

        return hashes;
    }

    private static @Nullable Unit findUnit(@NotNull Game game, int owner, int unitType) {
        UnitPool pool = game.getUnitSystem().requireUnitPool();

        for (int index = pool.getFirstOwnerIndex(owner); index != 0; index = pool.getNextOwnerIndex(index)) {
            Unit unit = pool.getUnit(index);

            if (unit.getUnitId() == unitType && !unit.isDestroyed()) {
                return unit;
            }
        }

        return null;
    }

    private static @Nullable Unit findTownCenter(@NotNull Game game, int owner) {
        UnitPool pool = game.getUnitSystem().requireUnitPool();
        ScrUnitTypeView units = game.requireGameData().getUnitTypes();

        for (int index = pool.getFirstOwnerIndex(owner); index != 0; index = pool.getNextOwnerIndex(index)) {
            Unit unit = pool.getUnit(index);

            if (!unit.isDestroyed() && (units.getFlags(unit.getUnitId()) & UnitTypeFlags.RESOURCE_DEPOT) != 0) {
                return unit;
            }
        }

        return null;
    }

    private static @NotNull Sprite findBaseSprite(@NotNull Game game) {
        Unit commandCenter = findUnit(game, RunningGameScenario.HUMAN, ScrUnitIds.TERRAN_COMMAND_CENTER);

        Assertions.assertNotNull(commandCenter);

        Sprite sprite = commandCenter.getSpriteOrNull();

        Assertions.assertNotNull(sprite);

        return sprite;
    }

    /**
     * Creates a completed unit, as {@link RunningGameScenario#createUnit} does, at a position away from the map edges.
     */
    private static @NotNull Unit createUnit(@NotNull Game game, int unitType, int positionX, int positionY, int owner) {
        UnitCreationContext creation = game.getUnitSystem().getUnitCreationContext();
        UnitAccounting accounting = game.getPlayerTable().requireUnitAccounting();

        Assertions.assertNotNull(creation);

        Unit unit = game.getUnitStorageConstructor().create(creation, accounting, unitType, positionX, positionY, owner,
                                                            owner | owner << CREATION_TAG_OWNER_SHIFT, RunningGameScenario.INSTRUCTION_LIMIT);

        Assertions.assertNotNull(unit);

        Player player = game.getPlayerTable().getPlayer(owner);

        Assertions.assertNotNull(player);
        Assertions.assertTrue(game.getUnitStorageConstructor().tryComplete(creation, unit, player.getType(), RunningGameScenario.INSTRUCTION_LIMIT));

        return unit;
    }

    /**
     * Creates the replay of {@link GameTestResources#REPLAY_TERRAN_VS_COMPUTER} with two commands of its player at {@link #TRAIN_FRAME}: select
     * the Command Center, train the added type.
     */
    private static @NotNull ReplayFile createTrainingReplay() throws IOException {
        ReplayFile original = readReplay();
        List<ReplayRuntime> runtimes = new ArrayList<>();

        runReplay(original, TRAIN_FRAME, runtimes);

        Game game = runtimes.getFirst().getGame();
        Unit commandCenter = findUnit(game, RunningGameScenario.HUMAN, ScrUnitIds.TERRAN_COMMAND_CENTER);

        Assertions.assertNotNull(commandCenter);

        ReplayCommand firstSelection = original.getCommands().stream().filter(command -> ReplaySelectionCommand.supports(command.getTypeId()))
                                               .findFirst().orElseThrow();
        int selectType = firstSelection.getTypeId() >= ReplayCommandIds.SELECT_121 ? ReplayCommandIds.SELECT_121 : ReplayCommandIds.SELECT;
        int handleWidth = selectType == ReplayCommandIds.SELECT_121 ? Integer.BYTES : Short.BYTES;
        long handle = game.getUnitSystem().requireUnitPool().getHandle(commandCenter.getIndex());
        ReplaySelectionCommand select = ReplaySelectionCommand.create(ReplaySelectionCommand.Operation.REPLACE, handleWidth, new long[]{handle});
        List<ReplayCommand> commands = new ArrayList<>();
        boolean injected = false;

        for (ReplayCommand command : original.getCommands()) {
            if (!injected && command.getFrame() > TRAIN_FRAME) {
                commands.add(new ReplayCommand(TRAIN_FRAME, firstSelection.getPlayerId(), selectType,
                                               ReplayCommandCodecs.encode(ReplaySelectionCommand.createCodec(selectType), select)));
                commands.add(new ReplayCommand(TRAIN_FRAME, firstSelection.getPlayerId(), ReplayCommandIds.TRAIN,
                                               ReplayCommandCodecs.encodeValue(ReplayCommandIds.TRAIN, addedId)));
                injected = true;
            }

            commands.add(command);
        }

        Assertions.assertTrue(injected);

        byte[] header = original.getHeaderSection();
        byte[] mapData = original.getMapData();

        Assertions.assertNotNull(header);
        Assertions.assertNotNull(mapData);

        byte[] written = new ReplayWriter(header,
                                          ReplayWriter.encodeCommands(commands),
                                          mapData,
                                          ReplayWriter.encodeNames(original.getPlayerNameBytes()),
                                          new byte[ReplayWriter.SKIN_SIZE],
                                          original.getLimits().getRequestedValues(),
                                          original.getGameVersion().getFlags(),
                                          new byte[ReplayWriter.CUSTOM_COLORS_SIZE],
                                          ReplayWriter.encodeVersionBlock(original.getGameVersion().getBuild(), 0)).write();

        return ReplayFile.parse(written);
    }

    /**
     * Plays the computer game ({@link #ARMY_SIZE}, {@link #WAVE_COUNT}) and returns the state hash of every frame.
     */
    private static @NotNull List<Long> playComputerGame() throws IOException {
        ReplayRuntime runtime = RunningGameScenario.start(requireGameData());
        Game game = runtime.getGame();
        Unit humanCenter = findUnit(game, RunningGameScenario.HUMAN, ScrUnitIds.TERRAN_COMMAND_CENTER);
        Unit computerCenter = findTownCenter(game, RunningGameScenario.COMPUTER);
        PlayerTechnology technology = game.getPlayerTable().getPlayerTechnology();
        List<Long> hashes = new ArrayList<>();

        Assertions.assertNotNull(humanCenter);
        Assertions.assertNotNull(computerCenter);
        Assertions.assertNotNull(technology);

        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            technology.setResearched(RunningGameScenario.HUMAN, ScrTechIds.STIM_PACKS, true);
            technology.setResearched(RunningGameScenario.COMPUTER, ScrTechIds.STIM_PACKS, true);

            for (int index = 0; index < ARMY_SIZE; index++) {
                RunningGameScenario.createUnit(game, addedId, computerCenter.getPositionX() + index * ARMY_SPACING,
                                               computerCenter.getPositionY() + FIGHT_OFFSET_Y, RunningGameScenario.COMPUTER);
            }
        });

        for (int wave = 0; wave < WAVE_COUNT; wave++) {
            RunningGameScenario.runInCommandDispatch(runtime, () -> {
                for (int index = 0; index < ARMY_SIZE; index++) {
                    int unitType = index % 2 == 0 ? addedId : ScrUnitIds.TERRAN_MARINE;
                    Unit unit = RunningGameScenario.createUnit(game, unitType, humanCenter.getPositionX() + index * ARMY_SPACING,
                                                               humanCenter.getPositionY() + FIGHT_OFFSET_Y, RunningGameScenario.HUMAN);

                    game.getOrders().issue(unit, ScrOrderIds.ATTACK_MOVE, null, computerCenter.getPositionX(), computerCenter.getPositionY(),
                                           ScrUnitIds.NONE, false, RunningGameScenario.INSTRUCTION_LIMIT);
                }
            });

            for (int frame = 0; frame < WAVE_FRAMES; frame++) {
                Assertions.assertTrue(runtime.step(0, game.getCurrentFrame() * runtime.getFrameDuration()));
                Assertions.assertNull(runtime.getFailure());

                hashes.add(game.computeStateHash());
            }
        }

        UnitAccounting accounting = game.getPlayerTable().requireUnitAccounting();
        int humanAdded = accounting.getTypeCount(addedId, RunningGameScenario.HUMAN);
        int computerAdded = accounting.getTypeCount(addedId, RunningGameScenario.COMPUTER);

        Assertions.assertTrue(humanAdded + computerAdded < WAVE_COUNT * ARMY_SIZE / 2 + ARMY_SIZE,
                              "Added units die in the fights: " + humanAdded + " of the human player and " + computerAdded +
                              " of the computer live.");

        return hashes;
    }


    @Test
    void added_type_reads_its_registration_through_the_game_tables() throws IOException {
        ReplayRuntime runtime = RunningGameScenario.start(requireGameData());
        GameData data = runtime.getGame().requireGameData();
        ScrUnitTypeView units = data.getUnitTypes();
        int marine = ScrUnitIds.TERRAN_MARINE;

        Assertions.assertEquals(ScrAddedUnitTypes.FIRST_ID, addedId);
        Assertions.assertEquals(1, units.getAddedCount());
        Assertions.assertTrue(units.isAddedType(addedId));
        Assertions.assertTrue(units.hasEntry(addedId));
        Assertions.assertFalse(units.isAddedType(ScrUnitIds.NONE));
        Assertions.assertFalse(units.hasEntry(addedId + 1));

        Assertions.assertEquals(ADDED_HIT_POINTS, units.getHitPoints(addedId));
        Assertions.assertEquals(ADDED_MINERAL_COST, units.getMineralCost(addedId));
        Assertions.assertEquals(ADDED_BUILD_TIME, units.getBuildTime(addedId));
        Assertions.assertEquals(ADDED_SUPPLY_REQUIRED, units.getSupplyRequired(addedId));

        Assertions.assertEquals(units.getFlags(marine), units.getFlags(addedId));
        Assertions.assertEquals(units.getFlingyType(marine), units.getFlingyType(addedId));
        Assertions.assertEquals(units.getGroundWeapon(marine), units.getGroundWeapon(addedId));
        Assertions.assertEquals(units.getPortrait(marine), units.getPortrait(addedId));
        Assertions.assertEquals(units.getReadySound(marine), units.getReadySound(addedId));
        Assertions.assertEquals(units.getYesSoundStart(marine), units.getYesSoundStart(addedId));
        Assertions.assertEquals(units.getTraits(marine), units.getTraits(addedId));
        Assertions.assertEquals(units.getInitialEnergy(marine), units.getInitialEnergy(addedId));

        Assertions.assertEquals(marine, data.getButtonSet(addedId));
        Assertions.assertEquals(ADDED_NAME, runtime.getGame().getUnitDisplayName(addedId));

        UnitStrengthTable strengths = runtime.getGame().getUnitStrengthTable();

        Assertions.assertNotNull(strengths);
        Assertions.assertEquals(1, strengths.getAddedCount());
        Assertions.assertTrue(strengths.getStrength(addedId, UnitStrengthTable.GROUND) > strengths.getStrength(marine, UnitStrengthTable.GROUND));
        Assertions.assertEquals(strengths.getStrength(marine, UnitStrengthTable.AIR) == 0,
                                strengths.getStrength(addedId, UnitStrengthTable.AIR) == 0);
    }

    @Test
    void added_type_is_trained_from_a_written_replay_that_plays_back_with_equal_hashes() throws IOException {
        ReplayFile replay = createTrainingReplay();

        Assertions.assertTrue(replay.getCommands().stream().anyMatch(command -> command.getTypeId() == ReplayCommandIds.TRAIN &&
                                                                                command.getFrame() == TRAIN_FRAME));

        List<ReplayRuntime> runtimes = new ArrayList<>();
        List<Long> first = runReplay(replay, LAST_FRAME, runtimes);
        List<Long> second = runReplay(replay, LAST_FRAME, runtimes);

        Assertions.assertEquals(first, second);

        for (ReplayRuntime runtime : runtimes) {
            Game game = runtime.getGame();
            UnitAccounting accounting = game.getPlayerTable().requireUnitAccounting();
            Unit trained = findUnit(game, RunningGameScenario.HUMAN, addedId);

            Assertions.assertNull(runtime.getFailure());
            Assertions.assertEquals(1, accounting.getCompletedTypeCount(addedId, RunningGameScenario.HUMAN));
            Assertions.assertEquals(1, accounting.getTypeCount(addedId, RunningGameScenario.HUMAN));
            Assertions.assertNotNull(trained);
            Assertions.assertEquals(ADDED_HIT_POINTS, trained.getHitPoints());
            Assertions.assertEquals(ScrUnitIds.TERRAN_MARINE, trained.getCurrentButtonSet());
        }

        List<ReplayRuntime> originalRuntimes = new ArrayList<>();
        List<Long> original = runReplay(readReplay(), LAST_FRAME, originalRuntimes);

        Assertions.assertEquals(first.subList(0, TRAIN_FRAME - 1), original.subList(0, TRAIN_FRAME - 1));
        Assertions.assertNotEquals(first.getLast(), original.getLast());
        Assertions.assertNull(findUnit(originalRuntimes.getFirst().getGame(), RunningGameScenario.HUMAN, addedId));
    }

    @Test
    void added_unit_fights_an_enemy_unit() throws IOException {
        ReplayRuntime runtime = RunningGameScenario.start(requireGameData());
        Game game = runtime.getGame();
        Sprite base = findBaseSprite(game);
        Unit[] units = new Unit[2];

        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            units[0] = createUnit(game, addedId, base.getX(), base.getY() + FIGHT_OFFSET_Y, RunningGameScenario.HUMAN);
            units[1] = createUnit(game, ScrUnitIds.TERRAN_GOLIATH, base.getX() + FIGHT_DISTANCE, base.getY() + FIGHT_OFFSET_Y,
                                  RunningGameScenario.COMPUTER);
        });

        Unit added = units[0];
        Unit enemy = units[1];
        int enemyHitPoints = enemy.getHitPoints();
        int addedHitPoints = added.getHitPoints();

        for (int frame = 0; frame < FIGHT_FRAMES && !enemy.isDestroyed() && !added.isDestroyed(); frame++) {
            Assertions.assertTrue(runtime.step(0, game.getCurrentFrame() * runtime.getFrameDuration()));
        }

        UnitAccounting accounting = game.getPlayerTable().requireUnitAccounting();

        Assertions.assertNull(runtime.getFailure());
        Assertions.assertTrue(enemy.isDestroyed() || enemy.getHitPoints() < enemyHitPoints, "The added unit damages the enemy.");
        Assertions.assertTrue(added.isDestroyed() || added.getHitPoints() < addedHitPoints, "The enemy damages the added unit.");
        Assertions.assertEquals(added.isDestroyed() ? 0 : 1, accounting.getTypeCount(addedId, RunningGameScenario.HUMAN));
    }

    @Test
    void native_only_paths_refuse_or_ignore_the_added_type() throws IOException {
        GameData installedData = requireGameData();
        ReplayRuntime runtime = RunningGameScenario.start(installedData);
        Game game = runtime.getGame();
        GameData data = game.requireGameData();
        PlayerUnitAvailability availability = new PlayerUnitAvailability(game.getEngineGame().getContent().getProgress());
        Sprite base = findBaseSprite(game);

        Assertions.assertNull(ScrNativeIds.unitOf(addedId));
        Assertions.assertThrows(IllegalArgumentException.class, () -> ScrUnits.getNativeId(installedData.getAddedUnitTypes().get(addedId).getType()));
        Assertions.assertThrows(IndexOutOfBoundsException.class, () -> data.getUnits().get(UnitsDat.HIT_POINTS, addedId));
        Assertions.assertEquals(UnitsDat.UNIT_COUNT, data.getUnitTypes().getCount());

        Assertions.assertTrue(availability.isAllowed(RunningGameScenario.HUMAN, addedId));
        Assertions.assertThrows(IllegalArgumentException.class, () -> availability.readByte(RunningGameScenario.HUMAN, addedId));

        Assertions.assertTrue(installedData.getAddedUnitTypes().isFrozen());
        Assertions.assertThrows(IllegalStateException.class,
                                () -> installedData.getAddedUnitTypes().add(new ScrAddedUnitType.Builder(new UnitType("Late"), "Late",
                                                                                                         ScrUnits.TERRAN_MARINE,
                                                                                                         installedData.getUnits()).build()));
        Assertions.assertThrows(IllegalArgumentException.class,
                                () -> new ScrAddedUnitType.Builder(new UnitType("Barracks copy"), "Barracks copy", ScrUnits.TERRAN_BARRACKS,
                                                                   installedData.getUnits()));
        Assertions.assertThrows(IllegalArgumentException.class,
                                () -> new ScrAddedUnitType.Builder(new UnitType("Goliath copy"), "Goliath copy", ScrUnits.TERRAN_GOLIATH,
                                                                   installedData.getUnits()));

        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Unit added = createUnit(game, addedId, base.getX(), base.getY() + FIGHT_OFFSET_Y, RunningGameScenario.HUMAN);
            int deaths = game.getPlayerTable().requireUnitAccounting().getScores().getDeaths(RunningGameScenario.HUMAN, addedId);

            Assertions.assertEquals(0, deaths);
            Assertions.assertFalse(added.isDestroyed());
        });
    }

    @Test
    void computer_player_plays_against_and_with_added_units_with_equal_hashes() throws IOException {
        List<Long> first = playComputerGame();
        List<Long> second = playComputerGame();

        Assertions.assertEquals(WAVE_COUNT * WAVE_FRAMES, first.size());
        Assertions.assertEquals(first, second);
    }
}
