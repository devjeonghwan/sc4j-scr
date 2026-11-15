package org.fogofwarlabs.sc4j.scr.game.ai;

import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.player.*;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Checks the script opcode groups inside a running game.
 */
@RequiresInstallation
final class ComputerScriptsTest {
    private static final int IDENTIFIER = 0x54534554;
    /**
     * The size of the directory pointer at the start of a script resource, which is also the address of the first script.
     */
    private static final int POINTER_SIZE = Integer.BYTES;
    /**
     * The bytes between the code and the directory of the assembled resource.
     */
    private static final int CODE_PADDING = 8;
    /**
     * The size of a main directory entry.
     */
    private static final int DIRECTORY_ENTRY_SIZE = 16;
    /**
     * The directory entries of the assembled resource: the first script and the second script.
     */
    private static final int DIRECTORY_ENTRY_COUNT = 2;
    /**
     * The size of the zero identifier that ends a directory.
     */
    private static final int DIRECTORY_TERMINATOR_SIZE = Integer.BYTES;
    /**
     * The offset of the script address in a directory entry, after the identifier.
     */
    private static final int ENTRY_ADDRESS_OFFSET = 4;
    /**
     * The size of the expansion resource: its directory pointer and an empty directory.
     */
    private static final int EXPANSION_SIZE = 8;
    private static final int BYTE_MASK = 0xFF;
    private static final int WORD_MASK = 0xFFFF;

    /**
     * The frame the replay runs to before the tests.
     */
    private static final int START_FRAME = 100;
    /**
     * The frames of the {@code wait} opcode that ends a test script.
     */
    private static final int WAIT_FRAMES = 50;
    /**
     * The size of a {@code wait} opcode with its WORD.
     */
    private static final int WAIT_INSTRUCTION_SIZE = 3;
    /**
     * The minutes of the {@code time_jump} opcode, which the elapsed game time has reached.
     */
    private static final int TIME_JUMP_MINUTES = 0;
    /**
     * The comparison of the {@code if_dif} opcode.
     */
    private static final int DIFFICULTY_COMPARISON = 0;
    /**
     * The difficulty of the {@code if_dif} opcode.
     */
    private static final int DIFFICULTY = 2;
    /**
     * A town count of the {@code try_townpoint} opcode that the computer player never reaches.
     */
    private static final int UNREACHED_TOWN_COUNT = 0xFF;
    private static final int RANDOM_SEED = 0x12345678;
    /**
     * The operand of the {@code set_attacks} opcode.
     */
    private static final int ATTACK_COUNT = 3;
    /**
     * The unit count of the {@code easy_attack} opcode.
     */
    private static final int EASY_ATTACK_COUNT = 2;
    /**
     * The first BYTE of the {@code prep_down} opcode.
     */
    private static final int PREP_DOWN_FIRST_BYTE = 200;
    /**
     * The second BYTE of the {@code prep_down} opcode, which the attack types keep at least.
     */
    private static final int PREP_DOWN_SECOND_BYTE = 2;
    /**
     * The general command rectangle that the scripts of the player state test start with.
     */
    private static final int @NotNull [] GENERAL_COMMAND_RECTANGLE = {10, 20, 30, 40};
    /**
     * The first opcode past the known opcodes.
     */
    private static final int UNKNOWN_OPCODE = 0x71;

    private static final int TOWN_WORKER_POSITION_X = 3700;
    private static final int TOWN_OTHER_POSITION_X = 3720;
    private static final int TOWN_POSITION_Y = 2400;
    /**
     * The rectangle of the {@code start_areatown} script around the town worker.
     */
    private static final int @NotNull [] TOWN_RECTANGLE = {3650, 2350, 3750, 2450};
    private static final int ENEMY_ZERGLING_POSITION_X = 3600;
    private static final int DOG_POSITION_X = 3620;
    private static final int ZERGLING_POSITION_Y = 2600;
    private static final int SILO_POSITION_X = 3200;
    private static final int SILO_POSITION_Y = 2800;
    private static final int CREATED_MARINE_POSITION_X = 3000;
    private static final int CREATED_MARINE_POSITION_Y = 2600;
    /**
     * The rectangle of the unit opcode scripts around the computer Zergling.
     */
    private static final int @NotNull [] UNIT_RECTANGLE = {3580, 2580, 3640, 2620};

