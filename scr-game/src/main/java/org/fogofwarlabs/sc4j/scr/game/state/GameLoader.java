package org.fogofwarlabs.sc4j.scr.game.state;

import org.fogofwarlabs.sc4j.base.exception.Exceptions;
import org.fogofwarlabs.sc4j.scr.core.GameSettings;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkFile;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkSection;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkThingy;
import org.fogofwarlabs.sc4j.scr.data.format.chk.ChkUnit;
import org.fogofwarlabs.sc4j.scr.game.map.MapSettingsLoaderUtils;
import org.fogofwarlabs.sc4j.scr.game.map.MapSpriteEffectLoader;
import org.fogofwarlabs.sc4j.scr.game.map.MapUnitLoader;
import org.fogofwarlabs.sc4j.scr.game.map.StartingUnitLoader;
import org.fogofwarlabs.sc4j.scr.game.player.PlayerTable;
import org.fogofwarlabs.sc4j.scr.game.player.StartLocation;
import org.fogofwarlabs.sc4j.scr.game.terrain.Foliage;
import org.fogofwarlabs.sc4j.scr.game.terrain.GameMap;
import org.fogofwarlabs.sc4j.scr.game.terrain.MapTileFlags;
import org.fogofwarlabs.sc4j.scr.game.unit.UnitCreationContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Initializes map objects from game settings and ordered scenario sections.
 * <p>
 * Replay decoding and header acceptance belong to the input adapter; this class has no replay-format dependency.
 */
public final class GameLoader {
    /**
     * The Start Location unit type ({@link org.fogofwarlabs.sc4j.scr.content.ScrUnitIds#SPECIAL_START_LOCATION}), kept as a compile-time
     * constant for callers.
     */
    public static final int START_LOCATION_UNIT_ID = 214;


    /**
     * Identifies a verified record-routing boundary without predicting construction success.
     */
    public enum InitializationRoute {
        /**
         * A THG2 record constructed as a sprite.
         */
        THG2_SPRITE_CONSTRUCTOR,
        /**
         * A THG2 record constructed as a unit.
         */
        THG2_UNIT_WRAPPER,
        /**
         * A THG2 record skipped by the creation filter.
         */
        THG2_FILTERED,
        /**
         * A UNIT record.
         */
        UNIT_RECORD
    }

    /**
     * Preserves one ordered map-initialization input and its known routing decision.
     * <p>
     * This is an input ledger: it holds no constructor results, pool indices, generations or random states, and it does
     * not route a UNIT record to a constructor.
     */
    public static final class InitializationRequest {
        private final @NotNull InitializationRoute route;
        private final int sectionHeaderOffset;
        private final int recordOffset;

        private final int typeIdentifier;
        private final int owner;
        private final int horizontal;
        private final int vertical;
        private final int flags;

        private InitializationRequest(@NotNull InitializationRoute route,
                                      int sectionHeaderOffset, int recordOffset,
                                      int typeIdentifier, int owner,
                                      int horizontal, int vertical,
                                      int flags) {
            this.route               = route;
            this.sectionHeaderOffset = sectionHeaderOffset;
            this.recordOffset        = recordOffset;

            this.typeIdentifier = typeIdentifier;
            this.owner          = owner;
            this.horizontal     = horizontal;
            this.vertical       = vertical;
            this.flags          = flags;
        }

        /**
         * @return the known record-routing boundary
         */
        public @NotNull InitializationRoute getRoute() {
            return this.route;
        }

        /**
         * @return the source section header offset in the original supplied CHK buffer
         */
        public int getSectionHeaderOffset() {
            return this.sectionHeaderOffset;
        }

        /**
         * @return the absolute source record offset in the original supplied CHK buffer
         */
        public int getRecordOffset() {
            return this.recordOffset;
        }

        /**
         * @return the original source type WORD
         */
        public int getTypeIdentifier() {
            return this.typeIdentifier;
        }

        /**
         * @return the owner passed by THG2 routing, or the unmodified UNIT owner byte
         */
        public int getOwner() {
            return this.owner;
        }

        /**
         * @return the unsigned source horizontal position WORD
         */
        public int getHorizontal() {
            return this.horizontal;
        }

        /**
         * @return the unsigned source vertical position WORD
         */
        public int getVertical() {
            return this.vertical;
        }

