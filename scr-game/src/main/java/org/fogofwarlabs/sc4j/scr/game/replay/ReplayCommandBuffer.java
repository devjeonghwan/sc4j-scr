package org.fogofwarlabs.sc4j.scr.game.replay;

import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommand;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommandBlock;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommandUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

/**
 * Holds the replay command buffer that replay playback loads each frame's command blocks into and runs them from.
 * <p>
 * The buffer is one memory image of three static arrays: the issuer byte of each entry, the entries' command bytes and
 * the size DWORD of each entry, each sized for {@value #CAPACITY} entries or bytes. Loading a frame copies its commands
 * into the image, one entry per command. A block that would fill the data array grows the buffer into a copy owned by
 * the current playback call: the copy keeps the image's data and sizes, and the commands from the growth on go to the
 * copy only, while their issuer bytes still go to the static issuer array. {@link #release()} drops the copy at the end
 * of the call.
 * <p>
 * A frame that runs in its loading call runs from the copy and executes its recorded commands. A frame that runs in a
 * later call runs from the image: the entries from the growth on take the sizes and bytes that earlier frames left in
 * the image, under this frame's issuer bytes, and the reads may continue past the data array into the sizes array.
 * Each entry is walked by the command lengths ({@link ReplayCommandUtils#walk}): a command that does not fit in its
 * entry is not run.
 */
public final class ReplayCommandBuffer {
    /**
     * The entry and data byte capacity of the static arrays.
     */
    public static final int CAPACITY = 0x200;

    /**
     * The image offsets of the issuer bytes, the command bytes and the size DWORDs.
     */
    private static final int ISSUER_OFFSET = 0;
    private static final int DATA_OFFSET = ISSUER_OFFSET + CAPACITY;
    private static final int SIZES_OFFSET = DATA_OFFSET + CAPACITY;
    private static final int IMAGE_SIZE = SIZES_OFFSET + CAPACITY * Integer.BYTES;
    /**
     * The frame result when no block is left to load.
     */
    private static final int NO_BLOCK = -1;

    private final @NotNull List<ReplayCommand> commands;
    private final @NotNull List<ReplayCommandBlock> blocks;

    /**
     * The index of the next block to load.
     */
    private int blockIndex;

    private final byte @NotNull [] image;
    /**
     * The little-endian view of {@link #image} for the size DWORDs.
     */
    private final @NotNull ByteBuffer imageView;
    /**
     * The number of entries of the loaded frame, zero once they ran.
     */
    private int count;

    /**
     * The command bytes of the grown copy owned by the current call, or {@code null} while the call uses the image.
     */
    private byte @Nullable [] grownData;
    /**
     * The entry sizes of the grown copy owned by the current call, or {@code null} while the call uses the image.
     */
    private int @Nullable [] grownSizes;

    /**
     * Creates a {@link ReplayCommandBuffer} instance with a zeroed image.
     *
     * @param commands the replay's commands
     * @param blocks   the replay's command blocks, each a range of {@code commands}
     */
    public ReplayCommandBuffer(@NotNull List<ReplayCommand> commands, @NotNull List<ReplayCommandBlock> blocks) {
        this.commands = commands;
        this.blocks   = blocks;

        this.blockIndex = 0;

        this.image     = new byte[IMAGE_SIZE];
        this.imageView = ByteBuffer.wrap(this.image).order(ByteOrder.LITTLE_ENDIAN);
        this.count     = 0;

        this.grownData  = null;
        this.grownSizes = null;
    }

    /**
     * @return the number of entries of the loaded frame, zero once they ran
     */
    public int getCount() {
        return this.count;
    }


    /**
     * Loads the next frame's command blocks: every following block with the frame of the first.
     * <p>
     * Each command becomes one entry. A block whose size, added to the bytes loaded before it, reaches the current
     * capacity grows the buffer into a copy owned by the current call.
     *
     * @return the loaded frame, or {@code -1} if no block is left (the entry count is then unchanged)
     */
    public int load() {
        if (this.blockIndex >= this.blocks.size()) {
            return NO_BLOCK;
        }

        int frame = (int) this.blocks.get(this.blockIndex).getFrame();
        int capacity = CAPACITY;
        int entry = 0;
        int used = 0;

        while (this.blockIndex < this.blocks.size() && (int) this.blocks.get(this.blockIndex).getFrame() == frame) {
            ReplayCommandBlock block = this.blocks.get(this.blockIndex++);
            int required = block.getSize() + used;
            int commandEnd = block.getFirstCommandIndex() + block.getCommandCount();

            if (required >= capacity) {
                this.grow(capacity, required);
                capacity = required;
            }

            for (int commandIndex = block.getFirstCommandIndex(); commandIndex < commandEnd; commandIndex++) {
                ReplayCommand command = this.commands.get(commandIndex);
                int size = 1 + command.getPayload().length;

                this.writeIssuer(entry, command.getPlayerId());
                this.writeCommand(used, command);
                this.writeSize(entry, size);
                entry++;
                used += size;
            }
        }

        if (entry == 0 && this.blockIndex >= this.blocks.size()) {
            return NO_BLOCK;
        }

        this.count = entry;

        return frame;
    }

