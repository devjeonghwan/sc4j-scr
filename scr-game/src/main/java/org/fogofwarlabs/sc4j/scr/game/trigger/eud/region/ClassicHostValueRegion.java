package org.fogofwarlabs.sc4j.scr.game.trigger.eud.region;

import org.fogofwarlabs.sc4j.base.binary.LittleEndianUtils;
import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.game.combat.ProjectilePhase;
import org.fogofwarlabs.sc4j.scr.game.frame.GameSpeed;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.trigger.TriggerContext;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicRegion;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * A generic host region over one Remastered global BYTE, WORD or DWORD that a world field holds: reads and writes copy the
 * value, so an EUD write changes the game's value.
 */
public final class ClassicHostValueRegion implements ClassicRegion {
    /**
     * The classic address of the tile update countdown of the frame phases.
     */
    public static final int UPDATE_TILES_COUNTDOWN = 0x51CE98;
    /**
     * The classic address of the frame duration table (seven DWORDs): the speed calculation fills it and the frame timing
     * reads it, so an EUD write changes the game's frame duration.
     */
    public static final int FRAME_DURATIONS = 0x5124D8;
    /**
     * The classic address of the random state: the generator advances it, the seed sets it with the call counters; an EUD
     * write replaces the state alone.
     */
    public static final int RANDOM_STATE = 0x51CA14;
    /**
     * The classic address of the turn-wait budget (write-ignored): the game loop gate takes {@code 1000} from it per call
     * and adds the speed's timeout back; the game start stores {@code 1000}. Outside a multiplayer session every timeout
     * is {@code 1000}, so the budget reads {@code 1000} whenever triggers run.
     */
    public static final int TURN_BUDGET = 0x51CEA0;
    /**
     * The classic address of the trigger-cycle WORD.
     */
    public static final int TRIGGER_CYCLE = 0x6509A0;
    /**
     * The classic address of the active bullet count.
     */
    public static final int ACTIVE_BULLETS = 0x64DEBC;
    /**
     * The classic address of the units-in-use DWORD (write-ignored): unit creation compares it with the capacity and
     * counts up, the release from the dying list counts down, the game-start reset clears it and the free-slot count
     * recomputes it as the capacity less the free slots.
     */
    public static final int UNITS_IN_USE = 0x6283F0;
    /**
     * The classic address of the current trigger player: the trigger loop stores each evaluated player there and the
     * conditions and actions read it, so a write changes the current player for the rest of that player's triggers; the
     * loop then stores the next player from its own iterator.
     */
    public static final int CURRENT_PLAYER = 0x6509B0;
    /**
     * The classic address of the multiplayer session BYTE (write-ignored).
     */
    public static final int MULTIPLAYER_SESSION = 0x57F0B4;

    /**
     * The mask of an unsigned BYTE.
     */
    private static final int BYTE_MASK = 0xFF;
    /**
     * The single-player value of the turn-wait budget.
     */
    private static final int SINGLE_PLAYER_TURN_BUDGET = 1000;
    /**
     * The classic address of the minimap color BYTE, the visible fog level.
     */
    private static final int VISIBLE_FOG_LEVEL = 0x657A9C;
    /**
     * The classic address of the session's turns in transit (write-ignored).
     */
    private static final int LATENCY_TURNS = 0x57F090;
    /**
     * The classic address of the session's maximum turn bytes (write-ignored).
     */
    private static final int MAXIMUM_TURN_BYTES = 0x57F0D8;


    /**
     * Creates {@link ClassicRegion} instances for the regions of the world's host DWORDs: the tile update countdown and the random state.
     *
     * @param world the world
     * @return the world's host DWORD regions
     */
    public static @NotNull List<ClassicRegion> create(@NotNull Game world) {
        return List.of(new ClassicHostValueRegion(UPDATE_TILES_COUNTDOWN, world::getUpdateTilesCountdown, world::setUpdateTilesCountdown),
                       new ClassicHostValueRegion(RANDOM_STATE, world.getRandom()::getState, world.getRandom()::writeState));
    }

