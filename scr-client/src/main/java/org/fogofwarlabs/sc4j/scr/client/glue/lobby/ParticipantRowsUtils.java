package org.fogofwarlabs.sc4j.scr.client.glue.lobby;

import org.fogofwarlabs.sc4j.engine.net.beaconlink.community.PlayerPresence;
import org.fogofwarlabs.sc4j.engine.net.beaconlink.community.PlayerStatus;
import org.fogofwarlabs.sc4j.scr.client.glue.GlueCanvas;
import org.fogofwarlabs.sc4j.scr.client.ui.dialog.WebPanelStyleUtils;
import org.fogofwarlabs.sc4j.scr.render.ui.TrueTypeText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Locale;

/**
 * Draws the rows of the pages' participant lists ({@code ParticipantsPanel}, {@code ChatAddFriendPanel}): a group's label ({@code .groupLabel}:
 * {@code eurostile-reg} {@code 9} px in {@code #4cc428}, border {@code 1} and padding {@code 4}; the {@code -} or {@code +} symbol in its
 * {@code 8} px column, {@code 4} before the name; the count in brackets {@code 4} after it) and a member's row ({@code .accountHolder}:
 * {@code 24} high, border {@code 1} and padding {@code 2}, its group's rows {@code 16} in). A member's row holds the {@code 18} px portrait, then
 * {@code 8} px further the name ({@code .toonName}: {@code 8} px {@code #e5e5e5}, {@code #4cc428} while selected) with a second name in brackets
 * in gray, and either a second line with the status icon ({@code 6} px) and text ({@code 7} px) or nothing; the mod right on the last line (gray
 * for a mod the player cannot play with) and the latency bars right on the first. The selected row has a {@code 1} px {@code #090} border on a
 * dark green fill ({@code .accountHolder.selected}); an offline member's row is dimmed ({@code .offlineMembers .expanded}: opacity
 * {@code .7}). The channel chat's member and friend lists and the add-friend popup share them.
 * <p>
 * A member's context menu ({@code .contextMenu}: {@code 100} wide on black) shows the member's name ({@code .nameHolder}: a {@code 1} px
 * {@code #090} border without its bottom, padding {@code 4}, the name centered in {@code 8} px {@code #abcde2}) over one button per action
 * ({@code .contextMenu button}: {@code 8} px {@code #4cc428}, padding {@code 4}, a {@code 1} px {@code #090} border shared with the next
 * button; hovered {@code #b4fc74} with that border on a dark green fill).
 */
final class ParticipantRowsUtils {
    /**
     * The height of a group's label and of a member's row.
     */
    static final float ROW_HEIGHT = 24;
    /**
     * The color of a member's name and status.
     */
    static final int TEXT_COLOR = 0xE5E5E5FF;
    /**
     * The space before a row's first content: a group label's border and padding before its symbol; the channel list's rows share it.
     */
    static final float ROW_INSET = 5;

