package org.fogofwarlabs.sc4j.scr.data.type.constant;

/**
 * Names weapons.dat effects applied when a projectile hits.
 */
public final class WeaponHitTypes {
    /**
     * No effect.
     */
    public static final int NONE = 0;
    /**
     * Damage to the target.
     */
    public static final int NORMAL = 1;
    /**
     * Splash damage to every unit around the hit.
     */
    public static final int RADIAL_SPLASH = 2;
    /**
     * Splash damage that spares allies.
     */
    public static final int ENEMY_SPLASH = 3;
    /**
     * Lockdown.
     */
    public static final int LOCKDOWN = 4;
    /**
     * Nuclear missile splash.
     */
    public static final int NUCLEAR_MISSILE = 5;
    /**
     * Parasite.
     */
    public static final int PARASITE = 6;
    /**
     * Spawn Broodlings.
     */
    public static final int BROODLINGS = 7;
    /**
     * EMP Shockwave.
     */
    public static final int EMP_SHOCKWAVE = 8;
    /**
     * Irradiate.
     */
    public static final int IRRADIATE = 9;
    /**
     * Ensnare.
     */
    public static final int ENSNARE = 10;
    /**
     * Plague.
     */
    public static final int PLAGUE = 11;
    /**
     * Stasis Field.
     */
    public static final int STASIS_FIELD = 12;
    /**
     * Dark Swarm.
     */
    public static final int DARK_SWARM = 13;
    /**
     * Consume.
     */
    public static final int CONSUME = 14;
    /**
     * Yamato Gun.
     */
    public static final int YAMATO_GUN = 15;
    /**
     * Restoration.
     */
    public static final int RESTORATION = 16;
    /**
     * Disruption Web.
     */
    public static final int DISRUPTION_WEB = 17;
    /**
     * Corrosive Acid, the Devourer's acid spores.
     */
    public static final int CORROSIVE_ACID = 18;
    /**
     * Mind Control.
     */
    public static final int MIND_CONTROL = 19;
    /**
     * Feedback.
     */
    public static final int FEEDBACK = 20;
    /**
     * Optical Flare.
     */
    public static final int OPTICAL_FLARE = 21;
    /**
     * Maelstrom.
     */
    public static final int MAELSTROM = 22;
    /**
     * Splash damage to air units only.
     */
    public static final int AIR_SPLASH = 24;

    private WeaponHitTypes() {
    }
}
