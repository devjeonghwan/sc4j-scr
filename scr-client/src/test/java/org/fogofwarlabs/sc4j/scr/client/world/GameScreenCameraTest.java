package org.fogofwarlabs.sc4j.scr.client.world;

import org.fogofwarlabs.sc4j.engine.client.display.DisplayScale;
import org.fogofwarlabs.sc4j.engine.client.display.UiSurface;
import org.fogofwarlabs.sc4j.engine.client.world.CameraScroll;
import org.fogofwarlabs.sc4j.engine.client.world.LinearScrollStepTable;
import org.fogofwarlabs.sc4j.engine.client.world.SteppedScrollRate;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests the game screen's camera of the engine camera scrolls: whole map pixels of the game screen's top-left corner, the native 640 by 383
 * play view at the native scale, the range ending at the play view rows or the whole window's rows, moves that land on whole pixels and the
 * camera's own limits, and a stepped engine scroll moving it.
 */
final class GameScreenCameraTest {
    private static final int MAP_SIZE = 128 * 32;
    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 960;
    private static final int GAME_COLUMNS = 640;
    private static final int PLAY_ROWS = 383;
    private static final int WINDOW_ROWS = 480;
    private static final int TARGET_X = 1000;
    private static final int TARGET_Y = 500;
    private static final float ODD_CENTER = 1234.4f;
    private static final int STEP_UNIT = 8;
    /**
     * The step count of the test table, whose one speed row takes the step index times the unit.
     */
    private static final int STEP_COUNT = 7;
    /**
     * The first two steps of the test table: one unit, then two.
     */
    private static final int FIRST_TWO_STEPS = 3 * STEP_UNIT;
    private static final int EDGE_WIDTH = 2;
    private static final int INTERFACE_WIDTH = 640;
    private static final int INTERFACE_HEIGHT = 480;

    private static @NotNull ClientCamera createCamera(@NotNull GameViewport viewport) {
        ClientCamera camera = new ClientCamera(MAP_SIZE, MAP_SIZE);

        viewport.setSurface(UiSurface.of(WINDOW_WIDTH, WINDOW_HEIGHT, DisplayScale.ONE));
        viewport.setInterface(false, true, false);
        camera.setViewport(viewport);
        camera.setNativeZoom();

        return camera;
    }

    @Test
    void game_screen_camera_moves_the_client_camera_in_whole_pixels_of_the_native_view() {
        GameViewport viewport = new GameViewport();
        ClientCamera client = createCamera(viewport);
        GameScreenCamera camera = GameScreenCamera.ofPlayView(client, viewport, MAP_SIZE, MAP_SIZE);
        GameScreenCamera centerView = GameScreenCamera.ofWholeView(client, viewport, MAP_SIZE, MAP_SIZE);

        Assertions.assertEquals(GAME_COLUMNS, camera.getViewWidth());
        Assertions.assertEquals(PLAY_ROWS, camera.getViewHeight());
        Assertions.assertEquals(MAP_SIZE - GAME_COLUMNS, camera.getMaximumX());
        Assertions.assertEquals(MAP_SIZE - PLAY_ROWS, camera.getMaximumY(), "the edge and key scroll end at the play view");
        Assertions.assertEquals(MAP_SIZE - WINDOW_ROWS, centerView.getMaximumY(), "a center view ends at the whole window");

        camera.moveTo(TARGET_X, TARGET_Y);

        Assertions.assertEquals(TARGET_X, camera.getX());
        Assertions.assertEquals(TARGET_Y, camera.getY());

        client.centerOn(ODD_CENTER, ODD_CENTER);

        Assertions.assertEquals(Math.rint(camera.getX()), camera.getX(), "a whole pixel");
        Assertions.assertEquals(Math.rint(camera.getY()), camera.getY());

        camera.moveTo(MAP_SIZE, MAP_SIZE);

        Assertions.assertEquals(camera.getMaximumX(), camera.getX(), "the client camera keeps its own limits");
        Assertions.assertEquals(camera.getMaximumY(), camera.getY());
    }

    @Test
    void stepped_engine_scroll_moves_the_game_screen_camera() {
        GameViewport viewport = new GameViewport();
        ClientCamera client = createCamera(viewport);
        GameScreenCamera camera = GameScreenCamera.ofPlayView(client, viewport, MAP_SIZE, MAP_SIZE);
        CameraScroll scroll = new CameraScroll(EDGE_WIDTH, new SteppedScrollRate(new LinearScrollStepTable(1, STEP_COUNT, STEP_UNIT)));

        camera.moveTo(TARGET_X, TARGET_Y);
        scroll.update(camera, 1, -1, 0, 0, true, INTERFACE_WIDTH, INTERFACE_HEIGHT, 0);
        scroll.update(camera, 1, -1, 0, 0, true, INTERFACE_WIDTH, INTERFACE_HEIGHT, 0);

        Assertions.assertEquals(TARGET_X + FIRST_TWO_STEPS, camera.getX(), "steps 1 and 2");
        Assertions.assertEquals(TARGET_Y - FIRST_TWO_STEPS, camera.getY());
    }
}
