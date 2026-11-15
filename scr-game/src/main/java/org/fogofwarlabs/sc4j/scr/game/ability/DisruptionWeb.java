package org.fogofwarlabs.sc4j.scr.game.ability;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.game.frame.UnitPhase;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.sprite.Sprite;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitPool;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitStatusFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.finder.UnitSpatialIndex;
import org.jetbrains.annotations.NotNull;

/**
 * Applies the neutral Disruption Web restriction pass.
 * <p>
 * After {@link UnitPhase#clearRestrictionFlags()} removes every cannot-attack restriction, each neutral Disruption Web queries its inner bounding box
 * with the unexpanded rectangle query and restores the restriction on every non-flying unit found, and on its loaded units or else its subunit.
 */
public final class DisruptionWeb {
    private final @NotNull Game game;

    /**
     * Creates a {@link DisruptionWeb} instance for one game.
     *
     * @param game the game this system belongs to
     */
    public DisruptionWeb(@NotNull Game game) {
        this.game = game;
    }

    /**
     * Restores the cannot-attack restriction under every neutral Disruption Web.
     *
     * @throws UnsupportedOperationException if the unit spatial index is unavailable or the unit pool is not initialized
     * @throws IllegalStateException         if the web has no sprite
     * @throws NullPointerException          if the world was built without game data
     */
    public void apply() {
        UnitPool pool = this.game.getUnitSystem().requireUnitPool();
        ScrUnitTypeView units = this.game.requireGameData().getUnitTypes();

        for (int poolIndex = pool.getFirstOwnerIndex(PlayerTable.NEUTRAL_PLAYER_ID); poolIndex != 0; poolIndex = pool.getNextOwnerIndex(poolIndex)) {
            Unit web = pool.getUnit(poolIndex);

            if (this.game.getBaseType(web) != ScrUnitIds.SPELL_DISRUPTION_WEB) {
                continue;
            }

            Sprite sprite = web.requireSprite();
            int positionX = (short) sprite.getX();
            int positionY = (short) sprite.getY();
            int left = (short) (positionX - units.getDimensionLeft(ScrUnitIds.SPELL_DISRUPTION_WEB));
            int upper = (short) (positionY - units.getDimensionUp(ScrUnitIds.SPELL_DISRUPTION_WEB));
            int right = (short) (positionX + units.getDimensionRight(ScrUnitIds.SPELL_DISRUPTION_WEB));
            int lower = (short) (positionY + units.getDimensionDown(ScrUnitIds.SPELL_DISRUPTION_WEB));
            UnitSpatialIndex index = this.game.getUnitSystem().getUnitSpatialIndex();

            if (index == null) {
                throw Exceptions.newUnsupportedOperationException("Native rectangle queries require the unit spatial index.");
            }

            int[] results = index.findAllInRectangleUnexpanded(left, upper, right, lower);

            for (int result : results) {
                Unit target = pool.getUnit(result);

                if ((target.getStatus() & UnitStatusFlags.FLYING) != 0) {
                    continue;
                }

                target.setStatus(target.getStatus() | UnitStatusFlags.CANNOT_ATTACK);

                if (this.game.getUnitQueries().isTransport(target)) {
                    for (int slot = 0; slot < Unit.LOADED_UNIT_SLOTS; slot++) {
                        Unit loaded = this.game.getUnitTransport().getLoadedUnit(target, slot);

                        if (loaded != null) {
                            loaded.setStatus(loaded.getStatus() | UnitStatusFlags.CANNOT_ATTACK);
                        }
                    }
                } else {
                    Unit subunit = target.getSubunit();

                    if (subunit != null) {
                        subunit.setStatus(subunit.getStatus() | UnitStatusFlags.CANNOT_ATTACK);
                    }
                }
            }
        }
    }
}
