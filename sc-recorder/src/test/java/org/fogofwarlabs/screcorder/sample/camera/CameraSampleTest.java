package org.fogofwarlabs.screcorder.sample.camera;

import org.fogofwarlabs.screcorder.capture.sampling.CaptureContext;
import org.fogofwarlabs.screcorder.sample.RangeMemoryScenario;
import org.fogofwarlabs.screcorder.sample.SampleRoundTripCheck;
import org.fogofwarlabs.screcorder.sample.context.ContextSample;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Checks that the camera sampler captures the view and input state, dereferences full-width mouse addresses and that its reader rejects an invalid
 * mouse presence.
 */
final class CameraSampleTest {
    private static final long MODULE_BASE = 0x100000000L;
    private static final long CONTEXT_ADDRESS = 0x120000000L;
    private static final int MODULE_BYTES = 0x1100000;

    private static final int SCREEN_X_OFFSET = 0xED3BC0;
    private static final int SCREEN_Y_OFFSET = 0xED3BC4;
    private static final int MAXIMUM_X_OFFSET = 0xED3B74;
    private static final int MAXIMUM_Y_OFFSET = 0xED3B78;
    private static final int PREVIOUS_X_OFFSET = 0xED3BB8;
    private static final int PREVIOUS_Y_OFFSET = 0xED3BBC;
    private static final int CENTER_VIEW_X_OFFSET = 0x10A1A80;
    private static final int CENTER_VIEW_Y_OFFSET = 0x10A1A84;
    private static final int CENTER_VIEW_ACTIVE_OFFSET = 0x10A1944;
    private static final int CURSOR_X_OFFSET = 0x10B6DC8;
    private static final int CURSOR_Y_OFFSET = 0x10B6DCC;
    private static final int BUTTONS_OFFSET = 0x1090610;
    private static final int TILE_X_OFFSET = 0xE0;
    private static final int TILE_Y_OFFSET = 0xE2;
    private static final int MOUSE_POINTER_OFFSET = 0xE7E4A8;
    private static final int MOUSE_COORDINATES_OFFSET = 0x140;

    private static final long AUXILIARY_ADDRESS = 0x140000000L;

    private static final int MOUSE_COORDINATES_BYTES = 2 * Integer.BYTES;
    private static final int CAMERA_EUD_MOUSE_PRESENCE_OFFSET = 33;

    @Test
    void camera_sampler_captures_the_view_and_dereferences_full_width_mouse_addresses() throws IOException {
        RangeMemoryScenario memory = new RangeMemoryScenario();
        byte[] moduleStorage = memory.allocateRange(MODULE_BASE, MODULE_BYTES);
        byte[] contextStorage = memory.allocateRange(CONTEXT_ADDRESS, ContextSample.MEMORY_BYTES);
        ByteBuffer module = ByteBuffer.wrap(moduleStorage).order(ByteOrder.LITTLE_ENDIAN);

        module.putInt(SCREEN_X_OFFSET, -20).putInt(SCREEN_Y_OFFSET, 30).putInt(MAXIMUM_X_OFFSET, 640).putInt(MAXIMUM_Y_OFFSET, 480);
        module.putInt(PREVIOUS_X_OFFSET, 7).putInt(PREVIOUS_Y_OFFSET, 8).putInt(CENTER_VIEW_X_OFFSET, 200).putInt(CENTER_VIEW_Y_OFFSET, 220);
        module.put(CENTER_VIEW_ACTIVE_OFFSET, (byte) 1).putInt(CURSOR_X_OFFSET, 50).putInt(CURSOR_Y_OFFSET, 60)
              .putShort(BUTTONS_OFFSET, (short) 0xFFFF);
        ByteBuffer.wrap(contextStorage).order(ByteOrder.LITTLE_ENDIAN).putShort(TILE_X_OFFSET, (short) 0xFFFF).putShort(TILE_Y_OFFSET, (short) 42);

        long mouseAddress = AUXILIARY_ADDRESS;

        module.putLong(MOUSE_POINTER_OFFSET, mouseAddress);
        ByteBuffer.wrap(memory.allocateRange(mouseAddress + MOUSE_COORDINATES_OFFSET, MOUSE_COORDINATES_BYTES)).order(ByteOrder.LITTLE_ENDIAN)
                  .putInt(-300).putInt(400);

        CameraSampleDefinition definition = new CameraSampleDefinition();
        CameraSample sample = definition.createSampler(new CaptureContext(memory, MODULE_BASE, CONTEXT_ADDRESS)).sample();
        CameraSample decoded = SampleRoundTripCheck.readAfterWrite(definition, sample);

        Assertions.assertEquals(-20, decoded.getScreenX());
        Assertions.assertEquals(30, decoded.getScreenY());
        Assertions.assertEquals(640, decoded.getMaximumX());
        Assertions.assertEquals(480, decoded.getMaximumY());
        Assertions.assertEquals(200, decoded.getCenterViewX());
        Assertions.assertEquals(1, decoded.getCenterViewActive());
        Assertions.assertTrue(decoded.isEudMouseAvailable());
        Assertions.assertEquals(-300, decoded.getEudMouseX());
        Assertions.assertEquals(400, decoded.getEudMouseY());
        Assertions.assertEquals(0xFFFF, decoded.getButtons());
        Assertions.assertEquals(0xFFFF, decoded.getTileX());
        Assertions.assertEquals(42, decoded.getTileY());

        module.putLong(MOUSE_POINTER_OFFSET, 0);
        Assertions.assertFalse(definition.createSampler(new CaptureContext(memory, MODULE_BASE, CONTEXT_ADDRESS)).sample().isEudMouseAvailable());

        module.putLong(MOUSE_POINTER_OFFSET, -1);
        Assertions.assertThrows(IOException.class, () -> definition.createSampler(new CaptureContext(memory, MODULE_BASE, CONTEXT_ADDRESS)).sample());
    }

    @Test
    void camera_reader_rejects_an_invalid_eud_mouse_presence() {
        CameraSampleDefinition definition = new CameraSampleDefinition();
        ByteBuffer invalidPresence = ByteBuffer.allocate(definition.getMaximumPayloadBytes());

        invalidPresence.put(CAMERA_EUD_MOUSE_PRESENCE_OFFSET, (byte) 2);
        Assertions.assertThrows(IOException.class, () -> definition.getReader().read(invalidPresence));
    }
}
