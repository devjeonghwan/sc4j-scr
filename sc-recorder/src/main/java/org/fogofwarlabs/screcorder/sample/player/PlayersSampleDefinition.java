package org.fogofwarlabs.screcorder.sample.player;

import org.fogofwarlabs.screcorder.capture.sampling.CaptureContext;
import org.fogofwarlabs.screcorder.capture.sampling.Sampler;
import org.fogofwarlabs.screcorder.exception.Exceptions;
import org.fogofwarlabs.screcorder.sample.SampleDefinition;
import org.fogofwarlabs.screcorder.sample.io.SampleReader;
import org.fogofwarlabs.screcorder.sample.io.SampleWriter;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines players payload reading, writing and bulk memory sampling.
 */
public final class PlayersSampleDefinition extends SampleDefinition<PlayersSample> {
    private static final int SAMPLE_IDENTIFIER = 1;
    private static final int PLAYER_COUNT = 8;
    private static final int ROW_BYTES = 53;

    private static final int PLAYER_CONTEXT_BYTES = 0x31F4;
    private static final int PLAYER_MINERALS_OFFSET = 0x0;
    private static final int PLAYER_GAS_OFFSET = 0x30;
    private static final int PLAYER_GATHERED_MINERALS_OFFSET = 0x90;
    private static final int PLAYER_GATHERED_GAS_OFFSET = 0x60;
    private static final int PLAYER_ZERG_SUPPLY_PROVIDED_OFFSET = 0x3054;
    private static final int PLAYER_ZERG_SUPPLY_USED_OFFSET = 0x3084;
    private static final int PLAYER_ZERG_SUPPLY_CAP_OFFSET = 0x30B4;
    private static final int PLAYER_TERRAN_SUPPLY_PROVIDED_OFFSET = 0x30E4;
    private static final int PLAYER_TERRAN_SUPPLY_USED_OFFSET = 0x3114;
    private static final int PLAYER_TERRAN_SUPPLY_CAP_OFFSET = 0x3144;
    private static final int PLAYER_PROTOSS_SUPPLY_PROVIDED_OFFSET = 0x3174;
    private static final int PLAYER_PROTOSS_SUPPLY_USED_OFFSET = 0x31A4;
    private static final int PLAYER_PROTOSS_SUPPLY_CAP_OFFSET = 0x31D4;

    private static final @NotNull SampleReader<PlayersSample> READER = PlayersSampleDefinition::read;
    private static final @NotNull SampleWriter<PlayersSample> WRITER = PlayersSampleDefinition::write;

    private static @NotNull PlayersSample read(@NotNull ByteBuffer input) throws IOException {
        input.order(ByteOrder.LITTLE_ENDIAN);

        if (input.remaining() < Integer.BYTES) {
            throw Exceptions.newIOException("Missing players row count");
        }

        int rowCount = input.getInt();

        if (rowCount < 0 || rowCount > PLAYER_COUNT || (long) rowCount * ROW_BYTES != input.remaining()) {
            throw Exceptions.newIOException("Invalid players row count or payload size: {0}", Integer.toUnsignedLong(rowCount));
        }

        List<PlayerState> rows = new ArrayList<>(rowCount);
        int previousIdentifier = -1;

        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            PlayerState row = new PlayerState(Byte.toUnsignedInt(input.get()),
                                              input.getInt(), input.getInt(), input.getInt(), input.getInt(),
                                              input.getInt(), input.getInt(), input.getInt(),
                                              input.getInt(), input.getInt(), input.getInt(),
                                              input.getInt(), input.getInt(), input.getInt());

            if (row.getIdentifier() <= previousIdentifier || row.getIdentifier() >= PLAYER_COUNT) {
                throw Exceptions.newIOException("Invalid or noncanonical player identifier");
            }

            rows.add(row);
            previousIdentifier = row.getIdentifier();
        }