    /**
     * Runs the loaded entries: adds the commands they run ({@link #readCommands}) in order. The entry count becomes zero.
     *
     * @param frame    the frame the commands are recorded for
     * @param commands the list the commands are added to
     */
    public void run(int frame, @NotNull List<ReplayCommand> commands) {
        this.readCommands(frame, commands);

        this.count = 0;
    }

    /**
     * Reads the commands the loaded entries run without running them: walks each entry's bytes by the command lengths and
     * adds every command that would run, with the entry's issuer byte, in order.
     * <p>
     * A call that grew the buffer reads its copy; any other call reads the image.
     *
     * @param frame    the frame the commands are recorded for
     * @param commands the list the commands are added to
     */
    public void readCommands(int frame, @NotNull List<ReplayCommand> commands) {
        byte[] data = this.grownData != null ? this.grownData : this.image;
        int dataStart = this.grownData != null ? 0 : DATA_OFFSET;
        int offset = 0;

        for (int entry = 0; entry < this.count; entry++) {
            int size = this.readSize(entry);
            int start = dataStart + offset;

            // Issuer bytes and command bytes past the image are memory the buffer does not represent: such an entry does not
            // run, and an entry reaching past the image ends there.
            if (size > 0 && offset >= 0 && start < data.length && ISSUER_OFFSET + entry < IMAGE_SIZE) {
                int issuer = Byte.toUnsignedInt(this.image[ISSUER_OFFSET + entry]);

                ReplayCommandUtils.walk(data, start, Math.min(size, data.length - start), (bytes, commandOffset, commandLength) ->
                        commands.add(new ReplayCommand(frame, issuer, Byte.toUnsignedInt(bytes[commandOffset]),
                                                       Arrays.copyOfRange(bytes, commandOffset + 1, commandOffset + commandLength))));
            }

            offset += size;
        }
    }

    /**
     * Drops the grown copy of the current call; the next call uses the image.
     */
    public void release() {
        this.grownData  = null;
        this.grownSizes = null;
    }

    /**
     * Grows the buffer into a new copy of the required capacity that keeps the current data and sizes up to the current
     * capacity.
     */
    private void grow(int capacity, int required) {
        byte[] data = new byte[required];
        int[] sizes = new int[required];

        if (this.grownData == null || this.grownSizes == null) {
            System.arraycopy(this.image, DATA_OFFSET, data, 0, capacity);

            for (int entry = 0; entry < capacity; entry++) {
                sizes[entry] = this.readSize(entry);
            }
        } else {
            System.arraycopy(this.grownData, 0, data, 0, capacity);
            System.arraycopy(this.grownSizes, 0, sizes, 0, capacity);
        }

        this.grownData  = data;
        this.grownSizes = sizes;
    }

    private void writeIssuer(int entry, int issuer) {
        // An issuer byte past the image is memory the buffer does not represent.
        if (ISSUER_OFFSET + entry < IMAGE_SIZE) {
            this.image[ISSUER_OFFSET + entry] = (byte) issuer;
        }
    }

    private void writeCommand(int used, @NotNull ReplayCommand command) {
        byte[] data = this.grownData != null ? this.grownData : this.image;
        int start = (this.grownData != null ? 0 : DATA_OFFSET) + used;

        data[start] = (byte) command.getTypeId();
        System.arraycopy(command.getPayload(), 0, data, start + 1, command.getPayload().length);
    }

    private int readSize(int entry) {
        if (this.grownSizes != null) {
            return this.grownSizes[entry];
        }

        // A size past the image is memory the buffer does not represent.
        return entry < CAPACITY ? this.imageView.getInt(SIZES_OFFSET + entry * Integer.BYTES) : 0;
    }

    private void writeSize(int entry, int size) {
        if (this.grownSizes != null) {
            this.grownSizes[entry] = size;
        } else {
            this.imageView.putInt(SIZES_OFFSET + entry * Integer.BYTES, size);
        }
    }
}
