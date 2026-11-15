package org.fogofwarlabs.sc4j.scr.client.ui.observer;

import org.fogofwarlabs.sc4j.engine.client.input.Key;
import org.fogofwarlabs.sc4j.engine.client.input.KeyModifier;
import org.fogofwarlabs.sc4j.scr.client.ui.text.GluText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Retains observer pane visibility and the pressed navigation icon.
 * Pane icons toggle their panes on release over the same icon; diplomacy and menu icons return actions for the caller.
 * A new instance shows every pane. Hotkeys use the same visibility and action transitions as icon activation.
 * Tooltip parts preserve header, connective and hotkey order while fitting the supplied text widths.
 */
public final class ReplaySideBar {
    private static final int BUTTON_COUNT = 8;
    private static final int END_MARGIN_COUNT = 2;

    /**
     * The page's icon size in CSS pixels (the {@code 640} pixel wide media block, {@code .navigationPanel .iconHolder .icon}).
     */
    public static final float ICON = 18;
    /**
     * The bar's width: the icons' left and right margins of {@code 4} around the icon.
     */
    public static final float WIDTH = 26;
    /**
     * The top margin of the first icon and the bottom margin of the last ({@code margin-top: 5.5px}).
     */
    public static final float END_MARGIN = 5.5f;
    /**
     * The left margin of every icon in the column.
     */
    public static final float ICON_LEFT = 4;
    /**
     * The distance between the tops of two icons: the icon and the margins of {@code 4} below and above.
     */
    public static final float PITCH = 26;
    /**
     * The bar's height: eight icons, seven gaps and the two end margins.
     */
    public static final float HEIGHT = END_MARGIN_COUNT * END_MARGIN + BUTTON_COUNT * ICON + (BUTTON_COUNT - 1) * (PITCH - ICON);
    /**
     * The bar's background ({@code .replayView .navigationPanel}, stretched to the bar).
     */
    public static final @NotNull String BACKGROUND_IMAGE = "NavigationBar.png";

    /**
     * The tooltip's content width ({@code min-width} and {@code max-width: 150px}).
     */
    public static final float TOOLTIP_WIDTH = 150;
    /**
     * The tooltip's padding.
     */
    public static final float TOOLTIP_PADDING = 5;
    /**
     * The tooltip's border width ({@code 1px solid #6c0000}).
     */
    public static final float TOOLTIP_BORDER = 1;
    /**
     * The tooltip's corner radius ({@code border-radius: 4px}).
     */
    public static final float TOOLTIP_RADIUS = 4;
    /**
     * The distance of the tooltip's right edge from the bar's right edge ({@code right: 30px}).
     */
    public static final float TOOLTIP_RIGHT = 30;
    /**
     * The tooltip's top relative to its icon's top ({@code margin-top: -6px}).
     */
    public static final float TOOLTIP_TOP = -6;
    /**
     * The tooltip's font size.
     */
    public static final float TOOLTIP_FONT = 8;
    /**
     * The gap after the header ({@code .tooltip_header} {@code margin-right: 2px}).
     */
    public static final float TOOLTIP_HEADER_GAP = 2;
    /**
     * The header and connective color ({@code .ToolTip} {@code color: #a2b1f5}).
     */
    public static final int TOOLTIP_TEXT = 0xA2B1F5FF;
    /**
     * The hotkey color ({@code .tooltip_hotkey} {@code color: #fff}).
     */
    public static final int TOOLTIP_HOTKEY = 0xFFFFFFFF;

    /**
     * A pane the bar shows and hides.
     */
    public enum Pane {
        /**
         * The production pane at the top left (replay only).
         */
        PRODUCTION,
        /**
         * The replay controls and progress panel.
         */
        REPLAY,
        /**
         * The minimap.
         */
        MINIMAP,
        /**
         * The selection panel.
         */
        UNIT,
        /**
         * The statistics pane at the top right.
         */
        STATISTICS,
        /**
         * The side bar itself ({@code R}, {@code Alt+W}).
         */
        NAVIGATION
    }

    /**
     * What a press asks of the game besides the bar's own panes.
     */
    public enum Action {
        /**
         * Nothing more: no button, or the bar changed a pane.
         */
        NONE,
        /**
         * A pane was shown or hidden.
         */
        TOGGLED,
        /**
         * Open the replay vision dialog.
         */
        OPEN_DIPLOMACY,
        /**
         * Open the game menu.
         */
        OPEN_MENU
    }

