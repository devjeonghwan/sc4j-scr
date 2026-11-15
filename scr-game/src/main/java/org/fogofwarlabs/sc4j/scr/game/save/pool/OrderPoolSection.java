package org.fogofwarlabs.sc4j.scr.game.save.pool;

import org.fogofwarlabs.sc4j.scr.game.order.OrderPool;
import org.fogofwarlabs.sc4j.scr.game.save.*;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameSetup;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Writes and restores all order slots and their free-list heads.
 * <p>
 * A section holds every order slot, allocated or free, as the classic 20-byte order ({@link OrderPool#writeSaveRecord},
 * without a count: the reader knows the pool size); two plain DWORDs follow, the one-based first and last free orders.
 */
public final class OrderPoolSection implements SaveSection {
    /**
     * The size of the plain DWORDs after the records: the first and the last free order.
     */
    public static final int HEADS_SIZE = 2 * Integer.BYTES;

    /**
     * Creates an {@link OrderPoolSection} instance.
     */
    public OrderPoolSection() {
    }

    /**
     * Writes the part from the saved game.
     *
     * @param context the saved game
     * @param output  the file output
     */
    @Override
    public void write(@NotNull SaveGameContext context, @NotNull SaveFileWriter output) {
        Game game = context.getGame();
        OrderPool orders = SaveGameUtils.require(game.getOrderPool(), "order pool");
        SaveReferences references = new SaveReferences(game);
        int capacity = SaveGameUtils.getNativeCapacity(game, GameSetup.ORDER_LIMIT, orders.getCapacity());
        ByteBuffer records = ByteBuffer.allocate(capacity * OrderPool.SAVE_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN);

        for (int poolIndex = 0; poolIndex < capacity; poolIndex++) {
            orders.writeSaveRecord(poolIndex,
                                   records.slice(poolIndex * OrderPool.SAVE_RECORD_SIZE, OrderPool.SAVE_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN),
                                   references);
        }

        output.writeSection(records.array());
        output.writeDword(references.encodeOrder(orders.getFirstFreeOrder()));
        output.writeDword(references.encodeOrder(orders.getLastFreeOrder()));
    }

    /**
     * Reads the part into the restored game.
     *
     * @param context the restored game
     * @param input   the file input
     */
    @Override
    public void read(@NotNull SaveGameContext context, @NotNull SaveFileReader input) {
        Game game = context.getGame();
        OrderPool orders = SaveGameUtils.require(game.getOrderPool(), "order pool");
        SaveReferences references = new SaveReferences(game);
        int capacity = SaveGameUtils.getNativeCapacity(game, GameSetup.ORDER_LIMIT, orders.getCapacity());
        ByteBuffer records = ByteBuffer.wrap(input.readSection(capacity * OrderPool.SAVE_RECORD_SIZE)).order(ByteOrder.LITTLE_ENDIAN);

        for (int poolIndex = 0; poolIndex < capacity; poolIndex++) {
            orders.readSaveRecord(poolIndex,
                                  records.slice(poolIndex * OrderPool.SAVE_RECORD_SIZE, OrderPool.SAVE_RECORD_SIZE).order(ByteOrder.LITTLE_ENDIAN),
                                  references);
        }

        orders.restoreSavedLists(references.decodeOrder(input.readDword()), references.decodeOrder(input.readDword()),
                                 SaveGameUtils.require(game.getUnitSystem().getUnitPool(), "unit pool"));
    }
}
