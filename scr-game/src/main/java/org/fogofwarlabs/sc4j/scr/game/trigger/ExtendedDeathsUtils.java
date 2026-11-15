package org.fogofwarlabs.sc4j.scr.game.trigger;

import org.fogofwarlabs.sc4j.scr.data.type.constant.TriggerUnitGroups;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerScores;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.presentation.MessageLines;
import org.fogofwarlabs.sc4j.scr.game.trigger.eud.ClassicAddressSpace;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Serves the extended Deaths operands of EUD maps: the selector classification (the player groups {@code 13..26}
 * expand, every other operand is one player index), the unit groups served by the loss counters instead of memory, the
 * reads and writes through the EUD address space, and the proof that a Set Deaths cannot reach a given trigger record.
 */
final class ExtendedDeathsUtils {
    /**
     * The group selectors {@code CURRENT_PLAYER..NON_ALLIED_VICTORY_PLAYERS} span this many values past the first.
     */
    private static final int GROUP_SELECTOR_SPAN = TriggerPlayerSelectors.NON_ALLIED_VICTORY_PLAYERS - TriggerPlayerSelectors.CURRENT_PLAYER;

    /**
     * Tests whether an extended operand names a player group whose players each take part.
     *
     * @param selector the player operand
     * @return {@code true} if the operand names a player group ({@code 14..26}), {@code false} otherwise (one
     * player index, {@link #getPlayerIndex}, which includes the current player {@code 13})
     */
    static boolean isPlayerGroup(int selector) {
        return selector != TriggerPlayerSelectors.CURRENT_PLAYER &&
               Integer.compareUnsigned(selector - TriggerPlayerSelectors.CURRENT_PLAYER, GROUP_SELECTOR_SPAN) <= 0;
    }

    /**
     * Returns the player index an operand names.
     *
     * @param context  the trigger context
     * @param selector an operand that is not a player group
     * @return the player index it names: the current player for {@code 13}, else the operand itself
     */
    static int getPlayerIndex(@NotNull TriggerContext context, int selector) {
        return selector == TriggerPlayerSelectors.CURRENT_PLAYER ? context.currentPlayer : selector;
    }

    /**
     * Tests whether an extended Set Deaths of a unit operand modifies the loss counters instead of writing memory.
     *
     * @param unitType the unit operand
     * @return {@code true} if the operand is a counter group ({@code 229..232}), {@code false} otherwise
     */
    static boolean isCounterWrite(int unitType) {
        return unitType >= TriggerUnitGroups.ANY_UNIT && unitType <= TriggerUnitGroups.FACTORIES;
    }

    /**
     * Proves that an extended Set Deaths cannot change a trigger record, for every player its selector may name: the
     * named player index, or every player slot for the current player and the groups.
     *
     * @param address  the classic address of the record
     * @param selector the player operand of the Set Deaths
     * @param unitType the unit operand of the Set Deaths
     * @return {@code true} if a write of the selector and unit WORD may reach the record's bytes
     * ({@link ClassicAddressSpace#canWriteDeathsRange}), {@code false} otherwise
     */
    static boolean mayWriteRecord(int address, int selector, int unitType) {
        if (isCounterWrite(unitType)) {
            return false;
        }

        if (selector != TriggerPlayerSelectors.CURRENT_PLAYER && !isPlayerGroup(selector)) {
            return ClassicAddressSpace.canWriteDeathsRange(address, TriggerRecordCodec.RECORD_SIZE,
                                                           ClassicAddressSpace.getAddress(selector, unitType), 1);
        }

        return ClassicAddressSpace.canWriteDeathsRange(address, TriggerRecordCodec.RECORD_SIZE,
                                                       ClassicAddressSpace.getAddress(0, unitType), PlayerTable.PLAYER_SLOTS);
    }

    /**
     * Counts deaths as the Deaths condition does: the loss counters through the player selector, or the address space in
     * EUD mode ({@link #readExtendedDeaths(TriggerContext, int, int, int)}).
     *
     * @param context  the trigger context
     * @param selector the player operand
     * @param unitType the unit operand
     * @param mask     the location DWORD of a masked condition, else {@code -1}
     * @return the counted deaths
     */
    static int countDeaths(@NotNull TriggerContext context, int selector, int unitType, int mask) {
        return context.extendedDeathsMode == 0 ? TriggerConditions.sumPlayers(context, selector, unitType, TriggerConditions.COUNTER_DEATHS)
                                               : readExtendedDeaths(context, selector, unitType, mask);
    }

    /**
     * Reads the extended deaths of an EUD map: player groups ({@code 14..26}) sum the per-player value over their
     * players, the current player ({@code 13}) and every other operand act as one player; unit groups {@code 229..231}
     * keep the counter sums (zero for a player operand above 11), and every other unit reads the address space, masked
     * by the location DWORD when the condition carries the {@code 'S','C'} marker.
     */
    private static int readExtendedDeaths(@NotNull TriggerContext context, int selector, int unitType, int mask) {
        if (isPlayerGroup(selector)) {
            int total = 0;

            for (int player = TriggerPlayersUtils.getNextSelectedPlayer(context, selector, TriggerContext.WALK_START);
                 player < PlayerTable.PLAYER_SLOTS;
                 player = TriggerPlayersUtils.getNextSelectedPlayer(context, selector, player)) {
                total += readPlayerExtendedDeaths(context, player, unitType, mask);
            }

            return total;
        }

        return readPlayerExtendedDeaths(context, getPlayerIndex(context, selector), unitType, mask);
    }

