package org.fogofwarlabs.sc4j.scr.game.save;

import org.fogofwarlabs.sc4j.scr.game.ai.ComputerPlayerPhase;
import org.fogofwarlabs.sc4j.scr.game.ai.ComputerUnitRecord;
import org.fogofwarlabs.sc4j.scr.game.order.OrderPool;
import org.fogofwarlabs.sc4j.scr.game.path.PathPool;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteEffect;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpriteEffectPool;
import org.fogofwarlabs.sc4j.scr.game.sprite.SpritePool;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitHandle;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.UncheckedIOException;

/**
 * Converts pooled game references to saved-record values and back for one game:
 * <ul>
 *     <li>a unit is its handle (one-based slot and generation; decoded back without the generation check);</li>
 *     <li>a sprite, an order and a thingy of the first list are their one-based slots;</li>
 *     <li>a path is its byte offset in the path pool of the pathing data ({@link PathPool#SAVED_RECORD_SIZE}-byte
 *     paths);</li>
 *     <li>a computer unit record is its kind in the high WORD and its one-based pool index in the low WORD.</li>
 * </ul>
 * The value {@code 0} stands for no object everywhere.
 */
public final class SaveReferences {
    private static final int RECORD_KIND_SHIFT = 16;
    private static final int RECORD_INDEX_MASK = 0xFFFF;

    /**
     * Encodes a sprite as its one-based slot.
     *
     * @param sprite a sprite, or {@code null}
     * @return its one-based slot, or {@code 0}
     */
    public static int encodeSprite(@Nullable Sprite sprite) {
        return sprite == null ? 0 : sprite.getPoolIndex() + 1;
    }


    private final @NotNull UnitPool units;
    private final @NotNull SpritePool sprites;
    private final @NotNull OrderPool orders;
    private final @NotNull SpriteEffectPool thingies;
    private final @Nullable ComputerPlayerPhase computers;

    /**
     * Creates a {@link SaveReferences} instance over a game's pools.
     *
     * @param game the game
     */
    public SaveReferences(@NotNull Game game) {
        this.units     = SaveGameUtils.require(game.getUnitSystem().getUnitPool(), "unit pool");
        this.sprites   = SaveGameUtils.require(game.getSpriteSystem().getSpritePool(), "sprite pool");
        this.orders    = SaveGameUtils.require(game.getOrderPool(), "order pool");
        this.thingies  = SaveGameUtils.require(game.getSpriteSystem().getSpriteEffectPool(), "sprite effect pool");
        this.computers = game.getComputerPlayerPhase();
    }

    /**
     * Encodes a unit as its handle.
     *
     * @param unit a unit, or {@code null}
     * @return its handle, or {@code 0}
     */
    public int encodeUnit(@Nullable Unit unit) {
        return unit == null ? 0 : this.units.getHandle(unit.getIndex());
    }

    /**
     * Encodes a one-based unit slot as its handle.
     *
     * @param poolIndex a one-based unit slot, or {@code 0}
     * @return its handle, or {@code 0}
     */
    public int encodeUnitIndex(int poolIndex) {
        return poolIndex == 0 ? 0 : this.units.getHandle(poolIndex);
    }

    /**
     * Decodes a saved handle to the unit of its slot, without the generation check.
     *
     * @param handle a saved handle
     * @return the unit of the handle's slot, or {@code null} for {@code 0}
     * @throws UncheckedIOException if the handle names no slot of the unit pool
     */
    public @Nullable Unit decodeUnit(int handle) {
        int poolIndex = this.decodeUnitIndex(handle);

        return poolIndex == 0 ? null : this.units.getUnit(poolIndex);
    }

    /**
     * Decodes a saved handle to its one-based slot, without the generation check.
     *
     * @param handle a saved handle
     * @return the one-based slot of the handle, or {@code 0} for {@code 0}
     * @throws UncheckedIOException if the handle names no slot of the unit pool
     */
    public int decodeUnitIndex(int handle) {
        if (handle == 0) {
            return 0;
        }

        int poolIndex = UnitHandle.getPoolIndex(handle, this.units.getCapacity());

        if (poolIndex == 0 || poolIndex > this.units.getCapacity()) {
            throw SaveFileReader.createFailure("A saved unit handle 0x{0} names no slot.", Integer.toHexString(handle));
        }

        return poolIndex;
    }

    /**
     * Resolves a saved handle with liveness and generation checks.
     *
     * @param handle a saved handle
     * @return the live unit of the handle, or {@code null} for {@code 0}, a dead or a stale handle
     */
    public @Nullable Unit decodeLiveUnit(int handle) {
        return this.units.resolveHandle(handle);
    }

