package org.fogofwarlabs.sc4j.scr.game.state;

import org.fogofwarlabs.sc4j.scr.data.format.terrain.TerrainTileset;
import org.fogofwarlabs.sc4j.scr.game.combat.ProjectilePhase;
import org.fogofwarlabs.sc4j.scr.game.frame.GameFrame;
import org.fogofwarlabs.sc4j.scr.game.frame.UnitPhase;
import org.fogofwarlabs.sc4j.scr.game.movement.UnitMovement;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteEffectPool;
import org.fogofwarlabs.sc4j.scr.game.visibility.MapVisibility;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Creates the frame controls of the small games that the cheat tests run cheats in.
 */
final class GameCheatsScenario {
    /**
     * The tile groups of the empty fixture tileset.
     */
    private static final int TERRAIN_GROUP_COUNT = 3;
    /**
     * The bytes of one fixture megatile: its minitile words.
     */
    private static final int TERRAIN_MEGATILE_BYTES = TerrainTileset.MINI_TILES_PER_ROW * TerrainTileset.MINI_TILES_PER_ROW * Short.BYTES;
    /**
     * The slots of the sprite effect pool.
     */
    private static final int SPRITE_EFFECT_CAPACITY = 1;

    /**
     * Creates a {@link GameFrame} instance with initial unit phase timers, a stationary movement context, an empty projectile phase, a one-slot
     * sprite effect pool and the map visibility of an empty fixture tileset.
     *
     * @param visibilityMode the visibility-override mode of the map visibility
     * @return the created {@link GameFrame} instance
     * @throws IOException if the fixture terrain records cannot be parsed
     */
    static @NotNull GameFrame createFrame(int visibilityMode) throws IOException {
        return new GameFrame(new UnitPhase.Context(UnitPhase.INITIAL_PRIMARY_REDISTRIBUTION_TIMER, UnitPhase.INITIAL_SECONDARY_REDISTRIBUTION_TIMER),
                             new UnitMovement.Context(0, 0),
                             new ProjectilePhase.Context(),
                             new SpriteEffectPool(SPRITE_EFFECT_CAPACITY),
                             new MapVisibility(TerrainTileset.parse(new byte[TERRAIN_GROUP_COUNT * TerrainTileset.CV5_RECORD_SIZE],
                                                                    new byte[TERRAIN_MEGATILE_BYTES]),
                                               new int[]{visibilityMode, 0, 0, 0, 0}));
    }


    /**
     * Creates a {@link GameCheatsScenario} instance.
     */
    private GameCheatsScenario() {
    }
}
