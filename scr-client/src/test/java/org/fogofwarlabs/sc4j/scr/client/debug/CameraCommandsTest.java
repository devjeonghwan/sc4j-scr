package org.fogofwarlabs.sc4j.scr.client.debug;

import org.fogofwarlabs.sc4j.engine.client.console.ConsoleCommandRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests the {@code /zoom} and {@code /pan} commands that replace the client's wheel zoom and drag panning bindings.
 */
final class CameraCommandsTest {
    /**
     * The zoom that {@code /zoom off} resets to, in percent of the native scale.
     */
    private static final int NATIVE_ZOOM_PERCENT = 100;

    /**
     * {@code /zoom} replaces the wheel zoom binding: off by default, on enables it, off disables it and resets.
     */
    @Test
    void zoom_command_zooms_and_switches_wheel_zoom() {
        ViewerCommandScenario client = new ViewerCommandScenario();
        ConsoleCommandRegistry registry = client.createRegistry();

        Assertions.assertEquals("Wheel zoom off, zoom 100%.", registry.run("/zoom"));
        Assertions.assertEquals("Wheel zoom on.", registry.run("/zoom on"));
        Assertions.assertTrue(client.wheelZoom);
        Assertions.assertEquals("Zoom 150%.", registry.run("/zoom 150"));
        Assertions.assertEquals("Zoom 400%.", registry.run("/zoom 900%"), "the reply names the zoom the camera kept");
        Assertions.assertEquals("Zoom 100%.", registry.run("/zoom reset"));

        registry.run("/zoom 200");

        Assertions.assertEquals("Wheel zoom off. Zoom 100%.", registry.run("/zoom off"));
        Assertions.assertFalse(client.wheelZoom);
        Assertions.assertEquals(NATIVE_ZOOM_PERCENT, client.zoomPercent);
        Assertions.assertTrue(registry.run("/zoom -5").startsWith("Use /zoom"));

        client.session = false;

        Assertions.assertEquals("No game view to zoom.", registry.run("/zoom 150"));
        Assertions.assertEquals("No game view to zoom.", registry.run("/zoom reset"));
        Assertions.assertEquals("Wheel zoom on.", registry.run("/zoom on"), "the switch works before a game");
    }

    /**
     * {@code /pan} replaces the middle and right drag binding.
     */
    @Test
    void pan_command_switches_drag_panning() {
        ViewerCommandScenario client = new ViewerCommandScenario();
        ConsoleCommandRegistry registry = client.createRegistry();

        Assertions.assertEquals("Drag panning off.", registry.run("/pan"));
        Assertions.assertEquals("Drag panning on.", registry.run("/pan on"));
        Assertions.assertTrue(client.dragPan);
        Assertions.assertEquals("Drag panning off.", registry.run("/pan off"));
        Assertions.assertFalse(client.dragPan);

        client.live = true;

        Assertions.assertEquals("Drag panning on.", registry.run("/pan on"), "the camera is local: live games too");
    }
}
