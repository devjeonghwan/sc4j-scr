package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;

/**
 * Holds the native tech identifiers (techdata.dat entries) of the SC:R mod as {@code int} constants: the values the SC:R
 * simulation stores and compares, and the values EUD, CHK sections, replays and saved games hold. {@link ScrTechs} holds the
 * engine identities; {@link ScrNativeIds} maps between the two.
 */
public final class ScrTechIds {
    /**
     * Stim Packs (techdata.dat entry {@code 0}).
     */
    public static final int STIM_PACKS = 0;
    /**
     * Lockdown (techdata.dat entry {@code 1}).
     */
    public static final int LOCKDOWN = 1;
    /**
     * EMP Shockwave (techdata.dat entry {@code 2}).
     */
    public static final int EMP_SHOCKWAVE = 2;
    /**
     * Spider Mines (techdata.dat entry {@code 3}).
     */
    public static final int SPIDER_MINES = 3;
    /**
     * Scanner Sweep (techdata.dat entry {@code 4}).
     */
    public static final int SCANNER_SWEEP = 4;
    /**
     * Tank Siege Mode (techdata.dat entry {@code 5}).
     */
    public static final int TANK_SIEGE_MODE = 5;
    /**
     * Defensive Matrix (techdata.dat entry {@code 6}).
     */
    public static final int DEFENSIVE_MATRIX = 6;
    /**
     * Irradiate (techdata.dat entry {@code 7}).
     */
    public static final int IRRADIATE = 7;
    /**
     * Yamato Gun (techdata.dat entry {@code 8}).
     */
    public static final int YAMATO_GUN = 8;
    /**
     * Cloaking Field (techdata.dat entry {@code 9}).
     */
    public static final int CLOAKING_FIELD = 9;
    /**
     * Personnel Cloaking (techdata.dat entry {@code 10}).
     */
    public static final int PERSONNEL_CLOAKING = 10;
    /**
     * Burrowing (techdata.dat entry {@code 11}).
     */
    public static final int BURROWING = 11;
    /**
     * Infestation (techdata.dat entry {@code 12}).
     */
    public static final int INFESTATION = 12;
    /**
     * Spawn Broodlings (techdata.dat entry {@code 13}).
     */
    public static final int SPAWN_BROODLINGS = 13;
    /**
     * Dark Swarm (techdata.dat entry {@code 14}).
     */
    public static final int DARK_SWARM = 14;
    /**
     * Plague (techdata.dat entry {@code 15}).
     */
    public static final int PLAGUE = 15;
    /**
     * Consume (techdata.dat entry {@code 16}).
     */
    public static final int CONSUME = 16;
    /**
     * Ensnare (techdata.dat entry {@code 17}).
     */
    public static final int ENSNARE = 17;
    /**
     * Parasite (techdata.dat entry {@code 18}).
     */
    public static final int PARASITE = 18;
    /**
     * Psionic Storm (techdata.dat entry {@code 19}).
     */
    public static final int PSIONIC_STORM = 19;
    /**
     * Hallucination (techdata.dat entry {@code 20}).
     */
    public static final int HALLUCINATION = 20;
    /**
     * Recall (techdata.dat entry {@code 21}).
     */
    public static final int RECALL = 21;
    /**
     * Stasis Field (techdata.dat entry {@code 22}).
     */
    public static final int STASIS_FIELD = 22;
    /**
     * Archon Warp (techdata.dat entry {@code 23}).
     */
    public static final int ARCHON_WARP = 23;
    /**
     * Restoration (techdata.dat entry {@code 24}).
     */
    public static final int RESTORATION = 24;
    /**
     * Disruption Web (techdata.dat entry {@code 25}).
     */
    public static final int DISRUPTION_WEB = 25;
    /**
     * Unused 26 (techdata.dat entry {@code 26}).
     */
    public static final int UNUSED_26 = 26;
    /**
     * Mind Control (techdata.dat entry {@code 27}).
     */
    public static final int MIND_CONTROL = 27;
    /**
     * Dark Archon Meld (techdata.dat entry {@code 28}).
     */
    public static final int DARK_ARCHON_MELD = 28;
    /**
     * Feedback (techdata.dat entry {@code 29}).
     */
    public static final int FEEDBACK = 29;
    /**
     * Optical Flare (techdata.dat entry {@code 30}).
     */
    public static final int OPTICAL_FLARE = 30;
    /**
     * Maelstrom (techdata.dat entry {@code 31}).
     */
    public static final int MAELSTROM = 31;
    /**
     * Lurker Aspect (techdata.dat entry {@code 32}).
     */
    public static final int LURKER_ASPECT = 32;
    /**
     * Unused 33 (techdata.dat entry {@code 33}).
     */
    public static final int UNUSED_33 = 33;
    /**
     * Healing (techdata.dat entry {@code 34}).
     */
    public static final int HEALING = 34;
    /**
     * Unused 35 (techdata.dat entry {@code 35}).
     */
    public static final int UNUSED_35 = 35;
    /**
     * Unused 36 (techdata.dat entry {@code 36}).
     */
    public static final int UNUSED_36 = 36;
    /**
     * Unused 37 (techdata.dat entry {@code 37}).
     */
    public static final int UNUSED_37 = 37;
    /**
     * Unused 38 (techdata.dat entry {@code 38}).
     */
    public static final int UNUSED_38 = 38;
    /**
     * Unused 39 (techdata.dat entry {@code 39}).
     */
    public static final int UNUSED_39 = 39;
    /**
     * Unused 40 (techdata.dat entry {@code 40}).
     */
    public static final int UNUSED_40 = 40;
    /**
     * Unused 41 (techdata.dat entry {@code 41}).
     */
    public static final int UNUSED_41 = 41;
    /**
     * Unused 42 (techdata.dat entry {@code 42}).
     */
    public static final int UNUSED_42 = 42;
    /**
     * Unused 43 (techdata.dat entry {@code 43}).
     */
    public static final int UNUSED_43 = 43;
    /**
     * The "none" sentinel index ({@code 44}) that follows the techdata.dat entries.
     */
    public static final int NONE = 44;

    /**
     * The number of values, from {@code 0} through {@link #NONE}.
     */
    private static final int VALUE_COUNT = 45;

    /**
     * @param identifier the value to check
     * @return {@code true} if the value is a tech identifier, {@code false} otherwise
     */
    public static boolean isValid(int identifier) {
        return identifier >= 0 && identifier < VALUE_COUNT;
    }

    /**
     * Checks that a value is a tech identifier.
     *
     * @param identifier the value to check
     * @return the value
     * @throws IllegalArgumentException if the value is not a tech identifier
     */
    public static int requireValid(int identifier) {
        if (!isValid(identifier)) {
            throw Exceptions.newIllegalArgumentException("Invalid technology type index: {0}", identifier);
        }

        return identifier;
    }

    /**
     * Returns a value, or {@link #NONE} for a value outside the table: a {@code switch} over the result without a
     * {@code NONE} case keeps its {@code default} branch for values read from replays, triggers or scripts.
     *
     * @param identifier the value
     * @return the value if it is valid, {@link #NONE} otherwise
     */
    public static int orNone(int identifier) {
        return isValid(identifier) ? identifier : NONE;
    }


    private ScrTechIds() {
    }
}
