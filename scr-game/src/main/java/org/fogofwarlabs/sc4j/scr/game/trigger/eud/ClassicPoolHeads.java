package org.fogofwarlabs.sc4j.scr.game.trigger.eud;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.game.combat.ProjectilePhase;
import org.fogofwarlabs.sc4j.scr.game.order.OrderPool;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteImagePool;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpritePool;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.region.ClassicStorageRegion;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.unit.ClassicSpriteTableRegion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Holds the classic free-list heads of the world's bullet, image, sprite and order pools.
 * Each head initially names the corresponding free entry in the classic table, or zero for an entry outside its main
 * array. Writes change the stored head and mark its list for commit. A commit follows the next-link DWORDs through
 * the map string section and expands the pool only after reaching a zero link; an unreadable or other-region link
 * leaves the pool unchanged. The heads are created with the EUD address space.
 */
public final class ClassicPoolHeads {
    /**
     * The longest chain walked before an unsupported-operation failure.
     */
    private static final int CHAIN_LIMIT = 1 << 24;

    /**
     * The first free bullet head.
     */
    private static final int BULLET_FIRST_FREE = 0x64EED8;

    /**
     * The last free bullet head.
     */
    private static final int BULLET_LAST_FREE = 0x64EEDC;

    /**
     * The first free image head.
     */
    private static final int IMAGE_FIRST_FREE = 0x57EB68;

    /**
     * The last free image head.
     */
    private static final int IMAGE_LAST_FREE = 0x57EB70;

    /**
     * The first free sprite head.
     */
    private static final int SPRITE_FIRST_FREE = 0x63FE30;

    /**
     * The last free sprite head.
     */
    private static final int SPRITE_LAST_FREE = 0x63FE34;

    /**
     * The first free order head.
     */
    private static final int ORDER_FIRST_FREE = 0x64B2E0;

    /**
     * The last free order head.
     */
    private static final int ORDER_LAST_FREE = 0x64B2E4;

    /**
     * One pool's pair of heads.
     */
    private static final class PoolList {
        private final @NotNull Head first;
        private final @NotNull Head last;
        private final @NotNull IntUnaryOperator expand;

        private boolean modified;

        PoolList(int firstBase, int lastBase, @NotNull IntUnaryOperator expand) {
            this.first  = new Head(firstBase, this);
            this.last   = new Head(lastBase, this);
            this.expand = expand;

            this.modified = false;
        }
    }

    /**
     * A head: the region's own DWORD; a write marks its list modified.
     */
    private static final class Head implements ClassicRegion {
        private final int base;
        private final @NotNull PoolList list;

        private int value;

        Head(int base, @NotNull PoolList list) {
            this.base = base;
            this.list = list;

            this.value = 0;
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
            return Integer.BYTES;
        }


        /**
         * Reads the unsigned byte at an offset.
         *
         * @param offset the byte offset, below {@link #getSize()}
         * @return the unsigned byte at the offset
         */
        @Override
        public int readByte(int offset) {
            return this.value >>> offset * Byte.SIZE & BYTE_MASK;
        }

        /**
         * Stores one byte, changing the domain state that holds it.
         *
         * @param offset the byte offset, below {@link #getSize()}
         * @param value  the byte value in the low eight bits
         */
        @Override
        public void writeByte(int offset, int value) {
            int shift = offset * Byte.SIZE;

            this.value         = LittleEndianUtils.replaceByte(this.value, shift, value);
            this.list.modified = true;
        }
    }

    /**
     * Creates a {@link ClassicPoolHeads} instance with the heads of the world's pools that exist.
     *
     * @param world the world
     * @return the created {@link ClassicPoolHeads} instance
     */
    public static @NotNull ClassicPoolHeads create(@NotNull Game world) {
        return create(world.getFrame() == null ? null : world.getFrame().getProjectiles(),
                      world.getSpriteSystem().getImagePool(),
                      world.getSpriteSystem().getSpritePool(),
                      world.getOrderPool());
    }

