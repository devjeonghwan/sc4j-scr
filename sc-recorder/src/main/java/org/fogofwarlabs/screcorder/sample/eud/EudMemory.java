package org.fogofwarlabs.screcorder.sample.eud;

import org.fogofwarlabs.screcorder.binary.LittleEndianUtils;
import org.fogofwarlabs.screcorder.capture.sampling.CaptureContext;
import org.fogofwarlabs.screcorder.capture.starcraft.StarCraftLayout;
import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;

/**
 * Traverses the bounded native emulator tree shared by descriptor and host collection.
 */
final class EudMemory {
    static final int MAXIMUM_REGIONS = 65536;
    static final int NODE_BYTES = 48;
    static final int LEFT_NODE_OFFSET = 8;
    static final int RIGHT_NODE_OFFSET = 16;
    static final int REGION_BASE_OFFSET = 8;
    static final int REGION_TYPE_OFFSET = 12;
    static final int REGION_HOST_OFFSET = 16;
    static final int REGION_SIZE_OFFSET = 32;
    static final int REGION_BOUND_OFFSET = 40;
    static final long IMAGE_POINTER_TAG = 1L << 62;
    static final long OTHER_POINTER_TAG = 1L << 61;

    private static final long MAXIMUM_UNSIGNED_INTEGER = 0xFFFFFFFFL;
    private static final long MAXIMUM_POINTER = 0x00007FFFFFFFFFFFL;

    private static final long GLOBALS_OFFSET = 0x105423CL;
    private static final int GLOBALS_BYTES = 12;
    private static final int EMULATOR_POINTER_OFFSET = 4;
    private static final int TREE_OFFSET = 8;
    private static final int TREE_BYTES = 40;
    private static final int TREE_COUNT_OFFSET = 32;
    private static final int NODE_KEY_OFFSET = 32;
    private static final int NODE_REGION_OFFSET = 40;
    private static final int REGION_BYTES = 48;

    static final class Node {
        private final long key;
        private final byte @NotNull [] region;

        Node(long key, byte @NotNull [] region) {
            this.key    = key;
            this.region = region;
        }


        long getKey() {
            return this.key;
        }

        byte @NotNull [] getRegion() {
            return this.region;
        }
    }

    /**
     * Normalizes a process pointer for recording: {@code 0} stays {@code 0}, a pointer into the executable image becomes
     * {@link #IMAGE_POINTER_TAG} plus its image offset, and any other pointer becomes {@link #OTHER_POINTER_TAG}
     * (presence only), because heap and arena bases move with every process.
     *
     * @param value      the raw pointer
     * @param moduleBase the executable image base
     * @return the normalized value
     */
    static long normalizePointer(long value, long moduleBase) {
        if (value == 0) {
            return 0;
        }

        long offset = value - moduleBase;

        return offset >= 0 && offset < StarCraftLayout.IMAGE_SIZE ? IMAGE_POINTER_TAG | offset : OTHER_POINTER_TAG;
    }

    /**
     * Returns a copy whose {@code 8}-byte aligned words that hold a user-mode pointer value (above {@code 32} bits and at most
     * {@code 0x00007FFFFFFFFFFF}) are normalized by {@link #normalizePointer(long, long)}.
     *
     * @param bytes      the raw memory
     * @param moduleBase the executable image base
     * @return the normalized copy
     */
    static byte @NotNull [] normalizePointerWords(byte @NotNull [] bytes, long moduleBase) {
        byte[] copy = bytes.clone();

        for (int offset = 0; offset + Long.BYTES <= copy.length; offset += Long.BYTES) {
            long value = LittleEndianUtils.readUnsigned(copy, offset, Long.BYTES);

            if (value > MAXIMUM_UNSIGNED_INTEGER && value <= MAXIMUM_POINTER) {
                long normalized = normalizePointer(value, moduleBase);

                for (int index = 0; index < Long.BYTES; index++) {
                    copy[offset + index] = (byte) (normalized >>> (index * Byte.SIZE));
                }
            }
        }

        return copy;
    }

    private static void requirePointer(long address) throws IOException {
        if (address <= 0 || address > MAXIMUM_POINTER) {
            throw Exceptions.newIOException("Invalid emulator memory pointer: {0}", address);
        }
    }

