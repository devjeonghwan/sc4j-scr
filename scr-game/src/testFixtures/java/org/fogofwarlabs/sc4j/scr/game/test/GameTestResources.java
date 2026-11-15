package org.fogofwarlabs.sc4j.scr.game.test;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.core.test.TestResourceUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

/**
 * Names the replays, saved games, maps and native captures that focused tests of the {@code scr-game} test resources read.
 * <p>
 * Each constant is a resource path relative to the class path root. The replays of focused tests are {@code <SOURCE>_<number>.rep} below
 * {@code replays/focused/<source>/}, numbered per folder; the same game may also be a case of the folder-driven replay tests
 * ({@code replays/detection}, {@code replays/native}), whose replays are named here only when a focused test reads one of them. A saved
 * game {@code saves/<id>.snx|.mlx} has the replay of the same game next to it ({@code saves/<id>.rep}). Modules other than {@code scr-game}
 * put its resource directory on their test runtime class path to read these files.
 */
public final class GameTestResources {
    /**
     * A STARDATA Protoss versus Zerg melee game of {@code 59160} frames on Judgement Day.
     */
    public static final @NotNull String REPLAY_PVZ_JUDGEMENT_DAY = "replays/focused/stardata/STARDATA_00000001.rep";
    /**
     * A STARDATA Zerg versus Zerg melee game of {@code 36604} frames on Heartbreak Ridge.
     */
    public static final @NotNull String REPLAY_ZVZ_HEARTBREAK_RIDGE = "replays/focused/stardata/STARDATA_00000002.rep";
    /**
     * A STARDATA Protoss versus Protoss melee game of {@code 9923} frames on The Fortress.
     */
    public static final @NotNull String REPLAY_PVP_FORTRESS = "replays/focused/stardata/STARDATA_00000003.rep";
    /**
     * A STARDATA three-player Use Map Settings game of {@code 21913} frames on Destination.
     */
    public static final @NotNull String REPLAY_UMS_DESTINATION = "replays/focused/stardata/STARDATA_00000004.rep";
    /**
     * A STARDATA Terran versus Terran melee game of {@code 12335} frames on Python.
     */
    public static final @NotNull String REPLAY_TVT_PYTHON = "replays/focused/stardata/STARDATA_00000005.rep";
    /**
     * A STARDATA six-player Use Map Settings game of {@code 13261} frames on a Python variant with preplaced cloaked and burrowed units.
     */
    public static final @NotNull String REPLAY_UMS_PYTHON_SIX_PLAYERS = "replays/focused/stardata/STARDATA_00000006.rep";
    /**
     * A STARDATA Zerg versus Protoss melee game of {@code 25739} frames on Python.
     */
    public static final @NotNull String REPLAY_ZVP_PYTHON = "replays/focused/stardata/STARDATA_00000007.rep";
    /**
     * A STARDATA two-player Use Map Settings game of {@code 5071} frames on Defiler Perversion.
     */
    public static final @NotNull String REPLAY_UMS_DEFILER_PERVERSION = "replays/focused/stardata/STARDATA_00000008.rep";
    /**
     * A STARDATA Protoss versus Zerg melee game of {@code 18672} frames on Grand Line.
     */
    public static final @NotNull String REPLAY_PVZ_GRAND_LINE = "replays/focused/stardata/STARDATA_00000009.rep";
    /**
     * A STARDATA Zerg versus Zerg melee game of {@code 53548} frames on Python.
     */
    public static final @NotNull String REPLAY_ZVZ_PYTHON = "replays/focused/stardata/STARDATA_00000010.rep";
    /**
     * A STARDATA three-player Use Map Settings game of {@code 25308} frames on New Baekdudaegan.
     */
    public static final @NotNull String REPLAY_UMS_BAEKDUDAEGAN = "replays/focused/stardata/STARDATA_00000011.rep";
    /**
     * A STARDATA Terran versus Terran melee game of {@code 26284} frames on New Baekdudaegan.
     */
    public static final @NotNull String REPLAY_TVT_BAEKDUDAEGAN = "replays/focused/stardata/STARDATA_00000012.rep";
    /**
     * A STARDATA Terran versus Zerg Top versus Bottom game of {@code 16402} frames on Luna.
     */
    public static final @NotNull String REPLAY_TVZ_LUNA = "replays/focused/stardata/STARDATA_00000013.rep";
    /**
     * A STARDATA three-player Use Map Settings game of {@code 3458} frames on a team Fighting Spirit map.
     */
    public static final @NotNull String REPLAY_UMS_TEAM_FIGHTING_SPIRIT = "replays/focused/stardata/STARDATA_00000014.rep";
    /**
     * A STARDATA two-player Use Map Settings game of {@code 13080} frames on Lost Temple.
     */
    public static final @NotNull String REPLAY_UMS_LOST_TEMPLE = "replays/focused/stardata/STARDATA_00000015.rep";
    /**
     * A STARDATA four-player capture-the-flag Use Map Settings game of {@code 12140} frames on a Python variant.
     */
    public static final @NotNull String REPLAY_UMS_CAPTURE_THE_FLAG = "replays/focused/stardata/STARDATA_00000016.rep";
    /**
     * A STARDATA Zerg versus Protoss melee game of {@code 16054} frames on Fighting Spirit.
     */
    public static final @NotNull String REPLAY_ZVP_FIGHTING_SPIRIT = "replays/focused/stardata/STARDATA_00000017.rep";
    /**
     * A STARDATA two-player Use Map Settings game of {@code 53058} frames on Micro Tournament.
     */
    public static final @NotNull String REPLAY_UMS_MICRO_TOURNAMENT = "replays/focused/stardata/STARDATA_00000018.rep";

