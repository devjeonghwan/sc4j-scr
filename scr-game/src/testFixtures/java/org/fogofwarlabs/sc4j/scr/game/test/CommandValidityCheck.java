package org.fogofwarlabs.sc4j.scr.game.test;

import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommand;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommandIds;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplaySelectionCommand;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayTargetCommand;
import org.fogofwarlabs.sc4j.scr.game.order.UnitSelection;
import org.fogofwarlabs.sc4j.scr.game.player.Player;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.recording.RecorderStateUtils;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayCommandIssuer;
import org.fogofwarlabs.sc4j.scr.game.replay.ReplayCommandObserver;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitHandle;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Counts, per player and per window of {@value #WINDOW_FRAMES} frames, the recorded commands that the simulated state does not explain.
 * <p>
 * A recording client only sends commands that its own game state allows. While the simulation agrees with the recording client, a command refers to
 * live units of its player, and the few exceptions come from units that died in the command latency. Once the simulation diverges, the players keep
 * commanding the units of the recorded game, and the simulation sees selections of missing, dead or foreign units, orders without a selection and
 * targets that do not exist. The observer reads the state before each command through the identities a recording uses (unit slot, generation and
 * owner, the player's selection and victory state) and changes nothing.
 */
public final class CommandValidityCheck implements ReplayCommandObserver {
    /**
     * The frames of one counting window, about {@code 40} seconds of game time at the fastest speed.
     */
    public static final int WINDOW_FRAMES = 960;

    private static final int COUNTER_COUNT = Counter.values().length;
    private static final int MULTIPLE_SELECTION = 2;
    private static final int LAST_ISSUER = 0xFF;
    private static final int NO_FRAME = -1;

    /**
     * Names the counted command events.
     */
    public enum Counter {
        /**
         * A command of the player.
         */
        COMMANDS,
        /**
         * A non-null unit handle of a selection command.
         */
        SELECTED_HANDLES,
        /**
         * A selected handle whose unit slot holds no live unit or a dying one.
         */
        SELECTED_DEAD,
        /**
         * A selected handle whose unit slot holds a live unit of another generation, or that addresses no unit slot.
         */
        SELECTED_STALE,
        /**
         * A selected handle of a selection of several units whose live unit belongs to another player. A client selects at most one unit of another
         * player.
         */
        SELECTED_FOREIGN,
        /**
         * A unit order or production command, which the client only sends with a selection.
         */
        ORDERS,
        /**
         * An order while the player's selection is empty.
         */
        ORDERS_WITHOUT_SELECTION,
        /**
         * A non-null target handle of a right click or targeted order.
         */
        TARGETS,
        /**
         * A target handle that resolves to no live unit.
         */
        TARGETS_MISSING,
        /**
         * A selection or order of a player whom the simulation counts as defeated or dropped.
         */
        AFTER_DEFEAT
    }

    private static boolean isOrder(int typeId) {
        return switch (typeId) {
            case ReplayCommandIds.BUILD, ReplayCommandIds.RIGHT_CLICK, ReplayCommandIds.TARGETED_ORDER, ReplayCommandIds.CANCEL_BUILD,
                 ReplayCommandIds.CANCEL_MORPH, ReplayCommandIds.STOP, ReplayCommandIds.CARRIER_STOP, ReplayCommandIds.REAVER_STOP,
                 ReplayCommandIds.RETURN_CARGO, ReplayCommandIds.TRAIN, ReplayCommandIds.CANCEL_TRAIN, ReplayCommandIds.CLOAK,
                 ReplayCommandIds.DECLOAK, ReplayCommandIds.UNIT_MORPH, ReplayCommandIds.UNSIEGE, ReplayCommandIds.SIEGE,
                 ReplayCommandIds.TRAIN_FIGHTER, ReplayCommandIds.UNLOAD_ALL, ReplayCommandIds.UNLOAD, ReplayCommandIds.MERGE_ARCHON,
                 ReplayCommandIds.HOLD_POSITION, ReplayCommandIds.BURROW, ReplayCommandIds.UNBURROW, ReplayCommandIds.LIFT_OFF,
                 ReplayCommandIds.TECH, ReplayCommandIds.CANCEL_TECH, ReplayCommandIds.UPGRADE, ReplayCommandIds.CANCEL_UPGRADE,
                 ReplayCommandIds.CANCEL_ADDON, ReplayCommandIds.BUILDING_MORPH, ReplayCommandIds.STIM, ReplayCommandIds.MERGE_DARK_ARCHON,
                 ReplayCommandIds.RIGHT_CLICK_121, ReplayCommandIds.TARGETED_ORDER_121, ReplayCommandIds.UNLOAD_121 -> true;
            default -> false;
        };
    }

    private static boolean isDefeated(@NotNull Game game, int player) {
        Player record = game.getPlayerTable().getPlayer(player);

        return record != null &&
               (record.getVictoryState() == Player.VICTORY_STATE_DEFEATED || record.getVictoryState() == Player.VICTORY_STATE_DROPPED);
    }

    private static @Nullable Unit resolve(@NotNull UnitPool pool, int handle) {
        try {
            return pool.resolveHandle(handle);
        } catch (UnsupportedOperationException exception) {
            return null;
        }
    }

    private static boolean isStale(@NotNull UnitPool pool, int handle) {
        int poolIndex = UnitHandle.getPoolIndex(handle, pool.getCapacity());

        if (poolIndex == 0 || poolIndex > pool.getCapacity()) {
            return true;
        }

        return RecorderStateUtils.isLive(pool, poolIndex) && pool.getGeneration(poolIndex) != UnitHandle.getGeneration(handle, pool.getCapacity());
    }


    private final @NotNull List<List<int[]>> counts;
    private final @NotNull List<List<int[]>> firstFrames;

    /**
     * Creates a {@link CommandValidityCheck} instance with no counted command.
     */
    public CommandValidityCheck() {
        this.counts      = new ArrayList<>();
        this.firstFrames = new ArrayList<>();

        for (int player = 0; player < PlayerTable.PLAYABLE_PLAYER_SLOTS; player++) {
            this.counts.add(new ArrayList<>());
            this.firstFrames.add(new ArrayList<>());
        }
    }

    /**
     * Returns the count of an event of a player in a window.
     *
     * @param player  the player slot from {@code 0..7}
     * @param window  the window index, the frame divided by {@value #WINDOW_FRAMES}
     * @param counter the event
     * @return the count, {@code 0} for a window after the player's last command
     */
    public int getCount(int player, int window, @NotNull Counter counter) {
        List<int[]> playerCounts = this.counts.get(player);

        return window < playerCounts.size() ? playerCounts.get(window)[counter.ordinal()] : 0;
    }

    /**
     * Returns the frame of the first event of a player in a window.
     *
     * @param player  the player slot from {@code 0..7}
     * @param window  the window index, the frame divided by {@value #WINDOW_FRAMES}
     * @param counter the event
     * @return the frame, or {@code -1} if the window has no such event
     */
    public int getFirstFrame(int player, int window, @NotNull Counter counter) {
        List<int[]> playerFrames = this.firstFrames.get(player);

        return window < playerFrames.size() ? playerFrames.get(window)[counter.ordinal()] : NO_FRAME;
    }

    /**
     * Returns the total count of an event of a player.
     *
     * @param player  the player slot from {@code 0..7}
     * @param counter the event
     * @return the count over all windows
     */
    public int getTotal(int player, @NotNull Counter counter) {
        int total = 0;

        for (int[] windowCounts : this.counts.get(player)) {
            total += windowCounts[counter.ordinal()];
        }

        return total;
    }

    /**
     * @param player the player slot from {@code 0..7}
     * @return the number of windows up to the player's last command
     */
    public int getWindowCount(int player) {
        return this.counts.get(player).size();
    }

    /**
     * Called before the command executes.
     * <p>
     * Counts the command and its events for the issuing player.
     *
     * @param game    the simulation state
     * @param command the recorded command
     */
    @Override
    public void onBeforeCommand(@NotNull Game game, @NotNull ReplayCommand command) {
        int typeId = command.getTypeId();
        int frame = (int) command.getFrame();
        boolean selection = ReplaySelectionCommand.supports(typeId);

        if (command.getPlayerId() < 0 || command.getPlayerId() > LAST_ISSUER) {
            return;
        }

        ReplayCommandIssuer issuer = ReplayCommandIssuer.resolve(game, command.getPlayerId());
        int player = issuer.getCommandPlayerIndex();

        if (issuer.getStatus() != ReplayCommandIssuer.Status.RESOLVED || player < 0 || player >= PlayerTable.PLAYABLE_PLAYER_SLOTS) {
            return;
        }

        this.count(player, frame, Counter.COMMANDS);

        if (!selection && !isOrder(typeId)) {
            return;
        }

        if (isDefeated(game, player)) {
            this.count(player, frame, Counter.AFTER_DEFEAT);
        }

        if (selection) {
            this.countSelection(game, command, player, frame);
        } else {
            this.countOrder(game, command, player, frame);
        }
    }

    /**
     * Called after the command executed without an exception.
     * <p>
     * Nothing is counted: the events only depend on the state before a command.
     *
     * @param game    the simulation state
     * @param command the recorded command
     */
    @Override
    public void onAfterCommand(@NotNull Game game, @NotNull ReplayCommand command) {
    }

    private void countSelection(@NotNull Game game, @NotNull ReplayCommand command, int player, int frame) {
        UnitPool pool = game.getUnitSystem().getUnitPool();

        if (pool == null) {
            return;
        }

        ReplaySelectionCommand request;

        try {
            request = ReplaySelectionCommand.parse(command);
        } catch (IOException exception) {
            return;
        }

        int handleCount = 0;

        for (int index = 0; index < request.getHandleCount(); index++) {
            if (request.getHandle(index) != 0) {
                handleCount++;
            }
        }

        for (int index = 0; index < request.getHandleCount(); index++) {
            int handle = (int) request.getHandle(index);

            if (handle == 0) {
                continue;
            }

            this.count(player, frame, Counter.SELECTED_HANDLES);

            Unit unit = resolve(pool, handle);

            if (unit == null) {
                this.count(player, frame, isStale(pool, handle) ? Counter.SELECTED_STALE : Counter.SELECTED_DEAD);
            } else if (handleCount >= MULTIPLE_SELECTION && unit.getOwnerIndex() != player) {
                this.count(player, frame, Counter.SELECTED_FOREIGN);
            }
        }
    }

    private void countOrder(@NotNull Game game, @NotNull ReplayCommand command, int player, int frame) {
        UnitSelection selection = game.getSelection();
        UnitPool pool = game.getUnitSystem().getUnitPool();

        this.count(player, frame, Counter.ORDERS);

        if (selection != null && selection.getSelectedUnit(player, 0) == null) {
            this.count(player, frame, Counter.ORDERS_WITHOUT_SELECTION);
        }

        if (!ReplayTargetCommand.supports(command.getTypeId()) || pool == null) {
            return;
        }

        int handle;

        try {
            handle = (int) ReplayTargetCommand.parse(command).getTargetHandle();
        } catch (IOException exception) {
            return;
        }

        if (handle == 0) {
            return;
        }

        this.count(player, frame, Counter.TARGETS);

        if (resolve(pool, handle) == null) {
            this.count(player, frame, Counter.TARGETS_MISSING);
        }
    }

    private void count(int player, int frame, @NotNull Counter counter) {
        List<int[]> playerCounts = this.counts.get(player);
        List<int[]> playerFrames = this.firstFrames.get(player);
        int window = Math.max(0, frame) / WINDOW_FRAMES;

        while (playerCounts.size() <= window) {
            int[] frames = new int[COUNTER_COUNT];

            Arrays.fill(frames, NO_FRAME);
            playerCounts.add(new int[COUNTER_COUNT]);
            playerFrames.add(frames);
        }

        if (playerCounts.get(window)[counter.ordinal()]++ == 0) {
            playerFrames.get(window)[counter.ordinal()] = frame;
        }
    }
}
