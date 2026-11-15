package org.fogofwarlabs.sc4j.scr.data.format.replay;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

/**
 * Tests target payload decoding independently of unit identity and order execution.
 */
final class ReplayTargetCommandTest {
    private static final int RIGHT_CLICK = 0x14;
    private static final int TARGETED_ORDER = 0x15;
    private static final int RIGHT_CLICK_121 = 0x60;
    private static final int TARGETED_ORDER_121 = 0x61;
    private static final int LEGACY_HANDLE_WIDTH = 2;
    private static final int HANDLE_WIDTH_121 = 4;
    private static final int @NotNull [] TARGET_TYPES = {RIGHT_CLICK, TARGETED_ORDER, RIGHT_CLICK_121, TARGETED_ORDER_121};
    /**
     * The payload bytes besides the handle of a right click: x, y and unit type WORDs and the queue byte.
     */
    private static final int RIGHT_CLICK_FIELDS_SIZE = 7;
    /**
     * The payload bytes besides the handle of a targeted order: the right-click fields and the order byte.
     */
    private static final int TARGETED_ORDER_FIELDS_SIZE = 8;
    private static final int UNIT_TYPE_NONE = 228;

    @Test
    void parse_should_decode_legacy_right_click_and_targeted_order_layouts() throws IOException {
        ReplayTargetCommand rightClick = ReplayTargetCommand.parse(
                new ReplayCommand(8, 2, RIGHT_CLICK,
                                  new byte[]{0x34, 0x12, 0x78, 0x56, (byte) 0xBC, (byte) 0x9A, (byte) 0xF0, (byte) 0xDE, (byte) 0x81})
        );
        ReplayTargetCommand targetedOrder = ReplayTargetCommand.parse(
                new ReplayCommand(8, 2, TARGETED_ORDER,
                                  new byte[]{0x34, 0x12, 0x78, 0x56, (byte) 0xBC, (byte) 0x9A, (byte) 0xF0, (byte) 0xDE, 6, (byte) 0x81})
        );

        for (ReplayTargetCommand target : new ReplayTargetCommand[]{rightClick, targetedOrder}) {
            Assertions.assertEquals(0x1234, target.getX());
            Assertions.assertEquals(0x5678, target.getY());
            Assertions.assertEquals(0x9ABC, target.getTargetHandle());
            Assertions.assertEquals(LEGACY_HANDLE_WIDTH, target.getHandleWidthBytes());
            Assertions.assertEquals(0xDEF0, target.getUnitTypeIdentifier());
            Assertions.assertEquals(0x81, target.getQueueFlags());
        }

        Assertions.assertEquals(ReplayTargetCommand.Operation.RIGHT_CLICK, rightClick.getOperation());
        Assertions.assertEquals(-1, rightClick.getOrderIdentifier());
        Assertions.assertEquals(ReplayTargetCommand.Operation.TARGETED_ORDER, targetedOrder.getOperation());
        Assertions.assertEquals(6, targetedOrder.getOrderIdentifier());
    }

    @Test
    void parse_should_decode_modern_handles_without_sign_extension() throws IOException {
        ReplayTargetCommand rightClick = ReplayTargetCommand.parse(
                new ReplayCommand(8, 2, RIGHT_CLICK_121,
                                  new byte[]{0, (byte) 0x80, (byte) 0xFF, (byte) 0xFF, 0x78, 0x56, 0x34, (byte) 0xF2, (byte) 0xE4, 0, 0})
        );
        ReplayTargetCommand targetedOrder = ReplayTargetCommand.parse(
                new ReplayCommand(8, 2, TARGETED_ORDER_121,
                                  new byte[]{0, (byte) 0x80,
                                             (byte) 0xFF, (byte) 0xFF,
                                             0x78, 0x56, 0x34, (byte) 0xF2,
                                             (byte) 0xE4, 0,
                                             (byte) 0xFE, (byte) 0xFF})
        );

        for (ReplayTargetCommand target : new ReplayTargetCommand[]{rightClick, targetedOrder}) {
            Assertions.assertEquals(0x8000, target.getX());
            Assertions.assertEquals(0xFFFF, target.getY());
            Assertions.assertEquals(0xF2345678L, target.getTargetHandle());
            Assertions.assertEquals(HANDLE_WIDTH_121, target.getHandleWidthBytes());
            Assertions.assertEquals(UNIT_TYPE_NONE, target.getUnitTypeIdentifier());
        }

        Assertions.assertEquals(-1, rightClick.getOrderIdentifier());
        Assertions.assertEquals(0, rightClick.getQueueFlags());
        Assertions.assertEquals(0xFE, targetedOrder.getOrderIdentifier());
        Assertions.assertEquals(0xFF, targetedOrder.getQueueFlags());
    }

