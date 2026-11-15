package org.fogofwarlabs.sc4j.scr.client.debug;

import org.fogofwarlabs.sc4j.base.log.Logger;
import org.fogofwarlabs.sc4j.scr.client.ClientSession;
import org.fogofwarlabs.sc4j.scr.client.configuration.InstallLayout;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayCommand;
import org.fogofwarlabs.sc4j.scr.data.format.replay.ReplayFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * A test-only command feed for the hidden client: replays the recorded commands of a saved game as the local player's commands of a live (LAN
 * developer lobby) game, so that a long play session can be reproduced for profiling. Set with {@code -Dsc4j.commandFeed=<replay>}; the game takes
 * the replay's seed. A command recorded at frame {@code f} is queued into the turn exchanged at frame {@code f - latency}
 * ({@code -Dsc4j.commandFeedLatency}, default {@code 3}, the LAN game's own turns in transit), so that it executes at its recorded frame.
 */
public final class CommandFeed implements LongConsumer {
    /**
     * The system property naming the replay.
     */
    public static final @NotNull String PROPERTY = "sc4j.commandFeed";

    private static final @NotNull Logger LOGGER = Logger.of(CommandFeed.class);
    private static final @NotNull String LATENCY_PROPERTY = "sc4j.commandFeedLatency";
    private static final int DEFAULT_FEED_LATENCY = 3;

    /**
     * Creates a {@link CommandFeed} instance from the replay named by the system property.
     *
     * @param installLayout the selected client directories
     * @return the created {@link CommandFeed} instance, or {@code null} when the property is not set
     * @throws IOException if the replay cannot be read
     */
    public static @Nullable CommandFeed fromProperty(@NotNull InstallLayout installLayout) throws IOException {
        String path = System.getProperty(PROPERTY);

        if (path == null || path.isBlank()) {
            return null;
        }

        ReplayFile replay = ReplayFile.parse(Files.readAllBytes(installLayout.resolveUserPath(Path.of(path))));

        LOGGER.info("COMMAND FEED: " + path + ", " + replay.getCommands().size() + " commands, seed 0x" +
                    Long.toHexString(replay.getHeader().getRandomSeed()));

        return new CommandFeed(replay, Integer.getInteger(LATENCY_PROPERTY, DEFAULT_FEED_LATENCY));
    }

    private final @NotNull ReplayFile replay;
    private final @NotNull List<ReplayCommand> commands;
    private final int latency;

    private @Nullable ClientSession session;
    private int nextCommandIndex;

    /**
     * Creates a {@link CommandFeed} instance for the recorded commands and turn latency.
     *
     * @param replay  the recorded commands
     * @param latency the turn latency in frames
     */
    private CommandFeed(@NotNull ReplayFile replay, int latency) {
        this.replay   = replay;
        this.commands = replay.getCommands();
        this.latency  = latency;

        this.session          = null;
        this.nextCommandIndex = 0;
    }


    /**
     * @return the recorded game's seed
     */
    public int getSeed() {
        return (int) this.replay.getHeader().getRandomSeed();
    }

    /**
     * Attaches the feed to the started live game.
     *
     * @param session the live game
     */
    public void attach(@NotNull ClientSession session) {
        this.session = session;

        session.setTurnListener(this);
    }

    /**
     * Performs this operation on the given argument.
     *
     * @param frame the input argument
     * @implSpec Submits commands due in this turn and discards commands whose scheduled turn has passed.
     */
    @Override
    public void accept(long frame) {
        ClientSession target = this.session;

        if (target == null) {
            return;
        }

        int player = target.getLocalPlayer();

        while (this.nextCommandIndex < this.commands.size() && this.commands.get(this.nextCommandIndex).getFrame() - this.latency <= frame) {
            ReplayCommand command = this.commands.get(this.nextCommandIndex++);

            if (command.getFrame() - this.latency < frame) {
                continue;
            }

            target.submitLocalCommand(player, command.getTypeId(), command.getPayload());
        }
    }
}