    /**
     * A LAN game of the SC4J client as host against native SC:R on the civilization defense map, desynchronized at frame {@code 8002}.
     */
    public static final @NotNull String REPLAY_CIVILIZATION_DEFENSE_BUILDER_DESYNC = "replays/focused/internal/INTERNAL_00000001.rep";
    /**
     * A single-player game of the SC4J client on the civilization defense map, {@code 48} frames; played back as a replay, the map's replay branch
     * reads an EUD address without a native region at frame {@code 2}.
     */
    public static final @NotNull String REPLAY_CIVILIZATION_DEFENSE_EUD_FAILURE = "replays/focused/internal/INTERNAL_00000002.rep";
    /**
     * A LAN game of the SC4J client against native SC:R on the EUD computer map, desynchronized by a shield repair.
     */
    public static final @NotNull String REPLAY_EUD_COMPUTER_SHIELD_REPAIR_DESYNC = "replays/focused/internal/INTERNAL_00000003.rep";
    /**
     * A LAN game of the SC4J client against native SC:R on the EUD computer map, desynchronized after a defeat.
     */
    public static final @NotNull String REPLAY_EUD_COMPUTER_DEFEAT_DESYNC = "replays/focused/internal/INTERNAL_00000004.rep";
    /**
     * A native SC:R replay of a Brood War Zerg campaign mission.
     */
    public static final @NotNull String REPLAY_CAMPAIGN_ZERG = "replays/focused/internal/INTERNAL_00000005.rep";
    /**
     * A native SC:R single-player melee game of {@code 730} frames, Zerg against a Zerg computer on Bottleneck.
     */
    public static final @NotNull String REPLAY_ZVZ_COMPUTER_SHORT = "replays/focused/internal/INTERNAL_00000006.rep";
    /**
     * A native SC:R Use Map Settings survival game of {@code 28956} frames with six computers.
     */
    public static final @NotNull String REPLAY_UMS_SURVIVAL = "replays/focused/internal/INTERNAL_00000007.rep";
    /**
     * A native SC:R single-player melee game of {@code 29100} frames against three computers on Cyclone.
     */
    public static final @NotNull String REPLAY_FREE_FOR_ALL_COMPUTERS = "replays/focused/internal/INTERNAL_00000008.rep";
    /**
     * A native SC:R single-player melee game of {@code 7054} frames, Terran against a random-race computer on Bottleneck.
     */
    public static final @NotNull String REPLAY_TERRAN_VS_COMPUTER = "replays/focused/internal/INTERNAL_00000009.rep";
    /**
     * An SC:R replay of a Use Map Settings game on the 64 x 64 EUD map {@code [EUD]} worm growing 1.2, whose triggers read the local screen
     * and queue commands into the outgoing turn from frame {@code 2} on.
     */
    public static final @NotNull String REPLAY_EUD_WORM_GROWING = "replays/focused/internal/INTERNAL_00000010.rep";
    /**
     * A native SC:R 1v1 ladder game of {@code 34408} frames on Colorless Fate whose replay command buffer runs a stale Restart Game command at frame
     * {@code 14988}, where native replay playback ends.
     */
    public static final @NotNull String REPLAY_STALE_RESTART_GAME = "replays/focused/cwal/CWAL_00000001.rep";
    /**
     * A native SC:R game on a 128 x 128 map whose first human starts in the top right corner, also a case of the native recordings.
     */
    public static final @NotNull String REPLAY_TOP_RIGHT_START = "replays/native/cwal/CWAL_00000001.rep";

