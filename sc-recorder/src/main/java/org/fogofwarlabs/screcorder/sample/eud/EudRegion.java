package org.fogofwarlabs.screcorder.sample.eud;

/**
 * Contains one emulator region descriptor, including native process addresses.
 */
public final class EudRegion {
    private final long key;
    private final long base;
    private final long size;
    private final long type;
    private final long virtualTable;
    private final long host;
    private final long bound;

    /**
     * Creates a {@link EudRegion} instance.
     *
     * @param key          the unsigned classic address key
     * @param base         the unsigned classic region base
     * @param size         the unsigned region size
     * @param type         the unsigned native type and flags
     * @param virtualTable the normalized virtual table pointer (image tag plus offset)
     * @param host         the normalized host pointer (image tag plus offset, or the other-pointer tag)
     * @param bound        the unsigned native bound
     */
    public EudRegion(long key, long base, long size, long type, long virtualTable, long host, long bound) {
        this.key          = key;
        this.base         = base;
        this.size         = size;
        this.type         = type;
        this.virtualTable = virtualTable;
        this.host         = host;
        this.bound        = bound;
    }


    /**
     * @return the unsigned classic address key
     */
    public long getKey() {
        return this.key;
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
     * @return the unsigned native type and flags
     */
    public long getType() {
        return this.type;
    }

    /**
     * @return the normalized virtual table pointer
     */
    public long getVirtualTable() {
        return this.virtualTable;
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
}
