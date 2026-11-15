package org.fogofwarlabs.sc4j.scr.game.frame;

import org.fogofwarlabs.sc4j.scr.game.ai.ScrComputerPlayerSystem;
import org.fogofwarlabs.sc4j.scr.game.combat.ProjectilePhase;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.visibility.MapVisibility;
import org.jetbrains.annotations.NotNull;

/**
 * The world frame phases of the SC:R systems: the computer players, map visibility, units, bullets, flingies and sprite effects. The game
 * places them in its {@link FramePhaseList} together with the phases of its rules; the world step of the engine frame runs that list.
 */
public enum ScrFramePhase implements FramePhase {
    /**
     * Runs the computer step of the game's computer player system ({@link ScrComputerPlayerSystem#update}) with this frame.
     */
    COMPUTER_PLAYERS {
        /**
         * Checks whether the game has what this phase needs before a frame starts: the computer player phase of its computer player
         * system ({@link ScrComputerPlayerSystem#isReady}). A frame whose phases are not all ready fails before any phase runs.
         *
         * @param game the game
         * @return {@code true} if the phase can run, {@code false} otherwise
         */
        @Override
        public boolean isReady(@NotNull Game game) {
            return game.getComputerPlayers().isReady();
        }


        /**
         * Runs this phase of the current frame.
         *
         * @param game  the game
         * @param frame the frame, with this frame's inputs
         */
        @Override
        public void update(@NotNull Game game, @NotNull GameFrame frame) {
            game.getComputerPlayers().update(frame);
        }
    },
    /**
     * Counts the tile update interval down; every hundredth frame the movement source byte is set, the visibility graphics cache is rebuilt and the
     * tile visibility is reset.
     */
    VISIBILITY {
        /**
         * Runs this phase of the current frame.
         *
         * @param game  the game
         * @param frame the frame, with this frame's inputs
         */
        @Override
        public void update(@NotNull Game game, @NotNull GameFrame frame) {
            int[] viewport = frame.getViewport();
            MapVisibility visibility = frame.getVisibility();

            if (game.getUpdateTilesCountdown() == 0) {
                game.setUpdateTilesCountdown(TILE_UPDATE_INTERVAL);
            }

            game.setUpdateTilesCountdown(game.getUpdateTilesCountdown() - 1);

            boolean updateTiles = game.getUpdateTilesCountdown() == 0;

            frame.getMovement().setSourceByte(updateTiles ? TILE_UPDATE_SOURCE : NO_TILE_UPDATE_SOURCE);

            if (updateTiles) {
                visibility.updateGraphicsCache(game,
                                               viewport[GameFrame.VIEWPORT_HORIZONTAL], viewport[GameFrame.VIEWPORT_VERTICAL],
                                               viewport[GameFrame.VIEWPORT_WIDTH], viewport[GameFrame.VIEWPORT_HEIGHT]);

                visibility.resetVisibility(game.getMap());
            }
        }
    },
    /**
     * Updates the units: dead units, movement, sprite visibility and detection, orders and building power.
     */
    UNITS {
        /**
         * Runs this phase of the current frame.
         *
         * @param game  the game
         * @param frame the frame, with this frame's inputs
         */
        @Override
        public void update(@NotNull Game game, @NotNull GameFrame frame) {
            game.getUnitPhase().update(frame.getUnits(),
                                       frame.getMovement(),
                                       frame.getOwnerPlayerTypes(),
                                       frame.isBuildingRefreshEnabled(), frame.isStatusRefreshEnabled(), frame.getBuildingPowerRefresh() != 0,
                                       frame.getElapsedHostTicks(),
                                       frame.getInstructionLimit());
        }
    },
    /**
     * Updates the bullets.
     */
    BULLETS {
        /**
         * Runs this phase of the current frame.
         *
         * @param game  the game
         * @param frame the frame, with this frame's inputs
         */
        @Override
        public void update(@NotNull Game game, @NotNull GameFrame frame) {
            game.getProjectilePhase().updateBullets(frame.getProjectiles(), frame.getInstructionLimit());
        }
    },
    /**
     * Updates the flingies of the projectile context.
     */
    FLINGIES {
        /**
         * Runs this phase of the current frame.
         *
         * @param game  the game
         * @param frame the frame, with this frame's inputs
         */
        @Override
        public void update(@NotNull Game game, @NotNull GameFrame frame) {
            ProjectilePhase.updateFlingies(frame.getProjectiles());
        }
    },
    /**
     * Runs the sprite effects (thingies) of the secondary pool.
     */
    SPRITE_EFFECTS {
        /**
         * Runs this phase of the current frame.
         *
         * @param game  the game
         * @param frame the frame, with this frame's inputs
         */
        @Override
        public void update(@NotNull Game game, @NotNull GameFrame frame) {
            game.getIscriptExecutor().updateSpriteEffects(frame.getSecondarySpriteEffects(), frame.getInstructionLimit());
        }
    };

    /**
     * The number of frames between two tile visibility updates.
     */
    private static final int TILE_UPDATE_INTERVAL = 100;
    /**
     * The movement source byte of a frame that updates the tile visibility.
     */
    private static final int TILE_UPDATE_SOURCE = 1;
    /**
     * The movement source byte of a frame without a tile visibility update.
     */
    private static final int NO_TILE_UPDATE_SOURCE = 0;
}