    /**
     * Creates a {@link ClassicPoolHeads} instance with the heads of the given pools.
     *
     * @param bullets the bullets, or {@code null}
     * @param images  the images, or {@code null}
     * @param sprites the sprites, or {@code null}
     * @param orders  the orders, or {@code null}
     * @return the created {@link ClassicPoolHeads} instance
     */
    static @NotNull ClassicPoolHeads create(ProjectilePhase.@Nullable Context bullets,
                                            @Nullable SpriteImagePool images,
                                            @Nullable SpritePool sprites,
                                            @Nullable OrderPool orders) {
        ClassicPoolHeads heads = new ClassicPoolHeads();

        if (bullets != null) {
            heads.add(BULLET_FIRST_FREE, BULLET_LAST_FREE,
                      ClassicStorageRegion.BULLET_TABLE, ClassicStorageRegion.BULLET_ENTRY_SIZE, bullets.getCapacity(),
                      bullets.getFirstFree(), findLastFreeIndex(bullets.getFirstFree(), bullets::getNext),
                      bullets::expandFreeList);
        }

        if (images != null) {
            heads.add(IMAGE_FIRST_FREE, IMAGE_LAST_FREE,
                      ClassicStorageRegion.IMAGE_TABLE, ClassicStorageRegion.IMAGE_ENTRY_SIZE, images.getCapacity(),
                      images.getFirstFreeIndex(), findLastFreeIndex(images.getFirstFreeIndex(), images::getNextFreeIndex),
                      images::expandFreeList);
        }

        if (sprites != null) {
            heads.add(SPRITE_FIRST_FREE, SPRITE_LAST_FREE,
                      ClassicSpriteTableRegion.BASE, ClassicSpriteTableRegion.ENTRY_SIZE, sprites.getNativeCapacity(),
                      sprites.getFirstFreeIndex(), sprites.getLastFreeIndex(),
                      sprites::expandFreeList);
        }

        if (orders != null) {
            OrderPool.Order first = orders.getFirstFreeOrder();
            OrderPool.Order last = orders.getLastFreeOrder();

            heads.add(ORDER_FIRST_FREE, ORDER_LAST_FREE,
                      ClassicStorageRegion.TYPE_3F_TABLE, ClassicStorageRegion.TYPE_3F_ENTRY_SIZE, orders.getNativeCapacity(),
                      first == null ? -1 : first.getPoolIndex(), last == null ? -1 : last.getPoolIndex(),
                      orders::expandFreeList);
        }

        return heads;
    }

    /**
     * Finds the last entry of a free list that keeps no tail here, reached through the next links.
     *
     * @param firstFree the first free entry, or a negative value for an empty list
     * @param next      the next link of an entry
     * @return the last entry, or {@code firstFree} for an empty list
     */
    private static int findLastFreeIndex(int firstFree, @NotNull IntUnaryOperator next) {
        int last = firstFree;

        for (int index = firstFree; index >= 0; index = next.applyAsInt(index)) {
            last = index;
        }

        return last;
    }

    /**
     * Counts a classic chain through readable map string-section links.
     *
     * @param space the address space the chain is read through
     * @param head  the classic address of the first node, or {@code 0}
     * @return the number of nodes, or {@code -1} when a link is unreadable or outside the map string section
     * @throws UnsupportedOperationException if the chain exceeds {@link #CHAIN_LIMIT} nodes
     */
    private static int countChainNodes(@NotNull ClassicAddressSpace space, int head) {
        int count = 0;
        int[] word = new int[1];

        for (int node = head; node != 0; count++) {
            if (count >= CHAIN_LIMIT) {
                throw Exceptions.newUnsupportedOperationException("The EUD free-list chain from 0x{0} exceeds {1} nodes; " +
                                                                  "native walks a cyclic chain without end.",
                                                                  String.format("%08X", head),
                                                                  CHAIN_LIMIT);
            }

            int link = node + Integer.BYTES;
            int aligned = link & ~ClassicRegion.DWORD_OFFSET_MASK;
            // The region of the last aligned DWORD the read reaches types the link.
            int typed = aligned == link ? aligned : aligned + Integer.BYTES;

            if (!space.readQuietly(link, word)) {
                return -1;
            }

            node = word[0];

            if (!ClassicDynamicRegionsUtils.isStringSection(space.findRegion(typed))) {
                return -1;
            }
        }

        return count;
    }


    private final @NotNull List<ClassicRegion> regions;

    private final @NotNull List<PoolList> lists;

    private ClassicPoolHeads() {
        this.regions = new ArrayList<>();
        this.lists   = new ArrayList<>();
    }

    /**
     * @return the head regions
     */
    public @NotNull List<ClassicRegion> getRegions() {
        return this.regions;
    }


    /**
     * Commits every list whose head a write modified.
     *
     * @param space the address space the chain is read through
     * @throws UnsupportedOperationException if a written chain exceeds the walked limit
     */
    public void commit(@NotNull ClassicAddressSpace space) {
        for (PoolList list : this.lists) {
            if (!list.modified) {
                continue;
            }

            list.modified = false;

            int count = countChainNodes(space, list.first.value);

            if (count >= 0) {
                list.expand.applyAsInt(count);
            }
        }
    }

    private void add(int firstBase, int lastBase,
                     int table, int entrySize, int mainCount,
                     int firstFree, int lastFree,
                     @NotNull IntUnaryOperator expand) {
        PoolList list = new PoolList(firstBase, lastBase, expand);

        list.first.value = firstFree < 0 || firstFree >= mainCount ? 0 : table + firstFree * entrySize;
        list.last.value  = lastFree < 0 || lastFree >= mainCount ? 0 : table + lastFree * entrySize;

        this.lists.add(list);
        this.regions.add(list.first);
        this.regions.add(list.last);
    }
}
