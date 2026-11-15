package org.fogofwarlabs.sc4j.scr.client.ui.status;

import org.fogofwarlabs.sc4j.scr.client.ui.text.StatText;
import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.game.order.UnitOrderEligibility;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitRacesUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Builds the tooltips of the selection panel's production controls: the queue slots and the egg / add-on slot (controls {@code 2..6}
 * and {@code 17}) and the research / upgrade slot (control {@code 15}).
 */
public final class ProductionTooltipUtils {
    /**
     * {@code stat_txt} {@code 802} {@code "Cancel Morph (%s)"}; {@code 803} {@code "Cancel Construction (%s)"} and {@code 804}
     * {@code "Cancel Warp (%s)"} follow by race.
     */
    private static final int CANCEL_UNIT = 802;
    /**
     * {@code stat_txt} {@code 805} {@code "Cancel Upgrade"}.
     */
    private static final int CANCEL_UPGRADE = 805;
    /**
     * {@code stat_txt} {@code 806} {@code "Cancel Research"}.
     */
    private static final int CANCEL_RESEARCH = 806;

    /**
     * The two-line cancellation label and product name format.
     */
    private static final @NotNull String CANCEL_FORMAT = "%s\n(%s)";

    /**
     * Builds the cancellation label and unit name for a production slot.
     * Disabled eggs, cocoons and lurker eggs have no tooltip.
     *
     * @param view   the game view
     * @param text   the {@code stat_txt} strings
     * @param active the active unit
     * @param icon   the slot icon
     * @return the tooltip text, or an empty string to clear the tooltip
     * @throws NullPointerException if the world was built without game data
     */
    public static @NotNull String buildQueue(@NotNull ScrGameView view, @NotNull StatText text, @NotNull Unit active, @NotNull StatusPanel.Icon icon) {
        if (icon.getKind() != StatusPanel.Icon.UNIT) {
            return "";
        }

        boolean egg = switch (ScrUnitIds.orNone(active.getUnitId())) {
            case ScrUnitIds.ZERG_EGG, ScrUnitIds.ZERG_COCOON, ScrUnitIds.ZERG_LURKER_EGG -> true;
            default -> false;
        };

        if (egg && UnitOrderEligibility.isDisabled(active)) {
            return "";
        }

        int race = UnitRacesUtils.getSupplyRace(view.requireGameData().getUnitTypes(), active.getUnitId());
        String format = text.getString(view, CANCEL_UNIT + race);

        return format.replace("%s", text.getUnitName(view, icon.getSubject()));
    }

    /**
     * Builds the tooltip of the research / upgrade slot: {@code "Cancel Research\n(<name>)"} or {@code "Cancel Upgrade\n(<name>)"}
     * with the {@code techdata.dat} or {@code upgrades.dat} label.
     *
     * @param view the game view
     * @param text the {@code stat_txt} strings
     * @param icon the slot icon
     * @return the tooltip text, or an empty string for another icon kind
     * @throws NullPointerException if the world was built without game data
     */
    public static @NotNull String buildCancel(@NotNull ScrGameView view, @NotNull StatText text, @NotNull StatusPanel.Icon icon) {
        int subject = icon.getSubject();

        return switch (icon.getKind()) {
            case StatusPanel.Icon.TECHNOLOGY -> String.format(CANCEL_FORMAT,
                                                              text.getString(view, CANCEL_RESEARCH),
                                                              text.getString(view, view.requireGameData().getTechTypes().getLabel(subject)));
            case StatusPanel.Icon.UPGRADE -> String.format(CANCEL_FORMAT,
                                                           text.getString(view, CANCEL_UPGRADE),
                                                           text.getString(view, view.requireGameData().getUpgradeTypes().getLabel(subject)));
            default -> "";
        };
    }


    private ProductionTooltipUtils() {
    }
}
