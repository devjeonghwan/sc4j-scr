package org.fogofwarlabs.sc4j.scr.client.world;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.base.log.Logger;
import org.fogofwarlabs.sc4j.scr.content.ScrAddedGraphics;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrResourceProvider;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.render.ClassicSceneRenderer;
import org.fogofwarlabs.sc4j.scr.render.WorldRenderer;
import org.fogofwarlabs.sc4j.scr.render.hd.HdImageSet;
import org.fogofwarlabs.sc4j.scr.render.hd.HdRenderAssets;
import org.fogofwarlabs.sc4j.scr.render.hd.HdSceneRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * Holds the SC:R skin of the world pass: the selected graphics (classic SD, or an HD quality whose image set carries the HD or Carbot skin) and
 * the scene backends that draw it, the classic backend for the renderer in use and the HD backend of the loaded map. The selection reaches the
 * engine world view only through the SC:R world layers of {@link ScrWorldPass}.
 */
public final class WorldSkin {
    private static final @NotNull Logger LOGGER = Logger.of(WorldSkin.class);

    /**
     * The upload time budget for each synchronous high-definition scene preload call.
     */
    private static final long HIGH_DEFINITION_PRELOAD_BUDGET_NANOSECONDS = Long.MAX_VALUE / 4;
    /**
     * Nanoseconds in one host millisecond.
     */
    private static final int NANOSECONDS_PER_MILLISECOND = 1_000_000;

    private HdRenderAssets.@Nullable Quality quality;
    private int lightingDebugView;

    private @Nullable ClassicSceneRenderer classicScene;
    private @Nullable HdSceneRenderer hdScene;

    /**
     * Creates a {@link WorldSkin} instance with the given graphics and no scene backend yet.
     *
     * @param quality the HD quality, or {@code null} for the classic graphics
     */
    public WorldSkin(HdRenderAssets.@Nullable Quality quality) {
        this.quality           = quality;
        this.lightingDebugView = 0;

        this.classicScene = null;
        this.hdScene      = null;
    }

    /**
     * @return the selected HD quality, or {@code null} for the classic graphics
     */
    public HdRenderAssets.@Nullable Quality getQuality() {
        return this.quality;
    }

    /**
     * Sets the selected graphics and closes the HD scene backend of the previous selection.
     *
     * @param quality the HD quality, or {@code null} for the classic graphics
     */
    public void setQuality(HdRenderAssets.@Nullable Quality quality) {
        this.closeHdScene();
        this.quality = quality;
    }

    /**
     * @return the selected HD lighting debug view index, or {@code -1} for none
     */
    public int getLightingDebugView() {
        return this.lightingDebugView;
    }

    /**
     * Sets the HD lighting debug view index; an open HD scene backend shows it at once.
     *
     * @param view the view index, or {@code -1} for none
     */
    public void setLightingDebugView(int view) {
        this.lightingDebugView = view;

        if (this.hdScene != null) {
            this.hdScene.setLightingDebugView(view);
        }
    }

    /**
     * @return the open HD scene backend, or {@code null} when none is open
     */
    public @Nullable HdSceneRenderer getHdScene() {
        return this.hdScene;
    }

    /**
     * @return {@code true} if an HD quality is selected and its scene backend is not open yet, {@code false} otherwise
     */
    public boolean isHdScenePending() {
        return this.quality != null && this.hdScene == null;
    }


    /**
     * Returns the classic scene backend for the renderer, creating it again for the loaded map when the renderer changed.
     *
     * @param renderer      the world renderer in use
     * @param storage       the game data storage the backend reads its images from
     * @param addedGraphics the added graphics of the game, whose images the backend reads from the mod files
     * @param tileset       the loaded map's tileset
     * @return the classic scene backend
     */
    public @NotNull ClassicSceneRenderer prepareClassicScene(@NotNull WorldRenderer renderer, @NotNull CascStorage storage,
                                                             @NotNull ScrAddedGraphics addedGraphics, int tileset) {
        if (this.classicScene == null || !this.classicScene.isDrawingWith(renderer)) {
            this.closeClassicScene();
            this.classicScene = new ClassicSceneRenderer(renderer, ScrResourceProvider.borrow(storage, addedGraphics), tileset);
        }

        return this.classicScene;
    }

    /**
     * Opens the HD scene backend of the selected quality for the loaded map and preloads its terrain in one step.
     *
     * @param renderer      the world renderer in use
     * @param tilesetAssets the HD assets of the map's tileset
     * @param images        the HD image set of the selected quality
     * @param map           the loaded map
     * @param lighting      whether HD lighting is on
     * @param streaming     whether images missing from the preload stream in; a screenshot run waits for every image instead
     * @throws IOException if the HD backend cannot read its assets
     */
    public void openHdScene(@NotNull WorldRenderer renderer, @NotNull HdRenderAssets tilesetAssets, @NotNull HdImageSet images,
                            @NotNull GameMap map, boolean lighting, boolean streaming) throws IOException {
        HdSceneRenderer scene = new HdSceneRenderer(renderer, tilesetAssets, images);

        try {
            scene.setLightingEnabled(lighting);
            scene.setLightingDebugView(Math.max(this.lightingDebugView, 0));
            scene.setStreaming(streaming);

            scene.startPreload(map::getTileIdentifier);

            long started = System.nanoTime();

            while (scene.isPreloading()) {
                scene.continuePreload(HIGH_DEFINITION_PRELOAD_BUDGET_NANOSECONDS);
            }

            LOGGER.debug("HD TERRAIN: " + (System.nanoTime() - started) / NANOSECONDS_PER_MILLISECOND + " ms");
        } catch (RuntimeException exception) {
            scene.close();

            throw exception;
        }

        this.hdScene = scene;
    }

    /**
     * Returns the open HD scene backend with the frame's graphics settings applied.
     *
     * @param smoothTurning      whether units turn smoothly
     * @param environmentEffects whether the environment effects are drawn
     * @return the HD scene backend
     * @throws IllegalStateException if no HD scene backend is open
     */
    public @NotNull HdSceneRenderer prepareHdScene(boolean smoothTurning, boolean environmentEffects) {
        HdSceneRenderer scene = this.hdScene;

        if (scene == null) {
            throw Exceptions.newIllegalStateException("No HD scene is open.");
        }

        scene.setSmoothTurning(smoothTurning);
        scene.setEnvironmentEffects(environmentEffects);

        return scene;
    }

    /**
     * Closes the HD scene backend, if one is open.
     */
    public void closeHdScene() {
        if (this.hdScene != null) {
            this.hdScene.close();
            this.hdScene = null;
        }
    }

    /**
     * Closes the classic scene backend, if one is open.
     */
    public void closeClassicScene() {
        if (this.classicScene != null) {
            this.classicScene.close();
            this.classicScene = null;
        }
    }
}
