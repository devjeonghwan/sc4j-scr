package org.fogofwarlabs.sc4j.scr.client.configuration;

import org.fogofwarlabs.sc4j.scr.render.hd.HdRenderAssets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The graphics choices of {@code /graphics}, the set F5 cycles: classic, HD2 and HD.
 */
public enum GraphicsChoice {
    /**
     * The classic (SD) graphics.
     */
    CLASSIC(null, "the classic (SD) graphics"),
    /**
     * The remastered graphics at half resolution.
     */
    HD2(HdRenderAssets.Quality.HD2, "the remastered graphics at half resolution"),
    /**
     * The remastered graphics at full resolution.
     */
    HD(HdRenderAssets.Quality.HD, "the remastered graphics at full resolution");

    private final HdRenderAssets.@Nullable Quality quality;
    private final @NotNull String description;

    /**
     * Creates a {@link GraphicsChoice} instance.
     *
     * @param quality     the asset quality, or {@code null} for classic graphics
     * @param description the displayed choice description
     */
    GraphicsChoice(HdRenderAssets.@Nullable Quality quality, @NotNull String description) {
        this.quality     = quality;
        this.description = description;
    }

    /**
     * @return the HD quality, or {@code null} for the classic graphics
     */
    public HdRenderAssets.@Nullable Quality getQuality() {
        return this.quality;
    }

    /**
     * @return the choice's name in commands, for example {@code hd2}
     */
    public @NotNull String getName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    /**
     * @return what the choice shows, for the command help
     */
    public @NotNull String getDescription() {
        return this.description;
    }
}
