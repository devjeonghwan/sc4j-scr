package org.fogofwarlabs.sc4j.scr.data.format.replay;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Retains decoded replay metadata, recorded player identities and game setup fields.
 */
public final class ReplayHeader {
    /**
     * Represents a single player slot of the game.
     */
    public static final class ReplayPlayer {
        private final int slotId;
        private final int id;
        private final int type;
        private final int race;
        private final int team;
        private final @NotNull String name;

        /**
         * Creates a {@link ReplayPlayer} instance.
         *
         * @param slotId the stored command player index, preserving all 32 bits
         * @param id     the recorded issuer comparison value, preserving all 32 bits
         * @param type   the player type
         * @param race   the player race
         * @param team   the player team
         * @param name   the player name
         */
        public ReplayPlayer(int slotId, int id, int type, int race, int team, @NotNull String name) {
            this.slotId = slotId;
            this.id     = id;
            this.type   = type;
            this.race   = race;
            this.team   = team;
            this.name   = name;
        }

        /**
         * Returns the first DWORD of the player record.
         * <p>
         * The recorded-command path uses this value as its applying player index. It is not the record's position in the header list.
         *
         * @return the stored command player index, preserving all 32 bits
         */
        public int getSlotId() {
            return this.slotId;
        }

        /**
         * @return the second DWORD, compared with a command block's issuer byte without truncation
         */
        public int getId() {
            return this.id;
        }

        /**
         * @return the player type
         */
        public int getType() {
            return this.type;
        }

        /**
         * @return the player race
         */
        public int getRace() {
            return this.race;
        }

        /**
         * @return the player team
         */
        public int getTeam() {
            return this.team;
        }

        /**
         * @return the player name
         */
        public @NotNull String getName() {
            return this.name;
        }
    }

    private final int engine;

    private final long frames;
    private final long randomSeed;

    private final @NotNull String title;
    private final int mapWidth;
    private final int mapHeight;
    /**
     * The number of available slots, kept with the recorded header although replay setup does not read it.
     */
    private final int availableSlotsCount;

    private final int speed;
    private final int type;
    /**
     * The game subtype WORD, kept with the recorded header although replay setup reads {@link ReplaySettings#getSubtypeValue} instead.
     */
    private final int subType;
    private final @NotNull String host;
    private final @NotNull String map;

    private final @NotNull List<ReplayPlayer> players;
    private final int @NotNull [] colors;
    private final @NotNull ReplaySettings settings;

    private final int campaignMission;

    /**
     * Creates a {@link ReplayHeader} instance.
     *
     * @param engine              the engine ID
     * @param frames              the number of frames
     * @param randomSeed          the shared game RNG seed
     * @param title               the game title
     * @param mapWidth            the map width in tiles
     * @param mapHeight           the map height in tiles
     * @param availableSlotsCount the number of available slots
     * @param speed               the game speed ID
     * @param type                the game type ID
     * @param subType             the game subtype
     * @param host                the host player name
     * @param map                 the map name
     * @param players             the player slots (12 entries)
     * @param colors              the player colors (8 entries)
     * @param settings            the recorded game setup settings
     */
    public ReplayHeader(int engine,
                        long frames, long randomSeed,
                        @NotNull String title,
                        int mapWidth, int mapHeight,
                        int availableSlotsCount,
                        int speed,
                        int type, int subType,
                        @NotNull String host,
                        @NotNull String map,
                        @NotNull List<ReplayPlayer> players, int @NotNull [] colors,
                        @NotNull ReplaySettings settings) {
        this(engine,
             frames, randomSeed,
             title,
             mapWidth, mapHeight,
             availableSlotsCount,
             speed,
             type, subType,
             host,
             map,
             players, colors,
             settings,
             0);
    }

    /**
     * Creates a {@link ReplayHeader} instance with a campaign mission.
     *
     * @param engine              the engine ID
     * @param frames              the number of frames
     * @param randomSeed          the shared game RNG seed
     * @param title               the game title
     * @param mapWidth            the map width in tiles
     * @param mapHeight           the map height in tiles
     * @param availableSlotsCount the number of available slots
     * @param speed               the game speed ID
     * @param type                the game type ID
     * @param subType             the game subtype
     * @param host                the host player name
     * @param map                 the map name
     * @param players             the player slots (12 entries)
     * @param colors              the player colors (8 entries)
     * @param settings            the recorded game setup settings
     * @param campaignMission     the campaign mission WORD at header offset {@code 5}
     */
    public ReplayHeader(int engine,
                        long frames, long randomSeed,
                        @NotNull String title,
                        int mapWidth, int mapHeight,
                        int availableSlotsCount,
                        int speed,
                        int type, int subType,
                        @NotNull String host,
                        @NotNull String map,
                        @NotNull List<ReplayPlayer> players, int @NotNull [] colors,
                        @NotNull ReplaySettings settings,
                        int campaignMission) {
        this.engine = engine;

        this.frames     = frames;
        this.randomSeed = randomSeed;

        this.title               = title;
        this.mapWidth            = mapWidth;
        this.mapHeight           = mapHeight;
        this.availableSlotsCount = availableSlotsCount;

        this.speed   = speed;
        this.type    = type;
        this.subType = subType;
        this.host    = host;
        this.map     = map;

        this.players  = players;
        this.colors   = colors;
        this.settings = settings;

        this.campaignMission = campaignMission;
    }

    /**
     * @return the engine ID
     */
    public int getEngine() {
        return this.engine;
    }

    /**
     * @return the number of frames
     */
    public long getFrames() {
        return this.frames;
    }

    /**
     * @return the shared game RNG seed
     */
    public long getRandomSeed() {
        return this.randomSeed;
    }

    /**
     * @return the game title
     */
    public @NotNull String getTitle() {
        return this.title;
    }

    /**
     * @return the map width in tiles
     */
    public int getMapWidth() {
        return this.mapWidth;
    }

    /**
     * @return the map height in tiles
     */
    public int getMapHeight() {
        return this.mapHeight;
    }

    /**
     * @return the game speed ID
     */
    public int getSpeed() {
        return this.speed;
    }

    /**
     * @return the game type ID
     */
    public int getType() {
        return this.type;
    }

    /**
     * @return the host player name
     */
    public @NotNull String getHost() {
        return this.host;
    }

    /**
     * @return the map name
     */
    public @NotNull String getMap() {
        return this.map;
    }

    /**
     * @return the player slots (12 entries)
     */
    public @NotNull List<ReplayPlayer> getPlayers() {
        return this.players;
    }

    /**
     * @return an independent copy of the eight recorded player color indices, in player slot order
     */
    public int @NotNull [] getColors() {
        return this.colors.clone();
    }

    /**
     * @return the recorded game setup settings
     */
    public @NotNull ReplaySettings getSettings() {
        return this.settings;
    }

    /**
     * @return the campaign mission WORD at header offset {@code 5}: an index into the mapdata.dat mission table, {@code 0} outside campaigns
     */
    public int getCampaignMission() {
        return this.campaignMission;
    }


    /**
     * Finds the first header record with the given full identifier value.
     * <p>
     * This metadata lookup does not implement slot eligibility or the extended runtime player records used by command dispatch.
     *
     * @param id the player ID
     * @return the matching player, or {@code null} if not found
     */
    public @Nullable ReplayPlayer findPlayer(int id) {
        for (ReplayPlayer player : this.players) {
            if (player.getId() == id) {
                return player;
            }
        }

        return null;
    }
}
