package org.fogofwarlabs.sc4j.scr.game.presentation;

import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.casc.CascStorage;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks parsed overlay state and the rejection of unsupported definition changes.
 */
@RequiresInstallation
final class ObserverOverlayTest {
    /**
     * The installed observer overlay definition.
     */
    private static final @NotNull String OVERLAY_DEFINITION = "SD/rez/BroadcastObserver.ui.json";
    /**
     * The record identifier of the parsed overlay.
     */
    private static final int RECORD_IDENTIFIER = 1;
    /**
     * The record type of the parsed overlay.
     */
    private static final int RECORD_TYPE = 15;
    /**
     * The parsed visibility flags of the overlay.
     */
    private static final int PARSED_FLAGS = 2;
    /**
     * The initial and binding runtime flags of a parsed overlay: none.
     */
    private static final int NO_RUNTIME_FLAGS = 0;
    /**
     * The bit flipped in the first definition byte to alter the node.
     */
    private static final int ALTERED_BIT = 1;

    @Test
    void parse_should_separate_parsed_visibility_from_runtime_flags_and_reject_altered_node() throws IOException {
        try (CascStorage storage = CascStorage.open(ExternalInput.INSTALLATION.getPath())) {
            byte[] contents = storage.readFile(OVERLAY_DEFINITION);
            ObserverOverlay overlay = ObserverOverlay.parse(contents);

            Assertions.assertEquals(RECORD_IDENTIFIER, overlay.getRecordIdentifier());
            Assertions.assertEquals(RECORD_TYPE, overlay.getRecordType());
            Assertions.assertEquals(PARSED_FLAGS, overlay.getParsedFlags());
            Assertions.assertEquals(NO_RUNTIME_FLAGS, overlay.getInitialRuntimeFlags());
            Assertions.assertEquals(NO_RUNTIME_FLAGS, overlay.getBindingRuntimeFlags());

            contents[0] ^= ALTERED_BIT;

            Assertions.assertThrows(UnsupportedOperationException.class, () -> ObserverOverlay.parse(contents));
        }
    }
}
