package org.fogofwarlabs.screcorder.sample.eud;

import org.jetbrains.annotations.Nullable;

/**
 * Contains one region host observation and the supported served value or flags {@code 0x02} head.
 */
public final class EudHostRegion {
    private final long key;
    private final long type;
    private final long host;
    private final long bound;
    private final long base;
    private final long size;
    private final @Nullable Long value;
    private final byte @Nullable [] head;

    /**
     * Creates a {@link EudHostRegion} instance.
     *
     * @param key   the unsigned classic address key
     * @param type  the unsigned native type and flags
     * @param host  the normalized host pointer (image tag plus offset, or the other-pointer tag)
     * @param bound the unsigned native bound
     * @param base  the unsigned classic region base
     * @param size  the unsigned region size
     * @param value the served DWORD for flags {@code 0x04} or {@code 0x24}, otherwise {@code null}
     * @param head  the {@code 64} host bytes for flags {@code 0x02}, otherwise {@code null}
     */
    public EudHostRegion(long key, long type, long host, long bound, long base, long size, @Nullable Long value, byte @Nullable [] head) {
        this.key   = key;
        this.type  = type;
        this.host  = host;
        this.bound = bound;
        this.base  = base;
        this.size  = size;
        this.value = value;
        this.head  = head == null ? null : head.clone();
    }


    /**
     * @return the unsigned classic address key
     */
    public long getKey() {
        return this.key;
    }

    /**
     * @return the unsigned native type and flags
     */
    public long getType() {
        return this.type;
    }

    /**
     * @return the normalized host pointer
     */
    public long getHost() {
        return this.host;
    }

    /**
     * @return the unsigned native bound
     */
    public long getBound() {
        return this.bound;
    }

    /**
     * @return the unsigned classic region base
     */
    public long getBase() {
        return this.base;
    }

    /**
     * @return the unsigned region size
     */
    public long getSize() {
        return this.size;
    }

    /**
     * @return the served DWORD for flags {@code 0x04} or {@code 0x24}, otherwise {@code null}
     */
    public @Nullable Long getValue() {
        return this.value;
    }

    /**
     * @return the {@code 64} host bytes for flags {@code 0x02}, otherwise {@code null}, copied when present
     */
    public byte @Nullable [] getHead() {
        return this.head == null ? null : this.head.clone();
    }
}