    /**
     * Creates a {@link ClassicRegion} instance for the write-ignored turn-wait budget {@link #TURN_BUDGET} of a game outside a multiplayer session.
     *
     * @return the created {@link ClassicRegion} instance, reading {@code 1000}
     */
    public static @NotNull ClassicRegion createSinglePlayerTurnBudgetRegion() {
        return new ClassicHostValueRegion(TURN_BUDGET, Integer.BYTES, () -> SINGLE_PLAYER_TURN_BUDGET, value -> {
        }, true);
    }

    /**
     * Creates a {@link ClassicRegion} instance for the turn-wait budget {@link #TURN_BUDGET} of a game whose timeouts are known: the game's
     * live budget ({@link GameSpeed#getTurnBudget()}), which every game loop takes {@code 1000} from and every turn step adds the timeout of
     * the speed option back to. With a timeout of {@code 1000} it reads {@code 1000} whenever triggers run.
     *
     * @param timing the game's speed configuration, timeouts and turn-wait budget
     * @return the created write-ignored {@link ClassicRegion} instance
     */
    public static @NotNull ClassicRegion createTurnBudgetRegion(@NotNull GameSpeed timing) {
        return new ClassicHostValueRegion(TURN_BUDGET, Integer.BYTES, timing::getTurnBudget, value -> {
        }, true);
    }

    /**
     * Creates a {@link ClassicRegion} instance for the trigger-cycle WORD region {@link #TRIGGER_CYCLE}.
     *
     * @param triggers the trigger context
     * @return the created {@link ClassicRegion} instance
     */
    public static @NotNull ClassicRegion createTriggerCycleRegion(@NotNull TriggerContext triggers) {
        return new ClassicHostValueRegion(TRIGGER_CYCLE, Short.BYTES, triggers::getCycleCountdown, triggers::setCycleCountdown);
    }

    /**
     * Creates a {@link ClassicRegion} instance for the current trigger player DWORD region {@link #CURRENT_PLAYER}.
     *
     * @param triggers the trigger context holding the current-player DWORD
     * @return the created {@link ClassicRegion} instance
     */
    public static @NotNull ClassicRegion createCurrentPlayerRegion(@NotNull TriggerContext triggers) {
        return new ClassicHostValueRegion(CURRENT_PLAYER, triggers::readCurrentPlayerDword, triggers::writeCurrentPlayerDword);
    }

    /**
     * Creates a {@link ClassicRegion} instance for the write-ignored multiplayer session BYTE region {@link #MULTIPLAYER_SESSION}.
     *
     * @param triggers the trigger context holding the session byte
     * @return the created write-ignored {@link ClassicRegion} instance
     */
    public static @NotNull ClassicRegion createMultiplayerSessionRegion(@NotNull TriggerContext triggers) {
        return new ClassicHostValueRegion(MULTIPLAYER_SESSION, Byte.BYTES, triggers::getMultiplayerSession, value -> {
        }, true);
    }

    /**
     * Creates {@link ClassicRegion} instances for one DWORD region per entry of the frame duration table {@link #FRAME_DURATIONS}.
     *
     * @param timing the game's frame timing
     * @return the regions
     */
    public static @NotNull List<ClassicRegion> createFrameDurationRegions(@NotNull GameSpeed timing) {
        List<ClassicRegion> regions = new ArrayList<>();

        for (int index = 0; index < timing.getTableSize(); index++) {
            int entry = index;

            regions.add(new ClassicHostValueRegion(FRAME_DURATIONS + entry * Integer.BYTES,
                                                   () -> timing.getTableDuration(entry), value -> timing.setTableDuration(entry, value)));
        }

        return regions;
    }

    /**
     * Creates a {@link ClassicRegion} instance for the write-ignored units-in-use DWORD region {@link #UNITS_IN_USE}.
     *
     * @param pool the unit pool
     * @return the created {@link ClassicRegion} instance
     */
    public static @NotNull ClassicRegion createUnitsInUseRegion(@NotNull UnitPool pool) {
        return new ClassicHostValueRegion(UNITS_IN_USE, Integer.BYTES, pool::getAllocatedCount, value -> {
            throw Exceptions.newIllegalStateException("The write-ignored units-in-use region is never written.");
        }, true);
    }

