package org.fogofwarlabs.screcorder.sample.ai;

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
import java.util.Arrays;
import java.util.List;

/**
 * Defines AI clocks, route caches, player memory, regions and unit AI state. Raw record addresses and embedded memory pointers remain
 * process-specific.
 */
public final class AiSampleDefinition extends SampleDefinition<AiSample> {
    private static final int SAMPLE_IDENTIFIER = 12;
    private static final int OWNER_COUNT = 8;
    private static final int MAXIMUM_REGIONS = 5000;

    private static final int MAXIMUM_UNSIGNED_BYTE = 0xFF;
    private static final int MAXIMUM_UNSIGNED_SHORT = 0xFFFF;
    private static final long MAXIMUM_UNSIGNED_INTEGER = 0xFFFFFFFFL;

    private static final int REGION_ROW_BYTES = Integer.BYTES + AiRegionState.MEMORY_BYTES;
    private static final int PLAYER_PREFIX_BYTES = Integer.BYTES * 4 + Long.BYTES +
                                                   AiPlayerState.ROUTE_MARK_BYTES + AiPlayerState.PLAYER_MEMORY_BYTES;
    private static final int UNIT_ROW_BYTES = Integer.BYTES * 16 + Long.BYTES * 2;
    private static final int PREFIX_BYTES = Integer.BYTES * 4;
    private static final int MAXIMUM_PAYLOAD_BYTES = PREFIX_BYTES + OWNER_COUNT * (PLAYER_PREFIX_BYTES + MAXIMUM_REGIONS * REGION_ROW_BYTES) +
                                                     StarCraftLayout.MAXIMUM_UNITS * UNIT_ROW_BYTES;
    private static final int SCALAR_STORAGE_BYTES = Long.BYTES * 2;

    private static final long REGION_COUNT_POINTER_OFFSET = 0x1097780L;
    private static final long ROUTE_CLOCK_OFFSET = 0xEC1C90L;
    private static final long ROUTE_SOURCE_OFFSET = 0xEC1CB0L;
    private static final long ROUTE_MARKS_OFFSET = 0xEBCE70L;
    private static final long REGION_TABLE_OFFSET = 0xEBCE30L;
    private static final long PLAYER_RECORD_OFFSET = 0xE7FED0L;
    private static final int PLAYER_RECORD_STRIDE = 0x6E8;

    private static final int RECORD_KIND_OFFSET = 0x10;
    private static final int KIND_WORD_POINTER_OFFSET = 0x20;
    private static final int KIND_WORD_KIND = 4;

    private static final int @NotNull [] REGION_ARENA_POINTER_OFFSETS = {32, 40, 56};
    private static final int @NotNull [] REGION_IMAGE_POINTER_OFFSETS = {64, 72};

    private static final @NotNull SampleReader<AiSample> READER = AiSampleDefinition::read;
    private static final @NotNull SampleWriter<AiSample> WRITER = AiSampleDefinition::write;

    /**
     * Replaces the process-specific pointers of one AI region row: the game arena pointers at {@code +32}, {@code +40} and {@code +56} become offsets
     * from the unit pool (same arena) and the executable image pointers at {@code +64} and {@code +72} become offsets from the module base. A
     * {@code 0} pointer stays {@code 0}.
     *
     * @param memory       the region rows
     * @param start        the row offset
     * @param arenaAddress the unit pool address
     * @param moduleBase   the executable image base
     */
    static void normalizeRegion(byte @NotNull [] memory, int start, long arenaAddress, long moduleBase) {
        ByteBuffer row = ByteBuffer.wrap(memory).order(ByteOrder.LITTLE_ENDIAN);

        for (int offset : REGION_ARENA_POINTER_OFFSETS) {
            long pointer = row.getLong(start + offset);
            row.putLong(start + offset, pointer == 0 ? 0 : pointer - arenaAddress);
        }

        for (int offset : REGION_IMAGE_POINTER_OFFSETS) {
            long pointer = row.getLong(start + offset);
            row.putLong(start + offset, pointer == 0 ? 0 : pointer - moduleBase);
        }
    }

