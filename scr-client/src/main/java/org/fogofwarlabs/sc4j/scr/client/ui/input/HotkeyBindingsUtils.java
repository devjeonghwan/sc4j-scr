package org.fogofwarlabs.sc4j.scr.client.ui.input;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses and formats command-button hotkey overrides using the fixed identifier and name table.
 * Formatting writes nonzero valid code points in table order. Parsing keeps recognized names and normalizes their first value code point.
 */
public final class HotkeyBindingsUtils {
    /**
     * The number of records of the table.
     */
    public static final int COUNT = 230;

    /**
     * The replacement code point that answers for an undecodable first character.
     */
    static final int REPLACEMENT_CHARACTER = 0xFFFD;
    /**
     * The exclusive upper bound of the code points whose case a key changes (ASCII).
     */
    static final int ASCII_LIMIT = 0x80;

    private static final char SEPARATOR = '=';
    private static final char LINE_END = '\n';

    /**
     * The command-button string identifiers and hotkey names in their fixed table order.
     */
    private static final Record @NotNull [] RECORDS = {
            new Record(324, "STR_RSRCH_STIM"), new Record(325, "STR_RSRCH_MAGNA"), new Record(326, "STR_RSRCH_EMP"),
            new Record(327, "STR_RSRCH_MINES"), new Record(328, "STR_RSRCH_SIEGE"), new Record(329, "STR_RSRCH_DEFMTX"),
            new Record(330, "STR_RSRCH_IRRADIATE"), new Record(331, "STR_RSRCH_YAMATO"), new Record(332, "STR_RSRCH_SHIP_CLOAK"),
            new Record(333, "STR_RSRCH_MAN_CLOAK"), new Record(334, "STR_USESTIM"), new Record(335, "STR_USEMAGNA"),
            new Record(336, "STR_USEMINES"), new Record(337, "STR_SCANNERSWEEP"), new Record(338, "STR_SIEGE_MODE"),
            new Record(339, "STR_TANK_MODE"), new Record(340, "STR_DEFMTX"), new Record(341, "STR_USEEMP"), new Record(342, "STR_IRRADIATE"),
            new Record(343, "STR_YAMATO"), new Record(344, "STR_CLOAK"), new Record(345, "STR_DECLOAK"), new Record(364, "STR_RSRCH_BURROW"),
            new Record(365, "STR_RSRCH_INFEST"), new Record(366, "STR_RSRCH_INFBROOD"), new Record(367, "STR_RSRCH_PLAGUE"),
            new Record(368, "STR_RSRCH_PARASITE"), new Record(369, "STR_RSRCH_BLOODBOIL"), new Record(370, "STR_RSRCH_ENSNARE"),
            new Record(371, "STR_RSRCH_CONSUME"), new Record(372, "STR_BURROW"), new Record(373, "STR_DEBURROW"), new Record(374, "STR_INFEST"),
            new Record(375, "STR_INFBROOD"), new Record(376, "STR_PLAGUE"), new Record(377, "STR_PARASITE"), new Record(378, "STR_BLOODBOIL"),
            new Record(379, "STR_CONSUME"), new Record(380, "STR_KERRIGAN_CONSUME"), new Record(381, "STR_ENSNARE"),
            new Record(395, "STR_RSRCH_PSISTORM"), new Record(396, "STR_RSRCH_HALLUCINATION"), new Record(397, "STR_RSRCH_RECALL"),
            new Record(398, "STR_RSRCH_STASIS"), new Record(399, "STR_RSRCH_SUMMON_ARCHON"), new Record(400, "STR_PSISTORM"),
            new Record(401, "STR_HALLUCINATION"), new Record(402, "STR_RECALL"), new Record(403, "STR_STASIS"),
            new Record(404, "STR_MAKE_P_ARCHON"), new Record(456, "STR_UP_T_ARMOR"), new Record(457, "STR_UP_T_VEHICLE_PLATING"),
            new Record(458, "STR_UP_T_SHIP_PLATING"), new Record(459, "STR_UP_Z_CARAPACE"), new Record(460, "STR_UP_Z_PLATING"),
            new Record(461, "STR_UP_P_ARMOR"), new Record(462, "STR_UP_P_PLATING"), new Record(463, "STR_UP_T_MAN_GUNS"),
            new Record(464, "STR_UP_T_VEHICLE_GUNS"), new Record(465, "STR_UP_T_SHIP_GUNS"), new Record(466, "STR_UP_Z_MELEE_ATTACKS"),
            new Record(467, "STR_UP_Z_MISSILE_ATTACKS"), new Record(468, "STR_UP_Z_FLYER_ATTACKS"), new Record(469, "STR_UP_P_GND_WEAPONS"),
            new Record(470, "STR_UP_P_AIR_WEAPONS"), new Record(471, "STR_UP_P_SHIELDS"), new Record(472, "STR_UP_MARINE_GUN_RANGE"),
            new Record(473, "STR_UP_VULTURE_SPEED"), new Record(475, "STR_UP_VESSEL_ENERGY"), new Record(476, "STR_UP_GHOST_SIGHT"),
            new Record(477, "STR_UP_GHOST_ENERGY"), new Record(478, "STR_UP_WRAITH_ENERGY"), new Record(479, "STR_UP_CRUISER_ENERGY"),
            new Record(480, "STR_UP_OVERLORD_TRANSPORT"), new Record(481, "STR_UP_OVERLORD_SIGHT"), new Record(482, "STR_UP_OVERLORD_SPEED"),
            new Record(483, "STR_UP_ZERGLING_SPEED"), new Record(484, "STR_UP_ZERGLING_ATTACK_SPEED"), new Record(485, "STR_UP_HYDRALISK_SPEED"),
            new Record(486, "STR_UP_HYDRALISK_ATTACK_RANGE"), new Record(487, "STR_UP_QUEEN_ENERGY"), new Record(488, "STR_UP_DEFILER_ENERGY"),
            new Record(489, "STR_UP_DRAGOON_ATTACK_RANGE"), new Record(490, "STR_UP_ZEALOT_SPEED"), new Record(491, "STR_UP_SCARAB_DAMAGE"),
            new Record(492, "STR_UP_REAVER_CAPACITY"), new Record(493, "STR_UP_SHUTTLE_SPEED"), new Record(494, "STR_UP_OBSERVER_SIGHT"),
            new Record(495, "STR_UP_OBSERVER_SPEED"), new Record(496, "STR_UP_TEMPLAR_ENERGY"), new Record(497, "STR_UP_SCOUT_SIGHT"),
            new Record(498, "STR_UP_SCOUT_SPEED"), new Record(499, "STR_UP_CARRIER_CAPACITY"), new Record(500, "STR_UP_ARBITER_ENERGY"),
            new Record(575, "STR_MAKE_Z_ZERGLING"), new Record(576, "STR_MAKE_Z_HYDRALISK"), new Record(577, "STR_MAKE_Z_ULTRALISK"),
            new Record(578, "STR_MAKE_Z_DRONE"), new Record(579, "STR_MAKE_Z_OVERLORD"), new Record(580, "STR_MAKE_Z_MUTALID"),
            new Record(581, "STR_GUARDIAN_ASPECT"), new Record(582, "STR_MAKE_Z_QUEEN"), new Record(583, "STR_MAKE_Z_DEFILER"),
            new Record(584, "STR_MAKE_Z_AVENGER"), new Record(585, "STR_MAKE_Z_INFESTED"), new Record(586, "STR_MAKE_T_MARINE"),
            new Record(587, "STR_MAKE_T_GHOST"), new Record(588, "STR_MAKE_T_FIREBAT"), new Record(589, "STR_MAKE_T_VULTURE"),
            new Record(590, "STR_MAKE_T_GOLIATH"), new Record(591, "STR_MAKE_T_TANK"), new Record(592, "STR_MAKE_T_SCV"),
            new Record(593, "STR_MAKE_T_WRAITH"), new Record(594, "STR_MAKE_T_VESSEL"), new Record(595, "STR_MAKE_T_DROPSHIP"),
            new Record(596, "STR_MAKE_T_BCRUISER"), new Record(597, "STR_MAKE_T_NUKE"), new Record(598, "STR_MAKE_P_OBSERVER"),
            new Record(599, "STR_MAKE_P_PROBE"), new Record(600, "STR_MAKE_P_ZEALOT"), new Record(601, "STR_MAKE_P_DRAGOON"),
            new Record(602, "STR_MAKE_P_TEMPLAR"), new Record(603, "STR_MAKE_P_SHUTTLE"), new Record(604, "STR_MAKE_P_SCOUT"),
            new Record(605, "STR_MAKE_P_ARBITER"), new Record(606, "STR_MAKE_P_CARRIER"), new Record(607, "STR_MAKE_P_INTERCEPTOR"),
            new Record(608, "STR_MAKE_P_REAVER"), new Record(609, "STR_MAKE_P_SCARAB"), new Record(613, "STR_BLD_HATCHERY"),
            new Record(614, "STR_BLD_CREEP_COLONY"), new Record(615, "STR_BLD_ZEXTRACTOR"), new Record(616, "STR_BLD_SPAWNING"),
            new Record(617, "STR_BLD_EVO_CHAMBER"), new Record(618, "STR_BLD_HYDRA_DEN"), new Record(619, "STR_BLD_NYDUS"),
            new Record(620, "STR_BLD_SPIRE"), new Record(621, "STR_BLD_NEST"), new Record(622, "STR_BLD_ULTRA_CAVERN"),
            new Record(623, "STR_BLD_DEFILER_MOUND"), new Record(624, "STR_BLD_LAIR"), new Record(625, "STR_BLD_HIVE"),
            new Record(626, "STR_BLD_GREATERSPIRE"), new Record(627, "STR_BLD_SPORE_COLONY"), new Record(628, "STR_BLD_SUNKEN_COLONY"),
            new Record(629, "STR_NYDUS_EXIT"), new Record(630, "STR_BLD_NEXUS"), new Record(631, "STR_BLD_PYLON"),
            new Record(632, "STR_BLD_ASSIMILATOR"), new Record(633, "STR_BLD_GATEWAY"), new Record(634, "STR_BLD_FORGE"),
            new Record(635, "STR_BLD_PHOTON"), new Record(636, "STR_BLD_CYBER_CORE"), new Record(637, "STR_BLD_SHIELDBATT"),
            new Record(638, "STR_BLD_ROBOTICS"), new Record(639, "STR_BLD_OBSERVATORY"), new Record(640, "STR_BLD_CITADEL"),
            new Record(641, "STR_BLD_ARCHIVES"), new Record(642, "STR_BLD_STARGATE"), new Record(643, "STR_BLD_FLEET_BEACON"),
            new Record(644, "STR_BLD_TRIBUNAL"), new Record(645, "STR_BLD_ROBOTICS_BAY"), new Record(646, "STR_BLD_TCOMMANDCTR"),
            new Record(647, "STR_BLD_DEPOT"), new Record(648, "STR_BLD_REFINERY"), new Record(649, "STR_BLD_BARRACKS"),
            new Record(650, "STR_BLD_ENGINEERING"), new Record(651, "STR_BLD_TURRET"), new Record(652, "STR_BLD_ACADEMY"),
            new Record(653, "STR_BLD_PILLBOX"), new Record(654, "STR_BLD_FACTORY"), new Record(655, "STR_BLD_TSTARPORT"),
            new Record(656, "STR_BLD_SCIENCE_FAC"), new Record(657, "STR_BLD_ARMORY"), new Record(658, "STR_BLD_COMSAT"),
            new Record(659, "STR_BLD_SILO"), new Record(660, "STR_BLD_DOCKS"), new Record(661, "STR_BLD_COVERT_OPS"),
            new Record(662, "STR_BLD_PHYSICS"), new Record(663, "STR_BLD_MACHINE"), new Record(664, "STR_MOVE"), new Record(665, "STR_STOP"),
            new Record(666, "STR_ATTACK"), new Record(667, "STR_PATROL"), new Record(668, "STR_HOLD"), new Record(669, "STR_WAYPOINTS"),
            new Record(670, "STR_LAND"), new Record(671, "STR_LIFTOFF"), new Record(672, "STR_RALLYPOINT"), new Record(673, "STR_RECHARGE"),
            new Record(674, "STR_SELECT_LARVA"), new Record(675, "STR_GATHER"), new Record(676, "STR_RETURN"), new Record(677, "STR_REPAIR"),
            new Record(678, "STR_BUILD"), new Record(679, "STR_BLD_ADVANCED"), new Record(680, "STR_MUTATE"), new Record(681, "STR_MUTATE_ADV"),
            new Record(682, "STR_MORPH_ADV"), new Record(683, "STR_PICKUP"), new Record(684, "STR_UNLOAD"), new Record(685, "STR_NUKESTRIKE"),
            new Record(686, "STR_PLACE_COP"), new Record(1245, "STR_RSRCH_CURE"), new Record(1246, "STR_RSRCH_MYOPIA"),
            new Record(1247, "STR_HEAL"), new Record(1248, "STR_CURE"), new Record(1249, "STR_MYOPIA"), new Record(1253, "STR_RSRCH_LURKERASPECT"),
            new Record(1260, "STR_RSRCH_DISRUPTOR"), new Record(1261, "STR_RSRCH_MINDCONTROL"), new Record(1262, "STR_RSRCH_PSYFEEDBACK"),
            new Record(1263, "STR_RSRCH_PARALIZE"), new Record(1264, "STR_MAKE_P_DARCHON"), new Record(1265, "STR_DISRUPTOR"),
            new Record(1266, "STR_MINDCONTROL"), new Record(1267, "STR_PSYFEEDBACK"), new Record(1268, "STR_USEPARALIZE"),
            new Record(1281, "STR_UP_MEDIC_ENERGY"), new Record(1282, "STR_UP_T_MISSILE_BOOST"), new Record(1283, "STR_UP_Z_ULTRA_SPEED"),
            new Record(1284, "STR_UP_Z_ULTRA_ARMOR"), new Record(1285, "STR_UP_CORSAIR_ENERGY"), new Record(1286, "STR_UP_DARCHON_ENERGY"),
            new Record(1288, "STR_DEVOURER_ASPECT"), new Record(1289, "STR_MAKE_Z_LURKER"), new Record(1290, "STR_MAKE_T_MEDIC"),
            new Record(1291, "STR_MAKE_T_FRIGATE"), new Record(1292, "STR_MAKE_P_CORSAIR"), new Record(1293, "STR_MAKE_P_DTEMPLAR")
    };

