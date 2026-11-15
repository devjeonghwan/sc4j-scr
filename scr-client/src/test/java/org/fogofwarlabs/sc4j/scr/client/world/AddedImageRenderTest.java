package org.fogofwarlabs.sc4j.scr.client.world;

import org.fogofwarlabs.sc4j.base.exception.IgnoreException;
import org.fogofwarlabs.sc4j.engine.client.host.ClientHost;
import org.fogofwarlabs.sc4j.engine.client.host.HostWindow;
import org.fogofwarlabs.sc4j.engine.client.host.WindowSettings;
import org.fogofwarlabs.sc4j.scr.content.ScrAddedGraphics;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.core.test.TestResultUtils;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.fogofwarlabs.sc4j.scr.data.resource.ScrResourceProvider;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayRuntime;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImage;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpritePool;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.test.AddedGraphicsScenario;
import org.fogofwarlabs.sc4j.scr.game.test.RunningGameScenario;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.visibility.LocalVision;
import org.fogofwarlabs.sc4j.scr.render.ClassicSceneRenderer;
import org.fogofwarlabs.sc4j.scr.render.OverlayPass;
import org.fogofwarlabs.sc4j.scr.render.SceneRenderer;
import org.fogofwarlabs.sc4j.scr.render.WorldRenderer;
import org.fogofwarlabs.sc4j.scr.render.hd.HdRenderAssets;
import org.fogofwarlabs.sc4j.scr.render.hd.HdSceneRenderer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.IntUnaryOperator;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Checks that the SD, HD and HD2 scene renderers, the HD ones also with lighting, draw an added image with its generated frames, shadow, team
 * color, selection circle and health bar in a hidden OpenGL context, and writes the frames as PNG samples into the result directory of the test
 * task.
 */
@RequiresInstallation
final class AddedImageRenderTest {
    private static final int FRAME_SIZE = 128;
    private static final int UNIT_CREATION_X = 3560;
    private static final int UNIT_CREATION_Y = 2160;
    private static final int SPRITE_X = FRAME_SIZE / 2;
    private static final int SPRITE_Y = FRAME_SIZE / 2;
    private static final int ALPHA_SHIFT = 24;
    private static final int OPAQUE_ALPHA = 0xFF;
    private static final @NotNull String RENDER_SMOKE_PROPERTY = "sc4j.render.smoke";
    private static final @NotNull String RENDER_SMOKE_MESSAGE = "Pass -Psc4j.render.smoke=true to run the GPU check.";

    /**
     * Creates the hidden window of an OpenGL core profile context in the frame size.
     *
     * @param host the host
     * @return the created window
     */
    private static @NotNull HostWindow createHiddenWindow(@NotNull ClientHost host) {
        return host.createWindow(WindowSettings.builder("Added image verification", FRAME_SIZE, FRAME_SIZE).build());
    }

    private static void hideOtherImages(@NotNull SpritePool sprites, @NotNull Sprite kept) {
        for (int index = 0; index < sprites.getCapacity(); index++) {
            if (sprites.isAllocated(index) && sprites.getSprite(index) != kept) {
                for (SpriteImage image : sprites.getSprite(index).getImages()) {
                    image.setFlags(image.getFlags() | SpriteImage.FLAG_HIDDEN);
                }
            }
        }
    }

    private static int @NotNull [] renderFrame(@NotNull WorldRenderer world, @NotNull SceneRenderer scene, @NotNull Game game,
                                               @NotNull IntUnaryOperator playerColors, @NotNull ByteBuffer pixels) {
        List<String> warnings = new ArrayList<>();

        world.beginFrame(FRAME_SIZE, FRAME_SIZE, FRAME_SIZE, FRAME_SIZE, 0, 0, 1);

        try {
            new WorldSceneExtraction().draw(scene, world, new ScrGameView(game), null, playerColors, warnings::add);
        } finally {
            scene.finishScene();
            world.endFrame();
        }

        Assertions.assertTrue(warnings.isEmpty(), () -> String.join("\n", warnings));
        glReadPixels(0, 0, FRAME_SIZE, FRAME_SIZE, GL_RGBA, GL_UNSIGNED_BYTE, pixels);

        int[] result = new int[FRAME_SIZE * FRAME_SIZE];

        for (int index = 0; index < result.length; index++) {
            result[index] = pixels.getInt(index * OverlayPass.RGBA_BYTES);
        }

        return result;
    }

    /**
     * Writes a frame as a PNG sample into the result directory of the test task.
     */
    private static void writeSample(int @NotNull [] frame, @NotNull String name) throws IOException {
        Path path = TestResultUtils.getResultPath(name + ".png");
        BufferedImage image = new BufferedImage(FRAME_SIZE, FRAME_SIZE, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < FRAME_SIZE; y++) {
            for (int x = 0; x < FRAME_SIZE; x++) {
                int rgba = frame[(FRAME_SIZE - 1 - y) * FRAME_SIZE + x];
                int red = rgba & OPAQUE_ALPHA;
                int green = rgba >>> Byte.SIZE & OPAQUE_ALPHA;
                int blue = rgba >>> Short.SIZE & OPAQUE_ALPHA;

                image.setRGB(x, y, OPAQUE_ALPHA << ALPHA_SHIFT | red << Short.SIZE | green << Byte.SIZE | blue);
            }
        }

        Files.createDirectories(path.toAbsolutePath().getParent());
        ImageIO.write(image, "png", path.toFile());
    }

