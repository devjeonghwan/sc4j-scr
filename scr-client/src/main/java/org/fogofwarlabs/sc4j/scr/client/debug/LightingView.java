package org.fogofwarlabs.sc4j.scr.client.debug;

import org.fogofwarlabs.sc4j.scr.render.hd.HdSceneRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The HD lighting debug views of {@code /debug lighting}, in the order of {@link HdSceneRenderer#LIGHTING_DEBUG_VIEWS}: they replace the deferred
 * lighting pass's output, so they show only with HD graphics and HD lighting on.
 */
public enum LightingView {
    /**
     * The normal lit scene.
     */
    OFF("the normal lit scene"),
    /**
     * The scene with each point light's radius ring and center.
     */
    LIGHTS("the scene with each point light's radius ring (in its colour) and centre"),
    /**
     * The light a white surface receives.
     */
    LIGHTING("the light a white surface receives: sun, shadows and point lights"),
    /**
     * The unlit surface colors.
     */
    ALBEDO("the unlit surface colours"),
    /**
     * The surface normals.
     */
    NORMALS("the surface normals as colours"),
    /**
     * The ambient occlusion.
     */
    AO("the ambient occlusion"),
    /**
     * The self-lit colors.
     */
    EMISSIVE("the emissive (self-lit) colours"),
    /**
     * The per-pixel lighting amount.
     */
    AMOUNT("the per-pixel amount of lighting over the base colour");

    /**
     * Finds a view by its name.
     *
     * @param name the name, in any case
     * @return the view, or {@code null} when no view has the name
     */
    public static @Nullable LightingView find(@NotNull String name) {
        for (LightingView view : values()) {
            if (view.getName().equals(name.toLowerCase(Locale.ROOT))) {
                return view;
            }
        }

        return null;
    }

    private final @NotNull String description;

    /**
     * Creates a {@link LightingView} instance.
     *
     * @param description the command-help description
     */
    LightingView(@NotNull String description) {
        this.description = description;
    }


    /**
     * @return the view's name in commands, for example {@code normals}
     */
    public @NotNull String getName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    /**
     * @return what the view shows, for the command help
     */
    public @NotNull String getDescription() {
        return this.description;
    }

    /**
     * @return the index into {@link HdSceneRenderer#LIGHTING_DEBUG_VIEWS}
     */
    public int getRendererIndex() {
        return this.ordinal();
    }

    /**
     * @return the view after this one, wrapping from the last to {@link #OFF}
     */
    public @NotNull LightingView getNextView() {
        LightingView[] views = values();

        return views[(this.ordinal() + 1) % views.length];
    }
}
