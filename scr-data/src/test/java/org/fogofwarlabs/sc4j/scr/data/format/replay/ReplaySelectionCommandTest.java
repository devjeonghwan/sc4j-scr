package org.fogofwarlabs.sc4j.scr.data.format.replay;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

/**
 * Tests raw selection request decoding independently of selection execution.
 */
final class ReplaySelectionCommandTest {
    private static final int SELECT = 0x09;
    private static final int SELECT_ADD = 0x0A;
    private static final int SELECT_REMOVE = 0x0B;
    private static final int SELECT_121 = 0x63;
    private static final int SELECT_ADD_121 = 0x64;
    private static final int SELECT_REMOVE_121 = 0x65;
    private static final int LEGACY_HANDLE_WIDTH = 2;
    private static final int HANDLE_WIDTH_121 = 4;
    private static final int @NotNull [] SELECTION_TYPES = {SELECT, SELECT_ADD, SELECT_REMOVE, SELECT_121, SELECT_ADD_121, SELECT_REMOVE_121};

    @Test
    void parse_should_decode_legacy_operations_and_keep_handle_order() throws IOException {
        int[] typeIdentifiers = {SELECT, SELECT_ADD, SELECT_REMOVE};
        ReplaySelectionCommand.Operation[] operations = {ReplaySelectionCommand.Operation.REPLACE,
                                                         ReplaySelectionCommand.Operation.ADD,
                                                         ReplaySelectionCommand.Operation.REMOVE};
        byte[] payload = {5, 0x34, 0x12, (byte) 0xFF, (byte) 0xFF, 0, (byte) 0x80, 0x34, 0x12, 0, 0};
        long[] expectedHandles = {0x1234, 0xFFFF, 0x8000, 0x1234, 0};

        for (int operationIndex = 0; operationIndex < typeIdentifiers.length; operationIndex++) {
            ReplayCommand command = new ReplayCommand(0, 0, typeIdentifiers[operationIndex], payload);
            ReplaySelectionCommand selection = ReplaySelectionCommand.parse(command);

            SelectionRequest request = selection;

            Assertions.assertEquals(operations[operationIndex], request.getOperation());
            Assertions.assertEquals(LEGACY_HANDLE_WIDTH, selection.getHandleWidthBytes());
            Assertions.assertEquals(expectedHandles.length, selection.getHandleCount());

            for (int handleIndex = 0; handleIndex < expectedHandles.length; handleIndex++) {
                Assertions.assertEquals(expectedHandles[handleIndex], selection.getHandle(handleIndex));
            }
        }
    }

    @Test
    void parse_should_decode_modern_operations_and_keep_unsigned_handles() throws IOException {
        int[] typeIdentifiers = {SELECT_121, SELECT_ADD_121, SELECT_REMOVE_121};
        ReplaySelectionCommand.Operation[] operations = {ReplaySelectionCommand.Operation.REPLACE,
                                                         ReplaySelectionCommand.Operation.ADD,
                                                         ReplaySelectionCommand.Operation.REMOVE};
        byte[] payload = {5, 0x78, 0x56, 0x34, 0x12, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
                          0, 0, 0, (byte) 0x80, 0x78, 0x56, 0x34, 0x12, 0, 0, 0, 0};
        long[] expectedHandles = {0x12345678L, 0xFFFFFFFFL, 0x80000000L, 0x12345678L, 0};

        for (int operationIndex = 0; operationIndex < typeIdentifiers.length; operationIndex++) {
            ReplayCommand command = new ReplayCommand(0, 0, typeIdentifiers[operationIndex], payload);
            ReplaySelectionCommand selection = ReplaySelectionCommand.parse(command);

            SelectionRequest request = selection;

            Assertions.assertEquals(operations[operationIndex], request.getOperation());
            Assertions.assertEquals(HANDLE_WIDTH_121, selection.getHandleWidthBytes());
            Assertions.assertEquals(expectedHandles.length, selection.getHandleCount());

            for (int handleIndex = 0; handleIndex < expectedHandles.length; handleIndex++) {
                Assertions.assertEquals(expectedHandles[handleIndex], selection.getHandle(handleIndex));
            }
        }
    }

