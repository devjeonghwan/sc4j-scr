package org.fogofwarlabs.sc4j.scr.client.glue.menu;

import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Edits shared action hotkeys, detects collisions per command card and tracks unit, race and saved-binding state.
 */
final class HotkeyEditor {
    /**
     * The key value of an action cleared by a binding collision.
     */
    static final int CLEARED = -1;
    /**
     * The key of an action without one.
     */
    static final int NONE = 0;

    private static final int UPPER_CASE_FIRST = 'A';
    private static final int UPPER_CASE_LAST = 'Z';
    private static final int LOWER_CASE_OFFSET = 'a' - 'A';
    /**
     * The page takes code points from the backquote up and refuses {@code {|}~}.
     */
    private static final int FIRST_ACCEPTED = '`';
    private static final int FIRST_REFUSED_BRACE = '{';
    private static final int LAST_REFUSED_TILDE = '~';
    /**
     * The punctuation the page takes below {@link #FIRST_ACCEPTED}.
     */
    private static final @NotNull String ACCEPTED_PUNCTUATION = "',.;[\\]";

    /**
     * Returns the accepted binding key after folding {@code A..Z} to {@code a..z}; accepts the backquote, {@code a..z},
     * code points from {@code 0x7F} up, and the punctuation {@code ' , . ; [ \ ]}.
     *
     * @param character the typed character's code point
     * @return the key, or {@link #NONE} when the page refuses the character
     */
    static int acceptKey(int character) {
        int key = character >= UPPER_CASE_FIRST && character <= UPPER_CASE_LAST ? character + LOWER_CASE_OFFSET : character;
        boolean accepted = !(key < FIRST_ACCEPTED || key >= FIRST_REFUSED_BRACE && key <= LAST_REFUSED_TILDE) ||
                           key < FIRST_ACCEPTED && ACCEPTED_PUNCTUATION.indexOf(key) >= 0;

        return accepted ? key : NONE;
    }

    private final @NotNull Map<Integer, Integer> keys;
    private final @NotNull Map<Integer, Integer> defaults;
    private final @NotNull Map<Integer, Integer> saved;

    private final @NotNull Set<HotkeyPanelModel.Unit> errorUnits;
    private final @NotNull Map<HotkeyPanelModel.Race, Integer> errorsByRace;
    private boolean unsavedChanges;

    /**
     * Creates a {@link HotkeyEditor} instance.
     *
     * @param device   the keys of {@code $device} by action id ({@link #NONE} or missing for none)
     * @param defaults the keys of {@code $default} by action id
     */
    HotkeyEditor(@NotNull Map<Integer, Integer> device, @NotNull Map<Integer, Integer> defaults) {
        this.keys     = new HashMap<>();
        this.defaults = new HashMap<>();
        this.saved    = new HashMap<>();

        this.errorUnits     = new HashSet<>();
        this.errorsByRace   = new EnumMap<>(HotkeyPanelModel.Race.class);
        this.unsavedChanges = false;

        for (int identifier : HotkeyPanelModel.getActions().keySet()) {
            int key = device.getOrDefault(identifier, NONE);

            this.keys.put(identifier, key);
            this.saved.put(identifier, key);
            this.defaults.put(identifier, defaults.getOrDefault(identifier, NONE));
        }
    }

    /**
     * @param identifier the action id
     * @return the action's key, {@link #NONE} or {@link #CLEARED}
     */
    int getKey(int identifier) {
        return this.keys.getOrDefault(identifier, NONE);
    }

    /**
     * @param identifier the action id
     * @return the action's default key, or {@link #NONE}
     */
    int getDefaultKey(int identifier) {
        return this.defaults.getOrDefault(identifier, NONE);
    }

    /**
     * @param identifier the action id
     * @return {@code true} if the action has no positive key binding, {@code false} otherwise
     */
    boolean isUnbound(int identifier) {
        return this.getKey(identifier) <= NONE;
    }

    /**
     * @return {@code true} if a unit is in error and Save is refused, {@code false} otherwise
     */
    boolean hasErrors() {
        return !this.errorUnits.isEmpty();
    }

    /**
     * @param unit the unit
     * @return {@code true} if the unit is in error, {@code false} otherwise
     */
    boolean hasError(HotkeyPanelModel.@NotNull Unit unit) {
        return this.errorUnits.contains(unit);
    }

    /**
     * @param race the race
     * @return {@code true} if a unit of the race is in error, {@code false} otherwise
     */
    boolean hasError(HotkeyPanelModel.@NotNull Race race) {
        return this.errorsByRace.getOrDefault(race, 0) > 0;
    }

    /**
     * @return {@code true} if the page counts its bindings as changed since they were loaded, {@code false} otherwise
     */
    boolean hasUnsavedChanges() {
        return this.unsavedChanges;
    }

    /**
     * Returns every action in identifier order with its key, using {@link #NONE} for an unbound action.
     *
     * @return the keys by action id
     */
    @NotNull Map<Integer, Integer> getBindings() {
        Map<Integer, Integer> bindings = new LinkedHashMap<>();

        for (int identifier : HotkeyPanelModel.getActions().keySet()) {
            bindings.put(identifier, Math.max(NONE, this.getKey(identifier)));
        }

        return bindings;
    }


    /**
     * Binds a key to an action and updates collision state.
     *
     * @param selected the selected unit
     * @param action   the selected action
     * @param key      the key, from {@link #acceptKey}
     */
    void rebind(HotkeyPanelModel.@NotNull Unit selected, HotkeyPanelModel.@NotNull Action action, int key) {
        this.keys.put(action.getIdentifier(), key);
        this.checkCollisions(selected, action);

        this.errorUnits.clear();
        this.errorsByRace.clear();

        for (HotkeyPanelModel.Unit unit : HotkeyPanelModel.getUnits()) {
            if (!this.checkCollisions(unit, action)) {
                continue;
            }

            this.errorUnits.add(unit);
            this.errorsByRace.merge(unit.getRace(), 1, Integer::sum);
        }
    }

    /**
     * Checks one unit's cards for collisions and stores whether its keys differ from the saved bindings.
     *
     * @param unit    the unit whose cards are checked
     * @param rebound the action that was bound
     * @return {@code true} if the unit is in error, {@code false} otherwise
     */
    private boolean checkCollisions(HotkeyPanelModel.@NotNull Unit unit, HotkeyPanelModel.@NotNull Action rebound) {
        int reboundKey = this.getKey(rebound.getIdentifier());
        boolean changed = false;
        boolean collisions = false;

        for (HotkeyPanelModel.Card card : unit.getCards()) {
            HotkeyPanelModel.Action collision = null;
            boolean holdsRebound = false;

            for (HotkeyPanelModel.Action action : card.getActions()) {
                int identifier = action.getIdentifier();
                int key = this.getKey(identifier);

                if (identifier == rebound.getIdentifier()) {
                    holdsRebound = true;
                } else if (key == reboundKey) {
                    collision = action;
                } else if (key <= NONE) {
                    collisions = true;
                }

                if (this.saved.getOrDefault(identifier, NONE) != key) {
                    changed = true;
                }
            }

            if (holdsRebound && collision != null) {
                collisions = true;
                this.keys.put(collision.getIdentifier(), CLEARED);
            }
        }

        this.unsavedChanges = changed;

        return collisions;
    }

    /**
     * Copies default keys into every action and clears errors, retaining the current unsaved-change flag.
     */
    void restoreDefaults() {
        this.keys.putAll(this.defaults);
        this.errorsByRace.clear();
        this.errorUnits.clear();
    }
}
