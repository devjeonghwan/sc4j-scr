package org.fogofwarlabs.sc4j.scr.client.ui.status;

import org.fogofwarlabs.sc4j.scr.client.ClientReplayScenario;
import org.fogofwarlabs.sc4j.scr.client.ClientSession;
import org.fogofwarlabs.sc4j.scr.client.ClientSessionScenario;
import org.fogofwarlabs.sc4j.scr.client.ui.text.StatText;
import org.fogofwarlabs.sc4j.scr.client.world.ScrGameView;
import org.fogofwarlabs.sc4j.scr.content.ScrUnitIds;
import org.fogofwarlabs.sc4j.scr.core.test.ExternalInput;
import org.fogofwarlabs.sc4j.scr.core.test.RequiresInstallation;
import org.fogofwarlabs.sc4j.scr.data.format.dat.DatTable;
import org.fogofwarlabs.sc4j.scr.game.state.Game;
import org.fogofwarlabs.sc4j.scr.game.state.GameData;
import org.fogofwarlabs.sc4j.scr.game.test.GameTestResources;
import org.fogofwarlabs.sc4j.scr.game.unit.Unit;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Checks that the production queue and cancellation tooltips name the queued, researched or upgraded item.
 */
@RequiresInstallation
final class ProductionTooltipUtilsTest {
    private static final int FASTEST_SPEED = 6;
    private static final int CANCEL_LABEL = 805;
    private static final int EMPTY_QUEUE_ICON_FRAME = 8;
    private static final int REPLAY_START_FRAME = 6000;
    /**
     * upgrades.dat {@code 0}: Terran Infantry Armor.
     */
    private static final int INFANTRY_ARMOR = 0;
    private static final int UPGRADE_ICON_FRAME = 292;
    private static final @NotNull String LINE_BREAK = String.valueOf((char) 0x0A);
    private static final @NotNull String REPLAY = GameTestResources.REPLAY_TVT_BAEKDUDAEGAN;

    @Test
    void build_cancel_should_name_upgrade_in_progress() throws IOException {
        try (ClientSession session = ClientSession.startCampaign(ExternalInput.INSTALLATION.getPath(),
                                                                 "bw-terran-1", "Tester", FASTEST_SPEED, ClientSessionScenario.GAME_SEED,
                                                                 "enUS")) {
            StatText text = StatText.load(session.getStorage(), "enUS");
            StatusPanel.Icon icon = new StatusPanel.Icon(UPGRADE_ICON_FRAME, StatusPanel.Icon.UPGRADE, INFANTRY_ARMOR);
            Game game = session.getGame();
            GameData gameData = game.getGameData();

            Assertions.assertNotNull(gameData);

            String label = text.getString(new ScrGameView(game), gameData.getUpgrades().get("Label", INFANTRY_ARMOR));

            Assertions.assertEquals(text.getString(new ScrGameView(game), CANCEL_LABEL) + "\n(" + label + ")",
                                    ProductionTooltipUtils.buildCancel(new ScrGameView(game), text, icon));
            Assertions.assertEquals("Cancel Upgrade\n(Terran Infantry Armor)",
                                    StatText.stripColors(ProductionTooltipUtils.buildCancel(new ScrGameView(game), text, icon)));
        }
    }

    @Test
    void production_tooltips_name_canceled_item() throws IOException {
        try (ClientSession session = ClientReplayScenario.start(REPLAY, REPLAY_START_FRAME)) {
            Game game = session.getGame();
            StatText text = StatText.load(session.getStorage(), "enUS");
            Unit commandCenter = ClientReplayScenario.findCompletedUnits(game, 0, ScrUnitIds.TERRAN_COMMAND_CENTER).getFirst();
            StatusPanel.Icon worker = new StatusPanel.Icon(ScrUnitIds.TERRAN_SCV, StatusPanel.Icon.UNIT, ScrUnitIds.TERRAN_SCV);
            StatusPanel.Icon emptySlot = new StatusPanel.Icon(EMPTY_QUEUE_ICON_FRAME, StatusPanel.Icon.EMPTY_QUEUE_SLOT, 0);

            Assertions.assertEquals("Cancel Construction (" + text.getUnitName(ScrUnitIds.TERRAN_SCV) + ")",
                                    ProductionTooltipUtils.buildQueue(new ScrGameView(game), text, commandCenter, worker));
            Assertions.assertEquals("", ProductionTooltipUtils.buildQueue(new ScrGameView(game), text, commandCenter, emptySlot));

            int technologyIdentifier = 0;
            String research = ProductionTooltipUtils.buildCancel(new ScrGameView(game), text,
                                                                 new StatusPanel.Icon(0, StatusPanel.Icon.TECHNOLOGY, technologyIdentifier));

            GameData gameData = game.getGameData();

            Assertions.assertNotNull(gameData);

            DatTable technologies = gameData.getTechdata();

            Assertions.assertNotNull(technologies);
            Assertions.assertEquals("Cancel Research" + LINE_BREAK + "(" + text.getString(technologies.get("Label", technologyIdentifier)) + ")",
                                    research);

            String upgrade = ProductionTooltipUtils.buildCancel(new ScrGameView(game), text, new StatusPanel.Icon(0, StatusPanel.Icon.UPGRADE, 0));

            Assertions.assertTrue(upgrade.startsWith("Cancel Upgrade" + LINE_BREAK + "("), upgrade);
        }
    }
}