    /**
     * Draws the selected test unit with and without its images and for two owners, and checks that the image draws pixels and that the team
     * color pixels follow the player color.
     */
    private static void checkScene(@NotNull WorldRenderer world, @NotNull SceneRenderer scene, @NotNull ReplayRuntime runtime, @NotNull Unit unit,
                                   @NotNull ByteBuffer pixels, @NotNull String name) throws IOException {
        Game game = runtime.getGame();
        Sprite sprite = unit.getSpriteOrNull();

        Assertions.assertNotNull(sprite);

        int[] drawn = renderFrame(world, scene, game, owner -> owner, pixels);
        int spriteOwner = sprite.getOwner();

        // The sprite of another player draws its team color pixels with that player's color row.
        RunningGameScenario.runInCommandDispatch(runtime, () -> sprite.setOwner(RunningGameScenario.COMPUTER));

        int[] otherOwner = renderFrame(world, scene, game, player -> player, pixels);

        RunningGameScenario.runInCommandDispatch(runtime, () -> sprite.setOwner(spriteOwner));

        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            for (SpriteImage image : sprite.getImages()) {
                image.setFlags(image.getFlags() | SpriteImage.FLAG_HIDDEN);
            }
        });

        int[] hidden = renderFrame(world, scene, game, owner -> owner, pixels);

        RunningGameScenario.runInCommandDispatch(runtime, () -> {
            for (SpriteImage image : sprite.getImages()) {
                image.setFlags(image.getFlags() & ~SpriteImage.FLAG_HIDDEN);
            }
        });

        writeSample(drawn, name);
        writeSample(otherOwner, name + "-other-owner");
        Assertions.assertFalse(Arrays.equals(drawn, hidden), name + ": the added image draws pixels.");
        Assertions.assertFalse(Arrays.equals(drawn, otherOwner), name + ": the team color follows the player color.");
    }

    @Test
    void scene_renderers_draw_the_added_image_with_its_generated_frames() throws IOException {
        Assumptions.assumeTrue(Boolean.getBoolean(RENDER_SMOKE_PROPERTY), RENDER_SMOKE_MESSAGE);

        ClientHost host = ClientHost.initialize();

        try {
            HostWindow window = createHiddenWindow(host);

            window.makeContextCurrent();
            host.createCapabilities();

            Path gamePath = ExternalInput.INSTALLATION.getPath();

            try (CascStorage storage = CascStorage.open(gamePath); GameData gameData = GameData.loadFrom(gamePath);
                 WorldRenderer world = new WorldRenderer()) {
                AddedGraphicsScenario added = AddedGraphicsScenario.register(gameData);
                ReplayRuntime runtime = RunningGameScenario.start(gameData);
                Game game = runtime.getGame();
                GameMap map = game.getMap();
                ScrAddedGraphics graphics = gameData.getAddedGraphics();
                Unit[] units = new Unit[1];
                int[] tiles = new int[map.getWidth() * map.getHeight()];

                for (int index = 0; index < tiles.length; index++) {
                    tiles[index] = map.getTileIdentifier(index);
                }

                world.loadMap(ScrResourceProvider.borrow(storage, graphics), map.getTileset(), map.getWidth(), map.getHeight(), tiles);

                RunningGameScenario.runInCommandDispatch(runtime, () -> {
                    Unit unit = RunningGameScenario.createUnit(game, added.getUnitTypeId(), UNIT_CREATION_X, UNIT_CREATION_Y, RunningGameScenario.HUMAN);
                    SpritePool sprites = game.getSpriteSystem().getSpritePool();
                    Sprite sprite = unit.getSpriteOrNull();

                    Assertions.assertNotNull(sprites);
                    Assertions.assertNotNull(sprite);

                    game.getUnitPositioning().move(unit, SPRITE_X, SPRITE_Y);
                    game.getUnitVisibility().updateFog(unit);
                    game.getInterfaceLink().getLocalSelection().setSelectedPoolIndexRaw(0, unit.getIndex());
                    game.getSelectionImages().selectUnit(unit, 0);
                    sprite.setVisibilityFlags(LocalVision.ALL_PLAYERS);
                    hideOtherImages(sprites, sprite);
                    game.getLocalVision().acceptDialog(LocalVision.ALL_PLAYERS, true);

                    units[0] = unit;
                });

                ByteBuffer pixels = MemoryUtil.memAlloc(FRAME_SIZE * FRAME_SIZE * OverlayPass.RGBA_BYTES);

                try {
                    try (ClassicSceneRenderer scene = new ClassicSceneRenderer(world, ScrResourceProvider.borrow(storage, graphics), map.getTileset())) {
                        checkScene(world, scene, runtime, units[0], pixels, "added-image-sd");
                    }

                    for (HdRenderAssets.Quality quality : HdRenderAssets.Quality.values()) {
                        HdSceneRenderer scene = new HdSceneRenderer(world, HdRenderAssets.load(ScrResourceProvider.borrow(storage, graphics),
                                                                                               map.getTileset(), quality));

                        try {
                            world.setRenderScaleLimit(quality.getScale());
                            checkScene(world, scene, runtime, units[0], pixels, "added-image-" + quality.name().toLowerCase(Locale.ROOT));
                            // The lit path draws the frames without lighting layers unlit and keeps the added image's team color.
                            scene.setLightingEnabled(true);
                            checkScene(world, scene, runtime, units[0], pixels, "added-image-" + quality.name().toLowerCase(Locale.ROOT) + "-lit");
                        } finally {
                            IgnoreException.execute(
                                    scene::close
                            );
                        }
                    }

                    Assertions.assertEquals(GL_NO_ERROR, glGetError());
                } finally {
                    IgnoreException.execute(
                            () -> MemoryUtil.memFree(pixels)
                    );
                }
            }
        } finally {
            host.close();
        }
    }
}