    /**
     * An icon of the bar, in page order.
     */
    public enum Button {
        /**
         * {@code .icon.factory}.
         */
        PRODUCTION(Pane.PRODUCTION, Action.TOGGLED, "production64_grey.png", "production64.png", "factory"),
        /**
         * {@code .icon.replay}.
         */
        REPLAY(Pane.REPLAY, Action.TOGGLED, "replay_disabled_64.png", "replay_hovergreen_64.png", "replay"),
        /**
         * {@code .icon.diplomacy}.
         */
        DIPLOMACY(null, Action.OPEN_DIPLOMACY, "diplomacy64.png", "diplomacy64.png", "diplomacy"),
        /**
         * {@code .icon.map}.
         */
        MAP(Pane.MINIMAP, Action.TOGGLED, "minimap64_grey.png", "minimap64.png", "map"),
        /**
         * {@code .icon.unit}.
         */
        UNIT(Pane.UNIT, Action.TOGGLED, "selecteduinit64_grey.png", "selecteduinit64.png", "unit"),
        /**
         * {@code .icon.stats}.
         */
        STATISTICS(Pane.STATISTICS, Action.TOGGLED, "playerstats64_grey.png", "playerstats64.png", "stats"),
        /**
         * {@code .icon.menu}.
         */
        MENU(null, Action.OPEN_MENU, "menu64.png", "menu64.png", "menu"),
        /**
         * {@code .icon.help}: no click handler, only its tooltip.
         */
        HELP(null, Action.NONE, "question64_grey.png", "question64_grey.png", "help");

        private final @Nullable Pane pane;
        private final @NotNull Action action;

        private final @NotNull String hiddenImage;
        private final @NotNull String shownImage;

        private final @NotNull String tooltipKey;

        Button(@Nullable Pane pane, @NotNull Action action, @NotNull String hiddenImage, @NotNull String shownImage, @NotNull String tooltipName) {
            this.pane   = pane;
            this.action = action;

            this.hiddenImage = hiddenImage;
            this.shownImage  = shownImage;

            this.tooltipKey = "observerScreen_tooltip_" + tooltipName;
        }

        /**
         * @return the pane the icon toggles, or {@code null} for an action icon
         */
        public @Nullable Pane getPane() {
            return this.pane;
        }

        /**
         * @return the {@code gluAll} key of the tooltip's text; the hotkeys are under the key with {@code _hotkey} appended
         */
        public @NotNull String getTooltipKey() {
            return this.tooltipKey;
        }

        /**
         * @param shown whether the icon's pane is shown
         * @return the icon image ({@code webui/dist/lib/images}): the color image of a shown pane, else the gray image
         */
        public @NotNull String getImage(boolean shown) {
            return shown ? this.shownImage : this.hiddenImage;
        }
    }

    /**
     * One word group of a tooltip ({@code .tooltip_header} or {@code .tooltip_hotkey}), kept on one line.
     */
    public static final class TooltipPart {
        private final @NotNull String text;
        private final int color;
        private final boolean header;

        /**
         * Creates a {@link TooltipPart} instance.
         *
         * @param text   the text
         * @param color  the packed {@code 0xRRGGBBAA} color
         * @param header whether the part is the header, followed by its {@link #TOOLTIP_HEADER_GAP}
         */
        public TooltipPart(@NotNull String text, int color, boolean header) {
            this.text   = text;
            this.color  = color;
            this.header = header;
        }

        /**
         * @return the text
         */
        public @NotNull String getText() {
            return this.text;
        }

        /**
         * @return the packed {@code 0xRRGGBBAA} color
         */
        public int getColor() {
            return this.color;
        }

        /**
         * @return {@code true} if the part is the header, {@code false} otherwise (a hotkey or the connective)
         */
        public boolean isHeader() {
            return this.header;
        }
    }

    /**
     * One laid out line of a tooltip ({@link #layoutTooltip}).
     */
    public static final class TooltipLine {
        private final @NotNull List<TooltipPart> parts;
        private float width;

        /**
         * Creates a {@link TooltipLine} instance without parts.
         */
        public TooltipLine() {
            this.parts = new ArrayList<>();
            this.width = 0;
        }