    private static @NotNull AiSample read(@NotNull ByteBuffer input) throws IOException {
        input.order(ByteOrder.LITTLE_ENDIAN);

        if (input.remaining() < PREFIX_BYTES) {
            throw Exceptions.newIOException("Truncated AI payload prefix");
        }

        int regionCount = input.getInt();
        long clock = Integer.toUnsignedLong(input.getInt());
        int playerCount = input.getInt();

        if (playerCount < 0 || playerCount > OWNER_COUNT || (long) playerCount * PLAYER_PREFIX_BYTES > input.remaining() - Integer.BYTES) {
            throw Exceptions.newIOException("Invalid AI player count");
        }

        List<AiPlayerState> players = new ArrayList<>(playerCount);

        for (int playerIndex = 0; playerIndex < playerCount; playerIndex++) {
            if (input.remaining() < PLAYER_PREFIX_BYTES) {
                throw Exceptions.newIOException("Truncated AI player state");
            }

            int owner = input.getInt();
            long routeClock = Integer.toUnsignedLong(input.getInt());
            int routeSource = input.getInt();
            byte[] routeMarks = new byte[AiPlayerState.ROUTE_MARK_BYTES];

            input.get(routeMarks);

            long regionTablePresence = input.getLong();
            byte[] playerMemory = new byte[AiPlayerState.PLAYER_MEMORY_BYTES];

            input.get(playerMemory);

            int sampledRegions = input.getInt();

            if (sampledRegions < 0 || sampledRegions > MAXIMUM_REGIONS || (long) sampledRegions * REGION_ROW_BYTES > input.remaining()) {
                throw Exceptions.newIOException("Invalid AI region count");
            }

            List<AiRegionState> regions = new ArrayList<>(sampledRegions);

            for (int regionIndex = 0; regionIndex < sampledRegions; regionIndex++) {
                int identifier = input.getInt();
                byte[] memory = new byte[AiRegionState.MEMORY_BYTES];

                input.get(memory);
                regions.add(new AiRegionState(identifier, memory));
            }

            players.add(new AiPlayerState(owner, routeClock, routeSource, routeMarks, regionTablePresence, playerMemory, regions));
        }

        if (input.remaining() < Integer.BYTES) {
            throw Exceptions.newIOException("Missing AI unit count");
        }

        int unitCount = input.getInt();

        if (unitCount < 0 || unitCount > StarCraftLayout.MAXIMUM_UNITS || (long) unitCount * UNIT_ROW_BYTES != input.remaining()) {
            throw Exceptions.newIOException("Invalid AI unit count or payload bounds");
        }

        List<AiUnitState> units = new ArrayList<>(unitCount);

        for (int unitIndex = 0; unitIndex < unitCount; unitIndex++) {
            units.add(new AiUnitState(input.getInt(), input.getInt(), input.getInt(), input.getLong(),
                                      input.getInt(), input.getLong(), input.getInt(), input.getInt(),
                                      input.getInt(), input.getInt(),
                                      input.getInt(), input.getInt(), input.getInt(), input.getInt(), input.getInt(),
                                      input.getInt(), input.getInt(), input.getInt()));
        }

        AiSample sample = new AiSample(regionCount, clock, players, units);

        validate(sample);

        return sample;
    }

