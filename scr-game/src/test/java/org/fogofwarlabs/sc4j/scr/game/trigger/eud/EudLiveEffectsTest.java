package org.fogofwarlabs.sc4j.scr.game.trigger.eud;

import org.fogofwarlabs.sc4j.scr.content.ScrTechIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommand;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerUpgrades;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplaySession;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

/**
 * Checks context and DAT write effects during replay execution.
 */
@RequiresInstallation
final class EudLiveEffectsTest {
    private static final int FRAME_COUNTER = 0x57F23C;
    private static final int EXPANSION = 0x58F440;
    private static final int LOBBY_COLORS = 0x58F47A;
    private static final int UNITS_GRAPHICS = 0x6644F8;
    private static final int UNITS_HIT_POINTS = 0x662350;
    private static final int CONTEXT_TAIL = 0x58F44C;

    private static final int FRAME_DURATION = 42;
    private static final int BELOW_COMMAND_CENTER = 96;

    /**
     * The frame the replay runs to before a test reads or writes the address space.
     */
    private static final int SETUP_FRAME = 10;
    /**
     * The frame the context test runs to after its writes.
     */
    private static final int FINAL_FRAME = 20;
    /**
     * The offset of the lobby color DWORD that replay loading clears.
     */
    private static final int CLEARED_LOBBY_COLORS_OFFSET = 2;
    /**
     * The four lobby colors that replay loading writes.
     */
    private static final int CLEARED_LOBBY_COLORS = 0x16161616;
    /**
     * The offset of the context tail DWORD that the context test writes.
     */
    private static final int CONTEXT_TAIL_SAMPLE_OFFSET = 0x400;
    /**
     * The value the context test writes into the context tail.
     */
    private static final int CONTEXT_TAIL_SAMPLE = 77;
    /**
     * The units.dat hit points, in 1/256 units, that the DAT write test writes.
     */
    private static final int WRITTEN_HIT_POINTS = 99 << Byte.SIZE;
    /**
     * The classic unit table, the size of its entries and the entry offset of the owner byte.
     */
    private static final int CLASSIC_UNIT_TABLE = 0x59CCA8;
    private static final int CLASSIC_UNIT_SIZE = 336;
    private static final int CLASSIC_UNIT_OWNER = 0x4C;
    /**
     * The game-data block, the offset of its classic upgrade levels in it and the classic upgrades of a level row.
     */
    private static final int GAME_DATA = 0x57F0F0;
    private static final int CLASSIC_LEVELS = 0xE1C0;
    private static final int CLASSIC_UPGRADES = 46;
    /**
     * The owner byte past the player slots that the upgrade level test writes for an SCV; its classic level row lies outside the upgrade tables.
     */
    private static final int WRITTEN_OWNER = 14;
    /**
     * The level byte the upgrade level test writes into that row.
     */
    private static final int WRITTEN_LEVEL = 9;
    private static final int BYTE_MASK = 0xFF;
    /**
     * The first owner byte past the player slots, which the research and upgrade completion tests write for a Command Center.
     */
    private static final int PAST_PLAYER = 12;
    /**
     * The game-data offsets of the legacy upgrading bits and the legacy researching bits of player {@code 0}, on which the first classic level and
     * the first classic researched byte of player {@code 12} lie.
     */
    private static final int LEGACY_UPGRADING = 0xE3E8;
    private static final int LEGACY_RESEARCHING = 0xDF74;
    /**
     * The game-data offset of the expansion maximum levels of player {@code 0}, on which the first researching bits of player {@code 12} lie.
     */
    private static final int EXPANSION_MAXIMUM_LEVELS = 0x10188;
    /**
     * The first expansion upgrade.
     */
    private static final int FIRST_EXPANSION_UPGRADE = 46;
    /**
     * The game-data offset of the expansion BYTE, on which the first upgrading bits of player {@code 12} lie.
     */
    private static final int EXPANSION_BYTE = 0x10350;
    /**
     * A byte with its two lowest bits set, and that byte with the lowest bit cleared.
     */
    private static final int TWO_LOW_BITS = 0x03;
    private static final int SECOND_BIT = 0x02;
    /**
     * The researched byte of a researched technology, and player {@code 0}, whose bytes the tests read.
     */
    private static final int RESEARCHED = 1;
    private static final int FIRST_PLAYER = 0;
    /**
     * The level of player {@code 0} that the upgrade completion test writes: the maximum level of the first classic upgrade of player {@code 12}.
     */
    private static final int PAST_PLAYER_MAXIMUM_LEVEL = 3;
    /**
     * The level the upgrade completion test researches.
     */
    private static final int TARGET_LEVEL = 1;
    /**
     * The animation and order activation budget of the completion tests.
     */
    private static final int INSTRUCTION_LIMIT = 10000;

