package org.fogofwarlabs.sc4j.scr.game.trigger.eud;

import org.fogofwarlabs.sc4j.scr.content.ScrTechIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTechnology;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerUpgrades;
import org.fogofwarlabs.sc4j.scr.game.player.StartLocation;
import org.fogofwarlabs.sc4j.scr.game.session.setup.LobbyResolution;
import org.fogofwarlabs.sc4j.scr.game.state.ContextExpansion;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.PlayerProgressTestUtils;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.region.ClassicContextTailRegion;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Checks context writes used by lobby colors, frames, expansion, start locations and the local relation table.
 */
final class EudGameContextWritesTest {
    private static final int TAIL = 0x58F44C;
    private static final int LOBBY_COLORS = 0x58F47A;
    /**
     * The game context offset of the local relation table.
     */
    private static final int LOCAL_RELATIONS = 0x2C7A;
    /**
     * A player whose relation an EUD write changes.
     */
    private static final int WRITTEN_PLAYER = 3;
    /**
     * The game context offset of the frame counter DWORD.
     */
    private static final int FRAME_COUNTER = 0x14C;
    /**
     * The frame the frame counter test starts at.
     */
    private static final int INITIAL_FRAME = 100;
    /**
     * The frame the frame counter test writes.
     */
    private static final int WRITTEN_FRAME = 5000;
    /**
     * The game context offset of the pause counts.
     */
    private static final int PAUSES = 0xE628;
    /**
     * The game context offset of the start locations.
     */
    private static final int START_LOCATIONS = 0xE630;
    /**
     * The game context offset of the expansion BYTE.
     */
    private static final int EXPANSION = 0x10350;
    private static final int MAP_DIMENSION = 8;
    /**
     * The player that a replay colors as allied: the neutral player.
     */
    private static final int NEUTRAL_PLAYER = 11;
    /**
     * The selection circle color of an own unit.
     */
    private static final int OWN_COLOR = 0;
    /**
     * The selection circle color of an allied unit.
     */
    private static final int ALLIED_COLOR = 1;
    /**
     * The selection circle color of an enemy unit.
     */
    private static final int ENEMY_COLOR = 2;
    private static final int BYTE_MASK = 0xFF;

    /**
     * Creates a {@link Game} instance without game data on an empty map.
     *
     * @return the created {@link Game} instance
     */
    private static @NotNull Game createWorld() {
        GameMap map = new GameMap(MAP_DIMENSION, MAP_DIMENSION, 0, new int[MAP_DIMENSION * MAP_DIMENSION], new int[MAP_DIMENSION * MAP_DIMENSION]);

        return Game.create(null, map, new LcgRandom(1));
    }

    /**
     * Combines four stored bytes into a DWORD.
     *
     * @param fields the game-context fields
     * @param offset the first byte offset
     * @return the combined DWORD
     */
    private static int readDword(@NotNull GameContextFields fields, int offset) {
        int value = 0;

        for (int index = 0; index < Integer.BYTES; index++) {
            value |= fields.readByte(offset + index) << index * Byte.SIZE;
        }

        return value;
    }

    /**
     * Writes a DWORD through its four bytes in order.
     *
     * @param fields the game-context fields
     * @param offset the first byte offset
     * @param value  the DWORD value
     */
    private static void writeDword(@NotNull GameContextFields fields, int offset, int value) {
        for (int index = 0; index < Integer.BYTES; index++) {
            fields.writeByte(offset + index, value >>> index * Byte.SIZE);
        }
    }


    @Test
    void context_tail_stores_writes_and_serves_the_resolved_lobby_colors() {
        Game world = createWorld();
        ClassicAddressSpace space = new ClassicAddressSpace();

        space.add(new ClassicContextTailRegion(world));

        Assertions.assertEquals(0x16161616, space.readDword(LOBBY_COLORS + 2), "Twelve slots without a colour.");
        Assertions.assertEquals(0, space.readDword(TAIL));

        space.writeDword(TAIL + 0x100, 0x12345678);

        Assertions.assertEquals(0x12345678, space.readDword(TAIL + 0x100), "The tail keeps what EUD writes.");

        space.writeDword(TAIL + 0x73A0, -1);

        Assertions.assertEquals(-1, space.readDword(TAIL + 0x73A0));

        byte[] colors = {3, 7, 4, 19, 18, 2, 12, 0,
                         LobbyResolution.COLOR_NONE, LobbyResolution.COLOR_NONE,
                         LobbyResolution.COLOR_NONE, LobbyResolution.COLOR_NONE};

        world.getGameContext().setContextLobbyColors(colors);

        Assertions.assertEquals(3 | 7 << 8 | 4 << 16 | 19 << 24, space.readDword(LOBBY_COLORS));

        space.writeDword(LOBBY_COLORS, 0x01020304);

        Assertions.assertEquals(4, world.getGameContext().getContextLobbyColors()[0], "An EUD write stores into the game's lobby colour bytes.");
    }