    private static void write(@NotNull ByteBuffer output, @NotNull AiSample sample) throws IOException {
        int encodedSize = validate(sample);

        if (output.remaining() < encodedSize || output.isReadOnly()) {
            throw Exceptions.newIOException("Insufficient writable AI buffer");
        }

        output.order(ByteOrder.LITTLE_ENDIAN).putInt(sample.getRegionCount()).putInt((int) sample.getClock()).putInt(sample.getPlayers().size());

        for (AiPlayerState player : sample.getPlayers()) {
            output.putInt(player.getOwner()).putInt((int) player.getRouteClock()).putInt(player.getRouteSource()).put(player.getRouteMarks());
            output.putLong(player.getRegionTablePresence()).put(player.getPlayerMemory()).putInt(player.getRegions().size());

            for (AiRegionState region : player.getRegions()) {
                output.putInt(region.getIdentifier()).put(region.getMemory());
            }
        }

        output.putInt(sample.getUnits().size());

        for (AiUnitState unit : sample.getUnits()) {
            output.putInt(unit.getIdentifier());
            output.putInt(unit.getType());
            output.putInt(unit.getOwner());
            output.putLong(unit.getStatus());
            output.putInt(unit.getAutoTargetIdentifier());
            output.putLong(unit.getRecordAddress());
            output.putInt(unit.getKind());
            output.putInt(unit.getKindWord());
            output.putInt(unit.getAirStrength());
            output.putInt(unit.getGroundStrength());
            output.putInt(unit.getOrder());
            output.putInt(unit.getOrderState());
            output.putInt(unit.getOrderTargetIdentifier());
            output.putInt(unit.getTargetX());
            output.putInt(unit.getTargetY());
            output.putInt(unit.getPositionX());
            output.putInt(unit.getPositionY());
            output.putInt(unit.getSecondaryOrder());
        }
    }

    private static int validate(@NotNull AiSample sample) throws IOException {
        if (sample.getRegionCount() < Short.MIN_VALUE || sample.getRegionCount() > Short.MAX_VALUE ||
            sample.getClock() < 0 || sample.getClock() > MAXIMUM_UNSIGNED_INTEGER ||
            sample.getPlayers().size() > OWNER_COUNT || sample.getUnits().size() > StarCraftLayout.MAXIMUM_UNITS) {
            throw Exceptions.newIOException("Invalid AI payload bounds or clock");
        }

        int encodedSize = PREFIX_BYTES + sample.getUnits().size() * UNIT_ROW_BYTES;
        int previousOwner = -1;

        for (AiPlayerState player : sample.getPlayers()) {
            if (player.getOwner() <= previousOwner || player.getOwner() >= OWNER_COUNT ||
                player.getRouteClock() < 0 || player.getRouteClock() > MAXIMUM_UNSIGNED_INTEGER ||
                player.getRegionTablePresence() < 0 || player.getRegions().size() > MAXIMUM_REGIONS ||
                (player.getRegionTablePresence() == 0 && !player.getRegions().isEmpty())) {
                throw Exceptions.newIOException("Invalid AI player identity, route clock or region table");
            }

            int previousRegion = -1;
            int regionLimit = Math.clamp(sample.getRegionCount(), 0, MAXIMUM_REGIONS);

            for (AiRegionState region : player.getRegions()) {
                if (region.getIdentifier() <= previousRegion || region.getIdentifier() >= regionLimit) {
                    throw Exceptions.newIOException("Invalid or unordered AI region identity");
                }

                previousRegion = region.getIdentifier();
            }

            encodedSize += PLAYER_PREFIX_BYTES + player.getRegions().size() * REGION_ROW_BYTES;
            previousOwner = player.getOwner();
        }

        int previousUnit = 0;

        for (AiUnitState unit : sample.getUnits()) {
            if (unit.getIdentifier() <= previousUnit || unit.getIdentifier() > StarCraftLayout.MAXIMUM_UNITS ||
                unit.getType() < 0 || unit.getType() > MAXIMUM_UNSIGNED_SHORT || unit.getOwner() < 0 || unit.getOwner() > MAXIMUM_UNSIGNED_BYTE ||
                unit.getStatus() < 0 || unit.getStatus() > MAXIMUM_UNSIGNED_INTEGER ||
                unit.getAutoTargetIdentifier() < 0 || unit.getAutoTargetIdentifier() > StarCraftLayout.MAXIMUM_UNITS ||
                unit.getRecordAddress() < 0 || unit.getKind() < -1 || unit.getKind() > MAXIMUM_UNSIGNED_BYTE ||
                (unit.getRecordAddress() == 0 && unit.getKind() != -1) ||
                (unit.getRecordAddress() != 0 && unit.getKind() == -1) ||
                unit.getKindWord() < -1 || unit.getKindWord() > MAXIMUM_UNSIGNED_SHORT ||
                (unit.getKind() != KIND_WORD_KIND && unit.getKindWord() != -1) ||
                unit.getAirStrength() < 0 || unit.getAirStrength() > MAXIMUM_UNSIGNED_SHORT ||
                unit.getGroundStrength() < 0 || unit.getGroundStrength() > MAXIMUM_UNSIGNED_SHORT ||
                unit.getOrder() < 0 || unit.getOrder() > MAXIMUM_UNSIGNED_BYTE ||
                unit.getOrderState() < 0 || unit.getOrderState() > MAXIMUM_UNSIGNED_BYTE ||
                unit.getOrderTargetIdentifier() < 0 || unit.getOrderTargetIdentifier() > StarCraftLayout.MAXIMUM_UNITS ||
                unit.getTargetX() < Short.MIN_VALUE || unit.getTargetX() > Short.MAX_VALUE ||
                unit.getTargetY() < Short.MIN_VALUE || unit.getTargetY() > Short.MAX_VALUE ||
                unit.getPositionX() < 0 || unit.getPositionX() > MAXIMUM_UNSIGNED_SHORT ||
                unit.getPositionY() < 0 || unit.getPositionY() > MAXIMUM_UNSIGNED_SHORT ||
                unit.getSecondaryOrder() < 0 || unit.getSecondaryOrder() > MAXIMUM_UNSIGNED_BYTE) {
                throw Exceptions.newIOException("Invalid or unordered AI unit state");
            }

            previousUnit = unit.getIdentifier();
        }

        return encodedSize;
    }