        /**
         * @return the unmodified THG2 flags WORD or UNIT state-flags WORD
         */
        public int getFlags() {
            return this.flags;
        }
    }

    /**
     * Creates an input ledger with THG2 records before UNIT records.
     * <p>
     * Duplicate sections retain discovery order within each tag. The ledger does not execute constructors, append
     * provisional units, assign runtime identities, consume RNG or include the subsequent incomplete starting-unit phase.
     *
     * @param settings the game policy supplying the map-settings predicate
     * @param mapFile  the indexed map
     * @return immutable ordered inputs with known THG2 routing and source offsets
     * @throws IllegalStateException if a record payload cannot be read with its original record size
     */
    public static @NotNull List<InitializationRequest> createInitializationLedger(@NotNull GameSettings settings, @NotNull ChkFile mapFile) {
        List<InitializationRequest> requests = new ArrayList<>();
        List<ChkThingy> thingies = mapFile.getThingies();
        int thingyIndex = 0;
        boolean useMapSettings = settings.usesMapSettings();

        for (ChkSection section : mapFile.getSections(ChkFile.THINGY_SECTION)) {
            for (int recordOffset = 0; recordOffset < section.getDeclaredSize(); recordOffset += ChkThingy.ENTRY_SIZE) {
                ChkThingy thingy = thingies.get(thingyIndex++);
                InitializationRoute route = thingy.isSprite()
                                            ? InitializationRoute.THG2_SPRITE_CONSTRUCTOR
                                            : thingy.requestsUnitCreation(useMapSettings)
                                              ? InitializationRoute.THG2_UNIT_WRAPPER
                                              : InitializationRoute.THG2_FILTERED;

                requests.add(new InitializationRequest(route,
                                                       section.getHeaderOffset(), thingy.getSourceOffset(),
                                                       thingy.getTypeIdentifier(), thingy.getCreationOwner(),
                                                       thingy.getHorizontal(), thingy.getVertical(),
                                                       thingy.getFlags()));
            }
        }

        List<ChkUnit> units = mapFile.getUnits();
        int unitIndex = 0;

        for (ChkSection section : mapFile.getSections(ChkFile.UNIT_SECTION)) {
            for (int recordOffset = 0; recordOffset < section.getDeclaredSize(); recordOffset += ChkUnit.ENTRY_SIZE) {
                ChkUnit unit = units.get(unitIndex++);

                requests.add(new InitializationRequest(InitializationRoute.UNIT_RECORD,
                                                       section.getHeaderOffset(), section.getPayloadOffset() + recordOffset,
                                                       unit.getUnitId(), unit.getOwner(),
                                                       unit.getX(), unit.getY(),
                                                       unit.getStateFlags()));
            }
        }

        return List.copyOf(requests);
    }