    /**
     * The color of an offline member's name and status: the page's text at opacity {@code .7}.
     */
    private static final int OFFLINE_COLOR = 0xA0A0A0FF;
    private static final float GROUP_TEXT_SIZE = 9;
    private static final int GROUP_TEXT = WebPanelStyleUtils.getFontSlot(TrueTypeText.WEB_REGULAR_FAMILY, GROUP_TEXT_SIZE,
                                                                         WebPanelStyleUtils.NORMAL_LINE_HEIGHT);
    private static final float NAME_TEXT_SIZE = 8;
    private static final float NAME_LINE = 9;
    private static final int NAME_TEXT = WebPanelStyleUtils.getFontSlot(TrueTypeText.WEB_REGULAR_FAMILY, NAME_TEXT_SIZE, NAME_LINE);
    private static final float STATUS_TEXT_SIZE = 7;
    private static final float STATUS_LINE = 9;
    private static final int STATUS_TEXT = WebPanelStyleUtils.getFontSlot(TrueTypeText.WEB_REGULAR_FAMILY, STATUS_TEXT_SIZE, STATUS_LINE);
    private static final @NotNull String GROUP_COUNT_FORMAT = "(%d)";
    private static final @NotNull String EXPANDED_SYMBOL = "-";
    private static final @NotNull String FOLDED_SYMBOL = "+";
    private static final @NotNull String ALIAS_FORMAT = "[%s]";
    private static final @NotNull String MOD_SEPARATOR = " ";
    /**
     * A group label's symbol column ({@link #ROW_INSET} in), the space after it and before the count.
     */
    private static final float SYMBOL_WIDTH = 8;
    private static final float SYMBOL_GAP = 4;
    private static final float COUNT_GAP = 4;
    /**
     * A member row's insets: the group's indent, the border and the padding, the portrait and the space before the names.
     */
    private static final float MEMBER_INDENT = 16;
    private static final float MEMBER_INSET = 3;
    private static final float PORTRAIT_SIZE = 18;
    private static final float INFO_GAP = 8;
    private static final float NAME_GAP = 4;
    private static final float STATUS_ICON_SIZE = 6;
    private static final float STATUS_ICON_GAP = 2;
    /**
     * The space between the right columns: the mod and the latency bars.
     */
    private static final float COLUMN_GAP = 4;
    private static final int ALIAS_COLOR = 0x808080FF;
    private static final int SELECTED_BORDER = 0x009900FF;
    private static final int SELECTED_FILL = 0x002A0099;
    private static final int CENTER_DIVISOR = 2;
    /**
     * The portraits of an online and an offline member, and the status icons ({@code .online}, {@code .away}, {@code .offline}).
     */
    private static final @NotNull String ONLINE_PORTRAIT = "App.png";
    private static final @NotNull String OFFLINE_PORTRAIT = "App_Offline.png";
    private static final @NotNull String ONLINE_ICON = "icon_presence_online.png";
    private static final @NotNull String AWAY_ICON = "icon_presence_away.png";
    private static final @NotNull String OFFLINE_ICON = "icon_presence_offline.png";
    /**
     * The context menu: its width, the name's and a button's height (border, padding, line, padding) and colors.
     */
    private static final float MENU_WIDTH = 100;
    private static final float MENU_PADDING = 4;
    private static final float MENU_LINE = 9;
    private static final float MENU_ROW_HEIGHT = 1 + MENU_PADDING + MENU_LINE + MENU_PADDING;
    private static final int MENU_NAME_COLOR = 0xABCDE2FF;
    private static final int MENU_HOVERED_FILL = 0x0A2A0099;
    private static final int NO_MENU_ITEM = -1;

    /**
     * Returns the mod of a member as the rows show it.
     *
     * @param presence the member's presence
     * @return the mod id and version
     */
    static @NotNull String getModText(@NotNull PlayerPresence presence) {
        return presence.getModId() + MOD_SEPARATOR + presence.getModVersion();
    }

    /**
     * Returns the status icon of a member.
     *
     * @param presence the member's presence, or {@code null} for an offline member
     * @return the icon's image name in the pages' images
     */
    static @NotNull String getStatusIcon(@Nullable PlayerPresence presence) {
        if (presence == null) {
            return OFFLINE_ICON;
        }

        return presence.getStatus() == PlayerStatus.AWAY ? AWAY_ICON : ONLINE_ICON;
    }

