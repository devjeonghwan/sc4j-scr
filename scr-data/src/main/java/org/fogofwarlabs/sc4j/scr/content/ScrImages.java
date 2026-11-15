package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.type.ImageType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Holds the image type identities of the SC:R mod, one per images.dat entry, in images.dat order: the entries the game code names
 * are constants, every other entry is named {@code "image <entry>"}. The mod registers them in this order, so the index of
 * each registered type is its images.dat entry ({@link ScrNativeIds}).
 */
public final class ScrImages {
    /**
     * The number of images.dat entries: the registered image types.
     */
    public static final int COUNT = 999;

    private static final @Nullable ImageType @NotNull [] TYPES = new ImageType[COUNT];

    /**
     * Warp Anchor (images.dat entry {@code 211}).
     */
    public static final @NotNull ImageType WARP_ANCHOR = create(211, "Warp Anchor");
    /**
     * Vespene Geyser (images.dat entry {@code 344}).
     */
    public static final @NotNull ImageType VESPENE_GEYSER = create(344, "Vespene Geyser");
    /**
     * Vespene Geyser2 (images.dat entry {@code 345}).
     */
    public static final @NotNull ImageType VESPENE_GEYSER2 = create(345, "Vespene Geyser2");
    /**
     * Lockdown Field Small (images.dat entry {@code 361}).
     */
    public static final @NotNull ImageType LOCKDOWN_FIELD_SMALL = create(361, "Lockdown Field Small");
    /**
     * Stasis Field Small (images.dat entry {@code 365}).
     */
    public static final @NotNull ImageType STASIS_FIELD_SMALL = create(365, "Stasis Field Small");
    /**
     * Defensive Matrix Front Small (images.dat entry {@code 371}).
     */
    public static final @NotNull ImageType DEFENSIVE_MATRIX_FRONT_SMALL = create(371, "Defensive Matrix Front Small");
    /**
     * Defensive Matrix Back Small (images.dat entry {@code 374}).
     */
    public static final @NotNull ImageType DEFENSIVE_MATRIX_BACK_SMALL = create(374, "Defensive Matrix Back Small");
    /**
     * Defensive Matrix Hit Small (images.dat entry {@code 377}).
     */
    public static final @NotNull ImageType DEFENSIVE_MATRIX_HIT_SMALL = create(377, "Defensive Matrix Hit Small");
    /**
     * Irradiate Small (images.dat entry {@code 380}).
     */
    public static final @NotNull ImageType IRRADIATE_SMALL = create(380, "Irradiate Small");
    /**
     * Irradiate Large (images.dat entry {@code 382}).
     */
    public static final @NotNull ImageType IRRADIATE_LARGE = create(382, "Irradiate Large");
    /**
     * Ensnare Overlay Small (images.dat entry {@code 384}).
     */
    public static final @NotNull ImageType ENSNARE_OVERLAY_SMALL = create(384, "Ensnare Overlay Small");
    /**
     * Ensnare Overlay Large (images.dat entry {@code 386}).
     */
    public static final @NotNull ImageType ENSNARE_OVERLAY_LARGE = create(386, "Ensnare Overlay Large");
    /**
     * Plague Overlay Small (images.dat entry {@code 388}).
     */
    public static final @NotNull ImageType PLAGUE_OVERLAY_SMALL = create(388, "Plague Overlay Small");
    /**
     * Plague Overlay Large (images.dat entry {@code 390}).
     */
    public static final @NotNull ImageType PLAGUE_OVERLAY_LARGE = create(390, "Plague Overlay Large");
    /**
     * Flag (images.dat entry {@code 392}).
     */
    public static final @NotNull ImageType FLAG = create(392, "Flag");
    /**
     * Mineral Chunk Type1 (images.dat entry {@code 397}).
     */
    public static final @NotNull ImageType MINERAL_CHUNK_TYPE1 = create(397, "Mineral Chunk Type1");
    /**
     * Mineral Chunk Type2 (images.dat entry {@code 398}).
     */
    public static final @NotNull ImageType MINERAL_CHUNK_TYPE2 = create(398, "Mineral Chunk Type2");
    /**
     * Protoss Gas Orb Type1 (images.dat entry {@code 399}).
     */
    public static final @NotNull ImageType PROTOSS_GAS_ORB_TYPE1 = create(399, "Protoss Gas Orb Type1");
    /**
     * Zerg Gas Sac Type1 (images.dat entry {@code 401}).
     */
    public static final @NotNull ImageType ZERG_GAS_SAC_TYPE1 = create(401, "Zerg Gas Sac Type1");
    /**
     * Terran Gas Tank Type1 (images.dat entry {@code 403}).
     */
    public static final @NotNull ImageType TERRAN_GAS_TANK_TYPE1 = create(403, "Terran Gas Tank Type1");
    /**
     * Terran Gas Tank Type2 (images.dat entry {@code 404}).
     */
    public static final @NotNull ImageType TERRAN_GAS_TANK_TYPE2 = create(404, "Terran Gas Tank Type2");
    /**
     * Shield Overlay (images.dat entry {@code 424}).
     */
    public static final @NotNull ImageType SHIELD_OVERLAY = create(424, "Shield Overlay");
    /**
     * Vespene Geyser Smoke1 (images.dat entry {@code 430}).
     */
    public static final @NotNull ImageType VESPENE_GEYSER_SMOKE1 = create(430, "Vespene Geyser Smoke1");
    /**
     * Vespene Geyser Smoke1 Overlay (images.dat entry {@code 435}).
     */
    public static final @NotNull ImageType VESPENE_GEYSER_SMOKE1_OVERLAY = create(435, "Vespene Geyser Smoke1 Overlay");
    /**
     * Flames1 Type1 Small (images.dat entry {@code 450}).
     */
    public static final @NotNull ImageType FLAMES1_TYPE1_SMALL = create(450, "Flames1 Type1 Small");
    /**
     * Flames8 Type3 Small (images.dat entry {@code 471}).
     */
    public static final @NotNull ImageType FLAMES8_TYPE3_SMALL = create(471, "Flames8 Type3 Small");
    /**
     * Flames1 Type1 Large (images.dat entry {@code 472}).
     */
    public static final @NotNull ImageType FLAMES1_TYPE1_LARGE = create(472, "Flames1 Type1 Large");
    /**
     * Flames8 Type3 Large (images.dat entry {@code 493}).
     */
    public static final @NotNull ImageType FLAMES8_TYPE3_LARGE = create(493, "Flames8 Type3 Large");
    /**
     * Science Vessel Overlay Part2 (images.dat entry {@code 539}).
     */
    public static final @NotNull ImageType SCIENCE_VESSEL_OVERLAY_PART2 = create(539, "Science Vessel Overlay Part2");
    /**
     * Hallucination Hit (images.dat entry {@code 545}).
     */
    public static final @NotNull ImageType HALLUCINATION_HIT = create(545, "Hallucination Hit");
    /**
     * Health Bar (images.dat entry {@code 560}).
     */
    public static final @NotNull ImageType HEALTH_BAR = create(560, "Health Bar");
    /**
     * Selection Circle 22pixels (images.dat entry {@code 561}).
     */
    public static final @NotNull ImageType SELECTION_CIRCLE_22PIXELS = create(561, "Selection Circle 22pixels");
    /**
     * Selection Circle Dashed 22pixels (images.dat entry {@code 571}).
     */
    public static final @NotNull ImageType SELECTION_CIRCLE_DASHED_22PIXELS = create(571, "Selection Circle Dashed 22pixels");
    /**
     * Psi Field1 Right Upper (images.dat entry {@code 584}).
     */
    public static final @NotNull ImageType PSI_FIELD1_RIGHT_UPPER = create(584, "Psi Field1 Right Upper");
    /**
     * Uraj (images.dat entry {@code 958}).
     */
    public static final @NotNull ImageType URAJ = create(958, "Uraj");
    /**
     * Khalis (images.dat entry {@code 959}).
     */
    public static final @NotNull ImageType KHALIS = create(959, "Khalis");
    /**
     * Restoration Hit Small (images.dat entry {@code 967}).
     */
    public static final @NotNull ImageType RESTORATION_HIT_SMALL = create(967, "Restoration Hit Small");
    /**
     * Mind Control Hit Small (images.dat entry {@code 973}).
     */
    public static final @NotNull ImageType MIND_CONTROL_HIT_SMALL = create(973, "Mind Control Hit Small");
    /**
     * Optical Flare Hit Small (images.dat entry {@code 976}).
     */
    public static final @NotNull ImageType OPTICAL_FLARE_HIT_SMALL = create(976, "Optical Flare Hit Small");
    /**
     * Feedback Small (images.dat entry {@code 979}).
     */
    public static final @NotNull ImageType FEEDBACK_SMALL = create(979, "Feedback Small");
    /**
     * Maelstrom Overlay Small (images.dat entry {@code 982}).
     */
    public static final @NotNull ImageType MAELSTROM_OVERLAY_SMALL = create(982, "Maelstrom Overlay Small");
    /**
     * Acid Spores 1 Overlay Small (images.dat entry {@code 986}).
     */
    public static final @NotNull ImageType ACID_SPORES_1_OVERLAY_SMALL = create(986, "Acid Spores 1 Overlay Small");

    private static final @NotNull List<@NotNull ImageType> TYPE_LIST;

    static {
        for (int nativeId = 0; nativeId < COUNT; nativeId++) {
            if (TYPES[nativeId] == null) {
                TYPES[nativeId] = new ImageType("image " + nativeId);
            }
        }

        TYPE_LIST = List.of(TYPES);
    }

    /**
     * Returns the identity of a images.dat entry.
     *
     * @param nativeId the images.dat entry, from {@code 0} to {@code COUNT - 1}
     * @return the identity
     * @throws IllegalArgumentException if the entry is outside the table
     */
    public static @NotNull ImageType get(int nativeId) {
        if (nativeId < 0 || nativeId >= COUNT) {
            throw Exceptions.newIllegalArgumentException("Invalid images.dat entry: {0}", nativeId);
        }

        return TYPE_LIST.get(nativeId);
    }

    /**
     * @return every identity in images.dat order, as the mod registers them
     */
    public static @NotNull List<@NotNull ImageType> getTypes() {
        return TYPE_LIST;
    }

    private static @NotNull ImageType create(int nativeId, @NotNull String debugName) {
        ImageType type = new ImageType(debugName);

        TYPES[nativeId] = type;

        return type;
    }


    private ScrImages() {
    }
}