    /**
     * A native SC:R saved game of a Use Map Settings game against computers ({@code .snx}).
     */
    public static final @NotNull String SAVE_LEVEL_COMPUTER = "saves/INTERNAL_00000001.snx";
    /**
     * A native SC:R saved game of a multiplayer Tom and Jerry Use Map Settings game ({@code .mlx}).
     */
    public static final @NotNull String SAVE_TOM_AND_JERRY = "saves/INTERNAL_00000002.mlx";
    /**
     * A native SC:R saved game of a multiplayer melee game with a computer on Bottleneck ({@code .mlx}).
     */
    public static final @NotNull String SAVE_MULTIPLAYER_WITH_COMPUTER = "saves/INTERNAL_00000003.mlx";
    /**
     * A native SC:R saved game of a single-player melee game against a computer on Bottleneck ({@code .snx}).
     */
    public static final @NotNull String SAVE_SINGLE_PLAYER_WITH_COMPUTER = "saves/INTERNAL_00000004.snx";

    /**
     * The EUD 3:3 Use Map Settings user map with computer players, also a case of the map detection.
     */
    public static final @NotNull String MAP_EUD_COMPUTER = "maps/user/eud-com.scx";
    /**
     * A single-player map of the classic map archive whose briefing plays WAV files from the map archive.
     */
    public static final @NotNull String MAP_ARCHIVE_NEW_AVALON = "maps/archive/new-avalon.scm";
    /**
     * A map of the classic map archive whose briefing shows a unit type without a portrait.
     */
    public static final @NotNull String MAP_ARCHIVE_DRAGON = "maps/archive/dragon.scx";

    /**
     * The native computer-player phase scheduler cases, compressed.
     */
    public static final @NotNull String CAPTURE_COMPUTER_PHASE_SCHEDULER = "native-capture/ai/computer-phase-domain.bin.gz";
    /**
     * The native resource-area tables of the map unit loading cases.
     */
    public static final @NotNull String CAPTURE_RESOURCE_AREAS = "native-capture/ai/resource-areas-expected.bin";

