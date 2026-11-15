package org.fogofwarlabs.sc4j.scr.game.trigger.eud.region;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicRegion;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.IntSupplier;

/**
 * A four-byte shadowed host region: a read returns the host DWORD until the first write; a write copies the host DWORD
 * into the region's shadow, marks it and changes only the shadow, so the host value itself never changes. The four such
 * regions serve the local player identities: two game-start copies, the local player and the local human, which the
 * loaders set to the same player.
 */
public final class ClassicShadowedHostRegion implements ClassicRegion {
    /**
     * The classic address of the first game-start copy of the local player.
     */
    private static final int FIRST_GAME_START_PLAYER = 0x512678;
    /**
     * The classic address of the second game-start copy of the local player.
     */
    private static final int SECOND_GAME_START_PLAYER = 0x51267C;
    /**
     * The classic address of the local player.
     */
    private static final int LOCAL_PLAYER = 0x512684;
    /**
     * The classic address of the local human.
     */
    private static final int LOCAL_HUMAN = 0x512688;


    /**
     * Creates {@link ClassicRegion} instances for the four local player regions.
     *
     * @param localPlayer the local player the four identities hold
     * @return the four local player regions
     */
    public static @NotNull List<ClassicRegion> createLocalPlayerRegions(@NotNull IntSupplier localPlayer) {
        return List.of(new ClassicShadowedHostRegion(FIRST_GAME_START_PLAYER, localPlayer),
                       new ClassicShadowedHostRegion(SECOND_GAME_START_PLAYER, localPlayer),
                       new ClassicShadowedHostRegion(LOCAL_PLAYER, localPlayer),
                       new ClassicShadowedHostRegion(LOCAL_HUMAN, localPlayer));
    }


    private final int base;
    private final @NotNull IntSupplier host;

    private boolean shadowed;
    private int shadow;


    /**
     * Creates a {@link ClassicShadowedHostRegion} instance.
     *
     * @param base the classic base
     * @param host the host DWORD
     */
    public ClassicShadowedHostRegion(int base, @NotNull IntSupplier host) {
        this.base = base;
        this.host = host;

        this.shadowed = false;
        this.shadow   = 0;
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
        int value = this.shadowed ? this.shadow : this.host.getAsInt();

        return value >>> offset * Byte.SIZE & BYTE_MASK;
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

        int previous = this.shadowed ? this.shadow : this.host.getAsInt();

        this.shadow   = LittleEndianUtils.replaceByte(previous, shift, value);
        this.shadowed = true;
    }
}