    /**
     * Creates a {@link ClassicRegion} instance for the active bullet count region {@link #ACTIVE_BULLETS}: bullet creation counts up, the release
     * counts down and the reset clears it.
     *
     * @param bullets the bullet storage
     * @return the created {@link ClassicRegion} instance
     */
    public static @NotNull ClassicRegion createActiveBulletCountRegion(ProjectilePhase.@NotNull Context bullets) {
        return new ClassicHostValueRegion(ACTIVE_BULLETS, bullets::getActiveCount, bullets::setActiveCount);
    }

    /**
     * Creates {@link ClassicRegion} instances for the regions of the local interface state, the minimap color BYTE, the session's turn limits and
     * the outgoing turn buffer with its length.
     *
     * @param world the world
     * @return the regions
     */
    public static @NotNull List<ClassicRegion> createLocalInterfaceRegions(@NotNull Game world) {
        LocalInterfaceState state = world.getInterfaceLink().getLocalInterface();

        return List.of(
                new ClassicHostValueRegion(LocalInterfaceState.MOUSE_BUTTONS_ADDRESS, Short.BYTES, state::getMouseButtons, state::writeMouseButtons),
                new ClassicHostValueRegion(LocalInterfaceState.GLUE_PALETTE_ADDRESS, state::getGluePalette, state::writeGluePalette),
                ClassicStorageRegion.createHostRegion(LocalInterfaceState.SCROLL_STEPS_ADDRESS, state.getScrollStepBytes()),
                new ClassicHostValueRegion(LocalInterfaceState.UNIT_SPEECH_MUTE_ADDRESS, state::getUnitSpeechMuted, state::setUnitSpeechMuted),
                new ClassicHostValueRegion(LocalInterfaceState.SELECTION_REFRESH_ADDRESS, Byte.BYTES,
                                           state::getSelectionRefresh, state::writeSelectionRefresh),
                new ClassicHostValueRegion(LocalInterfaceState.STALL_STAMP_ADDRESS, state::getStallStamp, state::writeStallStamp),
                new ClassicHostValueRegion(LocalInterfaceState.STALL_COUNTDOWN_ADDRESS, state::getStallCountdown, state::setStallCountdown),
                new ClassicHostValueRegion(LocalInterfaceState.PORTRAIT_REFRESH_ADDRESS, Byte.BYTES,
                                           state::getPortraitRefresh, state::writePortraitRefresh),
                new ClassicHostByteTableRegion(LocalInterfaceState.CHAT_LABEL_ADDRESS, LocalInterfaceState.CHAT_LABEL_SIZE,
                                               index -> state.getChatLabel()[index],
                                               (index, value) -> state.getChatLabel()[index] = (byte) value),
                new ClassicHostByteTableRegion(LocalInterfaceState.STATUS_PANEL_CACHE_ADDRESS,
                                               LocalInterfaceState.STATUS_PANEL_CACHE_DWORDS * Integer.BYTES,
                                               index -> state.getStatusPanelCache()[index / Integer.BYTES] >>> index % Integer.BYTES * Byte.SIZE,
                                               (index, value) -> {
                                                   int[] cache = state.getStatusPanelCache();
                                                   int shift = index % Integer.BYTES * Byte.SIZE;

                                                   cache[index / Integer.BYTES] = cache[index / Integer.BYTES] & ~(BYTE_MASK << shift) |
                                                                                  value << shift;
                                               }),
                new ClassicHostValueRegion(LocalInterfaceState.CHAT_MODE_ADDRESS, Byte.BYTES, state::getChatMode, state::writeChatMode),
                new ClassicHostValueRegion(VISIBLE_FOG_LEVEL, Byte.BYTES,
                                           world.getGameContext()::getVisibleFogLevel, world.getGameContext()::setVisibleFogLevel),
                new ClassicHostValueRegion(LocalInterfaceState.INTERFACE_TICK_ADDRESS, Integer.BYTES,
                                           state::getInterfaceTick, state::writeInterfaceTick),
                new ClassicHostValueRegion(LocalInterfaceState.DISPLAYED_BUTTON_SET_ADDRESS, Short.BYTES,
                                           state::getDisplayedButtonSet, state::writeDisplayedButtonSet),
                new ClassicHostValueRegion(LATENCY_TURNS, Integer.BYTES, world.getInterfaceLink()::getLatencyTurns, value -> {
                }, true),
                new ClassicHostValueRegion(MAXIMUM_TURN_BYTES, Integer.BYTES, world.getInterfaceLink()::getMaximumTurnBytes, value -> {
                }, true),
                // A map queues a network command by writing its bytes at the length and raising the length.
                new ClassicTurnBufferRegion(world),
                new ClassicHostValueRegion(ClassicTurnBufferRegion.LENGTH, Integer.BYTES,
                                           () -> world.getInterfaceLink().getOutgoingTurn().getLength(),
                                           value -> world.getInterfaceLink().getOutgoingTurn().setLength(value))
        );
    }


