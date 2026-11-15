package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicRegion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds the records one player's trigger walk ran last, by their position in the walk: the link that named each, its
 * region, and for a node its execution and the condition rows of its definition.
 * <p>
 * A walk that meets the same link at a position runs the same record, so it needs no region lookup while the region
 * layout is the same. Only aligned links whose next-link DWORD lies in their region are held, at the first
 * {@link #POSITION_LIMIT} positions; the condition rows are held once per node, for at most as many nodes.
 */
final class WalkCache {
    /**
     * The mask that reads a DWORD as an unsigned value.
     */
    private static final long DWORD_MASK = 0xFFFFFFFFL;

    /**
     * The walk positions held; a walk that runs more records (a looping chain) runs the rest without the cache.
     */
    private static final int POSITION_LIMIT = 1 << 16;

    private int @NotNull [] links;
    private @Nullable ClassicRegion @NotNull [] regions;
    private @Nullable RunningTrigger @NotNull [] nodes;
    /**
     * The condition rows of each node in {@link #nodes}, shared by its positions ({@link #nodeRows}).
     */
    private @Nullable TriggerConditions.ConditionRows @NotNull [] nodeConditions;

    /**
     * The condition rows of every held node; executions compare by identity, and the insertion order keeps a saved game's
     * order of the rows.
     */
    private final @NotNull Map<RunningTrigger, TriggerConditions.ConditionRows> nodeRows;

    /**
     * The region layout the entries belong to.
     */
    private int layoutVersion;

    /**
     * Creates an empty {@link WalkCache} instance.
     */
    WalkCache() {
        this.links          = new int[0];
        this.regions        = new ClassicRegion[0];
        this.nodes          = new RunningTrigger[0];
        this.nodeConditions = new TriggerConditions.ConditionRows[0];

        this.nodeRows = new LinkedHashMap<>();

        this.layoutVersion = -1;
    }

    /**
     * Returns the region of the record held at a walk position.
     *
     * @param position the walk position, one that {@link #holds} accepted
     * @return the region
     * @throws IllegalStateException if the position holds no record
     */
    @NotNull ClassicRegion getRegion(int position) {
        ClassicRegion region = this.regions[position];

        if (region == null) {
            throw Exceptions.newIllegalStateException("The walk position {0} holds no record.", position);
        }

        return region;
    }

    /**
     * @param position the walk position, one that {@link #holds} accepted
     * @return the offset of the link in its region
     */
    int getLinkOffset(int position) {
        return (int) ((this.links[position] & DWORD_MASK) - (this.getRegion(position).getBase() & DWORD_MASK));
    }

    /**
     * @param position the walk position, one that {@link #holds} accepted
     * @return the node held at the walk position, or {@code null} for a record inside another region
     */
    @Nullable RunningTrigger getNode(int position) {
        return this.nodes[position];
    }

    /**
     * Returns the condition rows of the node held at a walk position, described for the node's current definition.
     *
     * @param position the walk position of a node
     * @return the condition rows
     * @throws IllegalStateException if the position holds no node
     */
    @NotNull TriggerConditions.ConditionRows getNodeConditions(int position) {
        TriggerConditions.ConditionRows conditions = this.nodeConditions[position];

        if (conditions == null) {
            throw Exceptions.newIllegalStateException("The walk position {0} holds no node.", position);
        }

        conditions.describe(this.nodes[position].definition);

        return conditions;
    }


    /**
     * Drops the entries when the region layout changed since they were stored.
     *
     * @param layoutVersion the current region layout version
     */
    void validate(int layoutVersion) {
        if (layoutVersion == this.layoutVersion) {
            return;
        }

        Arrays.fill(this.regions, null);
        Arrays.fill(this.nodes, null);
        Arrays.fill(this.nodeConditions, null);
        this.nodeRows.clear();

        this.layoutVersion = layoutVersion;
    }

    /**
     * Tests whether a walk position holds the record of a link.
     *
     * @param position the walk position
     * @param link     the link
     * @return {@code true} if the walk position holds the record of the link, {@code false} otherwise
     */
    boolean holds(int position, int link) {
        return position < this.links.length && this.links[position] == link && this.regions[position] != null;
    }

    /**
     * Holds a record inside another region at a walk position.
     *
     * @param position the walk position
     * @param link     the link that named the record
     * @param region   the region holding the link
     */
    void storeRecord(int position, int link, @NotNull ClassicRegion region) {
        if (!this.store(position, link, region)) {
            return;
        }

        this.nodes[position]          = null;
        this.nodeConditions[position] = null;
    }

    /**
     * Holds a node at a walk position.
     *
     * @param position the walk position
     * @param link     the link that named the node
     * @param region   the node region
     * @param node     the node's execution
     * @return {@code true} if the position is held, {@code false} otherwise
     */
    boolean storeNode(int position, int link, @NotNull ClassicRegion region, @NotNull RunningTrigger node) {
        if (!this.store(position, link, region)) {
            return false;
        }

        TriggerConditions.ConditionRows conditions = this.nodeRows.get(node);

        if (conditions == null) {
            if (this.nodeRows.size() >= POSITION_LIMIT) {
                this.nodeRows.clear();
            }

            conditions = new TriggerConditions.ConditionRows();

            this.nodeRows.put(node, conditions);
        }

        this.nodes[position]          = node;
        this.nodeConditions[position] = conditions;

        return true;
    }

    /**
     * Holds the link and region of a walk position, growing the entries up to {@link #POSITION_LIMIT} positions.
     *
     * @param position the walk position
     * @param link     the link that named the record
     * @param region   the region holding the link
     * @return {@code true} if the position is held, {@code false} otherwise
     */
    private boolean store(int position, int link, @NotNull ClassicRegion region) {
        if (position >= POSITION_LIMIT) {
            return false;
        }

        if (position >= this.links.length) {
            int length = Math.min(POSITION_LIMIT, Math.max(position + 1, this.links.length * 2));

            this.links          = Arrays.copyOf(this.links, length);
            this.regions        = Arrays.copyOf(this.regions, length);
            this.nodes          = Arrays.copyOf(this.nodes, length);
            this.nodeConditions = Arrays.copyOf(this.nodeConditions, length);
        }

        this.links[position]   = link;
        this.regions[position] = region;

        return true;
    }
}
