package org.fogofwarlabs.sc4j.scr.game.state;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.game.trigger.CaptureTheFlag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Names the victory condition of a game: the victory BYTE of the game options that the game type template sets.
 * <p>
 * The game start selects the condition's trigger resource from it and registers its records for every player; Greed and
 * Slaughter pick the resource by the game options' subtype value DWORD and fail the game start for any other value.
 * {@link #CAPTURE_THE_FLAG} also creates the flag beacons first ({@link CaptureTheFlag}). Player removal kills the
 * departing player's units in {@link #CAPTURE_THE_FLAG} and {@link #SUDDEN_DEATH} instead of giving them to the neutral
 * player.
 */
public enum VictoryCondition {
    /**
     * No victory triggers: Use Map Settings and any value without a resource (the game start succeeds without one).
     */
    NONE(0, null),
    /**
     * Melee: {@code triggers\Melee.trg}.
     */
    MELEE(1, "Melee"),
    /**
     * Greed: {@code triggers\Greed<resources>.trg} for 2500, 5000, 7500 or 10000 resources.
     */
    GREED(3, "Greed", 2500, 5000, 7500, 10000),
    /**
     * Capture the Flag: the flag beacons, then {@code triggers\CTF.trg}.
     */
    CAPTURE_THE_FLAG(4, "CTF"),
    /**
     * Sudden Death: {@code triggers\SuddenDeath.trg}.
     */
    SUDDEN_DEATH(5, "SuddenDeath"),
    /**
     * Slaughter: {@code triggers\Slaughter<minutes>.trg} for 15, 30, 45 or 60 minutes.
     */
    SLAUGHTER(6, "Slaughter", 15, 30, 45, 60),
    /**
     * One on One: the Melee resource {@code triggers\Melee.trg}.
     */
    ONE_ON_ONE(7, "Melee");

    /**
     * The installed directory of the trigger resources.
     */
    private static final @NotNull String TRIGGER_DIRECTORY = "triggers/";
    /**
     * The extension of a trigger resource.
     */
    private static final @NotNull String TRIGGER_EXTENSION = ".trg";

    /**
     * Returns the condition of a victory BYTE; a value the game start has no resource for ({@code 0}, {@code 2}, above
     * {@code 7}) is {@link #NONE}.
     *
     * @param value the victory BYTE
     * @return the victory condition
     */
    public static @NotNull VictoryCondition of(int value) {
        for (VictoryCondition condition : values()) {
            if (condition.value == value) {
                return condition;
            }
        }

        return NONE;
    }

    private final int value;
    private final @Nullable String triggerName;
    private final int @NotNull [] subtypeValues;

    /**
     * Creates a {@link VictoryCondition} instance with its trigger resource selectors.
     *
     * @param value         the victory BYTE
     * @param triggerName   the trigger resource name, or {@code null} when none is loaded
     * @param subtypeValues the accepted subtype values, or an empty array for any value
     */
    VictoryCondition(int value, @Nullable String triggerName, int @NotNull ... subtypeValues) {
        this.value         = value;
        this.triggerName   = triggerName;
        this.subtypeValues = subtypeValues;
    }

    /**
     * @return the victory BYTE of this condition
     */
    public int getValue() {
        return this.value;
    }

    /**
     * Returns the installed trigger resource of the condition, as the game start selects it.
     *
     * @param subtypeValue the subtype value DWORD of the game options
     * @return the storage path, or {@code null} when the condition loads no resource
     * @throws IllegalArgumentException if Greed or Slaughter has another subtype value (the game start fails)
     */
    public @Nullable String getTriggerFile(long subtypeValue) {
        if (this.triggerName == null) {
            return null;
        }

        if (!this.acceptsSubtypeValue(subtypeValue)) {
            throw Exceptions.newIllegalArgumentException("{0} has no trigger resource for the subtype value {1}.", this, subtypeValue);
        }

        String suffix = this.subtypeValues.length == 0 ? "" : Long.toString(subtypeValue);

        return TRIGGER_DIRECTORY + this.triggerName + suffix + TRIGGER_EXTENSION;
    }


    /**
     * Checks a subtype value as the resource selection of the game start does: Greed and Slaughter accept only their four
     * template values, the other conditions any value.
     *
     * @param subtypeValue the subtype value DWORD of the game options
     * @return {@code true} if the game start can select the trigger resource, {@code false} otherwise
     */
    public boolean acceptsSubtypeValue(long subtypeValue) {
        if (this.subtypeValues.length == 0) {
            return true;
        }

        for (int accepted : this.subtypeValues) {
            if (accepted == subtypeValue) {
                return true;
            }
        }

        return false;
    }

    /**
     * Tells whether the game start creates the flag beacons.
     *
     * @return {@code true} if the game start creates the flag beacons, {@code false} otherwise
     */
    public boolean createsFlags() {
        return this == CAPTURE_THE_FLAG;
    }

    /**
     * Tells whether player removal kills the departing player's units; otherwise they go to the neutral player.
     *
     * @return {@code true} if player removal kills the departing player's units, {@code false} otherwise
     */
    public boolean killsDepartingUnits() {
        return this == CAPTURE_THE_FLAG || this == SUDDEN_DEATH;
    }
}
