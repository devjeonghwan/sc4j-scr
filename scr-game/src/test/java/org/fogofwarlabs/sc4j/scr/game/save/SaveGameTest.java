package org.fogofwarlabs.sc4j.scr.game.save;

import org.fogofwarlabs.sc4j.scr.content.ScrRaceIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerRelations;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.save.pool.*;
import org.fogofwarlabs.sc4j.scr.game.save.section.*;
import org.fogofwarlabs.sc4j.scr.game.session.LiveGame;
import org.fogofwarlabs.sc4j.scr.game.session.setup.MeleeGameSetup;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.GameStructureView;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitCreationContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Checks the local relation table that a saved game holds after it is read into a started replay or live game, and compares the saved games that SC4J
 * writes and reads with the native saves made at the same frame of the replayed game. The table is read and written at its offset in the saved game
 * structure, whose layout is part of the save file format.
 */
@RequiresInstallation
final class SaveGameTest {
    private static final int SAVE_FRAME = 500;
    /**
     * The game context offset of the local relation table.
     */
    private static final int LOCAL_RELATIONS = 0x2C7A;
    private static final int WRITTEN_PLAYER = 3;
    /**
     * A value that the rebuilt table never holds.
     */
    private static final int WRITTEN_RELATION = 7;
    /**
     * The selection circle colors of the local player's own units, of allied units and of enemy units.
     */
    private static final int OWN_COLOR = 0;
    private static final int ALLIED_COLOR = 1;
    private static final int ENEMY_COLOR = 2;
    /**
     * The alliance value of an enemy.
     */
    private static final int ENEMY_ALLIANCE = 0;

    private static final int LIVE_LOBBY_SEED = 0x6AB9B92E;
    private static final int HUMAN_LOBBY_SLOT = 0;

    /**
     * The random state DWORD of the game information part.
     */
    private static final int RANDOM_STATE_OFFSET = 4;

    private static final int GAME_STRUCTURE_SIZE = 0x17700;
    /**
     * The minerals, gas and gathered resources of the twelve players at the start of the game structure.
     */
    private static final int RESOURCES_SIZE = 0xC0;
    private static final int FORCES_OFFSET = 0xE4C0;
    private static final int FORCES_SIZE = 12;

    /**
     * The independent native unit-entry DWORD slot prefix, before classic field offsets.
     */
    private static final int UNIT_SLOT_SIZE = Integer.BYTES;
    private static final int UNIT_SPRITE = 0x0C;
    private static final int UNIT_EVENT_TIMER = 0x8A;
    private static final int UNIT_EVENT_COLOR = 0x8B;

    private static final int GUARD_RECORDS = 1000;
    private static final int GUARD_SIZE = 0x20;
    private static final int GUARD_KIND = 0x08;
    private static final int GUARD_RETRIES = 0x09;

    /**
     * The pathing part: the regions, then the contours, then the path records at its end.
     */
    private static final int PATHING_REGIONS_SIZE = 0x97A28;
    private static final int PATHS_SIZE = 0x20000;
    private static final int PATH_RECORD_SIZE = 0x80;

    private static final int SETTINGS_COUNT = 19;

    private static final @NotNull Set<String> TRIGGERS_AND_SETTINGS = Set.of("forces", "triggers", "settings");
    private static final @NotNull Set<String> ALL_PARTS = Set.of("random", "resources", "forces", "units", "bullets", "orders", "triggers",
                                                                 "pathing", "computer", "settings");

    /**
     * Creates the independently recorded unit-entry field labels.
     *
     * @return the unit-entry field labels by offset
     */
    private static @NotNull Map<Integer, String> createUnitFieldLabels() {
        return SaveGameComparisonCheck.createFieldLabels(0x04, "prev",
                                                         0x08, "next",
                                                         0x0C, "hp",
                                                         0x10, "sprite",
                                                         0x14, "moveDestination",
                                                         0x18, "moveTarget",
                                                         0x1C, "waypoint",
                                                         0x20, "directionTarget",
                                                         0x24, "movementFlags",
                                                         0x25, "heading",
                                                         0x26, "turnRate",
                                                         0x27, "targetHeading",
                                                         0x28, "flingy",
                                                         0x2A, "pad26",
                                                         0x2B, "control",
                                                         0x2C, "position",
                                                         0x30, "exactX",
                                                         0x34, "exactY",
                                                         0x38, "topSpeed",
                                                         0x3C, "speed",
                                                         0x40, "nextSpeed",
                                                         0x44, "velocityX",
                                                         0x48, "velocityY",
                                                         0x4C, "acceleration",
                                                         0x4E, "direction",
                                                         0x4F, "desiredDirection",
                                                         0x50, "owner",
                                                         0x51, "order",
                                                         0x52, "orderState",
                                                         0x53, "signal",
                                                         0x54, "targetType",
                                                         0x56, "word52",
                                                         0x58, "cooldowns",
                                                         0x5C, "orderTarget",
                                                         0x60, "orderTargetUnit",
                                                         0x64, "shields",
                                                         0x68, "type",
                                                         0x6A, "spare",
                                                         0x6C, "prevOwner",
                                                         0x70, "nextOwner",
                                                         0x74, "subunit",
                                                         0x78, "firstOrder",
                                                         0x7C, "lastOrder",
                                                         0x80, "autoTarget",
                                                         0x84, "connected",
                                                         0x88, "highlighted",
                                                         0x89, "orderTimer",
                                                         0x8A, "unused86",
                                                         0x8B, "alert",
                                                         0x8C, "previousType",
                                                         0x8E, "event8A",
                                                         0x8F, "event8B",
                                                         0x90, "eventBytes",
                                                         0x93, "kills",
                                                         0x94, "lastAttacker",
                                                         0x95, "visibilityTimer",
                                                         0x96, "spellTargets",
                                                         0x97, "userActions",
                                                         0x98, "buttonSet",
                                                         0x9A, "cloakCounter",
                                                         0x9B, "movementState",
                                                         0x9C, "buildQueue",
                                                         0xA6, "energy",
                                                         0xA8, "queueIndex",
                                                         0xA9, "generation",
                                                         0xAA, "secondaryOrder",
                                                         0xAB, "overlays",
                                                         0xAC, "hpGain",
                                                         0xAE, "shieldGain",
                                                         0xB0, "buildTime",
                                                         0xB2, "previousHp",
                                                         0xB4, "loaded",
                                                         0xC4, "union",
                                                         0xE0, "status",
                                                         0xE4, "carrying",
                                                         0xE5, "wireframe",
                                                         0xE6, "secondaryState",
                                                         0xE7, "destinationTimer",
                                                         0xE8, "detection",
                                                         0xEC, "secondaryPosition",
                                                         0xF0, "buildUnit",
                                                         0xF4, "cloakLinks",
                                                         0xFC, "kindLinks",
                                                         0x104, "path",
                                                         0x108, "pathTimer",
                                                         0x109, "pathFlags",
                                                         0x10A, "pad106",
                                                         0x10B, "healing",
                                                         0x10C, "contour",
                                                         0x114, "lifetime",
                                                         0x116, "matrixHp",
                                                         0x118, "timers",
                                                         0x120, "irradiatedBy",
                                                         0x124, "irradiateOwner",
                                                         0x125, "parasite",
                                                         0x126, "cycle",
                                                         0x127, "blind",
                                                         0x128, "maelstrom",
                                                         0x129, "pad125",
                                                         0x12A, "acid",
                                                         0x134, "nearIndex",
                                                         0x136, "loadedExtension",
                                                         0x138, "computer",
                                                         0x13C, "airStrength",
                                                         0x13E, "groundStrength",
                                                         0x140, "finderEdges",
                                                         0x150, "repulsion");
    }