    private final int base;
    private final int size;
    private final @NotNull IntSupplier getter;
    private final @NotNull IntConsumer setter;
    private final boolean writeIgnored;


    /**
     * Creates a {@link ClassicHostValueRegion} instance over a host DWORD.
     *
     * @param base   the classic base
     * @param getter reads the host DWORD
     * @param setter stores the host DWORD
     */
    public ClassicHostValueRegion(int base, @NotNull IntSupplier getter, @NotNull IntConsumer setter) {
        this(base, Integer.BYTES, getter, setter);
    }

    /**
     * Creates a {@link ClassicHostValueRegion} instance over a host value of the given size.
     *
     * @param base   the classic base
     * @param size   the size in bytes ({@code 1}, {@code 2} or {@code 4})
     * @param getter reads the host value
     * @param setter stores the host value
     */
    public ClassicHostValueRegion(int base, int size, @NotNull IntSupplier getter, @NotNull IntConsumer setter) {
        this(base, size, getter, setter, false);
    }

    private ClassicHostValueRegion(int base, int size, @NotNull IntSupplier getter, @NotNull IntConsumer setter, boolean writeIgnored) {
        this.base         = base;
        this.size         = size;
        this.getter       = getter;
        this.setter       = setter;
        this.writeIgnored = writeIgnored;
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
        return this.size;
    }

    /**
     * Tells whether the address space accepts writes without calling the region or changing its state.
     *
     * @return {@code true} if writes are ignored, {@code false} otherwise
     */
    @Override
    public boolean isWriteIgnored() {
        return this.writeIgnored;
    }


    /**
     * Reads the unsigned byte at an offset.
     *
     * @param offset the byte offset, below {@link #getSize()}
     * @return the unsigned byte at the offset
     */
    @Override
    public int readByte(int offset) {
        return this.getter.getAsInt() >>> offset * Byte.SIZE & BYTE_MASK;
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

        this.setter.accept(LittleEndianUtils.replaceByte(this.getter.getAsInt(), shift, value));
    }

    /**
     * Reads an aligned little-endian DWORD; regions whose domain values are DWORDs override it to read them whole.
     *
     * @param offset the byte offset of the DWORD, with four bytes inside the region
     * @return the DWORD
     */
    @Override
    public int readDword(int offset) {
        return this.size == Integer.BYTES ? this.getter.getAsInt() : ClassicRegion.super.readDword(offset);
    }

    /**
     * Stores a little-endian DWORD; regions whose domain values are DWORDs override it to store them whole.
     *
     * @param offset the byte offset of the DWORD, with four bytes inside the region
     * @param value  the DWORD
     */
    @Override
    public void writeDword(int offset, int value) {
        if (this.size == Integer.BYTES) {
            this.setter.accept(value);
        } else {
            ClassicRegion.super.writeDword(offset, value);
        }
    }
}