    private static int resolveUnitIdentifier(long address, long poolAddress, int poolSlots) throws IOException {
        if (address == 0) {
            return 0;
        }

        long offset = address - poolAddress;

        if (address < poolAddress || offset < 0 || offset % StarCraftLayout.UNIT_STRIDE != 0 || offset / StarCraftLayout.UNIT_STRIDE >= poolSlots) {
            throw Exceptions.newIOException("AI unit target is outside the unit pool");
        }

        return (int) (offset / StarCraftLayout.UNIT_STRIDE) + 1;
    }


    /**
     * Creates a {@link AiSampleDefinition} instance.
     */
    public AiSampleDefinition() {
        super(SAMPLE_IDENTIFIER, "ai", AiSample.class, MAXIMUM_PAYLOAD_BYTES);
    }


    /**
     * @return the bounded payload reader
     */
    @Override
    public @NotNull SampleReader<AiSample> getReader() {
        return READER;
    }

    /**
     * @return the canonical payload writer
     */
    @Override
    public @NotNull SampleWriter<AiSample> getWriter() {
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
    public int getEncodedSize(@NotNull AiSample sample) throws IOException {
        return validate(sample);
    }

    /**
     * Creates a {@link Sampler} instance with session-owned capture buffers.
     *
     * @param context the capture session
     * @return the created {@link Sampler} instance
     */
    @Override
    public @NotNull Sampler<AiSample> createSampler(@NotNull CaptureContext context) {
        byte[] scalarStorage = new byte[SCALAR_STORAGE_BYTES];
        ByteBuffer scalars = ByteBuffer.wrap(scalarStorage).order(ByteOrder.LITTLE_ENDIAN);
        byte[] routeMarks = new byte[AiPlayerState.ROUTE_MARK_BYTES];
        byte[] playerMemory = new byte[AiPlayerState.PLAYER_MEMORY_BYTES];
        byte[] regionMemory = new byte[MAXIMUM_REGIONS * AiRegionState.MEMORY_BYTES];
        byte[] unitMemory = new byte[StarCraftLayout.MAXIMUM_UNITS * StarCraftLayout.UNIT_STRIDE];
        ByteBuffer units = ByteBuffer.wrap(unitMemory).order(ByteOrder.LITTLE_ENDIAN);

        return () -> {
            MemoryReader memory = context.getMemory();
            long moduleBase = context.getModuleBase();

            memory.read(moduleBase + REGION_COUNT_POINTER_OFFSET, scalarStorage, 0, Long.BYTES);
            long regionCountAddress = scalars.getLong(0);

            if (regionCountAddress < 0 || regionCountAddress > Long.MAX_VALUE - Short.BYTES) {
                throw Exceptions.newIOException("Invalid AI region count address");
            }

            int regionCount = 0;

            if (regionCountAddress != 0) {
                memory.read(regionCountAddress, scalarStorage, 0, Short.BYTES);
                regionCount = scalars.getShort(0);
            }

            memory.read(context.getContextAddress() + StarCraftLayout.ELAPSED_SECONDS_OFFSET, scalarStorage, 0, Integer.BYTES);
            long clock = Integer.toUnsignedLong(scalars.getInt(0));

            int sampledRegions = Math.clamp(regionCount, 0, MAXIMUM_REGIONS);
            List<AiPlayerState> players = new ArrayList<>(OWNER_COUNT);

            memory.read(moduleBase + StarCraftLayout.UNIT_POOL_POINTER, scalarStorage, 0, scalarStorage.length);
            long arenaAddress = scalars.getLong(0);

            for (int owner = 0; owner < OWNER_COUNT; owner++) {
                memory.read(moduleBase + ROUTE_CLOCK_OFFSET + (long) owner * Integer.BYTES, scalarStorage, 0, Integer.BYTES);
                long routeClock = Integer.toUnsignedLong(scalars.getInt(0));
                memory.read(moduleBase + ROUTE_SOURCE_OFFSET + (long) owner * Integer.BYTES, scalarStorage, 0, Integer.BYTES);
                int routeSource = scalars.getInt(0);
                memory.read(moduleBase + ROUTE_MARKS_OFFSET + (long) owner * routeMarks.length, routeMarks, 0, routeMarks.length);
                memory.read(moduleBase + PLAYER_RECORD_OFFSET + (long) owner * PLAYER_RECORD_STRIDE, playerMemory, 0, playerMemory.length);
                memory.read(moduleBase + REGION_TABLE_OFFSET + (long) owner * Long.BYTES, scalarStorage, 0, Long.BYTES);
                long regionAddress = scalars.getLong(0);

                if (regionAddress < 0 || regionAddress > Long.MAX_VALUE - (long) sampledRegions * AiRegionState.MEMORY_BYTES) {
                    throw Exceptions.newIOException("Invalid AI region table address");
                }

                List<AiRegionState> regions = new ArrayList<>(regionAddress == 0 ? 0 : sampledRegions);

                if (regionAddress != 0 && sampledRegions != 0) {
                    memory.read(regionAddress, regionMemory, 0, sampledRegions * AiRegionState.MEMORY_BYTES);

                    for (int regionIndex = 0; regionIndex < sampledRegions; regionIndex++) {
                        int start = regionIndex * AiRegionState.MEMORY_BYTES;

                        normalizeRegion(regionMemory, start, arenaAddress, moduleBase);
                        regions.add(new AiRegionState(regionIndex, Arrays.copyOfRange(regionMemory, start, start + AiRegionState.MEMORY_BYTES)));
                    }
                }

                players.add(new AiPlayerState(owner, routeClock, routeSource, routeMarks, regionAddress == 0 ? 0 : 1, playerMemory, regions));
            }

            memory.read(moduleBase + StarCraftLayout.UNIT_POOL_POINTER, scalarStorage, 0, scalarStorage.length);
            long poolAddress = scalars.getLong(0);
            long declaredSlots = scalars.getLong(Long.BYTES);

            if (declaredSlots < 0 || declaredSlots > StarCraftLayout.MAXIMUM_UNITS ||
                poolAddress < 0 || poolAddress > Long.MAX_VALUE - declaredSlots * StarCraftLayout.UNIT_STRIDE ||
                (declaredSlots != 0 && poolAddress == 0)) {
                throw Exceptions.newIOException("Invalid AI unit pool metadata");
            }

            int poolSlots = (int) declaredSlots;
            List<AiUnitState> states = new ArrayList<>();

            if (poolSlots != 0) {
                memory.read(poolAddress, unitMemory, 0, poolSlots * StarCraftLayout.UNIT_STRIDE);

                for (int unitIndex = 0; unitIndex < poolSlots; unitIndex++) {
                    int start = unitIndex * StarCraftLayout.UNIT_STRIDE;

                    if (units.getLong(start + StarCraftLayout.UNIT_ACTIVE_OFFSET) == 0) {
                        continue;
                    }

                    long recordAddress = units.getLong(start + StarCraftLayout.UNIT_RECORD_ADDRESS_OFFSET);

                    if (recordAddress < 0 || recordAddress > Long.MAX_VALUE - KIND_WORD_POINTER_OFFSET - Long.BYTES) {
                        throw Exceptions.newIOException("Invalid unit AI record address");
                    }

                    int kind = -1;
                    int kindWord = -1;

                    if (recordAddress != 0) {
                        memory.read(recordAddress + RECORD_KIND_OFFSET, scalarStorage, 0, Byte.BYTES);
                        kind = Byte.toUnsignedInt(scalars.get(0));

                        if (kind == KIND_WORD_KIND) {
                            memory.read(recordAddress + KIND_WORD_POINTER_OFFSET, scalarStorage, 0, Long.BYTES);
                            long wordAddress = scalars.getLong(0);

                            if (wordAddress < 0 || wordAddress > Long.MAX_VALUE - Short.BYTES) {
                                throw Exceptions.newIOException("Invalid AI kind word address");
                            }

                            if (wordAddress != 0) {
                                memory.read(wordAddress, scalarStorage, 0, Short.BYTES);
                                kindWord = Short.toUnsignedInt(scalars.getShort(0));
                            }
                        }
                    }

                    states.add(new AiUnitState(
                            unitIndex + 1,
                            Short.toUnsignedInt(units.getShort(start + StarCraftLayout.UNIT_TYPE_OFFSET)),
                            Byte.toUnsignedInt(units.get(start + StarCraftLayout.UNIT_OWNER_OFFSET)),
                            Integer.toUnsignedLong(units.getInt(start + StarCraftLayout.UNIT_STATUS_OFFSET)),
                            resolveUnitIdentifier(units.getLong(start + StarCraftLayout.UNIT_AUTO_TARGET_OFFSET), poolAddress, poolSlots),
                            recordAddress == 0 ? 0 : recordAddress - moduleBase, kind, kindWord,
                            Short.toUnsignedInt(units.getShort(start + StarCraftLayout.UNIT_AIR_STRENGTH_OFFSET)),
                            Short.toUnsignedInt(units.getShort(start + StarCraftLayout.UNIT_GROUND_STRENGTH_OFFSET)),
                            Byte.toUnsignedInt(units.get(start + StarCraftLayout.UNIT_ORDER_OFFSET)),
                            Byte.toUnsignedInt(units.get(start + StarCraftLayout.UNIT_ORDER_STATE_OFFSET)),
                            resolveUnitIdentifier(units.getLong(start + StarCraftLayout.UNIT_ORDER_TARGET_IDENTIFIER_OFFSET), poolAddress, poolSlots),
                            units.getShort(start + StarCraftLayout.UNIT_ORDER_TARGET_X_OFFSET),
                            units.getShort(start + StarCraftLayout.UNIT_ORDER_TARGET_Y_OFFSET),
                            Short.toUnsignedInt(units.getShort(start + StarCraftLayout.UNIT_POSITION_X_OFFSET)),
                            Short.toUnsignedInt(units.getShort(start + StarCraftLayout.UNIT_POSITION_Y_OFFSET)),
                            Byte.toUnsignedInt(units.get(start + StarCraftLayout.UNIT_SECONDARY_ORDER_OFFSET))
                    ));
                }
            }

            return new AiSample(regionCount, clock, players, states);
        };
    }
}
