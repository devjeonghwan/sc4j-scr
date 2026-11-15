package org.fogofwarlabs.sc4j.scr.content;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.engine.content.type.SpriteType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Holds the sprite type identities of the SC:R mod, one per sprites.dat entry, in sprites.dat order: the entries the game code names
 * are constants, every other entry is named {@code "sprite <entry>"}. The mod registers them in this order, so the index of
 * each registered type is its sprites.dat entry ({@link ScrNativeIds}).
 */
public final class ScrSprites {
    /**
     * The number of sprites.dat entries: the registered sprite types.
     */
    public static final int COUNT = 517;

    private static final @Nullable SpriteType @NotNull [] TYPES = new SpriteType[COUNT];

    /**
     * Drone (sprites.dat entry {@code 140}).
     */
    public static final @NotNull SpriteType DRONE = create(140, "Drone");
    /**
     * Extractor (sprites.dat entry {@code 178}).
     */
    public static final @NotNull SpriteType EXTRACTOR = create(178, "Extractor");
    /**
     * Zerg Building Spawn Small (sprites.dat entry {@code 182}).
     */
    public static final @NotNull SpriteType ZERG_BUILDING_SPAWN_SMALL = create(182, "Zerg Building Spawn Small");
    /**
     * Probe (sprites.dat entry {@code 194}).
     */
    public static final @NotNull SpriteType PROBE = create(194, "Probe");
    /**
     * Assimilator (sprites.dat entry {@code 204}).
     */
    public static final @NotNull SpriteType ASSIMILATOR = create(204, "Assimilator");
    /**
     * Nuke Target Dot (sprites.dat entry {@code 231}).
     */
    public static final @NotNull SpriteType NUKE_TARGET_DOT = create(231, "Nuke Target Dot");
    /**
     * Scv (sprites.dat entry {@code 239}).
     */
    public static final @NotNull SpriteType SCV = create(239, "Scv");
    /**
     * Refinery (sprites.dat entry {@code 263}).
     */
    public static final @NotNull SpriteType REFINERY = create(263, "Refinery");
    /**
     * Vespene Geyser (sprites.dat entry {@code 275}).
     */
    public static final @NotNull SpriteType VESPENE_GEYSER = create(275, "Vespene Geyser");
    /**
     * Mineral Field Type1 (sprites.dat entry {@code 279}).
     */
    public static final @NotNull SpriteType MINERAL_FIELD_TYPE1 = create(279, "Mineral Field Type1");
    /**
     * Mineral Field Type2 (sprites.dat entry {@code 280}).
     */
    public static final @NotNull SpriteType MINERAL_FIELD_TYPE2 = create(280, "Mineral Field Type2");
    /**
     * Mineral Field Type3 (sprites.dat entry {@code 281}).
     */
    public static final @NotNull SpriteType MINERAL_FIELD_TYPE3 = create(281, "Mineral Field Type3");
    /**
     * White Circle Invisible (sprites.dat entry {@code 300}).
     */
    public static final @NotNull SpriteType WHITE_CIRCLE_INVISIBLE = create(300, "White Circle Invisible");
    /**
     * Cursor Marker (sprites.dat entry {@code 318}).
     */
    public static final @NotNull SpriteType CURSOR_MARKER = create(318, "Cursor Marker");
    /**
     * Psi Field Right Upper (sprites.dat entry {@code 321}).
     */
    public static final @NotNull SpriteType PSI_FIELD_RIGHT_UPPER = create(321, "Psi Field Right Upper");
    /**
     * Building Landing Dust Type1 (sprites.dat entry {@code 323}).
     */
    public static final @NotNull SpriteType BUILDING_LANDING_DUST_TYPE1 = create(323, "Building Landing Dust Type1");
    /**
     * Stasis Field Hit (sprites.dat entry {@code 361}).
     */
    public static final @NotNull SpriteType STASIS_FIELD_HIT = create(361, "Stasis Field Hit");
    /**
     * Ensnare (sprites.dat entry {@code 364}).
     */
    public static final @NotNull SpriteType ENSNARE = create(364, "Ensnare");
    /**
     * Hallucination Death1 (sprites.dat entry {@code 374}).
     */
    public static final @NotNull SpriteType HALLUCINATION_DEATH1 = create(374, "Hallucination Death1");
    /**
     * Bunker Overlay (sprites.dat entry {@code 377}).
     */
    public static final @NotNull SpriteType BUNKER_OVERLAY = create(377, "Bunker Overlay");
    /**
     * Flame Thrower (sprites.dat entry {@code 378}).
     */
    public static final @NotNull SpriteType FLAME_THROWER = create(378, "Flame Thrower");
    /**
     * Recall Field (sprites.dat entry {@code 379}).
     */
    public static final @NotNull SpriteType RECALL_FIELD = create(379, "Recall Field");
    /**
     * Medic (sprites.dat entry {@code 489}).
     */
    public static final @NotNull SpriteType MEDIC = create(489, "Medic");
    /**
     * Feedback Hit Small (sprites.dat entry {@code 500}).
     */
    public static final @NotNull SpriteType FEEDBACK_HIT_SMALL = create(500, "Feedback Hit Small");
    /**
     * White Circle2 (sprites.dat entry {@code 504}).
     */
    public static final @NotNull SpriteType WHITE_CIRCLE2 = create(504, "White Circle2");
    /**
     * Halo Rockets Trail (sprites.dat entry {@code 505}).
     */
    public static final @NotNull SpriteType HALO_ROCKETS_TRAIL = create(505, "Halo Rockets Trail");
    /**
     * Maelstrom Hit (sprites.dat entry {@code 514}).
     */
    public static final @NotNull SpriteType MAELSTROM_HIT = create(514, "Maelstrom Hit");

    private static final @NotNull List<@NotNull SpriteType> TYPE_LIST;

    static {
        for (int nativeId = 0; nativeId < COUNT; nativeId++) {
            if (TYPES[nativeId] == null) {
                TYPES[nativeId] = new SpriteType("sprite " + nativeId);
            }
        }

        TYPE_LIST = List.of(TYPES);
    }

    /**
     * Returns the identity of a sprites.dat entry.
     *
     * @param nativeId the sprites.dat entry, from {@code 0} to {@code COUNT - 1}
     * @return the identity
     * @throws IllegalArgumentException if the entry is outside the table
     */
    public static @NotNull SpriteType get(int nativeId) {
        if (nativeId < 0 || nativeId >= COUNT) {
            throw Exceptions.newIllegalArgumentException("Invalid sprites.dat entry: {0}", nativeId);
        }

        return TYPE_LIST.get(nativeId);
    }

    /**
     * @return every identity in sprites.dat order, as the mod registers them
     */
    public static @NotNull List<@NotNull SpriteType> getTypes() {
        return TYPE_LIST;
    }

    private static @NotNull SpriteType create(int nativeId, @NotNull String debugName) {
        SpriteType type = new SpriteType(debugName);

        TYPES[nativeId] = type;

        return type;
    }


    private ScrSprites() {
    }
}
