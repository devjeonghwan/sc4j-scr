package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.type.SoundType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Holds the sound type identities of the SC:R mod, one per sfxdata.dat entry, in sfxdata.dat order: the entries the game code names
 * are constants, every other entry is named {@code "sound <entry>"}. The mod registers them in this order, so the index of
 * each registered type is its sfxdata.dat entry ({@link ScrNativeIds}).
 */
public final class ScrSounds {
    /**
     * The number of sfxdata.dat entries: the registered sound types.
     */
    public static final int COUNT = 1144;

    private static final @Nullable SoundType @NotNull [] TYPES = new SoundType[COUNT];

    /**
     * Zerg Building Placement (sfxdata.dat entry {@code 4}).
     */
    public static final @NotNull SoundType ZERG_BUILDING_PLACEMENT = create(4, "Zerg Building Placement");
    /**
     * Burrow Down (sfxdata.dat entry {@code 16}).
     */
    public static final @NotNull SoundType BURROW_DOWN = create(16, "Burrow Down");
    /**
     * Burrow Up (sfxdata.dat entry {@code 17}).
     */
    public static final @NotNull SoundType BURROW_UP = create(17, "Burrow Up");
    /**
     * Nydus Canal Transit (sfxdata.dat entry {@code 19}).
     */
    public static final @NotNull SoundType NYDUS_CANAL_TRANSIT = create(19, "Nydus Canal Transit");
    /**
     * Transport Load (sfxdata.dat entry {@code 40}).
     */
    public static final @NotNull SoundType TRANSPORT_LOAD = create(40, "Transport Load");
    /**
     * Transport Unload (sfxdata.dat entry {@code 43}).
     */
    public static final @NotNull SoundType TRANSPORT_UNLOAD = create(43, "Transport Unload");
    /**
     * Nuclear Missile Launch (sfxdata.dat entry {@code 84}).
     */
    public static final @NotNull SoundType NUCLEAR_MISSILE_LAUNCH = create(84, "Nuclear Missile Launch");
    /**
     * Zerg Advisor Update 4 (sfxdata.dat entry {@code 127}).
     */
    public static final @NotNull SoundType ZERG_ADVISOR_UPDATE_4 = create(127, "Zerg Advisor Update 4");
    /**
     * Ghost Laser (sfxdata.dat entry {@code 239}).
     */
    public static final @NotNull SoundType GHOST_LASER = create(239, "Ghost Laser");
    /**
     * Unit Cloak (sfxdata.dat entry {@code 273}).
     */
    public static final @NotNull SoundType UNIT_CLOAK = create(273, "Unit Cloak");
    /**
     * Unit Decloak (sfxdata.dat entry {@code 274}).
     */
    public static final @NotNull SoundType UNIT_DECLOAK = create(274, "Unit Decloak");
    /**
     * Marine Stim Pack (sfxdata.dat entry {@code 278}).
     */
    public static final @NotNull SoundType MARINE_STIM_PACK = create(278, "Marine Stim Pack");
    /**
     * Science Vessel Defensive Matrix (sfxdata.dat entry {@code 349}).
     */
    public static final @NotNull SoundType SCIENCE_VESSEL_DEFENSIVE_MATRIX = create(349, "Science Vessel Defensive Matrix");
    /**
     * Science Vessel Irradiate (sfxdata.dat entry {@code 351}).
     */
    public static final @NotNull SoundType SCIENCE_VESSEL_IRRADIATE = create(351, "Science Vessel Irradiate");
    /**
     * Comsat Scanner Sweep (sfxdata.dat entry {@code 388}).
     */
    public static final @NotNull SoundType COMSAT_SCANNER_SWEEP = create(388, "Comsat Scanner Sweep");
    /**
     * Protoss Building Warp Start (sfxdata.dat entry {@code 528}).
     */
    public static final @NotNull SoundType PROTOSS_BUILDING_WARP_START = create(528, "Protoss Building Warp Start");
    /**
     * Protoss Building Warp End (sfxdata.dat entry {@code 529}).
     */
    public static final @NotNull SoundType PROTOSS_BUILDING_WARP_END = create(529, "Protoss Building Warp End");
    /**
     * Arbiter Recall Cast (sfxdata.dat entry {@code 550}).
     */
    public static final @NotNull SoundType ARBITER_RECALL_CAST = create(550, "Arbiter Recall Cast");
    /**
     * Arbiter Recall Arrival (sfxdata.dat entry {@code 552}).
     */
    public static final @NotNull SoundType ARBITER_RECALL_ARRIVAL = create(552, "Arbiter Recall Arrival");
    /**
     * Interceptor Launch (sfxdata.dat entry {@code 616}).
     */
    public static final @NotNull SoundType INTERCEPTOR_LAUNCH = create(616, "Interceptor Launch");
    /**
     * High Templar Hallucination (sfxdata.dat entry {@code 618}).
     */
    public static final @NotNull SoundType HIGH_TEMPLAR_HALLUCINATION = create(618, "High Templar Hallucination");
    /**
     * Hallucination Death (sfxdata.dat entry {@code 619}).
     */
    public static final @NotNull SoundType HALLUCINATION_DEATH = create(619, "Hallucination Death");
    /**
     * Queen Parasite (sfxdata.dat entry {@code 921}).
     */
    public static final @NotNull SoundType QUEEN_PARASITE = create(921, "Queen Parasite");
    /**
     * Medic Restoration (sfxdata.dat entry {@code 998}).
     */
    public static final @NotNull SoundType MEDIC_RESTORATION = create(998, "Medic Restoration");
    /**
     * Medic Optical Flare (sfxdata.dat entry {@code 1019}).
     */
    public static final @NotNull SoundType MEDIC_OPTICAL_FLARE = create(1019, "Medic Optical Flare");
    /**
     * Dark Archon Feedback (sfxdata.dat entry {@code 1061}).
     */
    public static final @NotNull SoundType DARK_ARCHON_FEEDBACK = create(1061, "Dark Archon Feedback");
    /**
     * Dark Archon Mind Control (sfxdata.dat entry {@code 1062}).
     */
    public static final @NotNull SoundType DARK_ARCHON_MIND_CONTROL = create(1062, "Dark Archon Mind Control");
    /**
     * Dark Archon Maelstrom (sfxdata.dat entry {@code 1064}).
     */
    public static final @NotNull SoundType DARK_ARCHON_MAELSTROM = create(1064, "Dark Archon Maelstrom");

