package org.fogofwarlabs.screcorder.sample.trigger;

import org.fogofwarlabs.screcorder.capture.MemoryReader;
import org.fogofwarlabs.screcorder.capture.sampling.CaptureContext;
import org.fogofwarlabs.screcorder.capture.sampling.Sampler;
import org.fogofwarlabs.screcorder.capture.starcraft.StarCraftLayout;
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
 * Defines explicit trigger wait and session flag capture.
 */
public final class TriggersSampleDefinition extends SampleDefinition<TriggersSample> {
    private static final int SAMPLE_IDENTIFIER = 9;
    private static final int PLAYER_COUNT = 8;

    private static final int MAXIMUM_UNSIGNED_BYTE = 0xFF;
    private static final int MAXIMUM_UNSIGNED_SHORT = 0xFFFF;
    private static final long DWORD_MASK = 0xFFFFFFFFL;

    private static final int WAIT_BYTES = PLAYER_COUNT * (Byte.BYTES + Integer.BYTES);
    private static final int PAYLOAD_BYTES = Short.BYTES + WAIT_BYTES + 2 * Integer.BYTES + 2 * Byte.BYTES;

    private static final int WAIT_TIMER_OFFSET = PLAYER_COUNT * Byte.BYTES;
    private static final long WAITING_OFFSET = 0x10B0E10;
    private static final long LOCAL_PLAYER_OFFSET = 0xDD5B5C;
    private static final long MULTIPLAYER_OFFSET = 0x10908DC;
    private static final int LOCAL_BYTES = 12;
    private static final int STORM_PLAYER_OFFSET = 8;

    private static final @NotNull SampleReader<TriggersSample> READER = TriggersSampleDefinition::read;
    private static final @NotNull SampleWriter<TriggersSample> WRITER = TriggersSampleDefinition::write;

    private static @NotNull TriggersSample read(@NotNull ByteBuffer input) throws IOException {
        if (input.remaining() != PAYLOAD_BYTES) {
            throw Exceptions.newIOException("Invalid trigger payload length");
        }

        input.order(ByteOrder.LITTLE_ENDIAN);

        int cycle = Short.toUnsignedInt(input.getShort());
        List<TriggerWaitState> waits = new ArrayList<>(PLAYER_COUNT);

        for (int playerIdentifier = 0; playerIdentifier < PLAYER_COUNT; playerIdentifier++) {
            waits.add(new TriggerWaitState(playerIdentifier, Byte.toUnsignedInt(input.get()), Integer.toUnsignedLong(input.getInt())));
        }

        return new TriggersSample(cycle, waits, input.getInt(), input.getInt(), Byte.toUnsignedInt(input.get()), Byte.toUnsignedInt(input.get()));
    }

    private static void write(@NotNull ByteBuffer output, @NotNull TriggersSample sample) throws IOException {
        validate(sample);

        if (output.remaining() < PAYLOAD_BYTES || output.isReadOnly()) {
            throw Exceptions.newIOException("Insufficient writable trigger buffer");
        }

        output.order(ByteOrder.LITTLE_ENDIAN).putShort((short) sample.getCycle());

        for (int playerIdentifier = 0; playerIdentifier < PLAYER_COUNT; playerIdentifier++) {
            for (TriggerWaitState wait : sample.getWaits()) {
                if (wait.getPlayerIdentifier() == playerIdentifier) {
                    output.put((byte) wait.getWaiting()).putInt((int) wait.getTimer());

                    break;
                }
            }
        }

        output.putInt(sample.getLocalPlayer()).putInt(sample.getStormPlayer());
        output.put((byte) sample.getMultiplayerFlag()).put((byte) sample.getReplayFlag());
    }