    /**
     * The native unit input of the first movement update.
     */
    public static final @NotNull String CAPTURE_FIRST_MOVEMENT_UPDATE_INPUT = "native-capture/movement/initial-movement-input.bin";
    /**
     * The native iscript position prediction cases of the movement update.
     */
    public static final @NotNull String CAPTURE_ISCRIPT_PREDICTION = "native-capture/movement/iscript-prediction.bin";
    /**
     * The native local collision cases of the map unit loading cases.
     */
    public static final @NotNull String CAPTURE_LOCAL_COLLISION_CASES = "native-capture/movement/local-collision-cases.bin";
    /**
     * The native local collision candidates collected in the map unit loading cases.
     */
    public static final @NotNull String CAPTURE_LOCAL_COLLISION_COLLECTED = "native-capture/movement/local-collision-collected.bin";
    /**
     * The native local collision results after the final update of the map unit loading cases.
     */
    public static final @NotNull String CAPTURE_LOCAL_COLLISION_FINAL = "native-capture/movement/local-collision-final.bin";
    /**
     * The adjusted movement path record of the map unit loading cases.
     */
    public static final @NotNull String CAPTURE_ADJUSTED_MOVEMENT_PATH = "native-capture/movement/movement-adjusted-path-java.bin";
    /**
     * The initial movement path record of the map unit loading cases.
     */
    public static final @NotNull String CAPTURE_MOVEMENT_PATH = "native-capture/movement/movement-path-java.bin";
    /**
     * The native cases of the movement state that clears the movement, state twelve.
     */
    public static final @NotNull String CAPTURE_CLEAR_STATE_TWELVE = "native-capture/movement/movement-state-twelve.bin";
    /**
     * The native cases of the arrival movement state.
     */
    public static final @NotNull String CAPTURE_ARRIVAL_STATE = "native-capture/movement/movement-state11.bin";
    /**
     * The native cases of the stationary movement state.
     */
    public static final @NotNull String CAPTURE_STATIONARY_STATE = "native-capture/movement/movement-state9.bin";
    /**
     * The native nearby-unit motion search cases.
     */
    public static final @NotNull String CAPTURE_NEARBY_MOTION = "native-capture/movement/nearby-motion-cases.bin";
    /**
     * The native terrain motion collision cases.
     */
    public static final @NotNull String CAPTURE_TERRAIN_MOTION = "native-capture/movement/terrain-motion-cases.bin";
    /**
     * The native unit facing update cases.
     */
    public static final @NotNull String CAPTURE_UNIT_FACING = "native-capture/movement/unit-facing.bin";
    /**
     * The native unit motion collision cases.
     */
    public static final @NotNull String CAPTURE_UNIT_MOTION_COLLISION = "native-capture/movement/unit-motion-cases.bin";
    /**
     * The native unit motion commit cases.
     */
    public static final @NotNull String CAPTURE_MOTION_COMMIT = "native-capture/movement/unit-motion-commit.bin";
    /**
     * The native unit motion update cases.
     */
    public static final @NotNull String CAPTURE_UNIT_MOTION = "native-capture/movement/unit-motion.bin";
    /**
     * The native unit position prediction cases.
     */
    public static final @NotNull String CAPTURE_UNIT_PREDICTION = "native-capture/movement/unit-prediction.bin";

    /**
     * The native moving-target prediction cases.
     */
    public static final @NotNull String CAPTURE_TARGET_PREDICTION = "native-capture/path/moving-target-cases.bin";
    /**
     * The native adjusted moving-target destination cases.
     */
    public static final @NotNull String CAPTURE_ADJUSTED_DESTINATION = "native-capture/path/moving-target-integration.bin";
    /**
     * The native path movement guard cases.
     */
    public static final @NotNull String CAPTURE_MOVEMENT_GUARD = "native-capture/path/path-guard.bin";
    /**
     * The native path pool operation cases.
     */
    public static final @NotNull String CAPTURE_PATH_POOL = "native-capture/path/path-pool.bin";
    /**
     * The native path route allocation cases.
     */
    public static final @NotNull String CAPTURE_ROUTE_ALLOCATION = "native-capture/path/path-route-allocation.bin";
    /**
     * The native path target lookup cases of the map unit loading cases.
     */
    public static final @NotNull String CAPTURE_PATH_TARGETS = "native-capture/path/path-targets.bin";
    /**
     * The native region route search results.
     */
    public static final @NotNull String CAPTURE_REGION_ROUTES = "native-capture/path/region-routes-expected.bin";

