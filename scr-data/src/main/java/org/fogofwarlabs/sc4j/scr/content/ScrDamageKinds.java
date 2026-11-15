package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.engine.content.combat.DamageKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Holds the damage kinds of the SC:R mod, in the order of the native damage type values of weapons.dat, the order the mod
 * registers them: independent, explosive, concussive, normal and ignore armor.
 */
public final class ScrDamageKinds {
    /**
     * Independent damage (damage type {@code 0}): one point per hit, whatever the armor.
     */
    public static final @NotNull DamageKind INDEPENDENT = new DamageKind("Independent");
    /**
     * Explosive damage (damage type {@code 1}).
     */
    public static final @NotNull DamageKind EXPLOSIVE = new DamageKind("Explosive");
    /**
     * Concussive damage (damage type {@code 2}).
     */
    public static final @NotNull DamageKind CONCUSSIVE = new DamageKind("Concussive");
    /**
     * Normal damage (damage type {@code 3}).
     */
    public static final @NotNull DamageKind NORMAL = new DamageKind("Normal");
    /**
     * Damage that ignores armor (damage type {@code 4}).
     */
    public static final @NotNull DamageKind IGNORE_ARMOR = new DamageKind("Ignore Armor");

    private static final @NotNull List<@NotNull DamageKind> TYPE_LIST = List.of(INDEPENDENT, EXPLOSIVE, CONCUSSIVE, NORMAL, IGNORE_ARMOR);

    /**
     * @return every damage kind in registration order
     */
    public static @NotNull List<@NotNull DamageKind> getTypes() {
        return TYPE_LIST;
    }

    /**
     * Finds the damage kind of a native damage type value of weapons.dat.
     *
     * @param damageType the native damage type
     * @return the damage kind, or {@code null} for a value past the native kinds
     */
    public static @Nullable DamageKind find(int damageType) {
        return damageType >= 0 && damageType < TYPE_LIST.size() ? TYPE_LIST.get(damageType) : null;
    }


    private ScrDamageKinds() {
    }
}