    /**
     * Decodes a saved one-based slot to its sprite.
     *
     * @param value a saved one-based slot
     * @return the sprite, or {@code null} for {@code 0}
     * @throws UncheckedIOException if the value names no slot of the sprite pool
     */
    public @Nullable Sprite decodeSprite(int value) {
        if (value == 0) {
            return null;
        }

        if (value < 0 || value > this.sprites.getCapacity()) {
            throw SaveFileReader.createFailure("A saved sprite reference {0} names no slot.", value);
        }

        return this.sprites.getSprite(value - 1);
    }

    /**
     * Encodes an order as its one-based slot.
     *
     * @param order an order, or {@code null}
     * @return its one-based slot, or {@code 0}
     */
    public int encodeOrder(OrderPool.@Nullable Order order) {
        return order == null ? 0 : order.getPoolIndex() + 1;
    }

    /**
     * Decodes a saved one-based slot to its order.
     *
     * @param value a saved one-based slot
     * @return the order, or {@code null} for {@code 0}
     * @throws UncheckedIOException if the value names no slot of the order pool
     */
    public OrderPool.@Nullable Order decodeOrder(int value) {
        if (value == 0) {
            return null;
        }

        if (value < 0 || value > this.orders.getCapacity()) {
            throw SaveFileReader.createFailure("A saved order reference {0} names no slot.", value);
        }

        return this.orders.getOrder(value - 1);
    }

    /**
     * Encodes a thingy of the first list as its one-based slot.
     *
     * @param thingy a thingy of the first list, or {@code null}
     * @return its one-based slot, or {@code 0}
     */
    public int encodeThingy(@Nullable SpriteEffect thingy) {
        return thingy == null ? 0 : thingy.getPoolIndex() + 1;
    }

    /**
     * Decodes a saved one-based slot of the first thingy list to its thingy.
     *
     * @param value a saved one-based slot of the first thingy list
     * @return the thingy, or {@code null} for {@code 0}
     * @throws UncheckedIOException if the value names no slot of the first thingy list
     */
    public @Nullable SpriteEffect decodeThingy(int value) {
        if (value == 0) {
            return null;
        }

        if (value < 0 || value > this.thingies.getCapacity()) {
            throw SaveFileReader.createFailure("A saved sprite effect reference {0} names no slot.", value);
        }

        return this.thingies.getSpriteEffect(value - 1);
    }

    /**
     * Encodes a path index as its byte offset in the path pool.
     *
     * @param pathIndex a zero-based path index, or {@code -1}
     * @return its byte offset in the path pool, or {@code 0}
     */
    public int encodePath(int pathIndex) {
        return pathIndex < 0 ? 0 : pathIndex * PathPool.SAVED_RECORD_SIZE;
    }

    /**
     * Decodes a saved byte offset in the path pool to its path index.
     *
     * @param value a saved byte offset in the path pool
     * @return the zero-based path index, or {@code -1} for {@code 0}
     * @throws UncheckedIOException if the value is negative or not a multiple of the path record size
     */
    public int decodePath(int value) {
        if (value == 0) {
            return -1;
        }

        if (value < 0 || value % PathPool.SAVED_RECORD_SIZE != 0) {
            throw SaveFileReader.createFailure("A saved path offset {0} names no path.", value);
        }

        return value / PathPool.SAVED_RECORD_SIZE;
    }

    /**
     * Encodes a computer unit record as its kind and one-based pool index.
     *
     * @param record a computer unit record, or {@code null}
     * @return its kind and one-based pool index, or {@code 0}
     */
    public int encodeComputerRecord(@Nullable ComputerUnitRecord record) {
        return record == null ? 0 : record.getKind() << RECORD_KIND_SHIFT | record.getPoolIndex() + 1;
    }

    /**
     * Decodes a saved kind and one-based pool index to its computer unit record.
     *
     * @param value a saved kind and one-based pool index
     * @return the record, or {@code null} for {@code 0}
     * @throws UncheckedIOException if the game has no computer players or the value names no record
     */
    public @Nullable ComputerUnitRecord decodeComputerRecord(int value) {
        if (value == 0) {
            return null;
        }

        int index = value & RECORD_INDEX_MASK;

        if (this.computers == null || index == 0) {
            throw SaveFileReader.createFailure("A saved computer unit record reference 0x{0} names no record.", Integer.toHexString(value));
        }

        try {
            return this.computers.getUnitRecord(value >>> RECORD_KIND_SHIFT, index - 1);
        } catch (IndexOutOfBoundsException exception) {
            throw SaveFileReader.createFailure("A saved computer unit record reference 0x{0} names no record.", Integer.toHexString(value));
        }
    }
}