    /**
     * The native larva position search cases.
     */
    public static final @NotNull String CAPTURE_LARVA_POSITION = "native-capture/placement/larva-position.bin";
    /**
     * The native larva primary order cases.
     */
    public static final @NotNull String CAPTURE_LARVA_PRIMARY_ORDER = "native-capture/placement/larva-primary.bin";
    /**
     * The native movement destination placement cases, compressed.
     */
    public static final @NotNull String CAPTURE_PLACEMENT_DESTINATIONS = "native-capture/placement/placement-destinations.bin.gz";
    /**
     * The native placement search cases.
     */
    public static final @NotNull String CAPTURE_PLACEMENT_SEARCH = "native-capture/placement/placement-expected.bin";
    /**
     * The native region placement search cases.
     */
    public static final @NotNull String CAPTURE_REGION_PLACEMENT_SEARCH = "native-capture/placement/region-placement-expected.bin";

    /**
     * The native fog sprite registration pool cases, compressed.
     */
    public static final @NotNull String CAPTURE_FOG_REGISTRATION = "native-capture/sprite/fog-registration-domain.bin.gz";
    /**
     * The native sprite effect allocation cases, compressed.
     */
    public static final @NotNull String CAPTURE_SPRITE_EFFECT_ALLOCATION = "native-capture/sprite/thingy-allocation-domain.bin.gz";
    /**
     * The native sprite effect primary loop cases, compressed.
     */
    public static final @NotNull String CAPTURE_SPRITE_EFFECT_PRIMARY_LOOP = "native-capture/sprite/thingy-primary-loop-domain.bin.gz";

    /**
     * The creep-fill derivative fixture containing retained terrain arrays and random-state cases.
     */
    public static final @NotNull String CAPTURE_CREEP_FILL = "native-capture/terrain/creep-fill.bin";
    /**
     * The native foliage population cases.
     */
    public static final @NotNull String CAPTURE_FOLIAGE_POPULATION = "native-capture/terrain/foliage-population.bin";
    /**
     * The native terrain regions of maps without Zerg creep, compressed.
     */
    public static final @NotNull String CAPTURE_NON_ZERG_TERRAIN_REGIONS = "native-capture/terrain/nonzerg-terrain-regions.bin.gz";
    /**
     * The native partial region records, compressed.
     */
    public static final @NotNull String CAPTURE_PARTIAL_REGIONS = "native-capture/terrain/partial-regions.bin.gz";
    /**
     * The native terrain regions of representative maps, compressed.
     */
    public static final @NotNull String CAPTURE_REPRESENTATIVE_TERRAIN_REGIONS = "native-capture/terrain/representative-terrain-regions.bin.gz";

    /**
     * The native evaluation of the installed melee triggers, compressed.
     */
    public static final @NotNull String CAPTURE_TRIGGER_EVALUATION = "native-capture/trigger/trigger-evaluation.bin.gz";

