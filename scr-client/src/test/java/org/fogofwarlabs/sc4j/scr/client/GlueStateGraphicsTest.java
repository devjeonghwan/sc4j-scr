package org.fogofwarlabs.sc4j.scr.client;

import org.fogofwarlabs.sc4j.engine.client.settings.SettingsGroup;
import org.fogofwarlabs.sc4j.engine.client.settings.SettingsStore;
import org.fogofwarlabs.sc4j.scr.client.configuration.ClientOptions;
import org.fogofwarlabs.sc4j.scr.client.configuration.GraphicsChoice;
import org.fogofwarlabs.sc4j.scr.client.configuration.InstallLayout;
import org.fogofwarlabs.sc4j.scr.render.hd.HdRenderAssets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Verifies the graphics setting on a fresh install: HD by default, and every F5 press selects the next graphics and writes them to the mod's
 * settings file at once, under the key the options popup writes ({@code graphics.renderer}), so that the next start reads them.
 */
final class GlueStateGraphicsTest {
    private static final @NotNull String GRAPHICS_GROUP = "graphics";
    private static final @NotNull String RENDERER = "renderer";

    /**
     * The client's graphics as {@code Main} keeps them, recording every selection.
     */
    private static final class RecordingSelection implements GlueState.GraphicsSelection {
        private final @NotNull List<GraphicsChoice> selected;

        private HdRenderAssets.@Nullable Quality quality;

        private RecordingSelection(HdRenderAssets.@Nullable Quality quality) {
            this.selected = new ArrayList<>();

            this.quality = quality;
        }

        @Override
        public HdRenderAssets.@Nullable Quality getQuality() {
            return this.quality;
        }

        @Override
        public void select(HdRenderAssets.@Nullable Quality selectedQuality) {
            this.quality = selectedQuality;
            this.selected.add(GraphicsChoice.of(selectedQuality));
        }
    }

    @TempDir
    @Nullable Path root;

    private @NotNull InstallLayout createLayout() {
        Path directory = this.root;

        Assertions.assertNotNull(directory);

        return InstallLayout.of(directory);
    }

    @Test
    void fresh_install_defaults_to_hd() {
        InstallLayout layout = this.createLayout();

        Assertions.assertFalse(Files.exists(layout.getSettingsFile()));
        Assertions.assertEquals(HdRenderAssets.Quality.HD, ClientOptions.parse(new String[0], layout).getSceneQuality());
    }

    @Test
    void f5_selects_the_next_graphics_and_writes_them_at_once() throws IOException {
        InstallLayout layout = this.createLayout();
        ClientOptions options = ClientOptions.parse(new String[0], layout);
        RecordingSelection selection = new RecordingSelection(options.getSceneQuality());
        GlueState glueState = new GlueState(options, selection);

        for (GraphicsChoice expected : List.of(GraphicsChoice.CLASSIC, GraphicsChoice.HD2, GraphicsChoice.HD)) {
            Assertions.assertEquals(expected, glueState.cycleGraphics());
            Assertions.assertEquals(expected, selection.selected.getLast());

            SettingsGroup graphics = SettingsStore.read(layout.getSettingsFile()).getGroup(GRAPHICS_GROUP);

            Assertions.assertNotNull(graphics);
            Assertions.assertEquals(expected.getName(), graphics.getString(RENDERER));
            Assertions.assertEquals(expected.getQuality(), ClientOptions.parse(new String[0], layout).getSceneQuality());
        }

        Assertions.assertEquals(List.of(GraphicsChoice.CLASSIC, GraphicsChoice.HD2, GraphicsChoice.HD), selection.selected);
    }
}