    /**
     * The fixed hotkey identifiers by name.
     */
    private static final @NotNull Map<String, Integer> IDENTIFIERS = new HashMap<>();

    /**
     * One record of the table.
     */
    private static final class Record {
        private final int identifier;
        private final @NotNull String name;

        private Record(int identifier, @NotNull String name) {
            this.identifier = identifier;
            this.name       = name;
        }
    }

    static {
        if (RECORDS.length != COUNT) {
            throw Exceptions.newIllegalStateException("The hotkey table has {0} records instead of {1}.", RECORDS.length, COUNT);
        }

        for (Record record : RECORDS) {
            IDENTIFIERS.put(record.name, record.identifier);
        }
    }

    /**
     * @param index the record index, {@code 0} to {@link #COUNT} {@code - 1}
     * @return the one-based {@code stat_txt} id of the record
     */
    public static int getIdentifier(int index) {
        return RECORDS[index].identifier;
    }

    /**
     * @param index the record index, {@code 0} to {@link #COUNT} {@code - 1}
     * @return the name of the record, the id's {@code stat_txt} key, for example {@code STR_MOVE}
     */
    public static @NotNull String getName(int index) {
        return RECORDS[index].name;
    }

    /**
     * Normalizes an ASCII-range key to lowercase.
     *
     * @param key the key code point
     * @return the stored key
     */
    public static int normalize(int key) {
        return key < ASCII_LIMIT ? Character.toLowerCase(key) : key;
    }