    private static int readPlayerExtendedDeaths(@NotNull TriggerContext context, int player, int unitType, int mask) {
        if (unitType >= TriggerUnitGroups.ANY_UNIT && unitType <= TriggerUnitGroups.BUILDINGS) {
            return Integer.compareUnsigned(player, PlayerTable.PLAYER_SLOTS) >= 0
                   ? 0
                   : TriggerConditions.readPlayerCounter(context, player, unitType, TriggerConditions.COUNTER_DEATHS);
        }

        return getExtendedDeathsOperations(context).readExtendedDeaths(player, unitType) & mask;
    }

    /**
     * Applies Set Deaths: the set, add and subtract modifiers through the player selector; outside EUD mode only players
     * below 8 are modified, in EUD mode {@link #writeExtendedDeaths(TriggerContext, int, int, int, int, int, boolean)}
     * writes; other modifiers do nothing.
     *
     * @param context  the trigger context
     * @param selector the player operand
     * @param unitType the unit operand
     * @param modifier the modifier
     * @param amount   the amount
     * @param mask     the location DWORD of a masked action, else {@code -1}
     * @param masked   whether the action carries the {@code 'S','C'} marker
     */
    static void setDeaths(@NotNull TriggerContext context, int selector, int unitType, int modifier, int amount, int mask, boolean masked) {
        if (!TriggerModifiers.isNumericModifier(modifier)) {
            return;
        }

        if (context.extendedDeathsMode != 0) {
            writeExtendedDeaths(context, selector, unitType, modifier, amount, mask, masked);

            return;
        }

        PlayerScores scores = context.requireAccounting().getScores();

        for (int selected = TriggerPlayersUtils.getNextSelectedPlayer(context, selector, TriggerContext.WALK_START);
             selected < PlayerTable.PLAYER_SLOTS;
             selected = TriggerPlayersUtils.getNextSelectedPlayer(context, selector, selected)) {
            if (selected < PlayerTable.PLAYABLE_PLAYER_SLOTS) {
                scores.modifyDeaths(selected, unitType, modifier, amount);
            }
        }
    }

    /**
     * Writes the extended deaths of an EUD map for the set, add and subtract modifiers: player groups modify each of
     * their players, the unit groups {@code 229..232} modify the loss counters of players below 8, and every other unit
     * writes the address space. With the {@code 'S','C'} marker the write keeps the unmasked bits of the old value and
     * computes the masked part from the masked old value and amount (subtraction clamps at zero).
     */
    private static void writeExtendedDeaths(@NotNull TriggerContext context,
                                            int selector,
                                            int unitType,
                                            int modifier, int amount,
                                            int mask, boolean masked) {
        if (isPlayerGroup(selector)) {
            for (int player = TriggerPlayersUtils.getNextSelectedPlayer(context, selector, TriggerContext.WALK_START);
                 player < PlayerTable.PLAYER_SLOTS;
                 player = TriggerPlayersUtils.getNextSelectedPlayer(context, selector, player)) {
                writePlayerExtendedDeaths(context, player, unitType, modifier, amount, mask, masked);
            }

            return;
        }

        writePlayerExtendedDeaths(context, getPlayerIndex(context, selector), unitType, modifier, amount, mask, masked);
    }

    private static void writePlayerExtendedDeaths(@NotNull TriggerContext context,
                                                  int player,
                                                  int unitType,
                                                  int modifier, int amount,
                                                  int mask, boolean masked) {
        if (isCounterWrite(unitType)) {
            if (Integer.compareUnsigned(player, PlayerTable.PLAYABLE_PLAYER_SLOTS) < 0) {
                context.requireAccounting().getScores().modifyDeaths(player, unitType, modifier, amount);
            }

            return;
        }

        ScrTriggerSystem.ExtendedDeathsOperations memory = getExtendedDeathsOperations(context);

        if (!masked) {
            memory.setExtendedDeaths(player, unitType, modifier, amount);

            return;
        }

        int previous = memory.readExtendedDeaths(player, unitType);
        int result = PlayerScores.modify(previous & mask, modifier, amount & mask);

        memory.setExtendedDeaths(player, unitType, TriggerModifiers.SET_TO, previous & ~mask | result & mask);
    }

    /**
     * Returns the extended deaths operations of the context, creating the EUD address space on first use.
     * <p>
     * An access the address space cannot serve, while evaluation is not stopped, sets the evaluation stop byte, so the
     * record and action loops stop and no trigger runs again; the failure notice follows with the complemented address,
     * and the game ends after the frame ({@link TriggerContext#hasEudFailure()}). A failure while evaluation is stopped
     * changes nothing.
     *
     * @param context the trigger context
     * @return the extended deaths operations
     */
    static @NotNull ScrTriggerSystem.ExtendedDeathsOperations getExtendedDeathsOperations(@NotNull TriggerContext context) {
        if (context.extendedDeathsOperations == null) {
            ClassicAddressSpace space = context.world != null
                                        ? ClassicAddressSpace.create(context, context.world)
                                        : ClassicAddressSpace.create(context,
                                                                     context.requireAccounting().getScores(),
                                                                     context.requireAccounting(),
                                                                     context.players == null
                                                                     ? null
                                                                     : Arrays.copyOf(context.players, PlayerTable.PLAYER_SLOTS),
                                                                     new MessageLines());

            space.setFailureListener(address -> {
                if (context.isEvaluationStopped()) {
                    return;
                }

                context.evaluationStopFlags = 1;
                context.eudFailed           = true;
                context.eudFailureAddress   = address;

                // No represented region carries the failure flag, so the notice is always the unsupported EUD map text.
                context.events.add(TriggerEvents.EUD_FAILURE_NOTICE, 0, ~address, 0);
            });

            context.extendedDeathsOperations = space;
        }

        return context.extendedDeathsOperations;
    }


    private ExtendedDeathsUtils() {
    }
}