        return new PlayersSample(rows);
    }

    private static void write(@NotNull ByteBuffer output, @NotNull PlayersSample sample) throws IOException {
        List<PlayerState> rows = getCanonicalRows(sample);
        int encodedSize = Integer.BYTES + rows.size() * ROW_BYTES;

        if (output.remaining() < encodedSize || output.isReadOnly()) {
            throw Exceptions.newIOException("Insufficient writable players buffer: {0}", encodedSize);
        }

        output.order(ByteOrder.LITTLE_ENDIAN).putInt(rows.size());

        for (PlayerState row : rows) {
            output.put((byte) row.getIdentifier());
            output.putInt(row.getMinerals());
            output.putInt(row.getGas());
            output.putInt(row.getGatheredMinerals());
            output.putInt(row.getGatheredGas());
            output.putInt(row.getZergSupplyProvided());
            output.putInt(row.getZergSupplyUsed());
            output.putInt(row.getZergSupplyCap());
            output.putInt(row.getTerranSupplyProvided());
            output.putInt(row.getTerranSupplyUsed());
            output.putInt(row.getTerranSupplyCap());
            output.putInt(row.getProtossSupplyProvided());
            output.putInt(row.getProtossSupplyUsed());
            output.putInt(row.getProtossSupplyCap());
        }
    }

    private static @NotNull List<PlayerState> getCanonicalRows(@NotNull PlayersSample sample) throws IOException {
        List<PlayerState> rows = sample.getPlayers();

        if (rows.size() > PLAYER_COUNT) {
            throw Exceptions.newIOException("Players row limit exceeded: {0}", rows.size());
        }

        boolean ordered = true;
        int previousIdentifier = -1;

        for (PlayerState row : rows) {
            if (row.getIdentifier() < 0 || row.getIdentifier() >= PLAYER_COUNT) {
                throw Exceptions.newIOException("Invalid player value");
            }

            if (row.getIdentifier() <= previousIdentifier) {
                ordered = false;
            }

            previousIdentifier = row.getIdentifier();
        }

        if (ordered) {
            return rows;
        }

        List<PlayerState> sortedRows = new ArrayList<>(rows);

        sortedRows.sort((first, second) -> Integer.compare(first.getIdentifier(), second.getIdentifier()));

        for (int rowIndex = 1; rowIndex < sortedRows.size(); rowIndex++) {
            if (sortedRows.get(rowIndex - 1).getIdentifier() == sortedRows.get(rowIndex).getIdentifier()) {
                throw Exceptions.newIOException("Duplicate player identifier");
            }
        }

        return sortedRows;
    }


    /**
     * Creates a {@link PlayersSampleDefinition} instance.
     */
    public PlayersSampleDefinition() {
        super(SAMPLE_IDENTIFIER, "players", PlayersSample.class, Integer.BYTES + PLAYER_COUNT * ROW_BYTES);
    }


    /**
     * @return the bounded payload reader
     */
    @Override
    public @NotNull SampleReader<PlayersSample> getReader() {
        return READER;
    }

    /**
     * @return the canonical payload writer
     */
    @Override
    public @NotNull SampleWriter<PlayersSample> getWriter() {
        return WRITER;
    }


    /**
     * Calculates the exact payload size before reserving reusable output storage.
     *
     * @param sample the sample to encode
     * @return the exact encoded byte count
     * @throws IOException if the sample is invalid
     */
    @Override
    public int getEncodedSize(@NotNull PlayersSample sample) throws IOException {
        return Integer.BYTES + getCanonicalRows(sample).size() * ROW_BYTES;
    }

    /**
     * Creates a {@link Sampler} instance with session-owned capture buffers.
     *
     * @param context the capture session
     * @return the created {@link Sampler} instance
     */
    @Override
    public @NotNull Sampler<PlayersSample> createSampler(@NotNull CaptureContext context) {
        byte[] storage = new byte[PLAYER_CONTEXT_BYTES];
        ByteBuffer memory = ByteBuffer.wrap(storage).order(ByteOrder.LITTLE_ENDIAN);

        return () -> {
            context.getMemory().read(context.getContextAddress(), storage, 0, storage.length);

            List<PlayerState> players = new ArrayList<>(PLAYER_COUNT);

            for (int rowIndex = 0; rowIndex < PLAYER_COUNT; rowIndex++) {
                int rowOffset = rowIndex * Integer.BYTES;

                players.add(new PlayerState(rowIndex,
                                            memory.getInt(PLAYER_MINERALS_OFFSET + rowOffset),
                                            memory.getInt(PLAYER_GAS_OFFSET + rowOffset),
                                            memory.getInt(PLAYER_GATHERED_MINERALS_OFFSET + rowOffset),
                                            memory.getInt(PLAYER_GATHERED_GAS_OFFSET + rowOffset),
                                            memory.getInt(PLAYER_ZERG_SUPPLY_PROVIDED_OFFSET + rowOffset),
                                            memory.getInt(PLAYER_ZERG_SUPPLY_USED_OFFSET + rowOffset),
                                            memory.getInt(PLAYER_ZERG_SUPPLY_CAP_OFFSET + rowOffset),
                                            memory.getInt(PLAYER_TERRAN_SUPPLY_PROVIDED_OFFSET + rowOffset),
                                            memory.getInt(PLAYER_TERRAN_SUPPLY_USED_OFFSET + rowOffset),
                                            memory.getInt(PLAYER_TERRAN_SUPPLY_CAP_OFFSET + rowOffset),
                                            memory.getInt(PLAYER_PROTOSS_SUPPLY_PROVIDED_OFFSET + rowOffset),
                                            memory.getInt(PLAYER_PROTOSS_SUPPLY_USED_OFFSET + rowOffset),
                                            memory.getInt(PLAYER_PROTOSS_SUPPLY_CAP_OFFSET + rowOffset)));
            }

            return new PlayersSample(players);
        };
    }
}