    @Test
    void local_relations_are_the_circle_colors_of_the_selection_images() {
        Game world = createWorld();

        world.setReplayPlayback(true);
        world.getSelectionImages().updateLocalRelations();

        GameContextFields fields = new GameContextFields(world, 0);

        for (int player = 0; player < PlayerTable.PLAYER_SLOTS; player++) {
            int expected = player == NEUTRAL_PLAYER ? ALLIED_COLOR : OWN_COLOR;

            Assertions.assertEquals(expected, fields.readByte(LOCAL_RELATIONS + player), "A replay colors player " + player + ".");
        }

        fields.writeByte(LOCAL_RELATIONS + WRITTEN_PLAYER, ENEMY_COLOR);

        Assertions.assertEquals(ENEMY_COLOR, world.getSelectionImages().getCircleColor(WRITTEN_PLAYER),
                                "An EUD write recolors the player's circles.");
    }

    @Test
    void frame_counter_is_stored_and_counts_on_from_the_written_value() {
        Game world = createWorld();

        world.setCurrentFrame(INITIAL_FRAME);

        GameContextFields fields = new GameContextFields(world, 0);

        Assertions.assertEquals(INITIAL_FRAME, readDword(fields, FRAME_COUNTER));

        writeDword(fields, FRAME_COUNTER, WRITTEN_FRAME);

        Assertions.assertEquals(WRITTEN_FRAME, world.getCurrentFrame(), "0x57F23C is the game's frame counter.");

        fields.writeByte(FRAME_COUNTER + 1, 0);

        Assertions.assertEquals(WRITTEN_FRAME & BYTE_MASK, world.getCurrentFrame(), "A byte write changes one byte of the DWORD.");

        world.setCurrentFrame(world.getCurrentFrame() + 1);

        Assertions.assertEquals((WRITTEN_FRAME & BYTE_MASK) + 1, readDword(fields, FRAME_COUNTER), "The game increments the stored counter.");
    }

    @Test
    void expansion_byte_gates_expansion_upgrades_and_technologies() {
        Game world = createWorld();
        ContextExpansion expansion = new ContextExpansion(true);
        PlayerUpgrades upgrades = PlayerProgressTestUtils.createPlayerUpgrades(expansion, null);
        PlayerTechnology technology = PlayerProgressTestUtils.createPlayerTechnology(expansion, null);

        world.getPlayerTable().setPlayerUpgrades(upgrades);
        world.getPlayerTable().setPlayerTechnology(technology);

        GameContextFields fields = new GameContextFields(world, 0);

        Assertions.assertEquals(1, fields.readByte(EXPANSION));

        fields.writeByte(EXPANSION, 0);

        Assertions.assertFalse(upgrades.isExpansionEnabled());

        upgrades.setLevel(0, ScrUpgradeIds.UNKNOWN_50, 1);

        Assertions.assertEquals(0, upgrades.getLevel(0, ScrUpgradeIds.UNKNOWN_50), "An expansion upgrade is ignored while the BYTE is zero.");

        fields.writeByte(EXPANSION, 5);

        Assertions.assertEquals(5, fields.readByte(EXPANSION), "Any non-zero BYTE is stored and read back.");
        Assertions.assertTrue(upgrades.isExpansionEnabled());

        upgrades.setLevel(0, ScrUpgradeIds.UNKNOWN_50, 1);

        Assertions.assertEquals(1, upgrades.getLevel(0, ScrUpgradeIds.UNKNOWN_50));

        technology.setResearched(0, ScrTechIds.OPTICAL_FLARE, true);

        Assertions.assertTrue(technology.isResearched(0, ScrTechIds.OPTICAL_FLARE), "The technology store shares the BYTE.");
    }

    @Test
    void pauses_and_start_locations_are_stored_in_the_game() {
        Game world = createWorld();

        for (int player = 0; player < PlayerTable.PLAYABLE_PLAYER_SLOTS; player++) {
            world.getPlayerTable().getStartLocations().add(new StartLocation(player, 100 + player, 200 + player));
        }

        GameContextFields fields = new GameContextFields(world, 0);

        Assertions.assertEquals(0x03030303, readDword(fields, PAUSES), "Every player starts with three pauses.");

        fields.writeByte(PAUSES + 2, 9);

        Assertions.assertEquals(9, world.getGameContext().getContextPauses()[2], "The pause test reads what EUD writes.");

        Assertions.assertEquals(103 | 203 << 16, readDword(fields, START_LOCATIONS + 3 * Integer.BYTES));

        writeDword(fields, START_LOCATIONS + 3 * Integer.BYTES, 640 | 480 << 16);

        StartLocation start = world.getPlayerTable().getStartLocations().get(3);

        Assertions.assertEquals(3, start.getOwner());
        Assertions.assertEquals(640, start.getX(), "The computer town selection reads the written start location.");
        Assertions.assertEquals(480, start.getY());
    }
}
