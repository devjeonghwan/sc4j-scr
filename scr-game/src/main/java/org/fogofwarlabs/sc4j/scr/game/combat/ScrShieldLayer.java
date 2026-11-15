package org.fogofwarlabs.sc4j.scr.game.combat;

import org.fogofwarlabs.sc4j.engine.content.combat.DamageHit;
import org.fogofwarlabs.sc4j.engine.content.combat.DamageLayer;
import org.fogofwarlabs.sc4j.engine.content.type.ContentType;
import org.fogofwarlabs.sc4j.scr.content.ScrDamageKinds;
import org.fogofwarlabs.sc4j.scr.content.ScrStats;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

/**
 * Takes a hit with the shields of an SC:R unit before its hit points: the shield damage layer the SC:R game registers on every unit type. The
 * points are the unit's shield points DWORD ({@link Unit#getShieldPoints()}), in units of {@code 1/256}; the full points are the shield amount
 * of a unit type with shields enabled.
 * <p>
 * The shields take part of a hit only while the unit type has shields enabled and the unit holds at least one whole point. A hit whose damage
 * kind does not ignore armor first loses the shield armor ({@link ScrStats#SHIELD_ARMOR}) of the unit's owner and type, down to half a point at
 * the least; the shields absorb as much of the rest as they hold. The weapon damage of the SC:R game ({@link UnitDamage#dealWeaponDamage}) takes
 * the absorbed points off the shields after it dealt the rest to the hit points.
 */
public final class ScrShieldLayer implements DamageLayer {
    /**
     * The name of the layer.
     */
    private static final @NotNull String NAME = "Shields";
    /**
     * The smallest damage of a hit after the shield armor, half a point.
     */
    private static final int MINIMUM_DAMAGE = 128;
    /**
     * The shields absorb only while they hold at least one whole point.
     */
    private static final int MINIMUM_ABSORBING_SHIELDS = 256;
    /**
     * The fraction bits of the shield points.
     */
    private static final int FIXED_POINT_FRACTION_BITS = 8;

    private final @NotNull Game game;

    /**
     * Creates a {@link ScrShieldLayer} instance for one game.
     *
     * @param game the game whose unit types and shield armor the layer reads
     */
    public ScrShieldLayer(@NotNull Game game) {
        this.game = game;
    }

    /**
     * @return the name of the layer that the client shows with its bar
     */
    @Override
    public @NotNull String getName() {
        return NAME;
    }

    /**
     * @param unit the unit
     * @return the points of the layer that the unit holds now
     * @throws IllegalArgumentException if the unit is not an SC:R unit
     */
    @Override
    public int getPoints(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit) {
        return Unit.of(unit).getShieldPoints();
    }

    /**
     * @param unit the unit
     * @return the points of the layer that the unit holds when the layer is full, {@code 0} if the unit's type has none of the layer
     * @throws IllegalArgumentException if the unit is not an SC:R unit
     * @throws NullPointerException     if the world was built without game data
     */
    @Override
    public int getMaximumPoints(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit) {
        ScrUnitTypeView unitTypes = this.game.requireGameData().getUnitTypes();
        int unitType = Unit.of(unit).getUnitId();

        return unitTypes.getShieldEnabled(unitType) != 0 ? unitTypes.getShieldAmount(unitType) << FIXED_POINT_FRACTION_BITS : 0;
    }

    /**
     * Sets the points of the layer that a unit holds.
     *
     * @param unit   the unit
     * @param points the points
     * @throws IllegalArgumentException if the unit is not an SC:R unit
     */
    @Override
    public void setPoints(org.fogofwarlabs.sc4j.engine.entity.@NotNull Unit unit, int points) {
        Unit.of(unit).setShieldPoints(points);
    }

    /**
     * Takes the layer's part of a hit: changes the hit's damage by the layer's own rules, such as its armour, and moves the part the layer
     * absorbs out of the hit's damage ({@link DamageHit#setDamage}). The layer's points do not change yet: the combat system takes the
     * returned part off them ({@link #setPoints}) when its rules say.
     * <p>
     * The shields of a unit type with shields enabled that hold at least one whole point take the shield armor off the damage, down to half a
     * point, unless the hit ignores armor, and absorb what they hold.
     *
     * @param hit the hit, whose damage is the part the layers before this one left
     * @return the absorbed part of the damage, {@code 0} if the layer takes none
     * @throws IllegalArgumentException      if the hit unit is not an SC:R unit
     * @throws NullPointerException          if the world was built without game data
     * @throws UnsupportedOperationException if the player upgrade levels are missing
     */
    @Override
    public int absorbDamage(@NotNull DamageHit hit) {
        Unit target = Unit.of(hit.getTargetUnit());
        int targetType = target.getUnitId();

        if (this.game.requireGameData().getUnitTypes().getShieldEnabled(targetType) == 0 || target.getShieldPoints() < MINIMUM_ABSORBING_SHIELDS) {
            return 0;
        }

        int damage = hit.getDamage();

        if (hit.getDamageKind() != ScrDamageKinds.IGNORE_ARMOR) {
            int shieldArmor = this.game.applyModifiers(ScrStats.SHIELD_ARMOR, target.getOwnerIndex(), targetType, ContentType.NO_INDEX, 0);

            if (damage > shieldArmor) {
                damage -= shieldArmor;
            } else {
                damage = MINIMUM_DAMAGE;
            }
        }

        int absorbed = Math.min(damage, target.getShieldPoints());

        hit.setDamage(damage - absorbed);

        return absorbed;
    }
}