    @Test
    void parse_should_keep_zero_and_maximum_fields_without_semantic_validation() throws IOException {
        for (int typeIdentifier : TARGET_TYPES) {
            int handleWidthBytes = typeIdentifier < RIGHT_CLICK_121 ? LEGACY_HANDLE_WIDTH : HANDLE_WIDTH_121;
            boolean targetedOrder = typeIdentifier == TARGETED_ORDER || typeIdentifier == TARGETED_ORDER_121;
            byte[] payload = new byte[handleWidthBytes + (targetedOrder ? TARGETED_ORDER_FIELDS_SIZE : RIGHT_CLICK_FIELDS_SIZE)];
            ReplayTargetCommand zero = ReplayTargetCommand.parse(new ReplayCommand(0, 0, typeIdentifier, payload));

            Assertions.assertEquals(0, zero.getTargetHandle());
            Assertions.assertEquals(0, zero.getUnitTypeIdentifier());
            Assertions.assertEquals(targetedOrder ? 0 : -1, zero.getOrderIdentifier());

            Arrays.fill(payload, (byte) 0xFF);

            ReplayTargetCommand maximum = ReplayTargetCommand.parse(new ReplayCommand(0, 0, typeIdentifier, payload));

            Assertions.assertEquals(65535, maximum.getX());
            Assertions.assertEquals(65535, maximum.getY());
            Assertions.assertEquals(handleWidthBytes == LEGACY_HANDLE_WIDTH ? 0xFFFFL : 0xFFFFFFFFL, maximum.getTargetHandle());
            Assertions.assertEquals(65535, maximum.getUnitTypeIdentifier());
            Assertions.assertEquals(targetedOrder ? 255 : -1, maximum.getOrderIdentifier());
            Assertions.assertEquals(255, maximum.getQueueFlags());
        }
    }

    @Test
    void parse_should_reject_every_truncated_payload_and_trailing_bytes() {
        int[] payloadLengths = {9, 10, 11, 12};

        for (int typeIndex = 0; typeIndex < TARGET_TYPES.length; typeIndex++) {
            int typeIdentifier = TARGET_TYPES[typeIndex];

            for (int length = 0; length <= payloadLengths[typeIndex] + 1; length++) {
                if (length == payloadLengths[typeIndex]) {
                    continue;
                }

                ReplayCommand command = new ReplayCommand(0, 0, typeIdentifier, new byte[length]);

                Assertions.assertThrows(IOException.class, () -> ReplayTargetCommand.parse(command));
            }
        }
    }

    @Test
    void only_the_four_target_types_are_supported_and_parsed() {
        for (int typeIdentifier : TARGET_TYPES) {
            Assertions.assertTrue(ReplayTargetCommand.supports(typeIdentifier));
        }

        for (int typeIdentifier : new int[]{Integer.MIN_VALUE, -1, 0, 0x13, 0x16, 0x5F, 0x62, 0x114, Integer.MAX_VALUE}) {
            Assertions.assertFalse(ReplayTargetCommand.supports(typeIdentifier));

            ReplayCommand command = new ReplayCommand(0, 0, typeIdentifier, new byte[12]);

            Assertions.assertThrows(IOException.class, () -> ReplayTargetCommand.parse(command));
        }
    }

    @Test
    void parse_should_snapshot_fields_without_retaining_or_changing_payload() throws IOException {
        byte[] payload = {0x34, 0x12, 0x78, 0x56, (byte) 0xBC, (byte) 0x9A, (byte) 0xE4, 0, 6, 1};
        byte[] original = payload.clone();
        ReplayTargetCommand target = ReplayTargetCommand.parse(new ReplayCommand(0, 0, TARGETED_ORDER, payload));

        Assertions.assertArrayEquals(original, payload);
        Arrays.fill(payload, (byte) 0);

        Assertions.assertEquals(0x1234, target.getX());
        Assertions.assertEquals(0x5678, target.getY());
        Assertions.assertEquals(0x9ABC, target.getTargetHandle());
        Assertions.assertEquals(UNIT_TYPE_NONE, target.getUnitTypeIdentifier());
        Assertions.assertEquals(6, target.getOrderIdentifier());
        Assertions.assertEquals(1, target.getQueueFlags());
    }
}
