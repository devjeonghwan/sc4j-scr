package org.fogofwarlabs.sc4j.scr.client;

import org.fogofwarlabs.sc4j.base.log.Logger;
import org.fogofwarlabs.sc4j.engine.client.render.WorldLayer;
import org.fogofwarlabs.sc4j.engine.client.render.WorldViewport;
import org.fogofwarlabs.sc4j.scr.client.world.ScrWorldPass;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkUnit;
import org.fogofwarlabs.sc4j.scr.game.presentation.ImageScriptPreview;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.render.SceneRenderer;
import org.fogofwarlabs.sc4j.scr.render.WorldRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Draws the map placements of a preview (no playback) over the SC:R world: the selected placement with its inspected frame or image preview,
 * the others in their first frame. A unit type whose images cannot load is skipped from then on with an asset warning.
 */
final class PlacementPreviewLayer implements WorldLayer {
    private static final @NotNull Logger LOGGER = Logger.of(PlacementPreviewLayer.class);

    private final @NotNull Supplier<@Nullable ClientSession> sessions;
    private final @NotNull UnitInspection inspection;
    private final @NotNull ScrWorldPass.Context context;

    private final @NotNull Set<Integer> unavailableUnitTypes;

    private @Nullable ScrWorldPass pass;

    /**
     * Creates a {@link PlacementPreviewLayer} instance.
     *
     * @param sessions   the source of the running session
     * @param inspection the inspection of the selected placement
     * @param context    the world pass context that gives the player colours and takes the warnings
     */
    PlacementPreviewLayer(@NotNull Supplier<@Nullable ClientSession> sessions, @NotNull UnitInspection inspection,
                          @NotNull ScrWorldPass.Context context) {
        this.sessions   = sessions;
        this.inspection = inspection;
        this.context    = context;

        this.unavailableUnitTypes = new HashSet<>();

        this.pass = null;
    }

    /**
     * @return the unit types whose images could not load since the last {@link #clearUnavailableUnitTypes}
     */
    @NotNull Set<Integer> getUnavailableUnitTypes() {
        return this.unavailableUnitTypes;
    }

    /**
     * Sets the world pass this layer draws in.
     *
     * @param pass the world pass
     */
    void setPass(@NotNull ScrWorldPass pass) {
        this.pass = pass;
    }


    /**
     * Forgets the unit types whose images could not load, for a new game.
     */
    void clearUnavailableUnitTypes() {
        this.unavailableUnitTypes.clear();
    }

    /**
     * Draws the layer for a frame.
     *
     * @param viewport the frame's viewport
     */
    @Override
    public void draw(@NotNull WorldViewport viewport) {
        ClientSession session = this.sessions.get();
        ScrWorldPass current = this.pass;

        if (session == null || current == null) {
            return;
        }

        WorldRenderer renderer = current.requireRenderer();
        ChkUnit selectedUnit = session.getSelectedPlacement();

        for (ChkUnit unit : session.isPlaybackRequested() ? List.<ChkUnit>of() : session.getPlacements()) {
            if (unit.getUnitId() >= session.getView().requireGameData().getUnitTypes().getCount()) {
                continue;
            }

            if (!this.unavailableUnitTypes.contains(unit.getUnitId())) {
                try {
                    ImageScriptPreview preview = this.inspection.getPreview();

                    if (unit == selectedUnit && preview != null) {
                        Sprite sprite = preview.getSprite();

                        for (int imageIndex = sprite.getImages().size() - 1; imageIndex >= 0; imageIndex--) {
                            SpriteImage image = sprite.getImages().get(imageIndex);

                            if ((image.getFlags() & SpriteImage.FLAG_HIDDEN) != 0) {
                                continue;
                            }

                            if (image.getModifier() == SpriteImage.MODIFIER_SHADOW) {
                                renderer.drawShadowImage(image.getImageTypeId(), image.getDrawnFrameIndex(),
                                                         sprite.getX() + image.getOffsetX(), sprite.getY() + image.getOffsetY(),
                                                         (image.getFlags() & SpriteImage.FLAG_HORIZONTALLY_FLIPPED) != 0);
                            } else if (image.getModifier() == SceneRenderer.DRAW_FUNCTION_NORMAL) {
                                renderer.drawImage(image.getImageTypeId(), image.getDrawnFrameIndex(), this.context.getPlayerColor(unit.getOwner()),
                                                   sprite.getX() + image.getOffsetX(), sprite.getY() + image.getOffsetY(),
                                                   (image.getFlags() & SpriteImage.FLAG_HORIZONTALLY_FLIPPED) != 0);
                            }
                        }
                    } else if (unit == selectedUnit) {
                        if (this.inspection.getFrameCount() == 0) {
                            this.inspection.setFrameCount(renderer.getUnitFrameCount(unit.getUnitId()));
                        }

                        renderer.drawUnit(unit.getUnitId(), this.inspection.getFrame(), this.context.getPlayerColor(unit.getOwner()),
                                          unit.getX(), unit.getY(), this.inspection.isFlipped());
                    } else {
                        renderer.drawUnit(unit.getUnitId(), this.context.getPlayerColor(unit.getOwner()), unit.getX(), unit.getY());
                    }
                } catch (IOException exception) {
                    this.unavailableUnitTypes.add(unit.getUnitId());
                    this.context.setAssetWarning("Some unit images could not load; missing types: " + this.unavailableUnitTypes.size());
                    LOGGER.warning("UNIT IMAGE " + unit.getUnitId() + ": " + exception.getMessage());
                }
            }
        }
    }
}