    /**
     * Draws a group's label: the symbol (folded or open), the name and the member count.
     *
     * @param canvas   the glue canvas
     * @param expanded whether the group shows its members
     * @param name     the group's name
     * @param count    the group's member count
     * @param left     the row's left edge in interface pixels
     * @param top      the row's top edge in interface pixels
     * @param width    the row's width
     */
    static void drawGroupLabel(@NotNull GlueCanvas canvas, boolean expanded, @NotNull String name, int count, float left, float top, float width) {
        float symbolLeft = left + ROW_INSET;
        float nameLeft = symbolLeft + SYMBOL_WIDTH + SYMBOL_GAP;
        String countText = String.format(Locale.ROOT, GROUP_COUNT_FORMAT, count);
        float countWidth = canvas.measure(GROUP_TEXT, countText);
        String fittedName = WebPanelStyleUtils.fitText(canvas, GROUP_TEXT, name, left + width - ROW_INSET - countWidth - COUNT_GAP - nameLeft);
        float countLeft = nameLeft + canvas.measure(GROUP_TEXT, fittedName) + COUNT_GAP;

        WebPanelStyleUtils.drawCenteredLine(canvas,
                                            GROUP_TEXT,
                                            expanded ? EXPANDED_SYMBOL : FOLDED_SYMBOL,
                                            symbolLeft, top,
                                            SYMBOL_WIDTH, ROW_HEIGHT,
                                            GlueCanvas.FORMAT_CENTER,
                                            WebPanelStyleUtils.BUTTON_TEXT);
        WebPanelStyleUtils.drawCenteredLine(canvas,
                                            GROUP_TEXT,
                                            fittedName,
                                            nameLeft, top,
                                            countLeft - nameLeft, ROW_HEIGHT,
                                            GlueCanvas.FORMAT_LEFT,
                                            WebPanelStyleUtils.BUTTON_TEXT);
        WebPanelStyleUtils.drawCenteredLine(canvas,
                                            GROUP_TEXT,
                                            countText,
                                            countLeft, top,
                                            countWidth + 1, ROW_HEIGHT,
                                            GlueCanvas.FORMAT_LEFT,
                                            WebPanelStyleUtils.BUTTON_TEXT);
    }

    /**
     * Draws a name line: the name, then the second name in brackets in gray, both cut to the line's room.
     *
     * @param canvas    the glue canvas
     * @param name      the name
     * @param alias     the second name, or an empty string for none
     * @param nameColor the packed color of the name
     * @param left      the line's left edge in interface pixels
     * @param top       the line's top edge in interface pixels
     * @param right     the line's right edge in interface pixels
     */
    static void drawNames(@NotNull GlueCanvas canvas, @NotNull String name, @NotNull String alias, int nameColor, float left, float top, float right) {
        String fittedName = WebPanelStyleUtils.fitText(canvas, NAME_TEXT, name, right - left);
        float nameWidth = canvas.measure(NAME_TEXT, fittedName);
        float aliasLeft = left + nameWidth + NAME_GAP;

        WebPanelStyleUtils.drawCenteredLine(canvas, NAME_TEXT, fittedName, left, top, nameWidth + 1, NAME_LINE, GlueCanvas.FORMAT_LEFT, nameColor);

        if (!alias.isEmpty() && aliasLeft < right) {
            WebPanelStyleUtils.drawCenteredLine(canvas,
                                                NAME_TEXT,
                                                WebPanelStyleUtils.fitText(canvas, NAME_TEXT, String.format(Locale.ROOT, ALIAS_FORMAT, alias),
                                                                           right - aliasLeft),
                                                aliasLeft, top,
                                                right - aliasLeft, NAME_LINE,
                                                GlueCanvas.FORMAT_LEFT,
                                                ALIAS_COLOR);
        }
    }