    /**
     * Records the part name when the two nullable byte arrays differ.
     *
     * @param name      the part name
     * @param ours      the simulated serialized bytes
     * @param theirs    the reference serialized bytes
     * @param differing the differing part names
     */
    private static void comparePart(@NotNull String name, byte @Nullable [] ours, byte @Nullable [] theirs, @NotNull List<String> differing) {
        if (!Arrays.equals(ours, theirs)) {
            differing.add(name);
        }
    }

    /**
     * Copies the selected byte range using the requested extent.
     *
     * @param data   the byte contents
     * @param offset the first byte offset
     * @param length the copied byte count
     * @return the copied byte range
     */
    private static byte @NotNull [] copySlice(byte @NotNull [] data, int offset, int length) {
        return Arrays.copyOfRange(data, offset, offset + length);
    }

    /**
     * Copies unit entries with the sprite and the interface event bytes of every entry cleared.
     *
     * @param units the saved unit entries
     * @return the copied unit entries
     */
    private static byte @NotNull [] copyWithoutLocalUnitFields(byte @NotNull [] units) {
        byte[] copy = units.clone();

        for (int entry = 0; entry + SaveGameLayoutCheck.UNIT_ENTRY_SIZE <= copy.length; entry += SaveGameLayoutCheck.UNIT_ENTRY_SIZE) {
            Arrays.fill(copy, entry + UNIT_SLOT_SIZE + UNIT_SPRITE, entry + UNIT_SLOT_SIZE + UNIT_SPRITE + Integer.BYTES, (byte) 0);
            copy[entry + UNIT_SLOT_SIZE + UNIT_EVENT_TIMER] = 0;
            copy[entry + UNIT_SLOT_SIZE + UNIT_EVENT_COLOR] = 0;
        }

        return copy;
    }

    /**
     * Copies guard records with the retry BYTE of the records on the free list (kind {@code 0}) cleared, which no game of the file wrote.
     *
     * @param guards the saved guard records
     * @return the copied guard records
     */
    private static byte @NotNull [] copyWithoutFreeGuardRetries(byte @NotNull [] guards) {
        byte[] copy = guards.clone();

        for (int record = 0; record < GUARD_RECORDS; record++) {
            if (copy[record * GUARD_SIZE + GUARD_KIND] == 0) {
                copy[record * GUARD_SIZE + GUARD_RETRIES] = 0;
            }
        }

        return copy;
    }