    private static final @NotNull List<@NotNull SoundType> TYPE_LIST;

    static {
        for (int nativeId = 0; nativeId < COUNT; nativeId++) {
            if (TYPES[nativeId] == null) {
                TYPES[nativeId] = new SoundType("sound " + nativeId);
            }
        }

        TYPE_LIST = List.of(TYPES);
    }

    /**
     * Returns the identity of a sfxdata.dat entry.
     *
     * @param nativeId the sfxdata.dat entry, from {@code 0} to {@code COUNT - 1}
     * @return the identity
     * @throws IllegalArgumentException if the entry is outside the table
     */
    public static @NotNull SoundType get(int nativeId) {
        if (nativeId < 0 || nativeId >= COUNT) {
            throw Exceptions.newIllegalArgumentException("Invalid sfxdata.dat entry: {0}", nativeId);
        }

        return TYPE_LIST.get(nativeId);
    }

    /**
     * @return every identity in sfxdata.dat order, as the mod registers them
     */
    public static @NotNull List<@NotNull SoundType> getTypes() {
        return TYPE_LIST;
    }

    private static @NotNull SoundType create(int nativeId, @NotNull String debugName) {
        SoundType type = new SoundType(debugName);

        TYPES[nativeId] = type;

        return type;
    }


    private ScrSounds() {
    }
}