    /**
     * Draws a member's row: the selection, the portrait, the name and the second name, the status line, the mod and the latency bars, each
     * cut to its room.
     *
     * @param canvas     the glue canvas
     * @param left       the row's left edge in interface pixels
     * @param top        the row's top edge in interface pixels
     * @param width      the row's width
     * @param selected   whether the row is the selected one
     * @param online     whether the member is online (an offline row is dimmed)
     * @param name       the member's name
     * @param alias      the second name shown in brackets after the name, or an empty string for none
     * @param nameColor  the packed color of the name while the row is not selected
     * @param statusIcon the status icon's image name ({@link #getStatusIcon}), or {@code null} for a row without a status line
     * @param status     the status text of the status line
     * @param mod        the member's mod, or an empty string for none
     * @param playable   whether this player can play with the member's mod
     * @param bars       the latency bar count ({@link LatencyBarsUtils#getBarCount}), {@code 0} for none
     * @throws IOException if an image cannot be read
     */
    static void drawMember(@NotNull GlueCanvas canvas,
                           float left, float top, float width,
                           boolean selected,
                           boolean online,
                           @NotNull String name, @NotNull String alias, int nameColor,
                           @Nullable String statusIcon, @NotNull String status,
                           @NotNull String mod, boolean playable,
                           int bars) throws IOException {
        if (selected) {
            WebPanelStyleUtils.drawBox(canvas, left, top, width, ROW_HEIGHT, 0, SELECTED_BORDER, SELECTED_FILL, false, false, true, true);
        }

        float portraitLeft = left + MEMBER_INDENT + MEMBER_INSET;
        float textLeft = portraitLeft + PORTRAIT_SIZE + INFO_GAP;
        float textRight = left + width - MEMBER_INSET;
        boolean twoLines = statusIcon != null;
        float firstTop = top + (ROW_HEIGHT - (twoLines ? NAME_LINE + STATUS_LINE : NAME_LINE)) / CENTER_DIVISOR;
        float secondTop = firstTop + NAME_LINE;
        float barsLeft = textRight - LatencyBarsUtils.WIDTH;
        float nameRight = bars > 0 ? barsLeft - COLUMN_GAP : textRight;
        int textColor = online ? TEXT_COLOR : OFFLINE_COLOR;
        int modText = twoLines ? STATUS_TEXT : NAME_TEXT;

        canvas.drawImage(canvas.getWebImage(online ? ONLINE_PORTRAIT : OFFLINE_PORTRAIT),
                         portraitLeft, top + (ROW_HEIGHT - PORTRAIT_SIZE) / CENTER_DIVISOR,
                         PORTRAIT_SIZE, PORTRAIT_SIZE);

        // The mod shares the name's line in a one-line row and the status's line otherwise; it takes at most half of its line.
        float modRight = twoLines ? textRight : nameRight;
        String fittedMod = mod.isEmpty() ? "" : WebPanelStyleUtils.fitText(canvas, modText, mod, (modRight - textLeft) / CENTER_DIVISOR);
        float modWidth = fittedMod.isEmpty() ? 0 : canvas.measure(modText, fittedMod) + COLUMN_GAP;

        if (!twoLines) {
            nameRight -= modWidth;
        }

        drawNames(canvas, name, alias, selected ? WebPanelStyleUtils.BUTTON_TEXT : online ? nameColor : OFFLINE_COLOR, textLeft, firstTop, nameRight);

        if (twoLines) {
            float statusLeft = textLeft + STATUS_ICON_SIZE + STATUS_ICON_GAP;

            canvas.drawImage(canvas.getWebImage(statusIcon),
                             textLeft, secondTop + (STATUS_LINE - STATUS_ICON_SIZE) / CENTER_DIVISOR,
                             STATUS_ICON_SIZE, STATUS_ICON_SIZE);
            WebPanelStyleUtils.drawCenteredLine(canvas,
                                                STATUS_TEXT,
                                                WebPanelStyleUtils.fitText(canvas, STATUS_TEXT, status, modRight - modWidth - statusLeft),
                                                statusLeft, secondTop,
                                                modRight - modWidth - statusLeft, STATUS_LINE,
                                                GlueCanvas.FORMAT_LEFT,
                                                textColor);
        }

        if (!fittedMod.isEmpty()) {
            WebPanelStyleUtils.drawCenteredLine(canvas,
                                                modText,
                                                fittedMod,
                                                modRight - modWidth, twoLines ? secondTop : firstTop,
                                                modWidth, twoLines ? STATUS_LINE : NAME_LINE,
                                                GlueCanvas.FORMAT_RIGHT,
                                                playable && online ? textColor : WebPanelStyleUtils.DISABLED_TEXT);
        }

        if (bars > 0) {
            LatencyBarsUtils.draw(canvas, bars, barsLeft, firstTop, NAME_LINE);
        }
    }


