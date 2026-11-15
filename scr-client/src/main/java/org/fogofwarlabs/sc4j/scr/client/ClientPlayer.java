package org.fogofwarlabs.sc4j.scr.client;

import org.fogofwarlabs.sc4j.engine.net.beaconlink.BeaconLink;
import org.fogofwarlabs.sc4j.engine.net.beaconlink.LinkAccount;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Names the player of the run on every screen: the account the launcher chose for the run, or a guest for a run without an account (a run
 * started without the launcher). The menus show the account as {@code Name#tag}; games, lobbies and Beacon.Link show its name. The player's
 * campaign progress and saved games live in a folder named after the account id. A guest plays as
 * {@value #GUEST_NAME} with its own folder and has no Beacon.Link.
 */
public final class ClientPlayer {
    /**
     * The name of a guest.
     */
    public static final @NotNull String GUEST_NAME = BeaconLink.GUEST_NAME;

    private static final @NotNull String GUEST_FOLDER_NAME = "guest";

    /**
     * Creates a {@link ClientPlayer} instance for the run's account.
     *
     * @param account the run's account, or {@code null} for a guest
     * @return the created {@link ClientPlayer} instance
     */
    public static @NotNull ClientPlayer of(@Nullable LinkAccount account) {
        return account == null ? new ClientPlayer(GUEST_NAME, GUEST_NAME, GUEST_FOLDER_NAME)
                               : new ClientPlayer(account.getName(), account.getTaggedName(), account.getId());
    }


    private final @NotNull String name;
    private final @NotNull String taggedName;
    private final @NotNull String folderName;

    private ClientPlayer(@NotNull String name, @NotNull String taggedName, @NotNull String folderName) {
        this.name       = name;
        this.taggedName = taggedName;
        this.folderName = folderName;
    }

    /**
     * @return the player's name in games, lobbies and Beacon.Link: the account name, or {@value #GUEST_NAME}
     */
    public @NotNull String getName() {
        return this.name;
    }

    /**
     * @return the name the menus show: the account name with its tag ({@code Name#abcd12}), or {@value #GUEST_NAME}
     */
    public @NotNull String getTaggedName() {
        return this.taggedName;
    }

    /**
     * @return the file name of the player's character file and saved-game folder: the account id, or {@code guest}
     */
    public @NotNull String getFolderName() {
        return this.folderName;
    }
}