    /**
     * Writes the settings string of a profile's keys.
     *
     * @param keys the keys by {@code stat_txt} id; ids outside the table, missing ids and zero keys are not written
     * @return the settings string
     */
    public static @NotNull String format(@NotNull Map<Integer, Integer> keys) {
        StringBuilder text = new StringBuilder();

        for (Record record : RECORDS) {
            int key = keys.getOrDefault(record.identifier, 0);

            if (key == 0 || !Character.isValidCodePoint(key) || Character.getType(key) == Character.SURROGATE) {
                continue;
            }

            text.append(record.name).append(SEPARATOR).appendCodePoint(key).append(LINE_END);
        }

        return text.toString();
    }

    /**
     * Reads a settings string.
     *
     * @param text the settings string
     * @return the keys it sets by {@code stat_txt} id, in line order
     */
    public static @NotNull Map<Integer, Integer> parse(@NotNull String text) {
        Map<Integer, Integer> keys = new LinkedHashMap<>();
        int lineStart = 0;

        while (lineStart < text.length()) {
            int lineEnd = text.indexOf(LINE_END, lineStart);

            if (lineEnd < 0) {
                lineEnd = text.length();
            }

            int separator = text.indexOf(SEPARATOR, lineStart);

            if (separator >= 0 && separator + 1 < lineEnd) {
                Integer identifier = IDENTIFIERS.get(text.substring(lineStart, separator));
                int key = text.codePointAt(separator + 1);

                if (identifier != null && key != REPLACEMENT_CHARACTER) {
                    keys.put(identifier, normalize(key));
                }
            }

            lineStart = lineEnd + 1;
        }

        return keys;
    }

    private HotkeyBindingsUtils() {
    }
}