    /**
     * The native timed indicator update cases.
     */
    public static final @NotNull String CAPTURE_TIMED_INDICATORS = "native-capture/ui/frame-indicators.bin";
    /**
     * The native frame timing cases.
     */
    public static final @NotNull String CAPTURE_FRAME_TIMING = "native-capture/ui/frame-timing.bin";
    /**
     * The native hidden statistics panel cases.
     */
    public static final @NotNull String CAPTURE_HIDDEN_STATISTICS_PANEL = "native-capture/ui/hidden-statistics-panel-cases.bin";
    /**
     * The native palette cycle update cases.
     */
    public static final @NotNull String CAPTURE_PALETTE_CYCLE_UPDATE = "native-capture/ui/palette-cycle-cases.bin";
    /**
     * The native static tileset palette cycle initialization.
     */
    public static final @NotNull String CAPTURE_PALETTE_STATIC_INITIALIZATION = "native-capture/ui/palette-cycle-initialization.bin";
    /**
     * The native palette cycle initialization of empty descriptors.
     */
    public static final @NotNull String CAPTURE_PALETTE_EMPTY_INITIALIZATION = "native-capture/ui/palette-empty-initialization.bin";
    /**
     * The native replay button condition cases.
     */
    public static final @NotNull String CAPTURE_REPLAY_BUTTON_CONDITION = "native-capture/ui/replay-button-condition-cases.bin";
    /**
     * The native replay button presentation cases.
     */
    public static final @NotNull String CAPTURE_REPLAY_BUTTON_PRESENTATION = "native-capture/ui/replay-button-presentation-cases.bin";
    /**
     * The native replay display cases.
     */
    public static final @NotNull String CAPTURE_REPLAY_DISPLAY = "native-capture/ui/replay-display-cases.bin";
    /**
     * The native replay label cases.
     */
    public static final @NotNull String CAPTURE_REPLAY_LABEL = "native-capture/ui/replay-label-cases.bin";
    /**
     * The native empty status panel callback cases.
     */
    public static final @NotNull String CAPTURE_EMPTY_STATUS_PANEL = "native-capture/ui/status-panel-cases.bin";
    /**
     * The native installed status button callback cases.
     */
    public static final @NotNull String CAPTURE_INSTALLED_STATUS_PANEL_CALLBACK = "native-capture/ui/status-panel-installed-callback-cases.bin";

    /**
     * The native accounting cases of the replay frame advance.
     */
    public static final @NotNull String CAPTURE_ACCOUNTING_ADVANCE = "native-capture/unit/accounting-advance.bin";
    /**
     * The native accounting storage of the unit completion cases, compressed.
     */
    public static final @NotNull String CAPTURE_COMPLETION_ACCOUNTING = "native-capture/unit/completion-accounting.bin.gz";
    /**
     * The native first-phase unit completion cases.
     */
    public static final @NotNull String CAPTURE_COMPLETION_FIRST_PHASE = "native-capture/unit/completion-first-phase.bin";
    /**
     * The native scalar unit fields after construction.
     */
    public static final @NotNull String CAPTURE_CONSTRUCTION_SCALARS = "native-capture/unit/construction-scalars.bin";
    /**
     * The native final unit completion cases, compressed.
     */
    public static final @NotNull String CAPTURE_FINAL_COMPLETION = "native-capture/unit/final-completion.bin.gz";
    /**
     * The native initial order assignment cases.
     */
    public static final @NotNull String CAPTURE_INITIAL_ORDERS = "native-capture/unit/initial-orders.bin";
    /**
     * The native player relation initialization cases.
     */
    public static final @NotNull String CAPTURE_PLAYER_RELATIONS = "native-capture/unit/player-relations.bin";
    /**
     * The native preplaced unit property cases, one unit record per case, compressed.
     */
    public static final @NotNull String CAPTURE_PREPLACED_PROPERTIES = "native-capture/unit/preplaced-properties.bin.gz";
    /**
     * The native primary order cases.
     */
    public static final @NotNull String CAPTURE_PRIMARY_ORDERS = "native-capture/unit/primary-orders.bin";
    /**
     * The native production record cases of the unit registry.
     */
    public static final @NotNull String CAPTURE_REGISTRY_RECORDS = "native-capture/unit/registry-records.bin";
    /**
     * The native subunit initial order cases.
     */
    public static final @NotNull String CAPTURE_SUBUNIT_INITIAL_ORDERS = "native-capture/unit/subunit-initial-orders.bin";
    /**
     * The native unit order wrapper cases.
     */
    public static final @NotNull String CAPTURE_UNIT_ORDER_WRAPPER = "native-capture/unit/unit-order-wrapper.bin";
    /**
     * The native unit section records of the map unit loading cases.
     */
    public static final @NotNull String CAPTURE_UNIT_SECTION = "native-capture/unit/unit-section-expected.bin";

