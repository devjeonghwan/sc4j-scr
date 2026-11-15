package org.fogofwarlabs.sc4j.scr.data.format.replay;

/**
 * Represents one block of the replay command stream: its frame, its size byte and the commands the loader splits from
 * it.
 * <p>
 * The commands are a range of the replay's command list ({@link ReplayFile#getCommands()}), in stream order. A frame's
 * commands may span several consecutive blocks with the same frame.
 */
public final class ReplayCommandBlock {
    private final long frame;
    private final int size;

    private final int firstCommandIndex;
    private final int commandCount;

    /**
     * Creates a {@link ReplayCommandBlock} instance.
     *
     * @param frame             the frame DWORD of the block header
     * @param size              the size byte of the block header
     * @param firstCommandIndex the index of the block's first command in the replay's command list
     * @param commandCount      the number of commands split from the block
     */
    public ReplayCommandBlock(long frame, int size, int firstCommandIndex, int commandCount) {
        this.frame = frame;
        this.size  = size;

        this.firstCommandIndex = firstCommandIndex;
        this.commandCount      = commandCount;
    }

    /**
     * @return the frame DWORD of the block header
     */
    public long getFrame() {
        return this.frame;
    }

    /**
     * @return the size byte of the block header: the bytes of its player bytes and commands as recorded
     */
    public int getSize() {
        return this.size;
    }

    /**
     * @return the index of the block's first command in the replay's command list
     */
    public int getFirstCommandIndex() {
        return this.firstCommandIndex;
    }

    /**
     * @return the number of commands split from the block
     */
    public int getCommandCount() {
        return this.commandCount;
    }
}
