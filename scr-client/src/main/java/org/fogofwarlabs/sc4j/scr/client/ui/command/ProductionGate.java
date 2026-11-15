package org.fogofwarlabs.sc4j.scr.client.ui.command;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.client.world.ScrPresentation;
import org.fogofwarlabs.sc4j.scr.game.presentation.CommandErrorEvents;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Runs the shared unit cost test before interface commands.
 * Supply is checked before minerals and gas, and the test updates the player's stored costs.
 * The interface removes the raised error event and shows it immediately, preventing a duplicate on the next frame.
 */
public final class ProductionGate {
    /**
     * The result of the gate: the {@code stat_txt} message and the sfx of a failure.
     */
    public enum Failure {
        /**
         * The used supply plus the requirement exceeds the race's cap: {@code 847} + race, {@code SND_Z_ERROR} + race.
         */
        SUPPLY_LIMIT(847, true, "SND_Z_ERROR"),
        /**
         * The available supply is short: {@code 844} + race, race key {@code insufficientFood} with the default
         * {@code SND_Z_NEED_POWER} + race.
         */
        SUPPLY_SHORT(844, true, "SND_Z_NEED_POWER"),
        /**
         * The minerals are short: {@code 850}, race key {@code insufficientMinerals} with the default {@code SND_Z_NEED_MINERALS}
         * + console race.
         */
        MINERALS(850, false, "SND_Z_NEED_MINERALS"),
        /**
         * The gas is short: {@code 851}, race key {@code insufficientGas} with the default {@code SND_Z_NEED_GAS} + console race.
         */
        GAS(851, false, "SND_Z_NEED_GAS");

        private final int textIdentifier;
        private final boolean raceSpecificText;
        private final @NotNull String soundName;

        Failure(int textIdentifier, boolean raceSpecificText, @NotNull String soundName) {
            this.textIdentifier   = textIdentifier;
            this.raceSpecificText = raceSpecificText;
            this.soundName        = soundName;
        }

        /**
         * @param race the race of the failure's sound ({@link #getSoundRace})
         * @return the one-based {@code stat_txt} id of the message
         */
        public int getTextIdentifier(int race) {
            return this.raceSpecificText ? this.textIdentifier + race : this.textIdentifier;
        }

        /**
         * @return the sfx name of the Zerg sound; the Terran and Protoss sounds follow it
         */
        public @NotNull String getSoundName() {
            return this.soundName;
        }

        /**
         * Returns the race of the sound: the unit type's race for supply failures, the console race (game) for resource failures.
         *
         * @param unitRace    the unit type's race
         * @param consoleRace the console race
         * @return the race from {@code 0} Zerg through {@code 2} Protoss
         */
        public int getSoundRace(int unitRace, int consoleRace) {
            return this.raceSpecificText ? unitRace : consoleRace;
        }
    }

    /**
     * Runs the gate for a player and a unit type.
     *
     * @param view         the game view
     * @param presentation the presentation state of the game, whose interface link counts the command errors
     * @param player       the player (the active unit's owner)
     * @param unitType     the units.dat type to produce
     * @return the failure, or {@code null} when the type can be produced
     */
    public static @Nullable Failure check(@NotNull ScrGameView view, @NotNull ScrPresentation presentation, int player, int unitType) {
        CommandErrorEvents events = presentation.getInterfaceLink().getCommandErrorEvents();
        int before = events.getCount();

        if (view.testUnitCost(player, unitType, true, true)) {
            return null;
        }

        if (events.getCount() == before) {
            throw Exceptions.newIllegalStateException("The cost test failed without an error for type {0}.", unitType);
        }

        CommandErrorEvents.Sound raised = events.get(before).getSound();
        // The interface shows the error itself at once; the event must not come back with the next frame's errors.
        events.truncate(before);

        return switch (raised) {
            case SUPPLY_LIMIT -> Failure.SUPPLY_LIMIT;
            case INSUFFICIENT_SUPPLY -> Failure.SUPPLY_SHORT;
            case INSUFFICIENT_MINERALS -> Failure.MINERALS;
            case INSUFFICIENT_GAS -> Failure.GAS;
            default -> throw Exceptions.newIllegalStateException("The cost test raised {0}.", raised);
        };
    }

    private ProductionGate() {
    }
}
