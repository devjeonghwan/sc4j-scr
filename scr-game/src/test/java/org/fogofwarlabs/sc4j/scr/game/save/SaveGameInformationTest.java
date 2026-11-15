package org.fogofwarlabs.sc4j.scr.game.save;

import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Checks the saved identity, random state and lobby-to-record slot mapping.
 */
final class SaveGameInformationTest {
    private static final int SAVING_LOBBY_SLOT = 0;

    private static final int MULTIPLAYER_SAVE_GAME_ID = 0x6ABFE896;
    private static final int MULTIPLAYER_RANDOM_STATE = 0xCB6CA4CF;
    private static final int MULTIPLAYER_RECORD_SLOT = 1;

    private static final int TOM_AND_JERRY_SAVE_GAME_ID = 0x6ABFE919;
    private static final int TOM_AND_JERRY_RANDOM_STATE = 0x66AC893C;
    private static final int TOM_AND_JERRY_RECORD_SLOT = 3;

    @Test
    void read_should_return_identity_random_state_and_slots_from_native_saves() throws Exception {
        Path multiplayerPath = GameTestResources.getPath(GameTestResources.SAVE_MULTIPLAYER_WITH_COMPUTER);
        Path tomAndJerryPath = GameTestResources.getPath(GameTestResources.SAVE_TOM_AND_JERRY);

        byte[] multiplayerData = Files.readAllBytes(multiplayerPath);

        Assertions.assertTrue(SaveGameFile.isNativeFile(multiplayerData));

        SaveGameInformation multiplayer = SaveGameInformation.read(multiplayerData);

        Assertions.assertEquals(MULTIPLAYER_SAVE_GAME_ID, multiplayer.getSaveGameId());
        Assertions.assertEquals(MULTIPLAYER_RANDOM_STATE, multiplayer.getRandomState());
        Assertions.assertEquals(SAVING_LOBBY_SLOT, multiplayer.getLobbySlot());
        Assertions.assertEquals(MULTIPLAYER_RECORD_SLOT, multiplayer.getRecordSlot());

        SaveGameInformation tomAndJerry = SaveGameInformation.read(Files.readAllBytes(tomAndJerryPath));

        Assertions.assertEquals(TOM_AND_JERRY_SAVE_GAME_ID, tomAndJerry.getSaveGameId());
        Assertions.assertEquals(TOM_AND_JERRY_RANDOM_STATE, tomAndJerry.getRandomState());
        Assertions.assertEquals(SAVING_LOBBY_SLOT, tomAndJerry.getLobbySlot());
        Assertions.assertEquals(TOM_AND_JERRY_RECORD_SLOT, tomAndJerry.getRecordSlot());
    }
}