    /**
     * Returns the height of a member's context menu.
     *
     * @param itemCount the number of its buttons
     * @return the height: the name and the buttons, with the last button's bottom border
     */
    static float getMenuHeight(int itemCount) {
        return (itemCount + 1) * MENU_ROW_HEIGHT + 1;
    }

    /**
     * @return the width of a member's context menu
     */
    static float getMenuWidth() {
        return MENU_WIDTH;
    }

    /**
     * Finds the button of a member's context menu under a point.
     *
     * @param x         the point's x
     * @param y         the point's y
     * @param left      the menu's left edge
     * @param top       the menu's top edge
     * @param itemCount the number of its buttons
     * @return the button's index, or {@code -1} if no button is under the point
     */
    static int findMenuItem(float x, float y, float left, float top, int itemCount) {
        float itemsTop = top + MENU_ROW_HEIGHT;

        if (!GlueCanvas.contains(x, y, left, itemsTop, MENU_WIDTH, itemCount * MENU_ROW_HEIGHT)) {
            return NO_MENU_ITEM;
        }

        return Math.min(itemCount - 1, (int) ((y - itemsTop) / MENU_ROW_HEIGHT));
    }

    /**
     * Draws a member's context menu: the member's name over the buttons, the hovered one highlighted.
     *
     * @param canvas  the glue canvas
     * @param name    the member's name
     * @param items   the buttons' labels
     * @param hovered the hovered button's index, or {@code -1} for none
     * @param left    the menu's left edge in interface pixels
     * @param top     the menu's top edge in interface pixels
     */
    static void drawMenu(@NotNull GlueCanvas canvas, @NotNull String name, @NotNull String @NotNull [] items, int hovered, float left, float top) {
        float textLeft = left + 1 + MENU_PADDING;
        float textWidth = MENU_WIDTH - 2 * (1 + MENU_PADDING);

        canvas.fillRectangle(left, top, MENU_WIDTH, getMenuHeight(items.length), WebPanelStyleUtils.BACKGROUND);
        WebPanelStyleUtils.drawBox(canvas, left, top, MENU_WIDTH, MENU_ROW_HEIGHT + 1, 0, SELECTED_BORDER, 0, false, false, true, false);
        WebPanelStyleUtils.drawCenteredLine(canvas,
                                            NAME_TEXT,
                                            WebPanelStyleUtils.fitText(canvas, NAME_TEXT, name, textWidth),
                                            textLeft, top + 1 + MENU_PADDING,
                                            textWidth, MENU_LINE,
                                            GlueCanvas.FORMAT_CENTER,
                                            MENU_NAME_COLOR);

        for (int index = 0; index < items.length; index++) {
            float itemTop = top + (index + 1) * MENU_ROW_HEIGHT;
            boolean highlighted = index == hovered;

            WebPanelStyleUtils.drawBox(canvas,
                                       left, itemTop,
                                       MENU_WIDTH, MENU_ROW_HEIGHT + 1,
                                       0,
                                       highlighted ? WebPanelStyleUtils.HIGHLIGHT_TEXT : SELECTED_BORDER,
                                       highlighted ? MENU_HOVERED_FILL : 0,
                                       false, false,
                                       true, true);
            WebPanelStyleUtils.drawCenteredLine(canvas,
                                                NAME_TEXT,
                                                WebPanelStyleUtils.fitText(canvas, NAME_TEXT, items[index], textWidth),
                                                textLeft, itemTop + 1 + MENU_PADDING,
                                                textWidth, MENU_LINE,
                                                GlueCanvas.FORMAT_CENTER,
                                                highlighted ? WebPanelStyleUtils.HIGHLIGHT_TEXT : WebPanelStyleUtils.BUTTON_TEXT);
        }
    }

    private ParticipantRowsUtils() {
    }
}
