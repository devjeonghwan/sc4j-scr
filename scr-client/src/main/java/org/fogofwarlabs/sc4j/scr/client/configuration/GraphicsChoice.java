package org.fogofwarlabs.sc4j.scr.client.configuration;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.render.hd.HdRenderAssets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The graphics choices of {@code /graphics} and the settings file's {@code graphics.renderer}, the set F5 cycles in this order: classic, HD2
 * and HD. HD is the default.
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
     * Returns the choice of an HD quality.
     *
     * @param quality the HD quality, or {@code null} for the classic graphics
     * @return the choice that shows the quality
     */
    public static @NotNull GraphicsChoice of(HdRenderAssets.@Nullable Quality quality) {
        for (GraphicsChoice choice : values()) {
            if (choice.quality == quality) {
                return choice;
            }
        }

        throw Exceptions.newIllegalStateException("No graphics choice shows {0}.", quality);
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
     * @return the choice F5 selects after this one: classic, HD2, HD, then classic again
     */
    public @NotNull GraphicsChoice getNext() {
        GraphicsChoice[] choices = values();

        return choices[(this.ordinal() + 1) % choices.length];
    }

    /**
     * @return what the choice shows, for the command help
     */
    public @NotNull String getDescription() {
        return this.description;
    }
}