    /**
     * The attack type entry of a Marine: its unit type plus one.
     */
    private static final int MARINE_ATTACK_TYPE = 1;
    private static final int IDLE_REGION_STATE = 0;
    private static final int ALLY_TOWN_REGION_STATE = 4;
    private static final int ENEMY_RELATION = 0;
    private static final int ALLIED_VICTORY_RELATION = 2;
    private static final int JUNK_YARD_DOG_ORDER = 187;

    private static @Nullable GameData gameData;
    private static @Nullable ReplayRuntime runtime;

    private static int computer;
    private static int human;

    /**
     * Assembles an aiscript.bin resource: the directory pointer, the code from address 4 and a 16-byte directory.
     */
    private static final class Program {
        private static void putEntry(byte @NotNull [] main, int offset, int identifier, int address) {
            for (int shift = 0; shift < Integer.BYTES; shift++) {
                main[offset + shift]                        = (byte) (identifier >>> shift * Byte.SIZE);
                main[offset + ENTRY_ADDRESS_OFFSET + shift] = (byte) (address >>> shift * Byte.SIZE);
            }
        }


        private final @NotNull ByteArrayOutputStream code;
        private final @NotNull Map<String, Integer> marks;
        private final @NotNull Map<Integer, String> labels;

        Program() {
            this.code   = new ByteArrayOutputStream();
            this.marks  = new HashMap<>();
            this.labels = new HashMap<>();

            this.marks.put("start", POINTER_SIZE);
        }

        int getAddress(@NotNull String name) {
            return this.marks.get(name);
        }


        @NotNull Program writeOpcode(int opcode) {
            return this.writeByte(opcode);
        }

        @NotNull Program writeByte(int value) {
            this.code.write(value);

            return this;
        }

        @NotNull Program writeWord(int value) {
            return this.writeByte(value & BYTE_MASK).writeByte(value >>> Byte.SIZE & BYTE_MASK);
        }

        void writeLabel(@NotNull String name) {
            this.labels.put(this.code.size(), name);

            this.writeWord(0);
        }

        @NotNull Program markAddress(@NotNull String name) {
            this.marks.put(name, POINTER_SIZE + this.code.size());

            return this;
        }

        @NotNull ComputerScriptProgram build(@Nullable String second) {
            byte[] body = this.code.toByteArray();

            for (Map.Entry<Integer, String> label : this.labels.entrySet()) {
                int target = this.getAddress(label.getValue());

                body[label.getKey()]     = (byte) target;
                body[label.getKey() + 1] = (byte) (target >>> Byte.SIZE);
            }

            int directory = POINTER_SIZE + body.length + CODE_PADDING;
            byte[] main = new byte[directory + DIRECTORY_ENTRY_SIZE * DIRECTORY_ENTRY_COUNT + DIRECTORY_TERMINATOR_SIZE];

            main[0] = (byte) directory;
            main[1] = (byte) (directory >>> Byte.SIZE);
            System.arraycopy(body, 0, main, POINTER_SIZE, body.length);
            putEntry(main, directory, IDENTIFIER, POINTER_SIZE);
            putEntry(main, directory + DIRECTORY_ENTRY_SIZE, IDENTIFIER + 1, second == null ? POINTER_SIZE : this.getAddress(second));

            byte[] expansion = new byte[EXPANSION_SIZE];

            expansion[0] = POINTER_SIZE;

            return new ComputerScriptProgram(main, expansion);
        }

        @NotNull ComputerScripts start(int owner) {
            return this.start(owner, null);
        }

        @NotNull ComputerScripts start(int owner, int @Nullable [] location) {
            Assertions.assertNotNull(runtime);

            ComputerScripts scripts = new ComputerScripts(runtime.getGame(), this.build("start"));

            Assertions.assertTrue(scripts.start(IDENTIFIER, owner, location));

            return scripts;
        }
    }

    @BeforeAll
    static void startGame() throws Exception {
        Path path = GameTestResources.getPath(GameTestResources.REPLAY_TERRAN_VS_COMPUTER);

        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(path));

        gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath());

        runtime = RunningGameScenario.createRuntime(replay, gameData);
        runtime.start(0, 0);

        Game game = runtime.getGame();

        while (game.getCurrentFrame() < START_FRAME) {
            Assertions.assertTrue(runtime.step(0, game.getCurrentFrame() * runtime.getFrameDuration()));
        }

        computer = -1;
        human    = -1;

        for (int player = 0; player < PlayerTable.PLAYABLE_PLAYER_SLOTS; player++) {
            Player setupPlayer = game.getPlayerTable().getPlayer(player);

            Assertions.assertNotNull(setupPlayer);

            int playerType = setupPlayer.getType();

            if (playerType == PlayerTypes.COMPUTER && computer == -1) {
                computer = player;
            } else if (playerType == PlayerTypes.HUMAN && human == -1) {
                human = player;
            }
        }

        Assertions.assertNotEquals(-1, computer);
        Assertions.assertNotEquals(-1, human);
    }

    private static @NotNull ComputerPlayerPhase requirePhase(@NotNull Game game) {
        ComputerPlayerPhase phase = game.getComputerPlayerPhase();

        Assertions.assertNotNull(phase);

        return phase;
    }

    @AfterAll
    static void close() {
        if (gameData != null) {
            gameData.close();
        }
    }


    @Test
    void control_opcodes_jump_and_set_random_seed() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            ComputerPlayerPhase phase = requirePhase(game);
            Program code = new Program();

            code.writeOpcode(ComputerScriptOpcodes.TIME_JUMP).writeByte(TIME_JUMP_MINUTES).writeLabel("time");
            code.writeOpcode(ComputerScriptOpcodes.STOP);
            code.markAddress("time").writeOpcode(ComputerScriptOpcodes.IF_DIF).writeByte(DIFFICULTY_COMPARISON).writeByte(DIFFICULTY)
                .writeLabel("difficulty");
            code.writeOpcode(ComputerScriptOpcodes.STOP);
            code.markAddress("difficulty").writeOpcode(ComputerScriptOpcodes.TRY_TOWNPOINT).writeByte(UNREACHED_TOWN_COUNT).writeLabel("never");
            code.writeOpcode(ComputerScriptOpcodes.SCOUT_WITH).writeWord(ScrUnitIds.ZERG_OVERLORD);
            code.writeOpcode(ComputerScriptOpcodes.HARASS_LOCATION);
            code.writeOpcode(ComputerScriptOpcodes.SET_RANDOMSEED).writeWord(RANDOM_SEED & WORD_MASK).writeWord(RANDOM_SEED >>> Short.SIZE);
            code.writeOpcode(ComputerScriptOpcodes.DEBUG).writeLabel("after");

            for (char character : "script".toCharArray()) {
                code.writeByte(character);
            }

            code.writeByte(0);
            code.markAddress("never").writeOpcode(ComputerScriptOpcodes.STOP);
            code.markAddress("after").writeOpcode(ComputerScriptOpcodes.WAIT).writeWord(WAIT_FRAMES);

            ComputerScripts scripts = code.start(computer);

            scripts.update(phase, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(RANDOM_SEED, game.getGameContext().getGameSeed(), "set_randomseed stores the DWORD as the game seed");
            Assertions.assertEquals(code.getAddress("after") + WAIT_INSTRUCTION_SIZE, scripts.getAddress(scripts.getFirstActive()),
                                    "debug jumps to its WORD");
        });
    }

    @Test
    void kill_thread_releases_killable_records() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            ComputerPlayerPhase phase = requirePhase(game);
            Program code = new Program();

            code.writeOpcode(ComputerScriptOpcodes.KILL_THREAD).writeOpcode(ComputerScriptOpcodes.STOP);
            code.markAddress("victim").writeOpcode(ComputerScriptOpcodes.KILLABLE).writeOpcode(ComputerScriptOpcodes.WAIT).writeWord(1);

            ComputerScripts scripts = new ComputerScripts(game, code.build("victim"));

            Assertions.assertTrue(scripts.start(IDENTIFIER, computer, null));
            Assertions.assertTrue(scripts.start(IDENTIFIER + 1, computer, null));

            scripts.update(phase, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertNotEquals(-1, scripts.getFirstActive(), "the killable record waits one frame");

            scripts.update(phase, RunningGameScenario.INSTRUCTION_LIMIT);
            scripts.update(phase, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(-1, scripts.getFirstActive(), "kill_thread marked the killable record for release");
        });
    }

    @Test
    void player_state_opcodes_write_computer_record() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            ComputerPlayerPhase phase = requirePhase(game);
            ComputerPlayer player = phase.getPlayer(computer);
            int flags = player.getFlags();
            short[] attack = player.getAttackTypes().clone();

            try {
                player.setFlags(flags | ComputerPlayer.FLAG_DEFAULT_BUILD_OFF);
                player.clearAttack(false);

                Program code = new Program();

                code.writeOpcode(ComputerScriptOpcodes.DEFAULT_BUILD);
                code.writeOpcode(ComputerScriptOpcodes.TARGET_EXPANSION);
                code.writeOpcode(ComputerScriptOpcodes.SET_ATTACKS).writeByte(ATTACK_COUNT);
                code.writeOpcode(ComputerScriptOpcodes.SET_GENCMD);
                code.writeOpcode(ComputerScriptOpcodes.EASY_ATTACK).writeByte(EASY_ATTACK_COUNT).writeWord(ScrUnitIds.TERRAN_MARINE);
                code.writeOpcode(ComputerScriptOpcodes.PREP_DOWN).writeByte(PREP_DOWN_FIRST_BYTE).writeByte(PREP_DOWN_SECOND_BYTE)
                    .writeWord(ScrUnitIds.TERRAN_MARINE);
                code.writeOpcode(ComputerScriptOpcodes.WAIT).writeWord(WAIT_FRAMES);

                ComputerScripts scripts = code.start(computer, GENERAL_COMMAND_RECTANGLE.clone());

                scripts.update(phase, RunningGameScenario.INSTRUCTION_LIMIT);

                Assertions.assertEquals(0, player.getFlags() & ComputerPlayer.FLAG_DEFAULT_BUILD_OFF, "default_build clears flag 4");
                Assertions.assertNotEquals(0, player.getFlags() & ComputerPlayer.FLAG_ATTACK_READY, "target_expansion sets flag 0x800");
                Assertions.assertTrue(player.isAttackPrepared(), "set_attacks writes +0x40b");
                Assertions.assertArrayEquals(GENERAL_COMMAND_RECTANGLE, player.getGeneralCommandRectangle());

                short[] types = player.getAttackTypes();

                Assertions.assertEquals(MARINE_ATTACK_TYPE, types[0], "prep_down keeps at least its second BYTE");
                Assertions.assertEquals(MARINE_ATTACK_TYPE, types[1]);
                Assertions.assertEquals(0, types[2], "easy_attack adds nothing at difficulty 1");
            } finally {
                player.setFlags(flags);
                player.setAttackPreparation(0);
                System.arraycopy(attack, 0, player.getAttackTypes(), 0, attack.length);
            }
        });
    }

    @Test
    void wait_opcodes_repeat_the_opcode_while_waiting() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            ComputerPlayerPhase phase = requirePhase(game);
            ComputerPlayer player = phase.getPlayer(computer);
            int flags = player.getFlags();

            try {
                player.setFlags(flags & ~ComputerPlayer.FLAG_REQUEST_SCAN);

                Program code = new Program();

                code.writeOpcode(ComputerScriptOpcodes.WAIT_FINISHATTACK);
                code.markAddress("upgrades").writeOpcode(ComputerScriptOpcodes.WAIT_UPGRADES);
                code.writeOpcode(ComputerScriptOpcodes.STOP);

                ComputerScripts scripts = code.start(computer);
                boolean attacking = phase.getRegions().isAttacking(computer);

                scripts.update(phase, RunningGameScenario.INSTRUCTION_LIMIT);

                int expected = attacking ? code.getAddress("start") : code.getAddress("upgrades");

                Assertions.assertEquals(expected, scripts.getAddress(scripts.getFirstActive()), "the waiting opcode runs again");
            } finally {
                player.setFlags(flags);
            }
        });
    }

    @Test
    void town_opcodes_require_town_and_start_area_town_founds_one_from_rectangle() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            ComputerPlayerPhase phase = requirePhase(game);
            Program implode = new Program();

            implode.writeOpcode(ComputerScriptOpcodes.IMPLODE);

            ComputerScripts scripts = implode.start(computer);

            Assertions.assertThrows(UnsupportedOperationException.class, () -> scripts.update(phase, RunningGameScenario.INSTRUCTION_LIMIT),
                                    "implode needs a town");

            Unit worker = RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_SCV, TOWN_WORKER_POSITION_X, TOWN_POSITION_Y, computer);
            Unit other = RunningGameScenario.createUnit(game, ScrUnitIds.ZERG_ZERGLING, TOWN_OTHER_POSITION_X, TOWN_POSITION_Y, human);
            boolean unbound = worker.getComputerRecord() == null;
            int towns = phase.countTowns(computer);
            Program code = new Program();

            code.writeOpcode(ComputerScriptOpcodes.START_AREATOWN);
            code.writeOpcode(ComputerScriptOpcodes.TRY_TOWNPOINT).writeByte(towns + 1).writeLabel("counted");
            code.writeOpcode(ComputerScriptOpcodes.STOP);
            code.markAddress("counted").writeOpcode(ComputerScriptOpcodes.WAIT).writeWord(WAIT_FRAMES);

            ComputerScripts area = code.start(computer, TOWN_RECTANGLE.clone());

            area.update(phase, RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertEquals(towns + 1, phase.countTowns(computer), "start_areatown founds a town");

            ComputerTown town = phase.getFirstTown(computer);

            Assertions.assertNotNull(town);

            if (unbound) {
                Sprite townWorkerSprite = worker.getSpriteOrNull();

                Assertions.assertNotNull(townWorkerSprite);

                Assertions.assertEquals((short) townWorkerSprite.getX(), town.positionX, "the unbound worker anchors the town");
            }

            Assertions.assertNotNull(worker.getComputerRecord(), "the owner's unit is bound");
            Assertions.assertNull(other.getComputerRecord(), "the other player's unit is filtered out");
            Assertions.assertEquals(code.getAddress("counted") + WAIT_INSTRUCTION_SIZE, area.getAddress(area.getFirstActive()),
                                    "try_townpoint jumps on an equal count");
        });
    }

    @Test
    void region_opcodes_update_owner_and_ally_region_states() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            ComputerPlayerPhase phase = requirePhase(game);
            ComputerRegions regions = phase.getRegions();
            PlayerRelations relations = game.getPlayerTable().getPlayerRelations();

            Assertions.assertNotNull(relations);

            int toward = relations.getAlliance(computer, human);
            int back = relations.getAlliance(human, computer);
            int width = game.getMap().getWidth() * TerrainTileset.TILE_SIZE;
            int height = game.getMap().getHeight() * TerrainTileset.TILE_SIZE;

            try {
                Program clear = new Program();

                clear.writeOpcode(ComputerScriptOpcodes.CLEAR_COMBATDATA);
                clear.writeOpcode(ComputerScriptOpcodes.BUILD_BUNKERS);
                clear.writeOpcode(ComputerScriptOpcodes.WAIT).writeWord(WAIT_FRAMES);

                ComputerScripts scripts = clear.start(computer, new int[]{0, 0, width, height});

                scripts.update(phase, RunningGameScenario.INSTRUCTION_LIMIT);

                int count = game.getComputerTerrain().getRegions().getRegionCount();

                for (int region = 1; region < count; region++) {
                    Assertions.assertEquals(IDLE_REGION_STATE, regions.getState(regions.getIndex(computer, region)), "clear_combatdata");
                }

                for (int region = 1; region < count; region++) {
                    int regionFlags = regions.getFlags(regions.getIndex(computer, region));

                    if ((regionFlags & ComputerRegionStates.FLAG_BUNKER_WANTED) != 0) {
                        Assertions.assertNotEquals(0, regionFlags & ComputerRegionStates.FLAG_TOWN, "build_bunkers marks town regions only");
                    }
                }

                int region = game.getComputerTerrain().getRegion(TOWN_WORKER_POSITION_X, TOWN_POSITION_Y);

                regions.setState(phase, regions.getIndex(computer, region), ComputerRegionStates.TOWN);
                regions.setState(phase, regions.getIndex(human, region), ComputerRegionStates.IDLE);
                relations.setAlliance(computer, human, PlayerRelations.ALLIED);
                relations.setAlliance(human, computer, PlayerRelations.ALLIED);

                Program help = new Program();

                help.writeOpcode(ComputerScriptOpcodes.HELP_IFTROUBLE);
                help.writeOpcode(ComputerScriptOpcodes.WAIT).writeWord(WAIT_FRAMES);

                help.start(computer).update(phase, RunningGameScenario.INSTRUCTION_LIMIT);

                Assertions.assertEquals(ALLY_TOWN_REGION_STATE,
                                        regions.getState(regions.getIndex(human, region)),
                                        "help_iftrouble activates the mutual ally's idle region");
            } finally {
                relations.setAlliance(computer, human, toward);
                relations.setAlliance(human, computer, back);
            }
        });
    }

    @Test
    void unit_opcodes_set_alliances_create_units_and_issue_orders() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            ComputerPlayerPhase phase = requirePhase(game);
            PlayerRelations relations = game.getPlayerTable().getPlayerRelations();

            Assertions.assertNotNull(relations);

            int toward = relations.getAlliance(computer, human);

            try {
                RunningGameScenario.createUnit(game, ScrUnitIds.ZERG_ZERGLING, ENEMY_ZERGLING_POSITION_X, ZERGLING_POSITION_Y, human);

                Unit dog = RunningGameScenario.createUnit(game, ScrUnitIds.ZERG_ZERGLING, DOG_POSITION_X, ZERGLING_POSITION_Y, computer);

                RunningGameScenario.createUnit(game, ScrUnitIds.TERRAN_NUCLEAR_SILO, SILO_POSITION_X, SILO_POSITION_Y, computer);

                Unit silo = null;
                UnitPool units = game.getUnitSystem().getUnitPool();

                Assertions.assertNotNull(units);

                for (int index = units.getFirstOwnerIndex(computer); index != 0 && silo == null; index = units.getNextOwnerIndex(index)) {
                    Unit unit = units.getUnit(index);

                    if (unit.getUnitId() == ScrUnitIds.TERRAN_NUCLEAR_SILO && unit.getSiloReadyDword() == 0) {
                        silo = unit;
                    }
                }

                Assertions.assertNotNull(silo);

                UnitAccounting accounting = game.getPlayerTable().getUnitAccounting();

                Assertions.assertNotNull(accounting);

                int marines = accounting.getTypeCount(ScrUnitIds.TERRAN_MARINE, computer);
                Program code = new Program();

                code.writeOpcode(ComputerScriptOpcodes.PLAYER_ALLY);
                code.writeOpcode(ComputerScriptOpcodes.JUNKYARD_DOG);
                code.writeOpcode(ComputerScriptOpcodes.CREATE_UNIT).writeWord(ScrUnitIds.TERRAN_MARINE)
                    .writeWord(CREATED_MARINE_POSITION_X).writeWord(CREATED_MARINE_POSITION_Y);
                code.writeOpcode(ComputerScriptOpcodes.CREATE_NUKE);
                code.writeOpcode(ComputerScriptOpcodes.WAIT).writeWord(WAIT_FRAMES);
                code.start(computer, UNIT_RECTANGLE.clone()).update(phase, RunningGameScenario.INSTRUCTION_LIMIT);

                Assertions.assertEquals(ALLIED_VICTORY_RELATION, relations.getAlliance(computer, human), "player_ally writes BYTE 2");
                Assertions.assertEquals(JUNK_YARD_DOG_ORDER, dog.getPrimaryOrder(), "junkyard_dog issues order 187");
                Assertions.assertEquals(marines + 1, accounting.getTypeCount(ScrUnitIds.TERRAN_MARINE, computer), "create_unit");
                Assertions.assertNotNull(silo.getSiloNuke(), "create_nuke arms the Silo");
                Assertions.assertTrue(silo.isSiloReady());
                Assertions.assertSame(silo, silo.getSiloNuke().getParentUnit());

                Program enemy = new Program();

                enemy.writeOpcode(ComputerScriptOpcodes.PLAYER_ENEMY);
                enemy.writeOpcode(ComputerScriptOpcodes.WAIT).writeWord(WAIT_FRAMES);
                enemy.start(computer, UNIT_RECTANGLE.clone()).update(phase, RunningGameScenario.INSTRUCTION_LIMIT);

                Assertions.assertEquals(ENEMY_RELATION, relations.getAlliance(computer, human), "player_enemy writes BYTE 0");
            } finally {
                relations.setAlliance(computer, human, toward);
            }
        });
    }

    @Test
    void fatal_error_and_unknown_opcodes_stop_with_exception() {
        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            Assertions.assertNotNull(runtime);

            Game game = runtime.getGame();
            ComputerPlayerPhase phase = requirePhase(game);
            Program fatal = new Program();

            fatal.writeOpcode(ComputerScriptOpcodes.FATAL_ERROR);

            ComputerScripts first = fatal.start(computer);

            Assertions.assertThrows(UnsupportedOperationException.class, () -> first.update(phase, RunningGameScenario.INSTRUCTION_LIMIT));

            Program outside = new Program();

            outside.writeOpcode(UNKNOWN_OPCODE);

            ComputerScripts second = outside.start(computer);

            Assertions.assertThrows(UnsupportedOperationException.class, () -> second.update(phase, RunningGameScenario.INSTRUCTION_LIMIT));
        });
    }
}
