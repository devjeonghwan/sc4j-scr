package org.fogofwarlabs.sc4j.scr.client.ui.status;

import org.fogofwarlabs.sc4j.scr.client.ui.text.StatText;
import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.content.ScrUpgradeIds;
import org.fogofwarlabs.sc4j.scr.content.ScrWeaponIds;
import org.fogofwarlabs.sc4j.scr.data.type.ScrUnitTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.ScrWeaponTypeView;
import org.fogofwarlabs.sc4j.scr.data.type.constant.UnitTypeFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Builds weapon, armor, shield and ammunition tooltips from the active unit and displayed icon.
 * Weapon damage uses the DAT damage factor and upgrade level. Armor includes the represented Ultralisk plating bonus.
 */
public final class StatIconTooltipUtils {
    private static final int DAMAGE_LABEL = 777;
    private static final int ARMOR_LABEL = 778;
    private static final int SHIELDS_LABEL = 779;
    private static final int INTERCEPTORS_NAME = 791;
    private static final int SCARABS_NAME = 792;
    private static final int NUKES_NAME = 793;
    private static final int SPIDER_MINES_NAME = 794;
    private static final int PER_ROCKET = 1301;

    /**
     * The chitinous plating armor bonus of Ultralisks.
     */
    private static final int CHITINOUS_BONUS = 2;

    private static final @NotNull String FORMAT = "%s\n%s %d";
    private static final @NotNull String BONUS_FORMAT = "%s\n%s %d+%d";

    /**
     * Builds the tooltip of a stat icon.
     *
     * @param view the game view
     * @param text the {@code stat_txt} strings
     * @param unit the active unit
     * @param icon the icon of the stat control (controls {@code 9..12})
     * @return the tooltip text, or an empty string for an icon kind without a tooltip
     * @throws NullPointerException if the world was built without game data
     */
    public static @NotNull String build(@NotNull ScrGameView view, @NotNull StatText text, @NotNull Unit unit, @NotNull StatusPanel.Icon icon) {
        int subject = icon.getSubject();

        return switch (icon.getKind()) {
            case StatusPanel.Icon.WEAPON -> buildWeapon(view,
                                                        text,
                                                        unit,
                                                        subject,
                                                        text.getString(view, view.requireGameData().getWeaponTypes().getLabel(subject)));
            case StatusPanel.Icon.ARMOR -> {
                ScrUnitTypeView units = view.requireGameData().getUnitTypes();
                int base = units.getArmor(unit.getUnitId());

                yield formatBonus(text.getString(view, view.requireGameData().getUpgradeTypes().getLabel(subject)),
                                  text.getString(view, ARMOR_LABEL),
                                  base,
                                  getArmorBonus(view, unit));
            }
            case StatusPanel.Icon.SHIELDS -> formatBonus(text.getString(view, view.requireGameData().getUpgradeTypes().getLabel(subject)),
                                                         text.getString(view, SHIELDS_LABEL),
                                                         0,
                                                         StatusPanel.getUpgradeLevel(view,
                                                                                     unit.getOwnerIndex(),
                                                                                     ScrUpgradeIds.PROTOSS_PLASMA_SHIELDS));
            case StatusPanel.Icon.UNIT -> switch (ScrUnitIds.orNone(subject)) {
                case ScrUnitIds.PROTOSS_SCARAB -> buildWeapon(view, text, unit, ScrWeaponIds.SCARAB, text.getString(view, SCARABS_NAME));
                case ScrUnitIds.PROTOSS_INTERCEPTOR -> buildWeapon(view, text, unit, ScrWeaponIds.PULSE_CANNON, text.getString(view, INTERCEPTORS_NAME));
                case ScrUnitIds.TERRAN_VULTURE_SPIDER_MINE -> buildWeapon(view,
                                                                          text,
                                                                          unit,
                                                                          ScrWeaponIds.SPIDER_MINES,
                                                                          text.getString(view, SPIDER_MINES_NAME));
                case ScrUnitIds.TERRAN_NUCLEAR_MISSILE -> text.getString(view, NUKES_NAME);
                default -> "";
            };
            default -> "";
        };
    }

    /**
     * Builds a damage tooltip: the base is the damage times the factor, the bonus is the damage bonus times the upgrade level
     * times the factor; Halo Rockets add {@code "per rocket"}.
     *
     * @param view   the game view
     * @param text   the {@code stat_txt} strings
     * @param unit   the unit whose upgrades apply
     * @param weapon the weapon type
     * @param name   the product name
     * @return the formatted tooltip
     * @throws NullPointerException if the world was built without game data
     */
    private static @NotNull String buildWeapon(@NotNull ScrGameView view, @NotNull StatText text, @NotNull Unit unit, int weapon, @NotNull String name) {
        ScrWeaponTypeView weapons = view.requireGameData().getWeaponTypes();
        int factor = weapons.getDamageFactor(weapon);
        int base = weapons.getDamageAmount(weapon) * factor;
        int bonus = weapons.getDamageBonus(weapon) * StatusPanel.getUpgradeLevel(view, unit.getOwnerIndex(), weapons.getDamageUpgrade(weapon)) * factor;
        String result = formatBonus(name, text.getString(view, DAMAGE_LABEL), base, bonus);

        return weapon == ScrWeaponIds.HALO_ROCKETS ? result + " " + text.getString(view, PER_ROCKET) : result;
    }

    /**
     * Formats the base value and includes a nonzero bonus.
     *
     * @param name  the product name
     * @param label the value label
     * @param base  the base value
     * @param bonus the additional value
     * @return the formatted tooltip
     */
    private static @NotNull String formatBonus(@NotNull String name, @NotNull String label, int base, int bonus) {
        if (bonus != 0) {
            return String.format(Locale.ROOT, BONUS_FORMAT, name, label, base, bonus);
        }

        return String.format(Locale.ROOT, FORMAT, name, label, base);
    }

    /**
     * Calculates the armor bonus: the level of the unit type's armor upgrade, plus {@code 2} for an Ultralisk that is a hero or
     * has Chitinous Plating (the expansion flag of the game is taken as set).
     *
     * @param view the game view
     * @param unit the unit
     * @return the armor bonus
     * @throws NullPointerException if the world was built without game data
     */
    private static int getArmorBonus(@NotNull ScrGameView view, @NotNull Unit unit) {
        ScrUnitTypeView units = view.requireGameData().getUnitTypes();
        int type = unit.getUnitId();
        int bonus = StatusPanel.getUpgradeLevel(view, unit.getOwnerIndex(), units.getArmorUpgrade(type));
        boolean ultralisk = type == ScrUnitIds.ZERG_ULTRALISK || type == ScrUnitIds.HERO_TORRASQUE;
        boolean hero = (units.getFlags(type) & UnitTypeFlags.HERO) != 0;

        if (ultralisk && (hero || StatusPanel.getUpgradeLevel(view, unit.getOwnerIndex(), ScrUpgradeIds.CHITINOUS_PLATING) != 0)) {
            bonus += CHITINOUS_BONUS;
        }

        return bonus;
    }


    private StatIconTooltipUtils() {
    }
}