    @Test
    void parse_should_accept_empty_selection_requests() throws IOException {
        for (int typeIdentifier : SELECTION_TYPES) {
            ReplayCommand command = new ReplayCommand(0, 0, typeIdentifier, new byte[]{0});

            Assertions.assertEquals(0, ReplaySelectionCommand.parse(command).getHandleCount());
        }
    }

    @Test
    void parse_should_keep_handle_counts_above_selection_capacity() throws IOException {
        for (int typeIdentifier : SELECTION_TYPES) {
            int handleWidthBytes = typeIdentifier < SELECT_121 ? LEGACY_HANDLE_WIDTH : HANDLE_WIDTH_121;

            for (int handleCount : new int[]{12, 13, 127, 128, 255}) {
                byte[] payload = new byte[1 + handleCount * handleWidthBytes];

                payload[0] = (byte) handleCount;

                for (int handleIndex = 0; handleIndex < handleCount; handleIndex++) {
                    payload[1 + handleIndex * handleWidthBytes] = (byte) handleIndex;
                }

                ReplaySelectionCommand selection = ReplaySelectionCommand.parse(new ReplayCommand(0, 0, typeIdentifier, payload));

                Assertions.assertEquals(handleCount, selection.getHandleCount());

                for (int handleIndex = 0; handleIndex < handleCount; handleIndex++) {
                    Assertions.assertEquals(handleIndex, selection.getHandle(handleIndex));
                }
            }
        }
    }

    @Test
    void parse_should_reject_every_truncated_payload_and_trailing_bytes() {
        for (int typeIdentifier : SELECTION_TYPES) {
            int handleWidthBytes = typeIdentifier < SELECT_121 ? LEGACY_HANDLE_WIDTH : HANDLE_WIDTH_121;
            byte[] payload = new byte[1 + 3 * handleWidthBytes];

            payload[0] = 3;

            for (int length = 0; length < payload.length; length++) {
                ReplayCommand truncatedCommand = new ReplayCommand(0, 0, typeIdentifier, Arrays.copyOf(payload, length));

                Assertions.assertThrows(IOException.class, () -> ReplaySelectionCommand.parse(truncatedCommand));
            }

            ReplayCommand trailingCommand = new ReplayCommand(0, 0, typeIdentifier, Arrays.copyOf(payload, payload.length + 1));
            ReplayCommand emptyTrailingCommand = new ReplayCommand(0, 0, typeIdentifier, new byte[]{0, 0});

            Assertions.assertThrows(IOException.class, () -> ReplaySelectionCommand.parse(trailingCommand));
            Assertions.assertThrows(IOException.class, () -> ReplaySelectionCommand.parse(emptyTrailingCommand));
        }
    }

    @Test
    void only_the_six_selection_types_are_supported_and_parsed() {
        for (int typeIdentifier : SELECTION_TYPES) {
            Assertions.assertTrue(ReplaySelectionCommand.supports(typeIdentifier));
        }

        for (int typeIdentifier : new int[]{Integer.MIN_VALUE, -1, 0, 0x08, 0x0C, 0x62, 0x66, 0x109, Integer.MAX_VALUE}) {
            Assertions.assertFalse(ReplaySelectionCommand.supports(typeIdentifier));

            ReplayCommand command = new ReplayCommand(0, 0, typeIdentifier, new byte[]{0});

            Assertions.assertThrows(IOException.class, () -> ReplaySelectionCommand.parse(command));
        }
    }

    @Test
    void parse_should_snapshot_handles_without_modifying_source_payload() throws IOException {
        byte[] payload = {2, 0x34, 0x12, 0x34, 0x12};
        byte[] originalPayload = payload.clone();
        ReplayCommand command = new ReplayCommand(19, 7, SELECT, payload);
        ReplaySelectionCommand selection = ReplaySelectionCommand.parse(command);

        Assertions.assertArrayEquals(originalPayload, payload);
        Arrays.fill(payload, (byte) 0);

        Assertions.assertEquals(2, selection.getHandleCount());
        Assertions.assertEquals(0x1234, selection.getHandle(0));
        Assertions.assertEquals(0x1234, selection.getHandle(1));
        Assertions.assertEquals(19, command.getFrame());
        Assertions.assertEquals(7, command.getPlayerId());
        Assertions.assertEquals(SELECT, command.getTypeId());
    }
}