    /**
     * The native map visibility cache update cases.
     */
    public static final @NotNull String CAPTURE_MAP_CACHE_UPDATE = "native-capture/visibility/map-cache-update.bin";

    private static final @NotNull String SAVES_FOLDER = "saves/";
    private static final @NotNull String REPLAY_SUFFIX = ".rep";
    private static final char EXTENSION_SEPARATOR = '.';
    /**
     * The name suffix of a compressed native capture.
     */
    private static final @NotNull String COMPRESSED_SUFFIX = ".gz";
    /**
     * The digest algorithm of the native capture hashes.
     */
    private static final @NotNull String DIGEST_ALGORITHM = "SHA-256";

    /**
     * Returns the file of a resource.
     *
     * @param resource one of the resource constants
     * @return the resource file
     * @throws IllegalStateException if the resource is not a file on the test class path
     */
    public static @NotNull Path getPath(@NotNull String resource) {
        return TestResourceUtils.getPath(resource);
    }

    /**
     * Reads a resource.
     *
     * @param resource one of the resource constants
     * @return the resource contents
     * @throws IOException           if the resource cannot be read
     * @throws IllegalStateException if the resource is not a file on the test class path
     */
    public static byte @NotNull [] readAllBytes(@NotNull String resource) throws IOException {
        return TestResourceUtils.readAllBytes(resource);
    }

    /**
     * Reads a native capture; a capture whose name ends in {@code .gz} is decompressed.
     *
     * @param resource one of the {@code CAPTURE_*} constants
     * @return the capture bytes
     * @throws IOException           if the capture cannot be read or decompressed
     * @throws IllegalStateException if the resource is not a file on the test class path
     */
    public static byte @NotNull [] readNativeCapture(@NotNull String resource) throws IOException {
        if (!resource.endsWith(COMPRESSED_SUFFIX)) {
            return readAllBytes(resource);
        }

        try (InputStream file = Files.newInputStream(getPath(resource)); InputStream input = new GZIPInputStream(file)) {
            return input.readAllBytes();
        }
    }

    /**
     * Reads a native capture as {@link #readNativeCapture(String)} does and checks the SHA-256 digest of the capture bytes.
     *
     * @param resource     one of the {@code CAPTURE_*} constants
     * @param expectedHash the expected SHA-256 digest in lower-case hexadecimal
     * @return the capture bytes
     * @throws IOException              if the capture cannot be read or decompressed
     * @throws NoSuchAlgorithmException if SHA-256 is unavailable
     * @throws IllegalStateException    if the resource is not a file on the test class path
     */
    public static byte @NotNull [] readNativeCapture(@NotNull String resource, @NotNull String expectedHash) throws IOException, NoSuchAlgorithmException {
        byte[] capture = readNativeCapture(resource);

        Assertions.assertEquals(expectedHash, HexFormat.of().formatHex(MessageDigest.getInstance(DIGEST_ALGORITHM).digest(capture)));

        return capture;
    }

    /**
     * Returns the replay of the game a saved game was saved from.
     *
     * @param save one of the {@code SAVE_*} constants
     * @return the replay resource path
     * @throws IllegalArgumentException if {@code save} is not a saved game resource
     */
    public static @NotNull String getSaveReplay(@NotNull String save) {
        int extensionIndex = save.lastIndexOf(EXTENSION_SEPARATOR);

        if (!save.startsWith(SAVES_FOLDER) || extensionIndex < SAVES_FOLDER.length()) {
            throw Exceptions.newIllegalArgumentException("Not a saved game resource: {0}", save);
        }

        return save.substring(0, extensionIndex) + REPLAY_SUFFIX;
    }


    /**
     * Creates a {@link GameTestResources} instance.
     */
    private GameTestResources() {
    }
}
