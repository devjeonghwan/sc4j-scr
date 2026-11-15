package org.fogofwarlabs.sc4j.scr.game.frame;

import org.fogofwarlabs.sc4j.scr.game.combat.ProjectilePhase;
import org.fogofwarlabs.sc4j.scr.game.movement.UnitMovement;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteEffectPool;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.visibility.MapVisibility;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Creates the frame state of the small test worlds that the combat and resource tests build on the first tileset.
 */
public final class GameFrameScenario {
    private static final int TILESET_INDEX = 0;
    private static final int SPRITE_EFFECT_CAPACITY = 1;
    private static final int VISIBILITY_CONTROL_COUNT = 5;

    /**
     * Creates a {@link GameFrame} instance with the initial redistribution timers, a cleared movement context, empty projectile lists, a
     * one-entry sprite effect pool and cleared map visibility controls on the first tileset.
     *
     * @param gameData the installed data that holds the tileset
     * @return the created {@link GameFrame} instance
     * @throws IOException if the tileset cannot be read
     */
    public static @NotNull GameFrame createFrame(@NotNull GameData gameData) throws IOException {
        ProjectilePhase.Context projectiles = new ProjectilePhase.Context();

        projectiles.initializeEmptyLists();

        return new GameFrame(new UnitPhase.Context(UnitPhase.INITIAL_PRIMARY_REDISTRIBUTION_TIMER, UnitPhase.INITIAL_SECONDARY_REDISTRIBUTION_TIMER),
                             new UnitMovement.Context(0, 0),
                             projectiles,
                             new SpriteEffectPool(SPRITE_EFFECT_CAPACITY),
                             new MapVisibility(gameData.getTerrainTileset(TILESET_INDEX), new int[VISIBILITY_CONTROL_COUNT]));
    }


    /**
     * Creates a {@link GameFrameScenario} instance.
     */
    private GameFrameScenario() {
    }
}
