package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.type.ContentType;
import org.fogofwarlabs.sc4j.engine.content.type.TechType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Holds the tech type identities of the SC:R mod, one per techdata.dat entry, in techdata.dat order. The mod registers them in this
 * order, so the index of each registered type is its techdata.dat entry ({@link ScrNativeIds}).
 */
public final class ScrTechs {
    /**
     * The number of techdata.dat entries: the registered tech types.
     */
    public static final int COUNT = 44;
    /**
     * The techdata.dat entry that names no tech type, stored as {@link ContentType#NO_INDEX} in the engine tables.
     */
    public static final int NATIVE_NONE = 44;

    private static final @NotNull TechType @NotNull [] TYPES = new TechType[COUNT];

    /**
     * Stim Packs (techdata.dat entry {@code 0}).
     */
    public static final @NotNull TechType STIM_PACKS = create(0, "Stim Packs");
    /**
     * Lockdown (techdata.dat entry {@code 1}).
     */
    public static final @NotNull TechType LOCKDOWN = create(1, "Lockdown");
    /**
     * EMP Shockwave (techdata.dat entry {@code 2}).
     */
    public static final @NotNull TechType EMP_SHOCKWAVE = create(2, "EMP Shockwave");
    /**
     * Spider Mines (techdata.dat entry {@code 3}).
     */
    public static final @NotNull TechType SPIDER_MINES = create(3, "Spider Mines");
    /**
     * Scanner Sweep (techdata.dat entry {@code 4}).
     */
    public static final @NotNull TechType SCANNER_SWEEP = create(4, "Scanner Sweep");
    /**
     * Tank Siege Mode (techdata.dat entry {@code 5}).
     */
    public static final @NotNull TechType TANK_SIEGE_MODE = create(5, "Tank Siege Mode");
    /**
     * Defensive Matrix (techdata.dat entry {@code 6}).
     */
    public static final @NotNull TechType DEFENSIVE_MATRIX = create(6, "Defensive Matrix");
    /**
     * Irradiate (techdata.dat entry {@code 7}).
     */
    public static final @NotNull TechType IRRADIATE = create(7, "Irradiate");
    /**
     * Yamato Gun (techdata.dat entry {@code 8}).
     */
    public static final @NotNull TechType YAMATO_GUN = create(8, "Yamato Gun");
    /**
     * Cloaking Field (techdata.dat entry {@code 9}).
     */
    public static final @NotNull TechType CLOAKING_FIELD = create(9, "Cloaking Field");
    /**
     * Personnel Cloaking (techdata.dat entry {@code 10}).
     */
    public static final @NotNull TechType PERSONNEL_CLOAKING = create(10, "Personnel Cloaking");
    /**
     * Burrowing (techdata.dat entry {@code 11}).
     */
    public static final @NotNull TechType BURROWING = create(11, "Burrowing");
    /**
     * Infestation (techdata.dat entry {@code 12}).
     */
    public static final @NotNull TechType INFESTATION = create(12, "Infestation");
    /**
     * Spawn Broodlings (techdata.dat entry {@code 13}).
     */
    public static final @NotNull TechType SPAWN_BROODLINGS = create(13, "Spawn Broodlings");
    /**
     * Dark Swarm (techdata.dat entry {@code 14}).
     */
    public static final @NotNull TechType DARK_SWARM = create(14, "Dark Swarm");
    /**
     * Plague (techdata.dat entry {@code 15}).
     */
    public static final @NotNull TechType PLAGUE = create(15, "Plague");
    /**
     * Consume (techdata.dat entry {@code 16}).
     */
    public static final @NotNull TechType CONSUME = create(16, "Consume");
    /**
     * Ensnare (techdata.dat entry {@code 17}).
     */
    public static final @NotNull TechType ENSNARE = create(17, "Ensnare");
    /**
     * Parasite (techdata.dat entry {@code 18}).
     */
    public static final @NotNull TechType PARASITE = create(18, "Parasite");
    /**
     * Psionic Storm (techdata.dat entry {@code 19}).
     */
    public static final @NotNull TechType PSIONIC_STORM = create(19, "Psionic Storm");
    /**
     * Hallucination (techdata.dat entry {@code 20}).
     */
    public static final @NotNull TechType HALLUCINATION = create(20, "Hallucination");
    /**
     * Recall (techdata.dat entry {@code 21}).
     */
    public static final @NotNull TechType RECALL = create(21, "Recall");
    /**
     * Stasis Field (techdata.dat entry {@code 22}).
     */
    public static final @NotNull TechType STASIS_FIELD = create(22, "Stasis Field");
    /**
     * Archon Warp (techdata.dat entry {@code 23}).
     */
    public static final @NotNull TechType ARCHON_WARP = create(23, "Archon Warp");
    /**
     * Restoration (techdata.dat entry {@code 24}).
     */
    public static final @NotNull TechType RESTORATION = create(24, "Restoration");
    /**
     * Disruption Web (techdata.dat entry {@code 25}).
     */
    public static final @NotNull TechType DISRUPTION_WEB = create(25, "Disruption Web");
    /**
     * Unused 26 (techdata.dat entry {@code 26}).
     */
    public static final @NotNull TechType UNUSED_26 = create(26, "Unused 26");
    /**
     * Mind Control (techdata.dat entry {@code 27}).
     */
    public static final @NotNull TechType MIND_CONTROL = create(27, "Mind Control");
    /**
     * Dark Archon Meld (techdata.dat entry {@code 28}).
     */
    public static final @NotNull TechType DARK_ARCHON_MELD = create(28, "Dark Archon Meld");
    /**
     * Feedback (techdata.dat entry {@code 29}).
     */
    public static final @NotNull TechType FEEDBACK = create(29, "Feedback");
    /**
     * Optical Flare (techdata.dat entry {@code 30}).
     */
    public static final @NotNull TechType OPTICAL_FLARE = create(30, "Optical Flare");
    /**
     * Maelstrom (techdata.dat entry {@code 31}).
     */
    public static final @NotNull TechType MAELSTROM = create(31, "Maelstrom");
    /**
     * Lurker Aspect (techdata.dat entry {@code 32}).
     */
    public static final @NotNull TechType LURKER_ASPECT = create(32, "Lurker Aspect");
    /**
     * Unused 33 (techdata.dat entry {@code 33}).
     */
    public static final @NotNull TechType UNUSED_33 = create(33, "Unused 33");
    /**
     * Healing (techdata.dat entry {@code 34}).
     */
    public static final @NotNull TechType HEALING = create(34, "Healing");
    /**
     * Unused 35 (techdata.dat entry {@code 35}).
     */
    public static final @NotNull TechType UNUSED_35 = create(35, "Unused 35");
    /**
     * Unused 36 (techdata.dat entry {@code 36}).
     */
    public static final @NotNull TechType UNUSED_36 = create(36, "Unused 36");
    /**
     * Unused 37 (techdata.dat entry {@code 37}).
     */
    public static final @NotNull TechType UNUSED_37 = create(37, "Unused 37");
    /**
     * Unused 38 (techdata.dat entry {@code 38}).
     */
    public static final @NotNull TechType UNUSED_38 = create(38, "Unused 38");
    /**
     * Unused 39 (techdata.dat entry {@code 39}).
     */
    public static final @NotNull TechType UNUSED_39 = create(39, "Unused 39");
    /**
     * Unused 40 (techdata.dat entry {@code 40}).
     */
    public static final @NotNull TechType UNUSED_40 = create(40, "Unused 40");
    /**
     * Unused 41 (techdata.dat entry {@code 41}).
     */
    public static final @NotNull TechType UNUSED_41 = create(41, "Unused 41");
    /**
     * Unused 42 (techdata.dat entry {@code 42}).
     */
    public static final @NotNull TechType UNUSED_42 = create(42, "Unused 42");
    /**
     * Unused 43 (techdata.dat entry {@code 43}).
     */
    public static final @NotNull TechType UNUSED_43 = create(43, "Unused 43");

    private static final @NotNull List<@NotNull TechType> TYPE_LIST;

    static {
        TYPE_LIST = List.of(TYPES);
    }

    /**
     * Returns the identity of a techdata.dat entry.
     *
     * @param nativeId the techdata.dat entry, from {@code 0} to {@code COUNT - 1}
     * @return the identity
     * @throws IllegalArgumentException if the entry is outside the table
     */
    public static @NotNull TechType get(int nativeId) {
        if (nativeId < 0 || nativeId >= COUNT) {
            throw Exceptions.newIllegalArgumentException("Invalid techdata.dat entry: {0}", nativeId);
        }

        return TYPES[nativeId];
    }

    /**
     * @return every identity in techdata.dat order, as the mod registers them
     */
    public static @NotNull List<@NotNull TechType> getTypes() {
        return TYPE_LIST;
    }

    private static @NotNull TechType create(int nativeId, @NotNull String debugName) {
        TechType type = new TechType(debugName);

        TYPES[nativeId] = type;

        return type;
    }


    private ScrTechs() {
    }
}