    private static int hostTimeMilliseconds;

    /**
     * Creates a {@link ReplayRuntime} instance and starts the replay fixture.
     *
     * @param gameData the installed game data
     * @return the created and started {@link ReplayRuntime} instance
     * @throws Exception if the replay cannot be loaded
     */
    private static @NotNull ReplayRuntime createReplayRuntime(@NotNull GameData gameData) throws Exception {
        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(GameTestResources.getPath(GameTestResources.REPLAY_TVT_BAEKDUDAEGAN)));
        ReplayRuntime runtime = RunningGameScenario.createRuntime(replay, gameData);

        runtime.start(0, 0);

        Assertions.assertNull(runtime.getFailure());

        return runtime;
    }

    /**
     * Steps the runtime until the selected frame.
     *
     * @param runtime     the replay runtime
     * @param targetFrame the target frame
     */
    private static void runUntilFrame(@NotNull ReplayRuntime runtime, int targetFrame) {
        while (runtime.getGame().getCurrentFrame() < targetFrame) {
            Assertions.assertTrue(runtime.step(FRAME_DURATION, hostTimeMilliseconds += FRAME_DURATION));
            Assertions.assertNull(runtime.getFailure(), () -> String.valueOf(runtime.getFailure()));
        }
    }

    /**
     * Finds the first created unit of a type.
     *
     * @param game     the running game
     * @param unitType the {@code units.dat} identifier
     * @return the unit
     */
    private static @NotNull Unit findUnit(@NotNull Game game, int unitType) {
        UnitPool pool = game.getUnitSystem().getUnitPool();

        Assertions.assertNotNull(pool);

        for (int poolIndex = pool.getFirstCreatedIndex(true); poolIndex != 0; poolIndex = pool.getNextCreatedIndex(poolIndex)) {
            if (pool.getUnit(poolIndex).getUnitId() == unitType) {
                return pool.getUnit(poolIndex);
            }
        }

        return Assertions.fail("The replay has no unit of type " + unitType + ".");
    }

    /**
     * Writes the low byte of an aligned DWORD of the classic address space, keeping its other bytes.
     *
     * @param space   the classic address space
     * @param address the aligned address
     * @param value   the byte
     */
    private static void writeLowByte(@NotNull ClassicAddressSpace space, int address, int value) {
        space.writeDword(address, space.readDword(address) & ~BYTE_MASK | value);
    }

    /**
     * Writes the owner byte of a unit through the classic unit table, as an EUD trigger does.
     *
     * @param space the classic address space
     * @param unit  the unit
     * @param owner the owner byte
     */
    private static void writeOwner(@NotNull ClassicAddressSpace space, @NotNull Unit unit, int owner) {
        writeLowByte(space, CLASSIC_UNIT_TABLE + (unit.getIndex() - 1) * CLASSIC_UNIT_SIZE + CLASSIC_UNIT_OWNER, owner);
    }


    @Test
    void context_tail_and_expansion_byte_are_served_in_a_replay() throws Exception {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = createReplayRuntime(gameData);

            runUntilFrame(runtime, SETUP_FRAME);

            Game game = runtime.getGame();
            ClassicAddressSpace space = ClassicAddressSpace.create(game);

            Assertions.assertEquals(CLEARED_LOBBY_COLORS, space.readDword(LOBBY_COLORS + CLEARED_LOBBY_COLORS_OFFSET),
                                    "Replay loading clears the lobby colours.");

            space.writeDword(CONTEXT_TAIL + CONTEXT_TAIL_SAMPLE_OFFSET, CONTEXT_TAIL_SAMPLE);

            Assertions.assertEquals(CONTEXT_TAIL_SAMPLE, space.readDword(CONTEXT_TAIL + CONTEXT_TAIL_SAMPLE_OFFSET));

            int engine = space.readDword(EXPANSION) & BYTE_MASK;

            PlayerUpgrades initialUpgrades = game.getPlayerTable().getPlayerUpgrades();

            Assertions.assertNotNull(initialUpgrades);

            Assertions.assertEquals(initialUpgrades.isExpansionEnabled() ? 1 : 0, engine);

            space.writeDword(EXPANSION, space.readDword(EXPANSION) & ~BYTE_MASK);

            PlayerUpgrades writtenUpgrades = game.getPlayerTable().getPlayerUpgrades();

            Assertions.assertNotNull(writtenUpgrades);

            Assertions.assertFalse(writtenUpgrades.isExpansionEnabled(), "The upgrade and technology gates read the written BYTE.");

            runUntilFrame(runtime, FINAL_FRAME);
        }
    }

    @Test
    void written_frame_counter_skips_the_loaded_command_block() throws Exception {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = createReplayRuntime(gameData);

            Game game = runtime.getGame();
            ReplaySession session = runtime.getSession();

            Assertions.assertNotNull(session);

            List<ReplayCommand> commands = ReplayFile.parse(GameTestResources.readAllBytes(GameTestResources.REPLAY_TVT_BAEKDUDAEGAN)).getCommands();

            int index;
            int before;

            do {
                before = session.getNextCommandIndex();

                Assertions.assertTrue(runtime.step(FRAME_DURATION, hostTimeMilliseconds += FRAME_DURATION));

                index = session.getNextCommandIndex();
            } while (index < commands.size() && (index != before || commands.get(index).getFrame() <= game.getCurrentFrame() + 1));

            Assertions.assertTrue(index < commands.size());

            long loaded = commands.get(index).getFrame();
            int loadedEnd = index;

            while (loadedEnd < commands.size() && commands.get(loadedEnd).getFrame() == loaded) {
                loadedEnd++;
            }

            ClassicAddressSpace.create(game).writeDword(FRAME_COUNTER, (int) loaded + 1);

            Assertions.assertEquals((int) loaded + 1, game.getCurrentFrame());

            Assertions.assertTrue(runtime.step(FRAME_DURATION, hostTimeMilliseconds += FRAME_DURATION));
            Assertions.assertNull(runtime.getFailure(), () -> String.valueOf(runtime.getFailure()));
            Assertions.assertEquals((int) loaded + 2, game.getCurrentFrame(), "The frame increment continues from the written counter.");

            long following = loadedEnd < commands.size() ? commands.get(loadedEnd).getFrame() : -1;
            int expected = loadedEnd;

            if (following >= 0 && following <= loaded + 1) {
                while (expected < commands.size() && commands.get(expected).getFrame() == following) {
                    expected++;
                }
            }

            Assertions.assertEquals(expected, session.getNextCommandIndex(), "The skipped block never runs; the next one runs once due.");
        }
    }

    @Test
    void unit_created_after_a_dat_write_uses_the_written_values() throws Exception {
        try (GameData gameData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = createReplayRuntime(gameData);

            runUntilFrame(runtime, SETUP_FRAME);

            Game game = runtime.getGame();
            ClassicAddressSpace space = ClassicAddressSpace.create(game);
            int marine = ScrUnitIds.TERRAN_MARINE;
            int firebat = ScrUnitIds.TERRAN_FIREBAT;
            GameData unitGameData = game.getGameData();

            Assertions.assertNotNull(unitGameData);

            int firebatFlingy = unitGameData.getUnits().get("Graphics", firebat);

            int graphics = space.readDword(UNITS_GRAPHICS + marine);

            space.writeDword(UNITS_GRAPHICS + marine, graphics & ~BYTE_MASK | firebatFlingy);
            space.writeDword(UNITS_HIT_POINTS + Integer.BYTES * marine, WRITTEN_HIT_POINTS);

            Unit commandCenter = null;
            UnitPool pool = game.getUnitSystem().getUnitPool();

            Assertions.assertNotNull(pool);

            for (int poolIndex = pool.getFirstCreatedIndex(true); poolIndex != 0 && commandCenter == null;
                 poolIndex = pool.getNextCreatedIndex(poolIndex)) {
                if (pool.getUnit(poolIndex).getUnitId() == ScrUnitIds.TERRAN_COMMAND_CENTER) {
                    commandCenter = pool.getUnit(poolIndex);
                }
            }

            Assertions.assertNotNull(commandCenter);

            Unit created = game.getTriggers().createUnit(marine, commandCenter.getPositionX(), commandCenter.getPositionY() + BELOW_COMMAND_CENTER,
                                                         commandCenter.getOwnerIndex(), RunningGameScenario.INSTRUCTION_LIMIT);

            Assertions.assertNotNull(created, "The Marine is placed below the Command Center.");

            Sprite sprite = created.getSpriteOrNull();

            Assertions.assertNotNull(sprite);

            GameData spriteGameData = game.getGameData();

            Assertions.assertNotNull(spriteGameData);

            Assertions.assertEquals(spriteGameData.getFlingy().get("Sprite", firebatFlingy), sprite.getSpriteTypeId(),
                                    "The new unit's sprite comes from the written flingy: the creation reads units.dat then.");
            Assertions.assertEquals(WRITTEN_HIT_POINTS, created.getHitPoints(), "The new unit's hit points come from the written units.dat value.");
        }
    }

    @Test
    void owner_written_past_the_players_reads_its_upgrade_level_from_the_game_context() throws Exception {
        try (GameData installedData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = createReplayRuntime(installedData);

            runUntilFrame(runtime, SETUP_FRAME);

            Game game = runtime.getGame();
            ClassicAddressSpace space = ClassicAddressSpace.create(game);
            GameData gameData = game.getGameData();
            PlayerUpgrades upgrades = game.getPlayerTable().getPlayerUpgrades();

            Assertions.assertNotNull(gameData);
            Assertions.assertNotNull(upgrades);

            Unit unit = findUnit(game, ScrUnitIds.TERRAN_SCV);
            int armorUpgrade = gameData.getUnitTypes().getArmorUpgrade(unit.getUnitId());

            writeOwner(space, unit, WRITTEN_OWNER);
            space.writeDword(GAME_DATA + CLASSIC_LEVELS + WRITTEN_OWNER * CLASSIC_UPGRADES + armorUpgrade, WRITTEN_LEVEL);

            Assertions.assertEquals(WRITTEN_OWNER, unit.getOwnerIndex());
            Assertions.assertEquals(WRITTEN_LEVEL, upgrades.getLevel(unit.getOwnerIndex(), armorUpgrade),
                                    "The armor level of the unit is the game-context byte its owner's level row addresses.");
        }
    }

    @Test
    void owner_written_past_the_players_completes_its_upgrade_on_the_game_context_bytes_its_rows_address() throws Exception {
        try (GameData installedData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = createReplayRuntime(installedData);

            runUntilFrame(runtime, SETUP_FRAME);

            Game game = runtime.getGame();
            ClassicAddressSpace space = ClassicAddressSpace.create(game);
            Unit building = findUnit(game, ScrUnitIds.TERRAN_COMMAND_CENTER);
            int upgrade = ScrUpgradeIds.TERRAN_INFANTRY_ARMOR;

            writeOwner(space, building, PAST_PLAYER);
            writeLowByte(space, GAME_DATA + CLASSIC_LEVELS, PAST_PLAYER_MAXIMUM_LEVEL);
            writeLowByte(space, GAME_DATA + EXPANSION_BYTE, TWO_LOW_BITS);
            building.setUpgradeType(upgrade);
            building.setUpgradeLevel(TARGET_LEVEL);
            building.setUpgradeResearchTime(0);

            game.getUnitResearch().updateUpgrade(building, INSTRUCTION_LIMIT);

            Assertions.assertEquals(TARGET_LEVEL, space.readDword(GAME_DATA + LEGACY_UPGRADING) & BYTE_MASK,
                                    "The level of player 12 is the first legacy upgrading byte of player 0.");
            Assertions.assertEquals(SECOND_BIT, space.readDword(GAME_DATA + EXPANSION_BYTE) & BYTE_MASK,
                                    "Clearing the upgrading bit of player 12 clears the lowest bit of the expansion BYTE.");
        }
    }

    @Test
    void owner_written_past_the_players_completes_its_research_on_the_game_context_bytes_its_rows_address() throws Exception {
        try (GameData installedData = GameData.loadFrom(ExternalInput.INSTALLATION.getPath())) {
            ReplayRuntime runtime = createReplayRuntime(installedData);

            runUntilFrame(runtime, SETUP_FRAME);

            Game game = runtime.getGame();
            ClassicAddressSpace space = ClassicAddressSpace.create(game);
            PlayerUpgrades upgrades = game.getPlayerTable().getPlayerUpgrades();
            Unit building = findUnit(game, ScrUnitIds.TERRAN_COMMAND_CENTER);

            Assertions.assertNotNull(upgrades);

            writeOwner(space, building, PAST_PLAYER);
            writeLowByte(space, GAME_DATA + EXPANSION_MAXIMUM_LEVELS, TWO_LOW_BITS);
            building.setResearchType(ScrTechIds.STIM_PACKS);
            building.setUpgradeResearchTime(0);

            game.getUnitResearch().updateResearch(building, INSTRUCTION_LIMIT);

            Assertions.assertEquals(RESEARCHED, space.readDword(GAME_DATA + LEGACY_RESEARCHING) & BYTE_MASK,
                                    "The researched byte of player 12 is the first legacy researching byte of player 0.");
            Assertions.assertEquals(SECOND_BIT, upgrades.getMaximumLevel(FIRST_PLAYER, FIRST_EXPANSION_UPGRADE),
                                    "Clearing the researching bit of player 12 clears the lowest bit of an expansion maximum level of player 0.");
        }
    }
}
