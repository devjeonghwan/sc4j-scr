package org.fogofwarlabs.sc4j.scr.game.state;

import org.fogofwarlabs.sc4j.scr.core.LcgRandom;
import org.fogofwarlabs.sc4j.scr.game.frame.GameFrame;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTypes;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.visibility.MapVisibility;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks reveal reset and requirement cheat flag storage.
 */
final class GameCheatsRevealTest {
    private static final int MAP_WIDTH = 8;
    private static final int MAP_HEIGHT = 8;
    private static final int TILE_COUNT = MAP_WIDTH * MAP_HEIGHT;
    private static final int CHECKED_TILE = 5;
    private static final int TILE_VISIBILITY_MASK = 0xFF;
    private static final int TILE_EXPLORED_MASK = 0xFF00;
    private static final int TILE_VISIBILITY_AND_EXPLORED_MASK = 0xFFFF;
    /**
     * The visibility bits of a tile that no player sees.
     */
    private static final int HIDDEN_TILE_VISIBILITY = 0xFF;
    /**
     * The visibility cheat flags of the reveal cheat and of the two requirement cheats.
     */
    private static final int REVEAL_CHEAT_FLAGS = 0x1;
    private static final int REQUIREMENT_CHEAT_FLAGS = 0x3000;

    private static final int HUMAN_PLAYER = 0;

    /**
     * Creates a {@link Game} instance with the frame controls required by these cheat checks.
     *
     * @return the created {@link Game} instance
     * @throws IOException if the fixture terrain records cannot be parsed
     */
    private static @NotNull Game createGame() throws IOException {
        Game game = Game.create(null, new GameMap(MAP_WIDTH, MAP_HEIGHT, 0, new int[TILE_COUNT], new int[TILE_COUNT]), new LcgRandom(1));

        game.setCheatsEnabled(1);
        game.getPlayerTable().setPlayer(HUMAN_PLAYER, new Player(HUMAN_PLAYER, HUMAN_PLAYER, PlayerTypes.HUMAN, 0, 0));
        game.setFrame(GameCheatsScenario.createFrame(0));

        return game;
    }


    @Test
    void black_sheep_wall_reveals_and_explores_every_tile_until_cleared() throws IOException {
        Game game = createGame();
        GameMap map = game.getMap();
        GameFrame activeFrame = game.getFrame();

        Assertions.assertNotNull(activeFrame);

        MapVisibility visibility = activeFrame.getVisibility();

        visibility.resetVisibility(map);

        Assertions.assertEquals(HIDDEN_TILE_VISIBILITY, map.getTileFlags(CHECKED_TILE) & TILE_VISIBILITY_MASK);

        game.getGameCheats().apply(GameCheats.BLACK_SHEEP_WALL);

        Assertions.assertEquals(0, map.getTileFlags(CHECKED_TILE) & TILE_VISIBILITY_AND_EXPLORED_MASK);
        Assertions.assertEquals(REVEAL_CHEAT_FLAGS, visibility.getCheatFlags());

        visibility.resetVisibility(map);

        Assertions.assertEquals(0, map.getTileFlags(CHECKED_TILE) & TILE_VISIBILITY_MASK);

        game.getGameCheats().apply(0);
        visibility.resetVisibility(map);

        Assertions.assertEquals(HIDDEN_TILE_VISIBILITY, map.getTileFlags(CHECKED_TILE) & TILE_VISIBILITY_MASK);
        Assertions.assertEquals(0, map.getTileFlags(CHECKED_TILE) & TILE_EXPLORED_MASK);
        Assertions.assertEquals(0, game.getRandom().getTotalRandomCounts());
    }

    @Test
    void requirement_cheats_store_their_bits_in_visibility_cheat_flags() throws IOException {
        Game game = createGame();

        game.getGameCheats().apply(GameCheats.MEDIEVAL_MAN | GameCheats.MODIFY_THE_PHASE_VARIANCE);

        GameFrame activeFrame = game.getFrame();

        Assertions.assertNotNull(activeFrame);

        Assertions.assertEquals(REQUIREMENT_CHEAT_FLAGS, activeFrame.getVisibility().getCheatFlags());
    }
}
