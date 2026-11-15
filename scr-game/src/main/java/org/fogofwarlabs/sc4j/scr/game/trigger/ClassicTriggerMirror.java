package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicAddressSpace;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicRegion;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.NativeRegionLayout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the classic trigger lists of Remastered's EUD emulation: the eight list heads and one trigger node region per
 * running trigger, which the trigger loop walks instead of its own lists while the map's triggers use EUD operands.
 * <p>
 * A head is a Storm list link of three DWORDs in the region's own storage: unused, the last node's link address and the
 * first node's link address; an empty list holds its own link address ({@code base + 4}) as the last link and its
 * inverse as the first. At map load every running trigger of every player gets a node region appended to its player's
 * list: the previous link (the head's link address for the first node), the next link (the inverse of the head's link
 * address for the last node, which ends the walk as a value that is not positive) and the node's trigger record
 * ({@link RunningTrigger#readByte}).
 */
final class ClassicTriggerMirror {
    /**
     * The classic address of the first list head.
     */
    static final int HEAD_BASE = 0x51A280;
    /**
     * The size of one list head.
     */
    static final int HEAD_SIZE = 12;
    /**
     * The size of a trigger node region.
     */
    static final int NODE_SIZE = 0x968;
    /**
     * The offset of the trigger record in a node.
     */
    static final int RECORD_OFFSET = 8;

    /**
     * The DWORDs of one list head.
     */
    private static final int HEAD_DWORDS = HEAD_SIZE / Integer.BYTES;
    /**
     * The head DWORD of the last node's link address, the head's own link field.
     */
    private static final int LAST_LINK_DWORD = 1;
    /**
     * The head DWORD of the first node's link address.
     */
    private static final int FIRST_LINK_DWORD = 2;
    /**
     * The offset of the head's link field, which the empty list's links name.
     */
    private static final int PREVIOUS_FIELD = LAST_LINK_DWORD * Integer.BYTES;

    /**
     * Serves the eight list heads; their twelve bytes are the region's own storage, so writes only change the stored
     * links.
     */
    private final class HeadRegion implements ClassicRegion {
        /**
         * @return the classic base address
         */
        @Override
        public int getBase() {
            return HEAD_BASE;
        }

        /**
         * @return the size in bytes
         */
        @Override
        public int getSize() {
            return PlayerTable.PLAYABLE_PLAYER_SLOTS * HEAD_SIZE;
        }


        /**
         * Reads the unsigned byte at an offset.
         *
         * @param offset the byte offset, below {@link #getSize()}
         * @return the unsigned byte at the offset
         */
        @Override
        public int readByte(int offset) {
            return ClassicTriggerMirror.this.heads[offset / Integer.BYTES] >>> (offset % Integer.BYTES) * Byte.SIZE & BYTE_MASK;
        }

        /**
         * Stores one byte, changing the domain state that holds it.
         *
         * @param offset the byte offset, below {@link #getSize()}
         * @param value  the byte value in the low eight bits
         * @throws UnsupportedOperationException if the region, or this part of it, rejects writes
         */
        @Override
        public void writeByte(int offset, int value) {
            int shift = (offset % Integer.BYTES) * Byte.SIZE;
            int index = offset / Integer.BYTES;
            int[] heads = ClassicTriggerMirror.this.heads;

            heads[index] = LittleEndianUtils.replaceByte(heads[index], shift, value);
        }

        /**
         * Reads an aligned little-endian DWORD; regions whose domain values are DWORDs override it to read them whole.
         *
         * @param offset the byte offset of the DWORD, with four bytes inside the region
         * @return the DWORD
         */
        @Override
        public int readDword(int offset) {
            return (offset & ClassicRegion.DWORD_OFFSET_MASK) == 0 ? ClassicTriggerMirror.this.heads[offset / Integer.BYTES]
                                                                   : ClassicRegion.super.readDword(offset);
        }

        /**
         * Stores a little-endian DWORD; regions whose domain values are DWORDs override it to store them whole.
         *
         * @param offset the byte offset of the DWORD, with four bytes inside the region
         * @param value  the DWORD
         */
        @Override
        public void writeDword(int offset, int value) {
            if ((offset & ClassicRegion.DWORD_OFFSET_MASK) == 0) {
                ClassicTriggerMirror.this.heads[offset / Integer.BYTES] = value;
            } else {
                ClassicRegion.super.writeDword(offset, value);
            }
        }
    }

    /**
     * Serves one trigger node: the previous and next links are fields of the region, the record is the running trigger's.
     */
    private static final class NodeRegion implements ClassicRegion {
        private final int base;
        private final @NotNull RunningTrigger trigger;

        private int previous;
        private int next;

        /**
         * Creates a {@link NodeRegion} instance with zero links.
         *
         * @param base    the classic base address of the node
         * @param trigger the running trigger whose record the node shows
         */
        NodeRegion(int base, @NotNull RunningTrigger trigger) {
            this.base    = base;
            this.trigger = trigger;

            this.previous = 0;
            this.next     = 0;
        }

        /**
         * @return the classic base address
         */
        @Override
        public int getBase() {
            return this.base;
        }

        /**
         * @return the size in bytes
         */
        @Override
        public int getSize() {
            return NODE_SIZE;
        }


        /**
         * Reads the unsigned byte at an offset.
         *
         * @param offset the byte offset, below {@link #getSize()}
         * @return the unsigned byte at the offset
         */
        @Override
        public int readByte(int offset) {
            if (offset < RECORD_OFFSET) {
                int link = offset < Integer.BYTES ? this.previous : this.next;

                return link >>> (offset % Integer.BYTES) * Byte.SIZE & BYTE_MASK;
            }

            return this.trigger.readByte(offset - RECORD_OFFSET);
        }

        /**
         * Stores one byte, changing the domain state that holds it.
         *
         * @param offset the byte offset, below {@link #getSize()}
         * @param value  the byte value in the low eight bits
         * @throws UnsupportedOperationException if the region, or this part of it, rejects writes
         */
        @Override
        public void writeByte(int offset, int value) {
            if (offset < RECORD_OFFSET) {
                int shift = (offset % Integer.BYTES) * Byte.SIZE;

                if (offset < Integer.BYTES) {
                    this.previous = LittleEndianUtils.replaceByte(this.previous, shift, value);
                } else {
                    this.next = LittleEndianUtils.replaceByte(this.next, shift, value);
                }

                return;
            }

            this.trigger.writeByte(offset - RECORD_OFFSET, value);
        }

        /**
         * Reads an aligned little-endian DWORD; regions whose domain values are DWORDs override it to read them whole.
         *
         * @param offset the byte offset of the DWORD, with four bytes inside the region
         * @return the DWORD
         */
        @Override
        public int readDword(int offset) {
            if (offset == 0) {
                return this.previous;
            }

            if (offset == Integer.BYTES) {
                return this.next;
            }

            return ClassicRegion.super.readDword(offset);
        }

        /**
         * Stores a little-endian DWORD; regions whose domain values are DWORDs override it to store them whole.
         *
         * @param offset the byte offset of the DWORD, with four bytes inside the region
         * @param value  the DWORD
         */
        @Override
        public void writeDword(int offset, int value) {
            if (offset == 0) {
                this.previous = value;
            } else if (offset == Integer.BYTES) {
                this.next = value;
            } else {
                ClassicRegion.super.writeDword(offset, value);
            }
        }
    }

    /**
     * Returns the running trigger of a node region.
     *
     * @param region a region found at a link address
     * @return the running trigger of a node region, or {@code null} for any other region
     */
    static @Nullable RunningTrigger getNodeTrigger(@NotNull ClassicRegion region) {
        return region instanceof NodeRegion node ? node.trigger : null;
    }

    /**
     * @param player the list owner
     * @return the link address of the player's head, the address of its link field
     */
    private static int getHeadLink(int player) {
        return HEAD_BASE + player * HEAD_SIZE + PREVIOUS_FIELD;
    }


    private final int @NotNull [] heads;
    private final @NotNull List<ClassicRegion> regions;
    private final @Nullable NodeRegion @NotNull [] lastNodes;

    private int nextBase;

    /**
     * Creates a {@link ClassicTriggerMirror} instance with empty lists.
     *
     * @param firstNodeBase the classic address the first node is allocated at
     */
    ClassicTriggerMirror(int firstNodeBase) {
        this.heads     = new int[PlayerTable.PLAYABLE_PLAYER_SLOTS * HEAD_DWORDS];
        this.regions   = new ArrayList<>();
        this.lastNodes = new NodeRegion[PlayerTable.PLAYABLE_PLAYER_SLOTS];

        this.nextBase = firstNodeBase;

        for (int player = 0; player < PlayerTable.PLAYABLE_PLAYER_SLOTS; player++) {
            this.resetHead(player);
        }

        this.regions.add(new HeadRegion());
    }

    /**
     * @return the head region followed by every node region
     */
    @NotNull List<ClassicRegion> getRegions() {
        return this.regions;
    }

    /**
     * @param player the list owner
     * @return the first link of the player's list, the first link DWORD of the head
     */
    int getFirstLink(int player) {
        return this.heads[player * HEAD_DWORDS + FIRST_LINK_DWORD];
    }

    /**
     * @param player the list owner
     * @return {@code true} if the player's list holds no node (the last link is the head's own), {@code false} otherwise
     */
    boolean isEmpty(int player) {
        return this.heads[player * HEAD_DWORDS + LAST_LINK_DWORD] == getHeadLink(player);
    }


    /**
     * Appends a node for a running trigger to a player's list, as the map load does.
     *
     * @param player  the list owner
     * @param trigger the running trigger whose record the node shows
     */
    void append(int player, @NotNull RunningTrigger trigger) {
        NodeRegion node = new NodeRegion(this.nextBase, trigger);

        this.nextBase = NativeRegionLayout.alignAddress(this.nextBase + NODE_SIZE);

        int link = getHeadLink(player);

        node.next = ~link;

        if (this.heads[player * HEAD_DWORDS + FIRST_LINK_DWORD] == ~link) {
            this.heads[player * HEAD_DWORDS + FIRST_LINK_DWORD] = node.base;
        }

        node.previous = this.heads[player * HEAD_DWORDS + LAST_LINK_DWORD];

        NodeRegion last = this.lastNodes[player];

        if (node.previous != link && last != null && last.base == node.previous) {
            last.next = node.base;
        }

        this.heads[player * HEAD_DWORDS + LAST_LINK_DWORD] = node.base;
        this.lastNodes[player]                             = node;

        this.regions.add(node);
    }

    /**
     * Releases a player's classic list as the head region's release does for a player whose result ends its triggers:
     * from the first link, while the link is positive and even, the next link is read at {@code link + 4} and the node
     * region holding the link is removed from the address space; a link that cannot be read ends the walk after its node.
     * The head then holds the empty list.
     *
     * @param player the list owner
     * @param space  the address space holding the nodes
     */
    void releaseList(int player, @NotNull ClassicAddressSpace space) {
        int[] next = new int[1];
        int link = this.getFirstLink(player);

        while (link > 0 && (link & 1) == 0) {
            boolean read = space.readQuietly(link + Integer.BYTES, next);
            ClassicRegion region = space.findRegion(link);

            if (region instanceof NodeRegion) {
                space.remove(region);
                this.regions.remove(region);
            }

            if (!read) {
                break;
            }

            link = next[0];
        }

        this.resetHead(player);

        this.lastNodes[player] = null;
    }

    /**
     * Makes a player's head hold the empty list: its own link address as the last link and the inverse as the first.
     *
     * @param player the list owner
     */
    private void resetHead(int player) {
        int link = getHeadLink(player);

        this.heads[player * HEAD_DWORDS]                    = 0;
        this.heads[player * HEAD_DWORDS + LAST_LINK_DWORD]  = link;
        this.heads[player * HEAD_DWORDS + FIRST_LINK_DWORD] = ~link;
    }
}
