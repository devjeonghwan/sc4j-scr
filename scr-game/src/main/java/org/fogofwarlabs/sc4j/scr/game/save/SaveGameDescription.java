package org.fogofwarlabs.sc4j.scr.game.save;

import org.fogofwarlabs.sc4j.scr.game.save.section.GameInformationSection;
import org.fogofwarlabs.sc4j.scr.game.state.GameRulesVersion;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parses and writes the readable saved-game banner, including player names, map details and format version.
 * <p>
 * The banner is UTF-8 text but for its file name line, which holds the save name bytes as the Save Game command carried
 * them.
 */
public final class SaveGameDescription {
    /**
     * The byte that ends the banner; the reader skips bytes up to it.
     */
    public static final int END_OF_BANNER = 0x1A;
    /**
     * The game type a game without a template name shows.
     */
    public static final @NotNull String CUSTOM_GAME_TYPE = "Custom";
    /**
     * The client version the banner names, the modeled executable's.
     */
    public static final @NotNull String CLIENT_VERSION = "1.23.10." + GameRulesVersion.CURRENT_BUILD;

    private static final @NotNull String RULE = "****************************************************";
    private static final @NotNull String LINE_END = "\r\n";
    private static final @NotNull String CLIENT_PREFIX = "** Starcraft Version ";
    private static final @NotNull String SINGLE_PLAYER_FORMAT = "** Single Player Save Format ver ";
    private static final @NotNull String MULTIPLAYER_FORMAT = "** Multi Player Save Format ver ";
    private static final @NotNull String FILENAME_LABEL = "  Filename:  ";
    private static final @NotNull String PLAYERS_LABEL = "   Players:  ";
    private static final @NotNull String PLAYERS_CONTINUATION = "             ";
    private static final @NotNull String GAME_TYPE_LABEL = " Game Type:  ";
    private static final @NotNull String TITLE_LABEL = " SCM Title:  ";
    private static final @NotNull String PATH_LABEL = "  SCM Path:  ";
    private static final @NotNull String EUD_FLAGS_LABEL = " EUD Flags:  ";

    /**
     * Creates a {@link SaveGameDescription} instance from a banner written by {@link #toBannerBytes()}.
     *
     * @param banner the banner bytes without its end byte
     * @return the created {@link SaveGameDescription} instance; lines the banner lacks stay empty
     */
    public static @NotNull SaveGameDescription parse(byte @NotNull [] banner) {
        byte[] lineEnd = LINE_END.getBytes(StandardCharsets.US_ASCII);
        String clientVersion = "";
        boolean multiplayer = false;
        byte[] filename = new byte[0];
        List<String> players = new ArrayList<>();
        String gameType = "";
        String scenarioTitle = "";
        String mapPath = "";
        int eudFlags = 0;
        boolean inPlayers = false;
        int next = 0;

        while (next < banner.length) {
            int start = next;
            int end = findLineEnd(banner, start, lineEnd);
            String line = new String(banner, start, end - start, StandardCharsets.UTF_8);

            next = end + lineEnd.length;

            if (line.startsWith(CLIENT_PREFIX)) {
                clientVersion = line.substring(CLIENT_PREFIX.length());
            } else if (line.startsWith(MULTIPLAYER_FORMAT)) {
                multiplayer = true;
            } else if (line.startsWith(FILENAME_LABEL)) {
                // The label is ASCII, so its characters are its bytes.
                filename = Arrays.copyOfRange(banner, start + FILENAME_LABEL.length(), end);
            } else if (line.startsWith(PLAYERS_LABEL)) {
                players.add(line.substring(PLAYERS_LABEL.length()));
                inPlayers = true;

                continue;
            } else if (inPlayers && line.startsWith(PLAYERS_CONTINUATION)) {
                players.add(line.substring(PLAYERS_CONTINUATION.length()));

                continue;
            } else if (line.startsWith(GAME_TYPE_LABEL)) {
                gameType = line.substring(GAME_TYPE_LABEL.length());
            } else if (line.startsWith(TITLE_LABEL)) {
                scenarioTitle = line.substring(TITLE_LABEL.length());
            } else if (line.startsWith(PATH_LABEL)) {
                mapPath = line.substring(PATH_LABEL.length());
            } else if (line.startsWith(EUD_FLAGS_LABEL)) {
                try {
                    eudFlags = Integer.parseInt(line.substring(EUD_FLAGS_LABEL.length()).trim());
                } catch (NumberFormatException exception) {
                    eudFlags = 0;
                }
            }

            inPlayers = false;
        }

        return new SaveGameDescription(clientVersion, multiplayer, filename, players, gameType, scenarioTitle, mapPath, eudFlags);
    }