    private final @NotNull CaptureContext context;
    private final byte @NotNull [] globals;
    private final byte @NotNull [] tree;
    private final byte @NotNull [] node;
    private final byte @NotNull [] region;

    EudMemory(@NotNull CaptureContext context) {
        this.context = context;
        this.globals = new byte[GLOBALS_BYTES];
        this.tree    = new byte[TREE_BYTES];
        this.node    = new byte[NODE_BYTES];
        this.region  = new byte[REGION_BYTES];
    }

    long getMode() {
        return LittleEndianUtils.readUnsigned32(this.globals, 0);
    }


    long readModeAndEmulator() throws IOException {
        this.context.getMemory().read(this.context.getModuleBase() + GLOBALS_OFFSET, this.globals, 0, this.globals.length);
        long emulator = LittleEndianUtils.readUnsigned(this.globals, EMULATOR_POINTER_OFFSET, Long.BYTES);

        if (emulator != 0) {
            requirePointer(emulator);
        }

        return emulator;
    }

    byte @NotNull [] readBytes(long address, int length) throws IOException {
        requirePointer(address);

        byte[] storage = new byte[length];

        this.context.getMemory().read(address, storage, 0, storage.length);

        return storage;
    }

    @NotNull List<Node> readNodes(long emulator) throws IOException {
        this.context.getMemory().read(emulator + TREE_OFFSET, this.tree, 0, this.tree.length);
        long root = LittleEndianUtils.readUnsigned(this.tree, 0, Long.BYTES);
        long count = LittleEndianUtils.readUnsigned(this.tree, TREE_COUNT_OFFSET, Long.BYTES);

        if (count < 0 || count > MAXIMUM_REGIONS) {
            throw Exceptions.newIOException("Invalid emulator region count: {0}", count);
        }

        long sentinel = emulator + TREE_OFFSET;
        ArrayDeque<Long> pending = new ArrayDeque<>();
        Set<Long> visited = new HashSet<>();
        Set<Long> keys = new HashSet<>();
        List<Node> nodes = new ArrayList<>((int) count);

        if (root != 0 && root != sentinel) {
            pending.push(root);
        }

        while (!pending.isEmpty()) {
            long address = pending.pop();
            requirePointer(address);

            if (!visited.add(address) || visited.size() > count) {
                throw Exceptions.newIOException("Emulator tree cycle or node count mismatch");
            }

            this.context.getMemory().read(address, this.node, 0, this.node.length);
            long key = LittleEndianUtils.readUnsigned32(this.node, NODE_KEY_OFFSET);

            if (!keys.add(key)) {
                throw Exceptions.newIOException("Duplicate emulator region key: {0}", key);
            }

            long regionAddress = LittleEndianUtils.readUnsigned(this.node, NODE_REGION_OFFSET, Long.BYTES);

            requirePointer(regionAddress);
            this.context.getMemory().read(regionAddress, this.region, 0, this.region.length);
            nodes.add(new Node(key, this.region.clone()));

            long left = LittleEndianUtils.readUnsigned(this.node, LEFT_NODE_OFFSET, Long.BYTES);
            long right = LittleEndianUtils.readUnsigned(this.node, RIGHT_NODE_OFFSET, Long.BYTES);

            if (right != 0 && right != sentinel) {
                pending.push(right);
            }

            if (left != 0 && left != sentinel) {
                pending.push(left);
            }
        }

        if (nodes.size() != count) {
            throw Exceptions.newIOException("Emulator tree has {0} nodes, expected {1}", nodes.size(), count);
        }

        this.context.getMemory().read(emulator + TREE_OFFSET, this.tree, 0, this.tree.length);

        if (root != LittleEndianUtils.readUnsigned(this.tree, 0, Long.BYTES) ||
            count != LittleEndianUtils.readUnsigned(this.tree, TREE_COUNT_OFFSET, Long.BYTES)) {
            throw Exceptions.newIOException("Emulator tree changed during collection");
        }

        nodes.sort(Comparator.comparingLong(Node::getKey));

        return nodes;
    }
}
