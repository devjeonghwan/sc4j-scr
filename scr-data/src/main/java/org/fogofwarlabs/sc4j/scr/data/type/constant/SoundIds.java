package org.fogofwarlabs.sc4j.scr.data.type.constant;

/**
 * Names sfxdata.dat entries used by game code.
 */
public final class SoundIds {
    /**
     * Sound {@code 4} ({@code Misc\ZBldgPlc.wav}).
     */
    public static final int ZERG_BUILDING_PLACEMENT = 4;
    /**
     * Sound {@code 16} ({@code Misc\BurrowDn.wav}): a unit burrows.
     */
    public static final int BURROW_DOWN = 16;
    /**
     * Sound {@code 17} ({@code Misc\BurrowUp.wav}): a unit unburrows.
     */
    public static final int BURROW_UP = 17;
    /**
     * Sound {@code 19} ({@code Misc\IntoNydus.wav}).
     */
    public static final int NYDUS_CANAL_TRANSIT = 19;
    /**
     * Sound {@code 40} ({@code Misc\ZOvTra00.wav}; {@code +1} is {@code Misc\TDrTra00.wav}, {@code +2} is
     * {@code Misc\PShTra00.wav}): a transport loads a unit, by the transport's race.
     */
    public static final int TRANSPORT_LOAD = 40;
    /**
     * Sound {@code 43} ({@code Misc\ZOvTra01.wav}; {@code +1} is {@code Misc\TDrTra01.wav}, {@code +2} is
     * {@code Misc\PShTra01.wav}): a transport unloads a unit, by the transport's race.
     */
    public static final int TRANSPORT_UNLOAD = 43;
    /**
     * Sound {@code 84} ({@code Bullet\TNsFir00.wav}).
     */
    public static final int NUCLEAR_MISSILE_LAUNCH = 84;
    /**
     * Sound {@code 127} ({@code Zerg\Advisor\ZAdUpd04.WAV}).
     */
    public static final int ZERG_ADVISOR_UPDATE_4 = 127;
    /**
     * Sound {@code 239} ({@code Terran\GHOST\TGhLas00.wav}).
     */
    public static final int GHOST_LASER = 239;
    /**
     * Sound {@code 273} ({@code Terran\PHOENIX\TPhClo00.WAV}): a unit that is not burrowed cloaks.
     */
    public static final int UNIT_CLOAK = 273;
    /**
     * Sound {@code 274} ({@code Terran\PHOENIX\TPhClo01.WAV}): a unit that is not burrowed decloaks.
     */
    public static final int UNIT_DECLOAK = 274;
    /**
     * Sound {@code 278} ({@code Terran\MARINE\TMaSti00.WAV}; {@code +1} is {@code Terran\MARINE\TMaSti01.WAV}): a unit
     * uses a stim pack.
     */
    public static final int MARINE_STIM_PACK = 278;
    /**
     * Sound {@code 349} ({@code Terran\VESSEL\TVeDef00.WAV}).
     */
    public static final int SCIENCE_VESSEL_DEFENSIVE_MATRIX = 349;
    /**
     * Sound {@code 351} ({@code Terran\VESSEL\TVeIrr00.WAV}).
     */
    public static final int SCIENCE_VESSEL_IRRADIATE = 351;
    /**
     * Sound {@code 388} ({@code Terran\bldg\tcsSca00.WAV}).
     */
    public static final int COMSAT_SCANNER_SWEEP = 388;
    /**
     * Sound {@code 528} ({@code Protoss\SHUTTLE\pshBld00.wav}).
     */
    public static final int PROTOSS_BUILDING_WARP_START = 528;
    /**
     * Sound {@code 529} ({@code Protoss\SHUTTLE\pshBld03.wav}).
     */
    public static final int PROTOSS_BUILDING_WARP_END = 529;
    /**
     * Sound {@code 550} ({@code Protoss\ARBITER\PAbFol00.WAV}; {@code +1} is {@code PAbFol01.WAV}).
     */
    public static final int ARBITER_RECALL_CAST = 550;
    /**
     * Sound {@code 552} ({@code Protoss\ARBITER\PAbFol02.WAV}; {@code +1} is {@code PAbFol03.WAV}).
     */
    public static final int ARBITER_RECALL_ARRIVAL = 552;
    /**
     * Sound {@code 616} ({@code Protoss\INTERCEP\PInLau00.WAV}).
     */
    public static final int INTERCEPTOR_LAUNCH = 616;
    /**
     * Sound {@code 618} ({@code Protoss\TEMPLAR\PTeHal00.WAV}).
     */
    public static final int HIGH_TEMPLAR_HALLUCINATION = 618;
    /**
     * Sound {@code 619} ({@code Protoss\TEMPLAR\PTeHal01.WAV}).
     */
    public static final int HALLUCINATION_DEATH = 619;
    /**
     * Sound {@code 921} ({@code Zerg\Queen\ZQuTag01.WAV}).
     */
    public static final int QUEEN_PARASITE = 921;
    /**
     * Sound {@code 998} ({@code Terran\Medic\TmedCure.wav}).
     */
    public static final int MEDIC_RESTORATION = 998;
    /**
     * Sound {@code 1019} ({@code Terran\Medic\Miopia1.wav}).
     */
    public static final int MEDIC_OPTICAL_FLARE = 1019;
    /**
     * Sound {@code 1061} ({@code Protoss\Darchon\feedback.wav}).
     */
    public static final int DARK_ARCHON_FEEDBACK = 1061;
    /**
     * Sound {@code 1062} ({@code Protoss\Darchon\mind.wav}).
     */
    public static final int DARK_ARCHON_MIND_CONTROL = 1062;
    /**
     * Sound {@code 1064} ({@code Protoss\Darchon\Parahit.wav}).
     */
    public static final int DARK_ARCHON_MAELSTROM = 1064;

    private SoundIds() {
    }
}