        /**
         * Creates a {@link TooltipLine} instance holding one part.
         *
         * @param part  the part
         * @param width the part's width
         */
        public TooltipLine(@NotNull TooltipPart part, float width) {
            this();
            this.add(part, width);
        }

        /**
         * @return the parts in reading order
         */
        public @NotNull List<TooltipPart> getParts() {
            return this.parts;
        }

        /**
         * @return the line's width, the header's gap included
         */
        public float getWidth() {
            return this.width;
        }

        private void add(@NotNull TooltipPart part, float width) {
            this.parts.add(part);

            this.width += width;
        }
    }

    /**
     * Builds a tooltip's parts as the page does: the header, then the hotkeys split at {@code |} joined by the connective
     * {@code observerScreen_tooltip_hotkey_or} between non-breaking spaces.
     *
     * @param button the icon
     * @param text   the {@code gluAll} strings
     * @return the parts in reading order
     */
    public static @NotNull List<TooltipPart> buildTooltip(@NotNull Button button, @NotNull GluText text) {
        List<TooltipPart> parts = new ArrayList<>();

        parts.add(new TooltipPart(text.get(button.getTooltipKey()), TOOLTIP_TEXT, true));

        String connective = " " + text.get("observerScreen_tooltip_hotkey_or") + " ";
        String[] hotkeys = text.get(button.getTooltipKey() + "_hotkey").split("\\|");

        for (int index = 0; index < hotkeys.length; index++) {
            if (index > 0) {
                parts.add(new TooltipPart(connective, TOOLTIP_TEXT, false));
            }

            parts.add(new TooltipPart(hotkeys[index], TOOLTIP_HOTKEY, false));
        }

        return parts;
    }

    /**
     * Lays a tooltip's parts out in lines as the page's inline blocks flow in the {@code 150} px box: a part goes after the line's earlier parts
     * when it fits, else it starts a new line; a header wider than the box wraps at its spaces into lines of its own, and the hotkeys start on
     * the line after it.
     *
     * @param parts        the parts of {@link #buildTooltip}
     * @param measure      the width of a text in the tooltip font
     * @param contentWidth the box's content width in the same units
     * @param headerGap    the gap after the header in the same units
     * @return the lines, each with its parts and width
     */
    public static @NotNull List<TooltipLine> layoutTooltip(@NotNull List<TooltipPart> parts, @NotNull ToDoubleFunction<String> measure,
                                                           float contentWidth, float headerGap) {
        List<TooltipLine> lines = new ArrayList<>();

        for (TooltipPart part : parts) {
            float width = (float) measure.applyAsDouble(part.getText()) + (part.isHeader() ? headerGap : 0);

            if (part.isHeader() && width > contentWidth) {
                StringBuilder row = new StringBuilder();

                for (String word : part.getText().split(" ")) {
                    String longer = row.isEmpty() ? word : row + " " + word;

                    if (!row.isEmpty() && measure.applyAsDouble(longer) > contentWidth) {
                        lines.add(new TooltipLine(new TooltipPart(row.toString(), part.getColor(), false),
                                                  (float) measure.applyAsDouble(row.toString())));
                        row.setLength(0);
                        row.append(word);
                    } else {
                        row.setLength(0);
                        row.append(longer);
                    }
                }

                lines.add(new TooltipLine(new TooltipPart(row.toString(), part.getColor(), false), (float) measure.applyAsDouble(row.toString())));
                lines.add(new TooltipLine());

                continue;
            }

            if (lines.isEmpty() || lines.getLast().getWidth() > 0 && lines.getLast().getWidth() + width > contentWidth) {
                lines.add(new TooltipLine());
            }

            lines.getLast().add(part, width);
        }

        if (!lines.isEmpty() && lines.getLast().getParts().isEmpty()) {
            lines.removeLast();
        }

        return lines;
    }

    /**
     * Returns the icon under a point of the bar.
     *
     * @param x the CSS x from the bar's left
     * @param y the CSS y from the bar's top
     * @return the icon, or {@code null} between icons and outside the bar
     */
    public static @Nullable Button findButton(float x, float y) {
        if (x < ICON_LEFT || x >= ICON_LEFT + ICON || y < END_MARGIN) {
            return null;
        }

        int index = (int) ((y - END_MARGIN) / PITCH);

        if (index >= Button.values().length || y - END_MARGIN - index * PITCH >= ICON) {
            return null;
        }

        return Button.values()[index];
    }