    @Test
    void read_should_rebuild_the_local_relation_table_when_the_saved_bytes_differ() throws Exception {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayFile replay = ReplayFile.parse(GameTestResources.readAllBytes(GameTestResources.REPLAY_ZVZ_HEARTBREAK_RIDGE));
            ReplayRuntime saved = SaveGameRoundTripScenario.startReplay(gameData, replay);

            SaveGameRoundTripScenario.advanceToFrame(saved, SAVE_FRAME);

            Game savedGame = saved.getGame();

            new GameStructureView(savedGame, savedGame.getTriggerContext()).writeContextByte(LOCAL_RELATIONS + WRITTEN_PLAYER, WRITTEN_RELATION);

            SaveGameDescription description = SaveGameRoundTripScenario.createDescription(false, "local relations");
            SaveGameFile file = SaveGameFile.parse(SaveGameUtils.write(new SaveGameContext(saved, null), description).toByteArray());
            ReplayRuntime loaded = SaveGameRoundTripScenario.startSavedGame(gameData, file, replay, SAVE_FRAME);

            SaveGameUtils.read(file, new SaveGameContext(loaded, null));

            Game game = loaded.getGame();
            GameStructureView view = new GameStructureView(game, game.getTriggerContext());

            for (int player = 0; player < PlayerTable.PLAYER_SLOTS; player++) {
                int expected = player == PlayerTable.NEUTRAL_PLAYER_ID ? ALLIED_COLOR : OWN_COLOR;

                Assertions.assertEquals(expected, view.readContextByte(LOCAL_RELATIONS + player), "A loaded replay colors player " + player + ".");
            }
        }
    }

    @Test
    void read_should_rebuild_live_local_relations_from_saved_outgoing_alliances() throws Exception {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayFile fixture = ReplayFile.parse(GameTestResources.readAllBytes(GameTestResources.REPLAY_TERRAN_VS_COMPUTER));
            byte[] mapData = fixture.getMapData();

            Assertions.assertNotNull(mapData);

            for (int alliance : new int[]{PlayerRelations.ALLIED, PlayerRelations.ALLIED_VICTORY}) {
                MeleeGameSetup setup = new MeleeGameSetup(mapData,
                                                          "live local relations", ScrRaceIds.TERRAN, HUMAN_LOBBY_SLOT,
                                                          new int[]{ScrRaceIds.TERRAN},
                                                          GameSpeed.FASTEST);
                LiveGame savedLive = new LiveGame(setup, LIVE_LOBBY_SEED);
                ReplayRuntime saved = ReplayRuntime.createLiveGame(savedLive.getReplay(), ChkFile.parseTolerant(setup.getMapData()),
                                                                   gameData, savedLive.getLocalPlayer(),
                                                                   RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                                   RunningGameScenario.INSTRUCTION_LIMIT);

                savedLive.startSession(saved.getGame(), saved.getFrameTiming());
                saved.start(0, 0);

                Assertions.assertNull(saved.getFailure(), "The live game must start before it is saved.");

                Game savedGame = saved.getGame();
                int localPlayer = savedLive.getLocalPlayer();
                UnitCreationContext savedCreation = savedGame.getUnitSystem().getUnitCreationContext();
                PlayerRelations savedRelations = savedGame.getPlayerTable().getPlayerRelations();
                int computerPlayer = -1;

                Assertions.assertFalse(savedGame.isReplayPlayback());
                Assertions.assertFalse(savedGame.getLocalVision().isObserver());
                Assertions.assertNotNull(savedCreation);
                Assertions.assertNotNull(savedRelations);
                Assertions.assertEquals(localPlayer, savedCreation.getLocalPlayer());

                for (int player = 0; player < PlayerTable.PLAYABLE_PLAYER_SLOTS; player++) {
                    Player opponent = savedGame.getPlayerTable().getPlayer(player);

                    if (opponent != null && opponent.getType() == PlayerTypes.COMPUTER) {
                        computerPlayer = player;

                        break;
                    }
                }

                Assertions.assertTrue(computerPlayer >= 0 && computerPlayer != localPlayer, "The melee game must have a computer opponent.");

                int[] alliances = new int[PlayerTable.PLAYER_SLOTS];

                alliances[localPlayer]                   = PlayerRelations.ALLIED;
                alliances[computerPlayer]                = alliance;
                alliances[PlayerTable.NEUTRAL_PLAYER_ID] = PlayerRelations.ALLIED;

                savedRelations.setAlliances(localPlayer, alliances);
                savedRelations.setAlliance(computerPlayer, localPlayer, PlayerRelations.ENEMY);

                byte[] expectedAlliances = savedRelations.getAllianceBytes();
                GameStructureView savedView = new GameStructureView(savedGame, savedGame.getTriggerContext());

                for (int player = 0; player < PlayerTable.PLAYER_SLOTS; player++) {
                    savedView.writeContextByte(LOCAL_RELATIONS + player, WRITTEN_RELATION);
                }

                SaveGameDescription description = SaveGameRoundTripScenario.createDescription(false, "live local relations");
                SaveGameFile file = SaveGameFile.parse(SaveGameUtils.write(new SaveGameContext(saved, savedLive), description).toByteArray());
                byte[] savedStructure = file.getGameStructure();

                Assertions.assertEquals(savedLive.getLocalSlot(), file.getRecordSlot());

                for (int player = 0; player < PlayerTable.PLAYER_SLOTS; player++) {
                    Assertions.assertEquals(WRITTEN_RELATION, Byte.toUnsignedInt(savedStructure[LOCAL_RELATIONS + player]),
                                            "The saved structure holds the written relation of player " + player + ".");
                }

                LiveGame loadedLive = SaveGameSession.read(file).createLiveGame();
                ReplayRuntime loaded = ReplayRuntime.createLiveGame(loadedLive.getReplay(), ChkFile.parseTolerant(loadedLive.getSetup().getMapData()),
                                                                    gameData, loadedLive.getLocalPlayer(),
                                                                    RunningGameScenario.VIEWPORT_WIDTH, RunningGameScenario.VIEWPORT_HEIGHT,
                                                                    RunningGameScenario.INSTRUCTION_LIMIT);

                loadedLive.startSession(loaded.getGame(), loaded.getFrameTiming());
                loaded.start(0, 0);

                Assertions.assertNull(loaded.getFailure(), "The saved live game must start before its parts are read.");
                Assertions.assertEquals(localPlayer, loadedLive.getLocalPlayer());
                Assertions.assertEquals(savedLive.getLocalSlot(), loadedLive.getLocalSlot());

                Game loadedGame = loaded.getGame();

                Assertions.assertFalse(loadedGame.isReplayPlayback());
                Assertions.assertFalse(loadedGame.getLocalVision().isObserver());

                SaveGameUtils.read(file, new SaveGameContext(loaded, loadedLive));

                PlayerRelations loadedRelations = loadedGame.getPlayerTable().getPlayerRelations();

                Assertions.assertNotNull(loadedRelations);
                Assertions.assertArrayEquals(expectedAlliances, loadedRelations.getAllianceBytes());
                Assertions.assertEquals(alliance, loadedRelations.getAlliance(localPlayer, computerPlayer));
                Assertions.assertEquals(ENEMY_ALLIANCE, loadedRelations.getAlliance(computerPlayer, localPlayer));

                for (int player = 0; player < PlayerTable.PLAYER_SLOTS; player++) {
                    int expected = player == localPlayer
                                   ? OWN_COLOR
                                   : player == computerPlayer || player == PlayerTable.NEUTRAL_PLAYER_ID ? ALLIED_COLOR : ENEMY_COLOR;

                    Assertions.assertEquals(expected, loadedGame.getSelectionImages().getCircleColor(player),
                                            "A loaded live game colors player " + player + " for alliance " + alliance + ".");
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {GameTestResources.SAVE_SINGLE_PLAYER_WITH_COMPUTER, GameTestResources.SAVE_LEVEL_COMPUTER,
                            GameTestResources.SAVE_MULTIPLAYER_WITH_COMPUTER, GameTestResources.SAVE_TOM_AND_JERRY})
    void write_reports_differences_from_native_save_parts(@NotNull String saveName) throws Exception {
        SaveGameRoundTripScenario.assumeSelected(saveName);

        Path save = GameTestResources.getPath(saveName);
        Path replayPath = GameTestResources.getPath(GameTestResources.getSaveReplay(saveName));

        SaveGameLayoutCheck layout = SaveGameLayoutCheck.parse(Files.readAllBytes(save));
        byte[] recordedGame = layout.requirePart("game");
        int frame = SaveGameLayoutCheck.readGameFrame(recordedGame);
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(replayPath));

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = SaveGameRoundTripScenario.startReplay(gameData, replay);

            SaveGameRoundTripScenario.advanceToFrame(runtime, frame);

            SaveGameContext context = new SaveGameContext(runtime, null);
            StringBuilder report = new StringBuilder("NATIVE SAVE " + saveName + " @" + frame);
            SaveFileReader structure = SaveGameComparisonCheck.createSectionReader(new GameStructureSection(), context);
            byte[] simulatedGame = structure.readSection(GAME_STRUCTURE_SIZE);

            SaveGameComparisonCheck.compareRuns("game", simulatedGame, recordedGame, report);

            context.getGame().getPsi().releaseProviders();

            ImagePoolSection images = new ImagePoolSection();
            SaveFileReader ours = SaveGameComparisonCheck.createSectionReader(images, context);
            byte[] simulatedImages = SaveGameComparisonCheck.readCountedRecords(ours, SaveGameLayoutCheck.IMAGE_ENTRY_SIZE);
            byte[] recordedImages = layout.requirePart("images");

            SaveGameComparisonCheck.compareRecords("images",
                                                   simulatedImages, recordedImages,
                                                   SaveGameLayoutCheck.IMAGE_ENTRY_SIZE, Short.BYTES,
                                                   SaveGameComparisonCheck.createFieldLabels(0x02, "prev",
                                                                                             0x06, "next",
                                                                                             0x0A, "id",
                                                                                             0x0C, "modifier",
                                                                                             0x0D, "frameOffset",
                                                                                             0x0E, "flags",
                                                                                             0x10, "offsetX",
                                                                                             0x11, "offsetY",
                                                                                             0x12, "header",
                                                                                             0x14, "pc",
                                                                                             0x16, "return",
                                                                                             0x18, "animation",
                                                                                             0x19, "wait",
                                                                                             0x1A, "frameBase",
                                                                                             0x1C, "frame",
                                                                                             0x1E, "positions",
                                                                                             0x2E, "grp",
                                                                                             0x32, "coloring",
                                                                                             0x36, "data",
                                                                                             0x3E, "sprite"),
                                                   report);

            ours = SaveGameComparisonCheck.createSectionReader(new SpritePoolSection(images), context);

            byte[] simulatedSprites = SaveGameComparisonCheck.readCountedRecords(ours, SaveGameLayoutCheck.SPRITE_ENTRY_SIZE);
            byte[] recordedSprites = layout.requirePart("sprites");

            SaveGameComparisonCheck.compareRecords("sprites",
                                                   simulatedSprites, recordedSprites,
                                                   SaveGameLayoutCheck.SPRITE_ENTRY_SIZE, Short.BYTES,
                                                   SaveGameComparisonCheck.createFieldLabels(0x02, "prev",
                                                                                             0x06, "next",
                                                                                             0x0A, "id",
                                                                                             0x0C, "owner",
                                                                                             0x0D, "selection",
                                                                                             0x0E, "visibility",
                                                                                             0x0F, "elevation",
                                                                                             0x10, "flags",
                                                                                             0x11, "timer",
                                                                                             0x12, "word",
                                                                                             0x14, "width",
                                                                                             0x15, "height",
                                                                                             0x16, "x",
                                                                                             0x18, "y",
                                                                                             0x1A, "main",
                                                                                             0x1E, "head",
                                                                                             0x22, "tail"),
                                                   report);

            byte[] simulatedSpriteFirstRows = ours.readBytes(SaveGameLayoutCheck.ROW_ARRAY_SIZE);
            byte[] recordedSpriteFirstRows = layout.requirePart("spriteFirstRows");

            SaveGameComparisonCheck.compareBytes("spriteFirstRows", simulatedSpriteFirstRows, recordedSpriteFirstRows, report);

            byte[] simulatedSpriteLastRows = ours.readBytes(SaveGameLayoutCheck.ROW_ARRAY_SIZE);
            byte[] recordedSpriteLastRows = layout.requirePart("spriteLastRows");

            SaveGameComparisonCheck.compareBytes("spriteLastRows", simulatedSpriteLastRows, recordedSpriteLastRows, report);

            ours = SaveGameComparisonCheck.createSectionReader(new ThingyPoolSection(), context);

            Map<Integer, String> thingyFields = SaveGameComparisonCheck.createFieldLabels(0x02, "prev", 0x06, "next", 0x0A, "life", 0x0E, "sprite");
            byte[] simulatedThingies = SaveGameComparisonCheck.readCountedRecords(ours, SaveGameLayoutCheck.THINGY_ENTRY_SIZE);
            byte[] recordedThingies = layout.requirePart("thingies");

            SaveGameComparisonCheck.compareRecords("thingies",
                                                   simulatedThingies, recordedThingies,
                                                   SaveGameLayoutCheck.THINGY_ENTRY_SIZE, Short.BYTES,
                                                   thingyFields,
                                                   report);

            byte[] simulatedSecondaryThingies = SaveGameComparisonCheck.readCountedRecords(ours, SaveGameLayoutCheck.THINGY_ENTRY_SIZE);
            byte[] recordedSecondaryThingies = layout.requirePart("secondaryThingies");

            SaveGameComparisonCheck.compareRecords("secondaryThingies",
                                                   simulatedSecondaryThingies, recordedSecondaryThingies,
                                                   SaveGameLayoutCheck.THINGY_ENTRY_SIZE, Short.BYTES,
                                                   thingyFields,
                                                   report);

            ours = SaveGameComparisonCheck.createSectionReader(new UnitPoolSection(), context);

            byte[] simulatedUnitFlags = ours.readSection(SaveGameLayoutCheck.UNIT_CAPACITY);
            byte[] recordedUnitFlags = layout.requirePart("unitFlags");

            SaveGameComparisonCheck.compareBytes("unitFlags", simulatedUnitFlags, recordedUnitFlags, report);

            int unitCount = ours.readDword();
            byte[] simulatedUnits = unitCount == 0 ? new byte[0] : ours.readSection(unitCount * SaveGameLayoutCheck.UNIT_ENTRY_SIZE);
            byte[] recordedUnits = layout.requirePart("units");

            SaveGameComparisonCheck.compareRecords("units",
                                                   simulatedUnits, recordedUnits,
                                                   SaveGameLayoutCheck.UNIT_ENTRY_SIZE, Integer.BYTES,
                                                   createUnitFieldLabels(),
                                                   report);

            byte[] simulatedUnitHeads = ours.readBytes(SaveGameLayoutCheck.UNIT_LIST_HEAD_COUNT * Integer.BYTES);
            byte[] recordedUnitHeads = layout.requirePart("unitHeads");

            SaveGameComparisonCheck.compareBytes("unitHeads", simulatedUnitHeads, recordedUnitHeads, report);

            ours = SaveGameComparisonCheck.createSectionReader(new BulletPoolSection(), context);

            byte[] simulatedBullets = SaveGameComparisonCheck.readCountedRecords(ours, SaveGameLayoutCheck.BULLET_ENTRY_SIZE);
            byte[] recordedBullets = layout.requirePart("bullets");

            SaveGameComparisonCheck.compareRecords("bullets",
                                                   simulatedBullets, recordedBullets,
                                                   SaveGameLayoutCheck.BULLET_ENTRY_SIZE, Short.BYTES,
                                                   SaveGameComparisonCheck.createFieldLabels(0x02, "prev",
                                                                                             0x06, "next",
                                                                                             0x0A, "hp",
                                                                                             0x0E, "sprite",
                                                                                             0x12, "moveTarget",
                                                                                             0x16, "moveTargetUnit",
                                                                                             0x1A, "waypoint",
                                                                                             0x1E, "targetWaypoint",
                                                                                             0x22, "movementFlags",
                                                                                             0x23, "heading",
                                                                                             0x24, "turnRate",
                                                                                             0x25, "nextDirection",
                                                                                             0x26, "flingy",
                                                                                             0x28, "pad",
                                                                                             0x29, "control",
                                                                                             0x2A, "position",
                                                                                             0x2E, "exact",
                                                                                             0x36, "topSpeed",
                                                                                             0x3A, "speed",
                                                                                             0x3E, "nextSpeed",
                                                                                             0x42, "velocity",
                                                                                             0x4A, "acceleration",
                                                                                             0x4C, "currentDirection",
                                                                                             0x4D, "desiredDirection",
                                                                                             0x4E, "owner",
                                                                                             0x4F, "state",
                                                                                             0x50, "behavior",
                                                                                             0x51, "signal",
                                                                                             0x52, "word50",
                                                                                             0x54, "word52",
                                                                                             0x56, "dword54",
                                                                                             0x5A, "target",
                                                                                             0x5E, "targetUnit",
                                                                                             0x62, "weapon",
                                                                                             0x63, "time",
                                                                                             0x64, "hitFlags",
                                                                                             0x65, "bounces",
                                                                                             0x66, "ownerUnit",
                                                                                             0x6A, "bounceTarget",
                                                                                             0x6E, "nearIndex",
                                                                                             0x6F, "byte6d",
                                                                                             0x70, "word6e"),
                                                   report);

            ours = SaveGameComparisonCheck.createSectionReader(new OrderPoolSection(), context);

            byte[] simulatedOrderRecords = ours.readSection(SaveGameLayoutCheck.ORDER_CAPACITY * SaveGameLayoutCheck.ORDER_ENTRY_SIZE);
            byte[] simulatedOrders = SaveGameComparisonCheck.addRecordIndexes(simulatedOrderRecords, SaveGameLayoutCheck.ORDER_ENTRY_SIZE);
            byte[] recordedOrders = SaveGameComparisonCheck.addRecordIndexes(layout.requirePart("orders"), SaveGameLayoutCheck.ORDER_ENTRY_SIZE);

            SaveGameComparisonCheck.compareRecords("orders",
                                                   simulatedOrders, recordedOrders,
                                                   SaveGameLayoutCheck.ORDER_ENTRY_SIZE + Short.BYTES, Short.BYTES,
                                                   SaveGameComparisonCheck.createFieldLabels(0x02, "prev",
                                                                                             0x06, "next",
                                                                                             0x0A, "order",
                                                                                             0x0B, "byte11",
                                                                                             0x0C, "type",
                                                                                             0x0E, "position",
                                                                                             0x12, "target"),
                                                   report);

            byte[] simulatedOrderHeads = ours.readBytes(SaveGameLayoutCheck.ORDER_LIST_HEAD_COUNT * Integer.BYTES);
            byte[] recordedOrderHeads = layout.requirePart("orderHeads");

            SaveGameComparisonCheck.compareBytes("orderHeads", simulatedOrderHeads, recordedOrderHeads, report);

            context.getGame().getPsi().restoreProviders(runtime.getInstructionLimit());

            ours = SaveGameComparisonCheck.createSectionReader(new TerrainOverlaySection(), context);
            ours.readDword();

            byte[] recordedTerrainWords = layout.requirePart("terrainWords");
            byte[] simulatedTerrainWords = ours.readSection(recordedTerrainWords.length);

            SaveGameComparisonCheck.compareRuns("terrainWords", simulatedTerrainWords, recordedTerrainWords, report);

            ours.readDword();

            byte[] recordedTerrainBytes = layout.requirePart("terrainBytes");
            byte[] simulatedTerrainBytes = ours.readSection(recordedTerrainBytes.length);

            SaveGameComparisonCheck.compareRuns("terrainBytes", simulatedTerrainBytes, recordedTerrainBytes, report);

            ours = SaveGameComparisonCheck.createSectionReader(new CreepRecessionSection(), context);

            byte[] simulatedCreepRecords = ours.readSection(SaveGameLayoutCheck.CREEP_RECORDS_SIZE);
            byte[] recordedCreepRecords = layout.requirePart("creepRecords");

            SaveGameComparisonCheck.compareRuns("creepRecords", simulatedCreepRecords, recordedCreepRecords, report);

            byte[] simulatedCreepHeads = ours.readSection(SaveGameLayoutCheck.CREEP_HEADS_SIZE);
            byte[] recordedCreepHeads = layout.requirePart("creepHeads");

            SaveGameComparisonCheck.compareRuns("creepHeads", simulatedCreepHeads, recordedCreepHeads, report);

            byte[] simulatedCreepLists = ours.readSection(SaveGameLayoutCheck.CREEP_LISTS_SIZE);
            byte[] recordedCreepLists = layout.requirePart("creepLists");

            SaveGameComparisonCheck.compareRuns("creepLists", simulatedCreepLists, recordedCreepLists, report);

            byte[] simulatedCreepTail = ours.readSection(SaveGameLayoutCheck.CREEP_TAIL_SIZE);
            byte[] recordedCreepTail = layout.requirePart("creepTail");

            SaveGameComparisonCheck.compareRuns("creepTail", simulatedCreepTail, recordedCreepTail, report);

            ours = SaveGameComparisonCheck.createSectionReader(new TriggerSection(), context);

            int triggerCount = ours.readWord();

            report.append(System.lineSeparator()).append("  triggerCount: native ");

            byte[] recordedTriggerCount = layout.requirePart("triggerCount");

            report.append(ByteBuffer.wrap(recordedTriggerCount).order(ByteOrder.LITTLE_ENDIAN).getInt()).append(" ours ").append(triggerCount);

            byte[] simulatedTriggers = ours.readSection(SaveGameLayoutCheck.TRIGGER_HEAD_SIZE +
                                                        triggerCount * SaveGameLayoutCheck.TRIGGER_ENTRY_SIZE);
            byte[] recordedTriggers = layout.requirePart("triggers");

            SaveGameComparisonCheck.compareRuns("triggers", simulatedTriggers, recordedTriggers, report);

            byte[] simulatedTriggerTail = ours.readSection(SaveGameLayoutCheck.TRIGGER_TAIL_SIZE);
            byte[] recordedTriggerTail = layout.requirePart("triggerTail");

            SaveGameComparisonCheck.compareRuns("triggerTail", simulatedTriggerTail, recordedTriggerTail, report);

            ours = SaveGameComparisonCheck.createSectionReader(new MapArraysSection(), context);

            byte[] simulatedMapTiles = ours.readSection(SaveGameLayoutCheck.MAP_TILES_SIZE);
            byte[] recordedMapTiles = layout.requirePart("mapTiles");

            SaveGameComparisonCheck.compareRuns("mapTiles", simulatedMapTiles, recordedMapTiles, report);

            byte[] simulatedMapArray = ours.readSection(SaveGameLayoutCheck.MAP_ARRAY_SIZE);
            byte[] recordedMapArray = layout.requirePart("mapArray");

            SaveGameComparisonCheck.compareRuns("mapArray", simulatedMapArray, recordedMapArray, report);

            byte[] simulatedMapFlags = ours.readSection(SaveGameLayoutCheck.MAP_FLAGS_SIZE);
            byte[] recordedMapFlags = layout.requirePart("mapFlags");

            SaveGameComparisonCheck.compareRuns("mapFlags", simulatedMapFlags, recordedMapFlags, report);

            ours = SaveGameComparisonCheck.createSectionReader(new SelectionSection(), context);

            byte[] simulatedSelections = ours.readSection(SaveGameLayoutCheck.SELECTIONS_SIZE);
            byte[] recordedSelections = layout.requirePart("selections");

            SaveGameComparisonCheck.compareRuns("selections", simulatedSelections, recordedSelections, report);

            ours = SaveGameComparisonCheck.createSectionReader(new PathingSection(), context);

            byte[] pathing = ours.readSection(ours.readDword());
            byte[] nativePathing = layout.requirePart("pathing");
            int pathsStart = pathing.length - PATHS_SIZE;
            int nativePathsStart = nativePathing.length - PATHS_SIZE;

            SaveGameComparisonCheck.compareRuns("pathingRegions",
                                                Arrays.copyOf(pathing, PATHING_REGIONS_SIZE),
                                                Arrays.copyOf(nativePathing, PATHING_REGIONS_SIZE),
                                                report);
            SaveGameComparisonCheck.compareRuns("pathingContours",
                                                Arrays.copyOfRange(pathing, PATHING_REGIONS_SIZE, pathsStart),
                                                Arrays.copyOfRange(nativePathing, PATHING_REGIONS_SIZE, nativePathsStart),
                                                report);

            byte[] simulatedPaths = Arrays.copyOfRange(pathing, pathsStart, pathing.length);
            byte[] recordedPaths = Arrays.copyOfRange(nativePathing, nativePathsStart, nativePathing.length);

            SaveGameComparisonCheck.compareRecords("paths",
                                                   SaveGameComparisonCheck.addRecordIndexes(simulatedPaths, PATH_RECORD_SIZE),
                                                   SaveGameComparisonCheck.addRecordIndexes(recordedPaths, PATH_RECORD_SIZE),
                                                   PATH_RECORD_SIZE + Short.BYTES, Short.BYTES,
                                                   SaveGameComparisonCheck.createFieldLabels(0x02, "source",
                                                                                             0x06, "waypoint",
                                                                                             0x0A, "destination",
                                                                                             0x0E, "frame",
                                                                                             0x12, "speed",
                                                                                             0x16, "collision",
                                                                                             0x1A, "flags",
                                                                                             0x1B, "timer",
                                                                                             0x1C, "slide",
                                                                                             0x1D, "fullRegions",
                                                                                             0x1E, "regions",
                                                                                             0x1F, "regionIndex",
                                                                                             0x20, "waypoints",
                                                                                             0x21, "waypointIndex",
                                                                                             0x22, "cells"),
                                                   report);

            ours = SaveGameComparisonCheck.createSectionReader(new MapSettingsSection(), context);

            for (int index = 0; index < SETTINGS_COUNT; index++) {
                byte[] recordedPart = layout.requirePart("settings" + index);
                byte[] simulatedPart = ours.readSection(recordedPart.length);

                SaveGameComparisonCheck.compareRuns("settings" + index, simulatedPart, recordedPart, report);
            }

            ours = SaveGameComparisonCheck.createSectionReader(new FoliageSection(), context);
            ours.readBytes(SaveGameLayoutCheck.FOLIAGE_HEADER_FIELD_COUNT * Integer.BYTES);

            byte[] simulatedFoliage = ours.readStream(SaveGameLayoutCheck.FOLIAGE_SIZE);
            byte[] recordedFoliage = layout.requirePart("foliage");

            SaveGameComparisonCheck.compareRuns("foliage", simulatedFoliage, recordedFoliage, report);

            System.out.println(report);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {GameTestResources.SAVE_SINGLE_PLAYER_WITH_COMPUTER, GameTestResources.SAVE_LEVEL_COMPUTER,
                            GameTestResources.SAVE_MULTIPLAYER_WITH_COMPUTER, GameTestResources.SAVE_TOM_AND_JERRY})
    void write_should_match_selected_native_save_parts(@NotNull String saveName) throws Exception {
        SaveGameRoundTripScenario.assumeSelected(saveName);

        Path save = GameTestResources.getPath(saveName);
        Path replayPath = GameTestResources.getPath(GameTestResources.getSaveReplay(saveName));

        byte[] nativeFile = Files.readAllBytes(save);
        SaveGameLayoutCheck theirs = SaveGameLayoutCheck.parse(nativeFile);
        int frame = SaveGameLayoutCheck.readGameFrame(theirs.requirePart("game"));
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(replayPath));
        Set<String> asserted = saveName.equals(GameTestResources.SAVE_LEVEL_COMPUTER) ? TRIGGERS_AND_SETTINGS : ALL_PARTS;
        List<String> differing = new ArrayList<>();

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = SaveGameRoundTripScenario.startReplay(gameData, replay);

            SaveGameRoundTripScenario.advanceToFrame(runtime, frame);

            byte[] simulated = SaveGameUtils.write(new SaveGameContext(runtime, null), SaveGameFile.parseDescription(nativeFile)).toByteArray();
            SaveGameLayoutCheck ours = SaveGameLayoutCheck.parse(simulated);

            if (asserted.contains("random")) {
                byte[] simulatedRandom = copySlice(ours.requirePart("information"), RANDOM_STATE_OFFSET, Integer.BYTES);
                byte[] recordedRandom = copySlice(theirs.requirePart("information"), RANDOM_STATE_OFFSET, Integer.BYTES);

                comparePart("random", simulatedRandom, recordedRandom, differing);
            }

            if (asserted.contains("resources")) {
                byte[] simulatedResources = copySlice(ours.requirePart("game"), 0, RESOURCES_SIZE);
                byte[] recordedResources = copySlice(theirs.requirePart("game"), 0, RESOURCES_SIZE);

                comparePart("resources", simulatedResources, recordedResources, differing);
            }

            if (asserted.contains("forces")) {
                byte[] simulatedForces = copySlice(ours.requirePart("game"), FORCES_OFFSET, FORCES_SIZE);
                byte[] recordedForces = copySlice(theirs.requirePart("game"), FORCES_OFFSET, FORCES_SIZE);

                comparePart("forces", simulatedForces, recordedForces, differing);
            }

            if (asserted.contains("units")) {
                comparePart("unitFlags", ours.findPart("unitFlags"), theirs.findPart("unitFlags"), differing);

                byte[] simulatedUnits = copyWithoutLocalUnitFields(ours.requirePart("units"));
                byte[] recordedUnits = copyWithoutLocalUnitFields(theirs.requirePart("units"));

                comparePart("units", simulatedUnits, recordedUnits, differing);
                comparePart("unitHeads", ours.findPart("unitHeads"), theirs.findPart("unitHeads"), differing);
            }

            if (asserted.contains("bullets")) {
                comparePart("bullets", ours.findPart("bullets"), theirs.findPart("bullets"), differing);
            }

            if (asserted.contains("orders")) {
                comparePart("orders", ours.findPart("orders"), theirs.findPart("orders"), differing);
                comparePart("orderHeads", ours.findPart("orderHeads"), theirs.findPart("orderHeads"), differing);
            }

            if (asserted.contains("triggers")) {
                comparePart("triggers", ours.findPart("triggers"), theirs.findPart("triggers"), differing);
                comparePart("triggerTail", ours.findPart("triggerTail"), theirs.findPart("triggerTail"), differing);
            }

            if (asserted.contains("pathing")) {
                comparePart("pathing", ours.findPart("pathing"), theirs.findPart("pathing"), differing);
            }

            if (asserted.contains("computer")) {
                for (String name : theirs.getParts().keySet()) {
                    if (!name.startsWith("ai")) {
                        continue;
                    }

                    if (name.equals("aiCaptains")) {
                        byte[] simulatedPart = copyWithoutFreeGuardRetries(ours.requirePart(name));
                        byte[] recordedPart = copyWithoutFreeGuardRetries(theirs.requirePart(name));

                        comparePart(name, simulatedPart, recordedPart, differing);
                    } else {
                        comparePart(name, ours.findPart(name), theirs.findPart(name), differing);
                    }
                }
            }

            if (asserted.contains("settings")) {
                for (int index = 0; index < SETTINGS_COUNT; index++) {
                    comparePart("settings" + index, ours.findPart("settings" + index), theirs.findPart("settings" + index), differing);
                }
            }
        }

        Assertions.assertEquals(List.of(), differing, saveName + ": parts that differ from the native file");
    }

    @ParameterizedTest
    @ValueSource(strings = {GameTestResources.SAVE_SINGLE_PLAYER_WITH_COMPUTER, GameTestResources.SAVE_LEVEL_COMPUTER,
                            GameTestResources.SAVE_MULTIPLAYER_WITH_COMPUTER, GameTestResources.SAVE_TOM_AND_JERRY})
    void read_reports_differences_from_simulated_replay(@NotNull String saveName) throws Exception {
        SaveGameRoundTripScenario.assumeSelected(saveName);

        Path save = GameTestResources.getPath(saveName);
        Path replayPath = GameTestResources.getPath(GameTestResources.getSaveReplay(saveName));

        byte[] data = Files.readAllBytes(save);
        SaveGameFile file = SaveGameFile.parse(data);
        int frame = SaveGameLayoutCheck.readGameFrame(file.getGameStructure());
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(replayPath));
        StringBuilder report = new StringBuilder("NATIVE LOAD " + saveName + " @" + frame);

        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime simulated = SaveGameRoundTripScenario.startReplay(gameData, replay);

            SaveGameRoundTripScenario.advanceToFrame(simulated, frame);

            byte[] simulatedSave = SaveGameUtils.write(new SaveGameContext(simulated, null),
                                                       file.getDescription(),
                                                       file.getSaveGameId()).toByteArray();
            ReplayRuntime loaded = SaveGameRoundTripScenario.startSavedGame(gameData, file, replay, frame);

            SaveGameUtils.read(file, new SaveGameContext(loaded, null));

            byte[] resaved = SaveGameUtils.write(new SaveGameContext(loaded, null), file.getDescription(), file.getSaveGameId()).toByteArray();

            report.append(System.lineSeparator()).append("  parts in which the loaded game's save differs from the simulated game's save: ")
                  .append(SaveGameRoundTripScenario.findDifferingParts(simulatedSave, resaved));
            report.append(System.lineSeparator()).append("  resaved identically: ").append(Arrays.equals(data, resaved));

            Map<String, byte[]> nativeParts = SaveGameLayoutCheck.parse(data).getParts();
            Map<String, byte[]> resavedParts = SaveGameLayoutCheck.parse(resaved).getParts();

            for (Map.Entry<String, byte[]> part : nativeParts.entrySet()) {
                byte[] other = resavedParts.get(part.getKey());

                if (!Arrays.equals(part.getValue(), other)) {
                    SaveGameComparisonCheck.compareRuns("resaved " + part.getKey(),
                                                        Objects.requireNonNullElse(other, new byte[0]), part.getValue(),
                                                        report);
                }
            }

            int mismatch = -1;

            while (SaveGameRoundTripScenario.stepFrame(simulated) & SaveGameRoundTripScenario.stepFrame(loaded)) {
                if (simulated.getGame().computeStateHash() != loaded.getGame().computeStateHash()) {
                    mismatch = simulated.getGame().getCurrentFrame();

                    break;
                }
            }

            while (SaveGameRoundTripScenario.stepFrame(simulated) & SaveGameRoundTripScenario.stepFrame(loaded)) {
                // Both games run to the replay's end.
            }

            report.append(System.lineSeparator()).append("  continued to frame ").append(simulated.getGame().getCurrentFrame())
                  .append(", first hash mismatch frame ").append(mismatch);
        }

        System.out.println(report);
    }
}