    /**
     * Creates map objects after the caller establishes the pools, random state and creation contexts.
     * <p>
     * Every THG2 occurrence precedes every UNIT occurrence. Each UNIT occurrence replaces the start buffer.
     * The game remains unready until the separate startup, registry and frame prerequisites are established.
     *
     * @param context                the established creation and completion inputs
     * @param game                   the construction state with its active initialization random scope
     * @param settings               the accepted game creation policy
     * @param foliageSeed            the original setup seed, independent of the current random generator state
     * @param mapFile                the original ordered map sections
     * @param expansionEnabled       the accepted expansion mode
     * @param restrictPreplacedUnits the preplaced-unit filter
     * @param teamStart              the starting-unit input of a team game, or {@code null}
     * @param cameraPosition         the retained camera tile WORD pair
     * @param viewportWidth          the original viewport width in pixels
     * @param viewportHeight         the original viewport height in pixels
     * @param instructionLimit       the checked script execution bound
     * @throws IllegalStateException if the construction scope is not fresh, or the foliage is enabled but not created
     */
    public static void initializeMapObjects(@NotNull UnitCreationContext context, @NotNull Game game,
                                            @NotNull GameSettings settings,
                                            int foliageSeed,
                                            @NotNull ChkFile mapFile,
                                            boolean expansionEnabled,
                                            boolean restrictPreplacedUnits,
                                            StartingUnitLoader.@Nullable TeamStart teamStart,
                                            int @NotNull [] cameraPosition,
                                            int viewportWidth, int viewportHeight,
                                            int instructionLimit) {
        if (game.hasInitializedWorldState() || game.getRandom().getEnabled() == 0 || game.getSpriteSystem().getStartupSpriteEffect() != null) {
            throw Exceptions.newIllegalStateException("Native map creation requires a fresh initialized construction scope.");
        }

        // The game start stores the setup seed as the game seed before it seeds the random generator; the foliage
        // placement reads it later.
        game.getGameContext().setGameSeed(foliageSeed);
        game.getUnitSystem().setUnitCreationContext(context);

        // Every tile starts unseen and unexplored by every player, in every game mode.
        for (int tileIndex = 0, tileCount = game.getMap().getTileFlagCount(); tileIndex < tileCount; tileIndex++) {
            game.getMap().applyMask(tileIndex, MapTileFlags.NOT_VISIBLE);
        }

        game.getIscriptExecutor().initializeSpriteEffects(instructionLimit);

        List<ChkThingy> thingies = mapFile.getThingies();
        List<MapSpriteEffectLoader.Creation> thingyCreations = new ArrayList<>();
        int firstRecord = 0;

        for (ChkSection section : mapFile.getSections(ChkFile.THINGY_SECTION)) {
            int followingRecord = firstRecord + section.getDeclaredSize() / ChkThingy.ENTRY_SIZE;

            thingyCreations.addAll(MapSpriteEffectLoader.load(context, game,
                                                              section,
                                                              thingies.subList(firstRecord, followingRecord),
                                                              settings,
                                                              expansionEnabled,
                                                              instructionLimit));

            firstRecord = followingRecord;
        }

        game.setMapSpriteEffectCreations(thingyCreations);

        if (settings.usesMapSettings()) {
            // Apply MASK and player settings between THG2 and UNIT; bytes past the map have no represented tile.
            for (ChkSection section : mapFile.getSections(ChkFile.MASK_SECTION)) {
                byte[] mask = section.getPayload();
                int count = Math.min(Math.min(mask.length, GameMap.MAXIMUM_TILE_COUNT), game.getMap().getTileFlagCount());

                for (int tileIndex = 0; tileIndex < count; tileIndex++) {
                    game.getMap().applyMask(tileIndex, mask[tileIndex]);
                }
            }

            MapSettingsLoaderUtils.applyPlayerSettings(game, mapFile);
        }

        int[] startPositions = new int[MapUnitLoader.START_POSITIONS_SIZE];
        List<ChkUnit> units = mapFile.getUnits();
        List<MapUnitLoader.Creation> unitCreations = new ArrayList<>();

        firstRecord = 0;

        for (ChkSection section : mapFile.getSections(ChkFile.UNIT_SECTION)) {
            int followingRecord = firstRecord + section.getDeclaredSize() / ChkUnit.ENTRY_SIZE;

            unitCreations.addAll(MapUnitLoader.load(context, game,
                                                    section,
                                                    units.subList(firstRecord, followingRecord),
                                                    settings,
                                                    startPositions,
                                                    cameraPosition,
                                                    viewportWidth, viewportHeight,
                                                    true,
                                                    restrictPreplacedUnits,
                                                    expansionEnabled,
                                                    instructionLimit));

            firstRecord = followingRecord;
        }

        game.setMapUnitCreations(unitCreations);
        game.getPlayerTable().getStartLocations().clear();

        for (int playerIndex = 0; playerIndex < PlayerTable.PLAYABLE_PLAYER_SLOTS; playerIndex++) {
            int positionIndex = playerIndex * MapUnitLoader.POSITION_COMPONENT_COUNT;

            game.getPlayerTable().getStartLocations().add(new StartLocation(playerIndex,
                                                                            startPositions[positionIndex], startPositions[positionIndex + 1]));
        }

        if (context.isFoliageEnabled()) {
            Foliage foliage = game.getFoliage();

            if (foliage == null) {
                throw Exceptions.newIllegalStateException("The world creation sets the foliage resource before the map objects.");
            }

            foliage.populate(game.getMap(), foliageSeed);
        }

        game.setStartingUnitCreations(StartingUnitLoader.create(context, game, settings, startPositions, teamStart, instructionLimit));
    }

    private GameLoader() {
    }
}