    /**
     * @param button the icon
     * @return the icon's top in CSS pixels from the bar's top
     */
    public static float getButtonTop(@NotNull Button button) {
        return END_MARGIN + button.ordinal() * PITCH;
    }

    private final @NotNull EnumSet<Pane> hidden;
    private @Nullable Button pressed;

    /**
     * Creates a {@link ReplaySideBar} instance for a new replay session: every pane shown.
     */
    public ReplaySideBar() {
        this.hidden  = EnumSet.noneOf(Pane.class);
        this.pressed = null;
    }

    /**
     * @param pane the pane
     * @return {@code true} if the pane is shown, {@code false} otherwise
     */
    public boolean isShown(@NotNull Pane pane) {
        return !this.hidden.contains(pane);
    }

    /**
     * Sets whether a pane is shown.
     *
     * @param pane  the pane
     * @param shown whether the pane is shown
     */
    public void setShown(@NotNull Pane pane, boolean shown) {
        if (shown) {
            this.hidden.remove(pane);
        } else {
            this.hidden.add(pane);
        }
    }

    /**
     * @return the icon pressed and not yet released, or {@code null}
     */
    public @Nullable Button getPressed() {
        return this.pressed;
    }


    /**
     * Starts a click on an icon: the page acts on the release over the same icon.
     *
     * @param button the icon under the press, or {@code null}
     */
    public void press(@Nullable Button button) {
        this.pressed = button;
    }

    /**
     * Ends a click: the icon's action runs when the release is over the pressed icon.
     *
     * @param button the icon under the release, or {@code null}
     * @return what the game does besides the bar's own panes
     */
    public @NotNull Action release(@Nullable Button button) {
        Button started = this.pressed;

        this.pressed = null;

        if (button == null || button != started) {
            return Action.NONE;
        }

        return this.activate(button);
    }

    /**
     * Runs an icon's action: a pane icon toggles its pane.
     *
     * @param button the icon
     * @return what the game does besides the bar's own panes
     */
    public @NotNull Action activate(@NotNull Button button) {
        Pane pane = button.getPane();

        if (pane != null) {
            this.setShown(pane, !this.isShown(pane));
        }

        return button.action;
    }

    /**
     * Applies a key as the page's hotkey handler does, first match wins: {@code E} (any modifiers) statistics; {@code Alt/Ctrl+F} production;
     * {@code Alt/Ctrl+I} or {@code Q} minimap; {@code Alt/Ctrl+U} or {@code W} without Alt unit panel; {@code Alt/Ctrl+W} or {@code R} the bar
     * itself; {@code A} every pane; {@code Alt/Ctrl+Z} diplomacy; {@code Alt/Ctrl+Y} replay panel. The page tests the left Alt key; the client's key
     * events carry one Alt bit.
     *
     * @param key       the key
     * @param modifiers the modifier mask ({@link KeyModifier#getMask()} bits)
     * @return what the game does: {@link Action#NONE} when the key is not the bar's
     */
    public @NotNull Action pressKey(@NotNull Key key, int modifiers) {
        boolean alt = KeyModifier.ALT.isIn(modifiers);
        boolean command = alt || KeyModifier.CONTROL.isIn(modifiers);

        if (key == Key.E) {
            return this.activate(Button.STATISTICS);
        }

        if (command && key == Key.F) {
            return this.activate(Button.PRODUCTION);
        }

        if (command && key == Key.I || key == Key.Q) {
            return this.activate(Button.MAP);
        }

        if (command && key == Key.U || key == Key.W && !alt) {
            return this.activate(Button.UNIT);
        }

        if (command && key == Key.W || key == Key.R) {
            this.setShown(Pane.NAVIGATION, !this.isShown(Pane.NAVIGATION));

            return Action.TOGGLED;
        }

        if (key == Key.A) {
            this.toggleAll();

            return Action.TOGGLED;
        }

        if (command && key == Key.Z) {
            return Action.OPEN_DIPLOMACY;
        }

        if (command && key == Key.Y) {
            return this.activate(Button.REPLAY);
        }

        return Action.NONE;
    }

    /**
     * Shows every pane, or hides every pane when all are shown.
     */
    public void toggleAll() {
        boolean show = !this.hidden.isEmpty();

        for (Pane pane : Pane.values()) {
            this.setShown(pane, show);
        }
    }
}
