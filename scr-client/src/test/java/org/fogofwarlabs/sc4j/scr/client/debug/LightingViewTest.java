package org.fogofwarlabs.sc4j.scr.client.debug;

import org.fogofwarlabs.sc4j.scr.render.hd.HdSceneRenderer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests that the lighting views of {@code /debug lighting} are the renderer's.
 */
final class LightingViewTest {
    @Test
    void lighting_views_match_renderer_views_and_resolve_by_name() {
        Assertions.assertEquals(HdSceneRenderer.LIGHTING_DEBUG_VIEWS.length, LightingView.values().length);

        for (LightingView view : LightingView.values()) {
            Assertions.assertEquals(HdSceneRenderer.LIGHTING_DEBUG_VIEWS[view.getRendererIndex()], view.getName());
        }

        Assertions.assertEquals(LightingView.AO, LightingView.find("AO"));
        Assertions.assertNull(LightingView.find("glow"));
        Assertions.assertEquals(LightingView.OFF, LightingView.AMOUNT.getNextView());
    }
}
