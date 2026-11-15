package org.fogofwarlabs.sc4j.scr.client.ui.text;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Checks team, map and fallback force names.
 */
final class ForceNamesUtilsTest {
    private static final @NotNull Map<String, String> NETWORK = Map.of("strFORCE1", "세력 1", "strFORCE2", "세력 2", "strTEAM1", "1팀");

    @Test
    void resolve_force_name_should_prefer_map_force_name() {
        Assertions.assertEquals("Players", ForceNamesUtils.resolveForceName(NETWORK::get, false, "Players", 1));
    }

    @Test
    void resolve_force_name_should_fall_back_to_str_force_when_unnamed() {
        Assertions.assertEquals("세력 2", ForceNamesUtils.resolveForceName(NETWORK::get, false, null, 2));
        Assertions.assertEquals("세력 2", ForceNamesUtils.resolveForceName(NETWORK::get, false, "", 2), "A joiner's empty 0x4b name is no name.");
    }

    @Test
    void resolve_force_name_should_use_team_name_in_team_game() {
        Assertions.assertEquals("1팀", ForceNamesUtils.resolveForceName(NETWORK::get, true, "Players", 1));
    }
}