    /**
     * Finds the next line end of a banner.
     *
     * @param banner  the banner bytes
     * @param start   the index the search starts at
     * @param lineEnd the line end bytes
     * @return the index of the line end, or the banner length when no line end follows
     */
    private static int findLineEnd(byte @NotNull [] banner, int start, byte @NotNull [] lineEnd) {
        for (int index = start; index + lineEnd.length <= banner.length; index++) {
            if (Arrays.equals(banner, index, index + lineEnd.length, lineEnd, 0, lineEnd.length)) {
                return index;
            }
        }

        return banner.length;
    }

    private final @NotNull String clientVersion;
    private final boolean multiplayer;
    private final byte @NotNull [] filename;
    private final @NotNull List<String> players;
    private final @NotNull String gameType;
    private final @NotNull String scenarioTitle;
    private final @NotNull String mapPath;
    private final int eudFlags;

    /**
     * Creates a {@link SaveGameDescription} instance.
     *
     * @param clientVersion the client version string
     * @param multiplayer   whether the game is a network game
     * @param filename      the save name bytes the Save Game command carried, without terminator
     * @param players       the human players' names
     * @param gameType      the game type name
     * @param scenarioTitle the scenario title
     * @param mapPath       the map path
     * @param eudFlags      the EUD flags of the game
     */
    public SaveGameDescription(@NotNull String clientVersion,
                               boolean multiplayer,
                               byte @NotNull [] filename,
                               @NotNull List<String> players,
                               @NotNull String gameType,
                               @NotNull String scenarioTitle,
                               @NotNull String mapPath,
                               int eudFlags) {
        this.clientVersion = clientVersion;
        this.multiplayer   = multiplayer;
        this.filename      = filename.clone();
        this.players       = List.copyOf(players);
        this.gameType      = gameType;
        this.scenarioTitle = scenarioTitle;
        this.mapPath       = mapPath;
        this.eudFlags      = eudFlags;
    }

    /**
     * @return the client version string
     */
    public @NotNull String getClientVersion() {
        return this.clientVersion;
    }

    /**
     * @return {@code true} if the game was a network game, {@code false} otherwise
     */
    public boolean isMultiplayer() {
        return this.multiplayer;
    }

    /**
     * @return a copy of the save name bytes the Save Game command carried, without terminator
     */
    public byte @NotNull [] getFilename() {
        return this.filename.clone();
    }

    /**
     * @return the human players' names
     */
    public @NotNull List<String> getPlayers() {
        return this.players;
    }

    /**
     * @return the game type name
     */
    public @NotNull String getGameType() {
        return this.gameType;
    }

    /**
     * @return the scenario title
     */
    public @NotNull String getScenarioTitle() {
        return this.scenarioTitle;
    }

    /**
     * @return the map path
     */
    public @NotNull String getMapPath() {
        return this.mapPath;
    }

    /**
     * @return the EUD flags of the game
     */
    public int getEudFlags() {
        return this.eudFlags;
    }

    /**
     * Writes the banner in its established line layout, without its end byte.
     *
     * @return the banner bytes
     */
    public byte @NotNull [] toBannerBytes() {
        StringBuilder head = new StringBuilder();
        StringBuilder tail = new StringBuilder();
        ByteArrayOutputStream banner = new ByteArrayOutputStream();

        head.append(RULE).append(LINE_END);
        head.append(CLIENT_PREFIX).append(this.clientVersion).append(LINE_END);
        head.append(this.multiplayer ? MULTIPLAYER_FORMAT : SINGLE_PLAYER_FORMAT).append(GameInformationSection.FORMAT).append('.')
            .append(GameInformationSection.VERSION).append(' ').append(LINE_END);
        head.append(RULE).append(LINE_END);
        head.append(LINE_END);
        head.append(FILENAME_LABEL);
        tail.append(LINE_END);

        for (int index = 0; index < this.players.size(); index++) {
            tail.append(index == 0 ? PLAYERS_LABEL : PLAYERS_CONTINUATION).append(this.players.get(index)).append(LINE_END);
        }

        tail.append(GAME_TYPE_LABEL).append(this.gameType).append(LINE_END);
        tail.append(TITLE_LABEL).append(this.scenarioTitle).append(LINE_END);
        tail.append(PATH_LABEL).append(this.mapPath).append(LINE_END);
        tail.append(EUD_FLAGS_LABEL).append(this.eudFlags).append(LINE_END).append(LINE_END);

        // The file name line holds the name bytes as they are.
        banner.writeBytes(head.toString().getBytes(StandardCharsets.UTF_8));
        banner.writeBytes(this.filename);
        banner.writeBytes(tail.toString().getBytes(StandardCharsets.UTF_8));

        return banner.toByteArray();
    }
}
