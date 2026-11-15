package org.fogofwarlabs.sc4j.scr.client.glue.popup;

import org.fogofwarlabs.sc4j.scr.client.glue.GlueContext;
import org.fogofwarlabs.sc4j.scr.client.glue.GluePopup;
import org.fogofwarlabs.sc4j.scr.client.glue.campaign.CampaignEpisodes;
import org.fogofwarlabs.sc4j.scr.client.ui.dialog.DialogPainter;
import org.fogofwarlabs.sc4j.scr.client.ui.dialog.ListBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Lists unlocked campaign entries in table order and returns the chosen entry or cancellation.
 */
public final class GlueMissionPopup extends GluePopup {
    private static final @NotNull String DIALOG = "gluHist";
    private static final int OK = 1;
    private static final int CANCEL = 3;
    private static final int LIST = 6;
    private static final int FIRST_ROW = 0;
    private static final @NotNull String PANEL = "glue/campaign/pHist.pcx";

    private final @NotNull List<CampaignEpisodes.Entry> entries;
    private final @NotNull Consumer<CampaignEpisodes.@Nullable Entry> result;

    private @Nullable ListBox list;

    /**
     * Creates a {@link GlueMissionPopup} instance.
     *
     * @param paletteFolder the palette folder of the screen below
     * @param entries       the unlocked entries, in table order
     * @param result        receives the chosen entry on Ok, or {@code null} on Cancel
     */
    public GlueMissionPopup(@NotNull String paletteFolder,
                            @NotNull List<CampaignEpisodes.Entry> entries,
                            @NotNull Consumer<CampaignEpisodes.@Nullable Entry> result) {
        super(DIALOG, paletteFolder, PANEL);

        this.entries = List.copyOf(entries);
        this.result  = result;

        this.list = null;
    }

    /**
     * Called after the dialog is loaded: hide, disable or fill controls here.
     * <p>
     * The popup lists the entries' mission names over the panel and selects the first one.
     *
     * @param glueContext the shared services
     */
    @Override
    protected void onOpen(@NotNull GlueContext glueContext) {
        ListBox missions = this.addListBox(LIST);

        missions.setTransparent(true);

        for (CampaignEpisodes.Entry entry : this.entries) {
            missions.addItem(glueContext.getMissionNames().getMissionName(entry.getIdentifier()), DialogPainter.CODE_BUTTON);
        }

        missions.select(this.entries.isEmpty() ? ListBox.NO_SELECTION : FIRST_ROW, missions.getVisibleRows(glueContext.getCanvas()));
        this.list = missions;
    }

    /**
     * Called when a control is activated by a click, its hotkey, Esc or Enter.
     *
     * @param control the control id
     */
    @Override
    protected void onActivate(int control) {
        if (control == OK) {
            int selection = this.list == null ? ListBox.NO_SELECTION : this.list.getSelection();

            if (selection == ListBox.NO_SELECTION) {
                return;
            }

            this.finish();
            this.result.accept(this.entries.get(selection));
        } else if (control == CANCEL) {
            this.finish();
            this.result.accept(null);
        }
    }
}
