package org.fogofwarlabs.sc4j.scr.game.presentation;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.game.ability.TransportCargo;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplaySession;
import org.fogofwarlabs.sc4j.scr.game.session.OutgoingTurnBuffer;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.region.LocalInterfaceState;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.region.LocalKeyStates;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.region.LocalView;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Connects one game to the local interface: the presentation events the simulation raises for the client (sounds, under-attack alerts, command
 * errors, notifications, message lines) and the local inputs it reads (key states, interface state, view, the local selection, the outgoing turn
 * buffer and turn limits of the session).
 * <p>
 * None of this is simulation state; the state hash does not cover it.
 */
public final class InterfaceLink {
    /**
     * The value of the turn limits while no session supplied them.
     */
    private static final int UNSUPPLIED = -1;
    /**
     * The status panel cache entry of the first cargo slot.
     */
    private static final int FIRST_CARGO_CACHE_ENTRY = 1;

    private final @NotNull SoundEvents soundEvents;
    private final @NotNull AttackAlertEvents attackAlertEvents;
    private final @NotNull CommandErrorEvents commandErrorEvents;
    private final @NotNull NotificationEvents notificationEvents;
    private final @NotNull MessageLines messageLines;

    private final @NotNull LocalKeyStates localKeyStates;
    private final @NotNull LocalInterfaceState localInterface;
    private final @NotNull LocalView localView;
    private @NotNull LocalSelection localSelection;

    /**
     * The local outgoing command buffer, or {@code null} when no session supplies it.
     */
    private @Nullable OutgoingTurnBuffer outgoingTurn;
    /**
     * The turns in transit of the session, or {@link #UNSUPPLIED}.
     */
    private int latencyTurns;
    /**
     * The maximum turn bytes of the session, or {@link #UNSUPPLIED}.
     */
    private int maximumTurnBytes;

    /**
     * Creates an {@link InterfaceLink} instance without a local interface or session.
     */
    public InterfaceLink() {
        this.soundEvents        = new SoundEvents();
        this.attackAlertEvents  = new AttackAlertEvents();
        this.commandErrorEvents = new CommandErrorEvents();
        this.notificationEvents = new NotificationEvents();
        this.messageLines       = new MessageLines();

        this.localKeyStates = new LocalKeyStates();
        this.localInterface = new LocalInterfaceState();
        this.localView      = new LocalView();
        this.localSelection = new HeadlessLocalSelection();

        this.outgoingTurn     = null;
        this.latencyTurns     = UNSUPPLIED;
        this.maximumTurnBytes = UNSUPPLIED;
    }

    /**
     * @return the sound events raised since the client last cleared them, excluded from simulation checksums
     */
    public @NotNull SoundEvents getSoundEvents() {
        return this.soundEvents;
    }

    /**
     * @return the current frame's under-attack alert calls, excluded from simulation checksums
     */
    public @NotNull AttackAlertEvents getAttackAlertEvents() {
        return this.attackAlertEvents;
    }

    /**
     * @return the command error messages raised during the current game-loop iteration, by its commands and its frame (presentation only; cleared
     * when the next iteration starts, {@link ReplaySession#step})
     */
    public @NotNull CommandErrorEvents getCommandErrorEvents() {
        return this.commandErrorEvents;
    }

    /**
     * @return the unit notifications raised during the current game-loop iteration (presentation only; cleared when the next iteration starts,
     * {@link ReplaySession#step})
     */
    public @NotNull NotificationEvents getNotificationEvents() {
        return this.notificationEvents;
    }

    /**
     * @return the displayed message lines, which EUD-mode maps can read
     */
    public @NotNull MessageLines getMessageLines() {
        return this.messageLines;
    }

    /**
     * @return the local key states, a local input that EUD-mode maps can read
     */
    public @NotNull LocalKeyStates getLocalKeyStates() {
        return this.localKeyStates;
    }

    /**
     * @return the local interface state (mouse buttons and chat mode) that the EUD emulation serves
     */
    public @NotNull LocalInterfaceState getLocalInterface() {
        return this.localInterface;
    }

    /**
     * @return the local view (screen and mouse position) that the EUD emulation serves, a local input of the interface
     */
    public @NotNull LocalView getLocalView() {
        return this.localView;
    }

    /**
     * @return the local interface's selection the simulation reports to; without a local interface a {@link HeadlessLocalSelection} holding
     * {@link LocalSelection#CAPACITY} slots
     */
    public @NotNull LocalSelection getLocalSelection() {
        return this.localSelection;
    }

    /**
     * @return the local outgoing turn buffer
     * @throws UnsupportedOperationException if neither a live session nor replay playback supplied it
     */
    public @NotNull OutgoingTurnBuffer getOutgoingTurn() {
        if (this.outgoingTurn == null) {
            throw Exceptions.newUnsupportedOperationException("EUD addresses 0x00654880 and 0x00654AA0 are the local outgoing turn buffer and " +
                                                              "its length, which no session supplied.");
        }

        return this.outgoingTurn;
    }

    /**
     * @return the turns in transit of the session
     * @throws UnsupportedOperationException if no session supplied the turn limits
     */
    public int getLatencyTurns() {
        if (this.latencyTurns < 0) {
            throw Exceptions.newUnsupportedOperationException("EUD address 0x0057F090 is the session's turns in transit, which no session supplied.");
        }

        return this.latencyTurns;
    }

    /**
     * @return the maximum bytes of one outgoing turn of the session
     * @throws UnsupportedOperationException if no session supplied the turn limits
     */
    public int getMaximumTurnBytes() {
        if (this.maximumTurnBytes < 0) {
            throw Exceptions.newUnsupportedOperationException("EUD address 0x0057F0D8 is the session's maximum turn bytes, " +
                                                              "which no session supplied.");
        }

        return this.maximumTurnBytes;
    }

    /**
     * Sets the local interface's selection that the simulation reports to (live play only; a replay records the resulting commands).
     *
     * @param localSelection the local selection, or {@code null} for a new {@link HeadlessLocalSelection}
     */
    public void setLocalSelection(@Nullable LocalSelection localSelection) {
        this.localSelection = localSelection != null ? localSelection : new HeadlessLocalSelection();
    }

    /**
     * Sets the local outgoing turn buffer, which the live session or replay playback owns.
     *
     * @param outgoingTurn the buffer, or {@code null} when not available
     */
    public void setOutgoingTurn(@Nullable OutgoingTurnBuffer outgoingTurn) {
        this.outgoingTurn = outgoingTurn;
    }

    /**
     * Sets the turn limits of the local session: the turns in transit and the maximum bytes of one turn.
     *
     * @param latencyTurns     the turns in transit
     * @param maximumTurnBytes the maximum turn bytes
     */
    public void setSessionTurnLimits(int latencyTurns, int maximumTurnBytes) {
        this.latencyTurns     = latencyTurns;
        this.maximumTurnBytes = maximumTurnBytes;
    }


    /**
     * Publishes the raw hit points of every cargo slot to the status panel cache, using {@code 0} for a slot without a live unit.
     *
     * @param transport the cargo of this game's transports
     * @param unit      the displayed transport
     */
    public void cacheCargoHitPoints(@NotNull TransportCargo transport, @NotNull Unit unit) {
        for (int slot = 0; slot < Unit.LOADED_UNIT_SLOTS; slot++) {
            Unit carried = transport.getLoadedUnit(unit, slot);

            this.localInterface.setStatusPanelCacheEntry(FIRST_CARGO_CACHE_ENTRY + slot, carried == null ? 0 : carried.getHitPoints());
        }
    }
}
