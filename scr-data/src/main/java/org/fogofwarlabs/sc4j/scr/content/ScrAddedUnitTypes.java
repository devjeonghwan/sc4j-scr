package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.type.UnitType;
import org.fogofwarlabs.sc4j.engine.content.type.WeaponType;
import org.fogofwarlabs.sc4j.scr.data.format.dat.WeaponsDat;
import org.fogofwarlabs.sc4j.scr.data.type.constant.TriggerUnitGroups;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Registers the unit types that a mod adds after the native {@code units.dat} entries ({@link ScrAddedUnitType}) and the weapons it adds after the
 * native {@code weapons.dat} entries for them ({@link ScrAddedWeapon}), each in registration order.
 * <p>
 * The SC:R unit type id of the {@code n}-th added type is {@link #FIRST_ID}{@code  + n}: above the native entries {@code 0..227}, the none id
 * {@code 228} and the trigger group ids {@code 229..232} ({@link TriggerUnitGroups}), so no native id changes meaning. The ids stay within the
 * WORD fields of commands, replays and unit records, below {@code 0xFF00}, where requirement scripts keep their opcode words. The engine content
 * registers the added types right after the native ones, so the engine index of an added type is its id minus {@link #CONTENT_INDEX_SHIFT}.
 * <p>
 * The {@code weapons.dat} id of the {@code n}-th added weapon is {@link #FIRST_WEAPON_ID}{@code  + n}: above the native entries {@code 0..129}
 * and the none id {@code 130}, within the BYTE weapon fields of {@code units.dat} and of the bullet records. The engine content registers the
 * added weapons right after the native ones, so the engine index of an added weapon is its id minus {@link #WEAPON_CONTENT_INDEX_SHIFT}. A
 * game's weapon view reads an added weapon's values from its own columns ({@code WeaponsDat}); the native ids keep their DAT memory.
 * <p>
 * The registration is open until the content of the games is built from it ({@link #freeze()}); with no added type every table, replay and
 * hash is the native one. A replay holds the ids only: it plays back with the same registration.
 */
public final class ScrAddedUnitTypes {
    /**
     * The SC:R unit type id of the first added type, the first id after the trigger group ids.
     */
    public static final int FIRST_ID = TriggerUnitGroups.FACTORIES + 1;
    /**
     * The highest SC:R unit type id an added type may get, below the opcode words of the requirement scripts.
     */
    public static final int LAST_ID = 0xFEFF;
    /**
     * The difference between the SC:R id and the engine content index of an added type: the ids {@code 228..232} have no content type.
     */
    public static final int CONTENT_INDEX_SHIFT = FIRST_ID - ScrUnits.COUNT;
    /**
     * The {@code weapons.dat} id of the first added weapon, the first id after the none id.
     */
    public static final int FIRST_WEAPON_ID = WeaponsDat.FIRST_ADDED_WEAPON;
    /**
     * The highest {@code weapons.dat} id an added weapon may get, the largest value of the BYTE weapon fields.
     */
    public static final int LAST_WEAPON_ID = 0xFF;
    /**
     * The difference between the {@code weapons.dat} id and the engine content index of an added weapon: the none id has no content type.
     */
    public static final int WEAPON_CONTENT_INDEX_SHIFT = FIRST_WEAPON_ID - ScrWeapons.COUNT;

    /**
     * Converts the SC:R id of an added type to its engine content index.
     *
     * @param unitTypeIdentifier the SC:R unit type id, from {@link #FIRST_ID} on
     * @return the engine content index
     */
    public static int toContentIndex(int unitTypeIdentifier) {
        return unitTypeIdentifier - CONTENT_INDEX_SHIFT;
    }


    private final @NotNull List<@NotNull ScrAddedUnitType> types;
    private final @NotNull Map<@NotNull UnitType, @NotNull Integer> ids;
    private final @NotNull List<@NotNull ScrAddedWeapon> weapons;
    private final @NotNull Map<@NotNull WeaponType, @NotNull Integer> weaponIds;

    private boolean frozen;

    /**
     * Creates a {@link ScrAddedUnitTypes} instance without added types, open for registration.
     */
    public ScrAddedUnitTypes() {
        this.types     = new ArrayList<>();
        this.ids       = new IdentityHashMap<>();
        this.weapons   = new ArrayList<>();
        this.weaponIds = new IdentityHashMap<>();

        this.frozen = false;
    }

    /**
     * @return the number of added types
     */
    public int getCount() {
        return this.types.size();
    }

    /**
     * @return the added types in registration order, the order of their ids
     */
    public @NotNull List<@NotNull ScrAddedUnitType> getTypes() {
        return Collections.unmodifiableList(this.types);
    }

    /**
     * @return {@code true} if the registration is closed, {@code false} otherwise
     */
    public boolean isFrozen() {
        return this.frozen;
    }


    /**
     * Adds a unit type after the registered ones.
     *
     * @param type the added type
     * @return the SC:R unit type id of the type
     * @throws IllegalStateException    if the registration is closed
     * @throws IllegalArgumentException if the identity is a native unit type or already added, or no id is left
     */
    public int add(@NotNull ScrAddedUnitType type) {
        if (this.frozen) {
            throw Exceptions.newIllegalStateException("The added unit types are closed: {0}", type.getType());
        }

        UnitType identity = type.getType();
        int id = FIRST_ID + this.types.size();

        if (ScrUnits.getTypes().contains(identity) || this.ids.containsKey(identity)) {
            throw Exceptions.newIllegalArgumentException("The unit type {0} is already registered.", identity);
        }

        if (id > LAST_ID) {
            throw Exceptions.newIllegalArgumentException("No unit type id is left for {0}.", identity);
        }

        this.types.add(type);
        this.ids.put(identity, id);

        return id;
    }

    /**
     * Adds a weapon after the registered ones; an added unit type names it through its weapon values.
     *
     * @param weapon the added weapon
     * @return the {@code weapons.dat} id of the weapon
     * @throws IllegalStateException    if the registration is closed
     * @throws IllegalArgumentException if the identity is a native weapon or already added, or no id is left
     */
    public int addWeapon(@NotNull ScrAddedWeapon weapon) {
        if (this.frozen) {
            throw Exceptions.newIllegalStateException("The added weapons are closed: {0}", weapon.getType());
        }

        WeaponType identity = weapon.getType();
        int id = FIRST_WEAPON_ID + this.weapons.size();

        if (ScrWeapons.getTypes().contains(identity) || this.weaponIds.containsKey(identity)) {
            throw Exceptions.newIllegalArgumentException("The weapon {0} is already registered.", identity);
        }

        if (id > LAST_WEAPON_ID) {
            throw Exceptions.newIllegalArgumentException("No weapon id is left for {0}.", identity);
        }

        this.weapons.add(weapon);
        this.weaponIds.put(identity, id);

        return id;
    }

    /**
     * @return the added weapons in registration order, the order of their ids
     */
    public @NotNull List<@NotNull ScrAddedWeapon> getWeapons() {
        return Collections.unmodifiableList(this.weapons);
    }

    /**
     * Tests whether an id names an added weapon.
     *
     * @param weaponIdentifier a {@code weapons.dat} id or any other value
     * @return {@code true} if the id names an added weapon, {@code false} otherwise
     */
    public boolean containsWeapon(int weaponIdentifier) {
        int index = weaponIdentifier - FIRST_WEAPON_ID;

        return index >= 0 && index < this.weapons.size();
    }

    /**
     * Closes the registration; the content of the games is built from the closed registration.
     */
    public void freeze() {
        this.frozen = true;
    }

    /**
     * Tests whether an id names an added unit type.
     *
     * @param unitTypeIdentifier a SC:R unit type id or any other value
     * @return {@code true} if the id names an added type, {@code false} otherwise
     */
    public boolean contains(int unitTypeIdentifier) {
        int index = unitTypeIdentifier - FIRST_ID;

        return index >= 0 && index < this.types.size();
    }

    /**
     * Returns the added type of an id.
     *
     * @param unitTypeIdentifier the SC:R unit type id
     * @return the added type
     * @throws IllegalArgumentException if the id names no added type
     */
    public @NotNull ScrAddedUnitType get(int unitTypeIdentifier) {
        if (!this.contains(unitTypeIdentifier)) {
            throw Exceptions.newIllegalArgumentException("Not an added unit type id: {0}", unitTypeIdentifier);
        }

        return this.types.get(unitTypeIdentifier - FIRST_ID);
    }

    /**
     * Returns the native unit type id of the base type of an added type ({@link ScrAddedUnitType#getBaseType()}): the type whose per-type rules
     * apply to units of the added type.
     *
     * @param unitTypeIdentifier the SC:R unit type id
     * @return the native unit type id of the base type
     * @throws IllegalArgumentException if the id names no added type
     */
    public int getBaseId(int unitTypeIdentifier) {
        return ScrUnits.getNativeId(this.get(unitTypeIdentifier).getBaseType());
    }

    /**
     * Returns the SC:R unit type id of an added identity.
     *
     * @param type the engine identity of an added type
     * @return the SC:R unit type id
     * @throws IllegalArgumentException if the identity is not added
     */
    public int getId(@NotNull UnitType type) {
        Integer id = this.ids.get(type);

        if (id == null) {
            throw Exceptions.newIllegalArgumentException("Not an added unit type: {0}", type);
        }

        return id;
    }
}