    private static void validate(@NotNull TriggersSample sample) throws IOException {
        if (sample.getCycle() < 0 || sample.getCycle() > MAXIMUM_UNSIGNED_SHORT ||
            sample.getMultiplayerFlag() < 0 || sample.getMultiplayerFlag() > MAXIMUM_UNSIGNED_BYTE ||
            sample.getReplayFlag() < 0 || sample.getReplayFlag() > MAXIMUM_UNSIGNED_BYTE || sample.getWaits().size() != PLAYER_COUNT) {
            throw Exceptions.newIOException("Invalid trigger cycle, flags or wait count");
        }

        int seenPlayers = 0;

        for (TriggerWaitState wait : sample.getWaits()) {
            int playerIdentifier = wait.getPlayerIdentifier();

            if (playerIdentifier < 0 || playerIdentifier >= PLAYER_COUNT || (seenPlayers & 1 << playerIdentifier) != 0 ||
                wait.getWaiting() < 0 || wait.getWaiting() > MAXIMUM_UNSIGNED_BYTE || (wait.getTimer() & ~DWORD_MASK) != 0) {
                throw Exceptions.newIOException("Invalid or duplicate trigger wait state");
            }

            seenPlayers |= 1 << playerIdentifier;
        }
    }


    /**
     * Creates a {@link TriggersSampleDefinition} instance.
     */
    public TriggersSampleDefinition() {
        super(SAMPLE_IDENTIFIER, "triggers", TriggersSample.class, PAYLOAD_BYTES);
    }


    /**
     * @return the bounded payload reader
     */
    @Override
    public @NotNull SampleReader<TriggersSample> getReader() {
        return READER;
    }

    /**
     * @return the canonical payload writer
     */
    @Override
    public @NotNull SampleWriter<TriggersSample> getWriter() {
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
    public int getEncodedSize(@NotNull TriggersSample sample) throws IOException {
        validate(sample);

        return PAYLOAD_BYTES;
    }

    /**
     * Creates a {@link Sampler} instance with session-owned capture buffers.
     *
     * @param context the capture session
     * @return the created {@link Sampler} instance
     */
    @Override
    public @NotNull Sampler<TriggersSample> createSampler(@NotNull CaptureContext context) {
        byte[] cycleStorage = new byte[Short.BYTES];
        byte[] waitStorage = new byte[WAIT_BYTES];
        byte[] localStorage = new byte[LOCAL_BYTES];
        byte[] multiplayerStorage = new byte[Byte.BYTES];
        byte[] replayStorage = new byte[Byte.BYTES];
        ByteBuffer cycle = ByteBuffer.wrap(cycleStorage).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer waits = ByteBuffer.wrap(waitStorage).order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer local = ByteBuffer.wrap(localStorage).order(ByteOrder.LITTLE_ENDIAN);

        return () -> {
            MemoryReader memory = context.getMemory();

            memory.read(context.getModuleBase() + StarCraftLayout.TRIGGER_CYCLE, cycleStorage, 0, cycleStorage.length);
            memory.read(context.getModuleBase() + WAITING_OFFSET, waitStorage, 0, waitStorage.length);
            memory.read(context.getModuleBase() + LOCAL_PLAYER_OFFSET, localStorage, 0, localStorage.length);
            memory.read(context.getModuleBase() + MULTIPLAYER_OFFSET, multiplayerStorage, 0, multiplayerStorage.length);
            memory.read(context.getModuleBase() + StarCraftLayout.REPLAY_FLAG, replayStorage, 0, replayStorage.length);

            List<TriggerWaitState> states = new ArrayList<>(PLAYER_COUNT);

            for (int playerIdentifier = 0; playerIdentifier < PLAYER_COUNT; playerIdentifier++) {
                states.add(new TriggerWaitState(playerIdentifier, Byte.toUnsignedInt(waits.get(playerIdentifier)),
                                                Integer.toUnsignedLong(waits.getInt(WAIT_TIMER_OFFSET + playerIdentifier * Integer.BYTES))));
            }

            return new TriggersSample(Short.toUnsignedInt(cycle.getShort(0)), states, local.getInt(0), local.getInt(STORM_PLAYER_OFFSET),
                                      Byte.toUnsignedInt(multiplayerStorage[0]), Byte.toUnsignedInt(replayStorage[0]));
        };
    }
}
