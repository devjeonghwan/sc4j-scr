package org.fogofwarlabs.sc4j.scr.client.ui.dialog;

import org.fogofwarlabs.sc4j.engine.client.dialog.ComboBoxModel;
import org.fogofwarlabs.sc4j.engine.resource.dialog.DialogControl;
import org.fogofwarlabs.sc4j.scr.render.ui.InterfaceText;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Draws classic or web-style combo boxes over the engine {@link ComboBoxModel} (items, the items that can be chosen, the selection, the
 * open list, its direction and the hovered item); this class keeps each item's data value and colors, the native metrics and the look.
 * Classic rows use dialog sheet frames; web rows use shared web-panel colors, fonts and rounded boxes. The list opens upward when its
 * explicit web direction or the classic screen space requires it ({@link ComboBoxModel#isUpwardNeeded}).
 * <p>
 * A combo box is the SC:R control of its {@code COMBOBOX} definition: a screen attaches it in place of the control its dialog built
 * ({@link ScrDialog#attach}), and it draws its own bar ({@link #drawAt}) and open list ({@link #drawList}).
 */
public final class ComboBox extends ScrControl {
    /**
     * No selected item.
     */
    public static final int NO_SELECTION = ComboBoxModel.NO_SELECTION;

    private static final int BAR = 77;
    private static final int HOVERED_BAR = 80;
    private static final int OPEN_BAR = 71;
    private static final int ARROW = 74;
    private static final int HOVERED_ARROW = 75;
    private static final int DISABLED_ARROW = 76;
    private static final int LAST_ROW = 65;
    /**
     * The frame whose width is the bar tile (frame {@code 35} with the small font).
     */
    private static final int TILE = 59;
    private static final int ROW = 62;
    /**
     * The frame distance of the small font variants ({@code 53} against {@code 77}).
     */
    private static final int SMALL_FONT_OFFSET = 24;
    private static final int TEXT_INDENT = 4;
    private static final int ARROW_MARGIN = 4;
    private static final int LINE_GAP = 3;
    /**
     * An item drawn in the color code of its state.
     */
    private static final int NO_COLOR = 0;
    /**
     * The web drop-down's border, padding and arrow ({@code .selectedOption}, {@code .icon}).
     */
    private static final float WEB_BORDER = 1;
    private static final float WEB_PADDING_X = 2;
    private static final float WEB_ARROW = 11;
    private static final @NotNull String WEB_ARROW_IMAGE = "down_arrow_yellow_32.png";
    private static final @NotNull String WEB_ACTIVE_ARROW_IMAGE = "down_arrow_white_32.png";
    /**
     * The web list's rows ({@code .selectableOption}: the {@code 9} px line), its padding and the rows' side padding.
     */
    private static final float WEB_ROW = 9;
    private static final float WEB_LIST_PADDING = 1;
    private static final float WEB_ROW_PADDING_X = 2;
    private static final @NotNull String ELLIPSIS = "\u2026";

    /**
     * Holds one item's text, data value and colors.
     */
    private static final class Item {
        private final @NotNull String text;
        private final int value;
        private final int color;
        private final int hoverColor;

        private Item(@NotNull String text, int value, int color, int hoverColor) {
            this.text       = text;
            this.value      = value;
            this.color      = color;
            this.hoverColor = hoverColor;
        }
    }

    /**
     * Cuts a text to a width with an ellipsis ({@code text-overflow: ellipsis}).
     */
    private static @NotNull String fitWeb(@NotNull DialogPainter canvas, @NotNull String text, float width) {
        if (canvas.measure(WebPanelStyleUtils.DROPDOWN_TEXT, text) <= width) {
            return text;
        }

        String shown = text;

        while (!shown.isEmpty() && canvas.measure(WebPanelStyleUtils.DROPDOWN_TEXT, shown + ELLIPSIS) > width) {
            shown = shown.substring(0, shown.length() - 1);
        }

        return shown + ELLIPSIS;
    }

    /**
     * Returns the dialog sheet frame of the bar a closed small-text drop-down draws, the glue's drop-downs' bar: the plain or the hovered bar.
     * A glue screen's framed button or text field draws it as its frame.
     *
     * @param hovered whether the bar is the hovered one
     * @return the bar's first frame (its left end; the middle and the right end follow)
     */
    public static int getSmallTextBarFrame(boolean hovered) {
        return (hovered ? HOVERED_BAR : BAR) - SMALL_FONT_OFFSET;
    }

    /**
     * Returns the tile width of a small-text drop-down's bar ({@link #getSmallTextBarFrame}).
     *
     * @param canvas the dialog painter
     * @param sheet  the dialog sheet, for example {@code glue/palnl/dlg.dds.grp}
     * @return the width of the bar's tile
     * @throws IOException if the sheet cannot be read
     */
    public static int getSmallTextBarTile(@NotNull DialogPainter canvas, @NotNull String sheet) throws IOException {
        return canvas.getMetricWidth(sheet, TILE - SMALL_FONT_OFFSET);
    }

    private final @NotNull String sheetPath;
    private final @NotNull ComboBoxModel<Item> model;

    private boolean arrowVisible;
    private boolean webPanelStyle;
    private boolean opensUpward;
    /**
     * The closed web control label shown without a selected option, or an empty string.
     */
    private @NotNull String label;
    private @NotNull String placeholder;

    /**
     * Creates a {@link ComboBox} instance.
     *
     * @param control the dialog control
     * @param sheet   the dialog sheet whose frames draw the bars and arrows, for example {@code glue/palnl/dlg.dds.grp}
     */
    public ComboBox(@NotNull DialogControl control, @NotNull String sheet) {
        super(control);

        this.sheetPath = sheet;
        this.model     = new ComboBoxModel<>();

        this.arrowVisible  = true;
        this.webPanelStyle = false;
        this.opensUpward   = false;
        this.label         = "";
        this.placeholder   = "";
    }

    /**
     * Sets whether a web-list item can be chosen; disabled items draw dark text with a strike-through.
     *
     * @param index   the item index
     * @param enabled whether the item can be chosen
     */
    public void setItemEnabled(int index, boolean enabled) {
        this.model.setItemEnabled(index, enabled);
    }

    /**
     * @return the number of items
     */
    public int getItemCount() {
        return this.model.getItemCount();
    }

    /**
     * @param index the item index
     * @return the item's text
     */
    public @NotNull String getItemText(int index) {
        return this.model.getItem(index).text;
    }

    /**
     * @return the selected index, or {@link #NO_SELECTION}
     */
    public int getSelection() {
        return this.model.getSelection();
    }

    /**
     * @param fallback the value without a selection
     * @return the selected item's data value, or {@code fallback}
     */
    public int getSelectedValue(int fallback) {
        int selection = this.model.getSelection();

        return selection == NO_SELECTION ? fallback : this.model.getItem(selection).value;
    }

    /**
     * Sets whether the combo box takes input and draws enabled: the owner's dialog state, which the combo box takes from its own
     * control state when it draws as a control ({@link #drawAt}). A combo box that does not take input closes its list.
     *
     * @param enabled whether the combo box takes input
     */
    public void setInputEnabled(boolean enabled) {
        this.model.setInputEnabled(enabled);
    }

    /**
     * @return {@code true} if the item list is open, {@code false} otherwise
     */
    public boolean isOpen() {
        return this.model.isOpen();
    }

    /**
     * Sets whether the item list is open.
     *
     * @param open whether the list is open
     */
    public void setOpen(boolean open) {
        this.model.setOpen(open);
    }

    /**
     * Sets whether the item list is open; an open list that does not fit below the bar opens upward when there is more room above the control
     * than below it.
     *
     * @param canvas the glue canvas
     * @param open   whether the list is open
     * @param above  the room from the top of the screen down to the control's top, in interface pixels
     * @param below  the room from the control's top down to the bottom of the screen, in interface pixels
     * @throws IOException if the sheet cannot be read
     */
    public void setOpen(@NotNull DialogPainter canvas, boolean open, float above, float below) throws IOException {
        if (this.webPanelStyle) {
            this.model.setOpen(open, this.opensUpward);

            return;
        }

        float barHeight = this.getBarHeight(canvas);
        float listHeight = this.model.getItemCount() * this.getRowHeight(canvas);

        this.model.setOpen(open, ComboBoxModel.isUpwardNeeded(barHeight, listHeight, above, below));
    }

    /**
     * Sets whether the arrow is drawn.
     *
     * @param arrowVisible whether the arrow is drawn (a bar without a list, such as a team header, has none)
     */
    public void setArrowVisible(boolean arrowVisible) {
        this.arrowVisible = arrowVisible;
    }

    /**
     * Sets the web panel drawing style.
     *
     * @param webPanelStyle whether the combo box stands for a web page drop-down, drawn in the page's look (see the class comment)
     */
    public void setWebPanelStyle(boolean webPanelStyle) {
        this.webPanelStyle = webPanelStyle;
    }

    /**
     * Sets whether the popup opens upward.
     *
     * @param opensUpward whether the web drop-down's list opens above the box
     */
    public void setOpensUpward(boolean opensUpward) {
        this.opensUpward = opensUpward;
    }

    /**
     * Sets the value the closed web drop-down shows while no option is selected, in the normal text color: the slot name of the lobby's name lists,
     * whose options are only the actions.
     *
     * @param label the shown value, or an empty string for none
     */
    public void setLabel(@NotNull String label) {
        this.label = label;
    }

    /**
     * Sets the placeholder text.
     *
     * @param placeholder the text shown grayed while nothing is selected, or an empty string for none
     */
    public void setPlaceholder(@NotNull String placeholder) {
        this.placeholder = placeholder;
    }

    /**
     * @param canvas the glue canvas
     * @return the closed bar height in interface pixels
     * @throws IOException if the sheet cannot be read
     */
    public float getBarHeight(@NotNull DialogPainter canvas) throws IOException {
        if (this.webPanelStyle) {
            return this.getDefinition().getHeight();
        }

        return canvas.getSheetFrame(this.sheetPath, this.getFrameIndex(canvas, BAR)).getHeight();
    }

    private float getRowHeight(@NotNull DialogPainter canvas) throws IOException {
        if (this.webPanelStyle) {
            return WEB_ROW;
        }

        return Math.max(canvas.getLineHeight(canvas.getControlFontSlot(this.getDefinition())) - LINE_GAP,
                        canvas.getSheetFrame(this.sheetPath, this.getFrameIndex(canvas, BAR)).getHeight());
    }

    /**
     * @return the open list's top as an offset from the control top: below the bar, or above it when it opens upward
     */
    private float getListTop(@NotNull DialogPainter canvas) throws IOException {
        if (this.webPanelStyle) {
            return this.model.isUpward() ? -this.model.getItemCount() * WEB_ROW : this.getDefinition().getHeight() + WEB_LIST_PADDING;
        }

        return this.model.isUpward() ? -this.model.getItemCount() * this.getRowHeight(canvas) : this.getBarHeight(canvas);
    }

    /**
     * Sets the hovered item.
     *
     * @param index the item under the pointer, or {@link #NO_SELECTION}
     */
    public void setHoveredItem(int index) {
        this.model.setHoveredItem(index);
    }

    /**
     * Removes the items, clears the selection and closes the list.
     */
    public void clear() {
        this.model.clear();
    }

    /**
     * Appends an item.
     *
     * @param text  the item text
     * @param value the item's data value
     */
    public void addItem(@NotNull String text, int value) {
        this.addItem(text, value, NO_COLOR);
    }

    /**
     * Appends an item drawn in a color of its own while enabled.
     *
     * @param text  the item text
     * @param value the item's data value
     * @param color the packed {@code 0xRRGGBBAA} text color, or {@code 0} for the color codes
     */
    public void addItem(@NotNull String text, int value, int color) {
        this.addItem(text, value, color, color);
    }

    /**
     * Appends an item drawn in colors of its own while enabled (a web page color class: its color and its lighter {@code :hover} color).
     *
     * @param text       the item text
     * @param value      the item's data value
     * @param color      the packed {@code 0xRRGGBBAA} text color, or {@code 0} for the color codes
     * @param hoverColor the packed color while hovered or expanded
     */
    public void addItem(@NotNull String text, int value, int color, int hoverColor) {
        this.model.addItem(new Item(text, value, color, hoverColor));
    }

    /**
     * Selects the first item with a data value.
     *
     * @param value the data value
     * @return {@code true} if an item has the value, {@code false} otherwise (the selection is then cleared)
     */
    public boolean selectValue(int value) {
        int index = NO_SELECTION;

        for (int item = this.model.getItemCount() - 1; item >= 0; item--) {
            if (this.model.getItem(item).value == value) {
                index = item;
            }
        }

        this.model.select(index);

        return index != NO_SELECTION;
    }

    /**
     * Selects an item.
     *
     * @param index the selected index, or {@link #NO_SELECTION}
     */
    public void select(int index) {
        this.model.select(index);
    }

    /**
     * Takes the press of the open list, which comes before every other control: the item under it becomes the selection when it is
     * another one ({@link ComboBoxModel#chooseItem}), and the list closes.
     *
     * @param index the item under the press ({@link #findItem}), or {@link #NO_SELECTION}
     * @return {@code true} if the selection changed, {@code false} otherwise
     */
    public boolean pressOpenList(int index) {
        this.model.setOpen(false);

        return this.model.chooseItem(index);
    }

    private int getFrameIndex(@NotNull DialogPainter canvas, int frame) throws IOException {
        float fontHeight = canvas.getLineHeight(canvas.getControlFontSlot(this.getDefinition())) - LINE_GAP;

        return (fontHeight < canvas.getSheetFrame(this.sheetPath, BAR).getHeight())
               ? frame - SMALL_FONT_OFFSET
               : frame;
    }

    /**
     * Finds the open list's row at an offset from the control top.
     *
     * @param canvas  the glue canvas
     * @param offsetX the offset from the control left
     * @param offsetY the offset from the control top
     * @return the item index, or {@link #NO_SELECTION}
     * @throws IOException if the sheet cannot be read
     */
    public int findItem(@NotNull DialogPainter canvas, float offsetX, float offsetY) throws IOException {
        return this.model.findItem(offsetX, offsetY - this.getListTop(canvas), this.getDefinition().getWidth(), this.getRowHeight(canvas));
    }

    /**
     * Draws the control natively at a position: a plain control through its dialog's procedure painter ({@link ScrDialog#setPainter}),
     * nothing while it is not in an SC:R dialog with one; a widget subclass draws its own look.
     * <p>
     * The combo box draws its bar enabled while the control is active and hovered while the pointer is on it
     * ({@link #draw(DialogPainter, float, float, boolean)}).
     *
     * @param painter the SC:R dialog painter
     * @param x       the control's left edge in interface pixels, the shift included
     * @param y       the control's top edge in interface pixels, the vertical shift included
     * @throws IOException if the sheet cannot be read
     */
    @Override
    public void drawAt(@NotNull DialogPainter painter, float x, float y) throws IOException {
        this.setInputEnabled(this.isActive());
        this.draw(painter, x, y, this.isHovered());
    }

    /**
     * Draws the closed combo box.
     *
     * @param canvas  the glue canvas
     * @param left    the control left
     * @param top     the control top
     * @param hovered whether the pointer is over the control
     * @throws IOException if the sheet cannot be read
     */
    public void draw(@NotNull DialogPainter canvas, float left, float top, boolean hovered) throws IOException {
        if (this.webPanelStyle) {
            this.drawWeb(canvas, left, top, hovered);

            return;
        }

        boolean enabled = this.model.isInputEnabled();
        boolean open = this.model.isOpen();
        int selection = this.model.getSelection();
        int bar = open ? OPEN_BAR : hovered && enabled ? HOVERED_BAR : BAR;
        int arrow = !enabled ? DISABLED_ARROW : hovered && !open ? HOVERED_ARROW : ARROW;
        float width = this.getDefinition().getWidth();

        canvas.drawBar(this.sheetPath, this.getFrameIndex(canvas, bar),
                       left, top,
                       width,
                       canvas.getMetricWidth(this.sheetPath, this.getFrameIndex(canvas, TILE)));

        float textRight = left + width - 1;

        if (this.arrowVisible && (enabled || !this.webPanelStyle)) {
            DialogPainter.Frame arrowImage = canvas.getSheetFrame(this.sheetPath, this.getFrameIndex(canvas, arrow));

            textRight -= arrowImage.getWidth() + ARROW_MARGIN;

            canvas.drawFrame(arrowImage, textRight, top + ARROW_MARGIN);
        }

        float barHeight = this.getBarHeight(canvas);
        int format = ControlPainterUtils.ROW_TEXT_FORMAT;

        if (selection == NO_SELECTION) {
            // Without a selection the placeholder shows grayed (the web dropdown's "grayed" option, such as Select Race).
            if (!this.placeholder.isEmpty()) {
                canvas.drawCodeText(canvas.getControlFontSlot(this.getDefinition()),
                                    this.placeholder,
                                    left + TEXT_INDENT, top,
                                    textRight - left - TEXT_INDENT, barHeight,
                                    format,
                                    DialogPainter.CODE_DISABLED);
            }

            return;
        }

        int code = !enabled ? DialogPainter.CODE_DISABLED : hovered ? DialogPainter.CODE_HIGHLIGHT : DialogPainter.CODE_BUTTON;

        this.drawItemText(canvas,
                          selection,
                          left + TEXT_INDENT,
                          top,
                          textRight - left - TEXT_INDENT,
                          barHeight,
                          format,
                          enabled ? code : DialogPainter.CODE_DISABLED);
    }

    /**
     * Draws the closed web drop-down ({@code .selectedOption}).
     */
    private void drawWeb(@NotNull DialogPainter canvas, float left, float top, boolean hovered) throws IOException {
        float width = this.getDefinition().getWidth();
        float height = this.getDefinition().getHeight();
        boolean enabled = this.model.isInputEnabled();
        boolean open = this.model.isOpen();
        boolean upward = this.model.isUpward();
        int selection = this.model.getSelection();
        boolean active = open || hovered && enabled;
        int border = !enabled ? WebPanelStyleUtils.DISABLED_BORDER : active ? WebPanelStyleUtils.ACTIVE_BORDER : WebPanelStyleUtils.BORDER;

        WebPanelStyleUtils.drawBox(canvas,
                                   left, top, width, height,
                                   border, WebPanelStyleUtils.BACKGROUND,
                                   !(open && upward), !(open && !upward),
                                   true, true);

        float contentLeft = left + WEB_BORDER + WEB_PADDING_X;
        float contentRight = left + width - WEB_BORDER - WEB_PADDING_X;

        if (this.arrowVisible) {
            // The arrow keeps its place when invisible (a disabled drop-down).
            if (enabled) {
                canvas.drawFrame(canvas.getWebImage(active ? WEB_ACTIVE_ARROW_IMAGE : WEB_ARROW_IMAGE),
                                 contentRight - WEB_ARROW, top + (height - WEB_ARROW) / 2,
                                 WEB_ARROW, WEB_ARROW);
            }

            contentRight -= WEB_ARROW;
        }

        String text;
        int color;

        if (selection == NO_SELECTION && !this.label.isEmpty()) {
            text  = this.label;
            color = !enabled ? WebPanelStyleUtils.DISABLED_TEXT : active ? WebPanelStyleUtils.HIGHLIGHT_TEXT : WebPanelStyleUtils.BUTTON_TEXT;
        } else if (selection == NO_SELECTION) {
            text  = this.placeholder;
            color = WebPanelStyleUtils.DISABLED_TEXT;
        } else {
            Item item = this.model.getItem(selection);

            text = item.text;

            int own = active ? item.hoverColor : item.color;

            color = !enabled
                    ? WebPanelStyleUtils.DISABLED_TEXT
                    : own != NO_COLOR ? own : active ? WebPanelStyleUtils.HIGHLIGHT_TEXT : WebPanelStyleUtils.BUTTON_TEXT;
        }

        if (!text.isEmpty()) {
            WebPanelStyleUtils.drawText(canvas,
                                        WebPanelStyleUtils.DROPDOWN_TEXT,
                                        fitWeb(canvas, text, contentRight - contentLeft),
                                        contentLeft, top + (height - WEB_ROW) / 2,
                                        contentRight - contentLeft, WEB_ROW,
                                        InterfaceText.LEFT | InterfaceText.TOP,
                                        color);
        }
    }

    /**
     * Draws the open web list ({@code .selectableOptionHolder}).
     */
    private void drawWebList(@NotNull DialogPainter canvas, float left, float top) {
        float width = this.getDefinition().getWidth();
        boolean upward = this.model.isUpward();
        int count = this.model.getItemCount();
        float listHeight = count * WEB_ROW + WEB_LIST_PADDING + WEB_BORDER;
        float listTop = upward ? top - listHeight : top + this.getDefinition().getHeight();

        WebPanelStyleUtils.drawBox(canvas,
                                   left, listTop, width, listHeight,
                                   WebPanelStyleUtils.ACTIVE_BORDER, WebPanelStyleUtils.BACKGROUND,
                                   upward, !upward,
                                   upward, !upward);

        float y = upward ? top - count * WEB_ROW : listTop + WEB_LIST_PADDING;
        float textLeft = left + WEB_BORDER + WEB_ROW_PADDING_X;
        float textWidth = width - 2 * (WEB_BORDER + WEB_ROW_PADDING_X);

        for (int index = 0; index < count; index++, y += WEB_ROW) {
            Item item = this.model.getItem(index);
            int color;

            if (!this.model.isItemEnabled(index)) {
                color = WebPanelStyleUtils.DISABLED_BORDER;

                // A disabled item is struck through.
                float strike = Math.min(canvas.measure(WebPanelStyleUtils.DROPDOWN_TEXT, item.text), textWidth);

                canvas.fillRectangle(textLeft, y + WEB_ROW / 2, strike, 1, color);
            } else {
                boolean hovered = index == this.model.getHoveredItem();
                int own = hovered ? item.hoverColor : item.color;

                color = own != NO_COLOR ? own : hovered ? WebPanelStyleUtils.HIGHLIGHT_TEXT : WebPanelStyleUtils.BUTTON_TEXT;
            }

            WebPanelStyleUtils.drawText(canvas,
                                        WebPanelStyleUtils.DROPDOWN_TEXT,
                                        fitWeb(canvas, item.text, textWidth),
                                        textLeft, y,
                                        textWidth, WEB_ROW,
                                        InterfaceText.LEFT | InterfaceText.TOP,
                                        color);
        }
    }

    /**
     * Draws an item's text: in its own color while it has one and the code is not the disabled one, else in the code.
     */
    private void drawItemText(@NotNull DialogPainter canvas, int index, float left, float top, float width, float height, int format, int code) {
        Item item = this.model.getItem(index);
        int slot = canvas.getControlFontSlot(this.getDefinition());

        if (item.color != NO_COLOR && code != DialogPainter.CODE_DISABLED) {
            canvas.drawColorText(slot, item.text, left, top, width, height, format, item.color);
        } else {
            canvas.drawCodeText(slot, item.text, left, top, width, height, format, code);
        }
    }

    /**
     * Draws the open list after other controls, below or above the closed bar according to its current direction.
     *
     * @param canvas the glue canvas
     * @param left   the control left
     * @param top    the control top
     * @throws IOException if the sheet cannot be read
     */
    public void drawList(@NotNull DialogPainter canvas, float left, float top) throws IOException {
        if (!this.model.isOpen()) {
            return;
        }

        if (this.webPanelStyle) {
            this.drawWebList(canvas, left, top);

            return;
        }

        float rowHeight = this.getRowHeight(canvas);
        float y = top + this.getListTop(canvas);
        int tile = canvas.getMetricWidth(this.sheetPath, this.getFrameIndex(canvas, TILE));
        // The list's outer edge row: the bottom one below the bar, the top one above it.
        boolean upward = this.model.isUpward();
        int edgeRow = upward ? 0 : this.model.getItemCount() - 1;
        int edgeFrame = upward ? TILE : LAST_ROW;

        for (int index = 0; index < this.model.getItemCount(); index++) {
            canvas.drawBar(this.sheetPath,
                           this.getFrameIndex(canvas, index == edgeRow ? edgeFrame : ROW),
                           left, y,
                           this.getDefinition().getWidth(),
                           tile);

            int code = index == this.model.getHoveredItem() ? DialogPainter.CODE_HIGHLIGHT : DialogPainter.CODE_BUTTON;

            this.drawItemText(canvas,
                              index,
                              left + TEXT_INDENT,
                              y,
                              this.getDefinition().getWidth() - TEXT_INDENT,
                              rowHeight + 1,
                              InterfaceText.LEFT | InterfaceText.TOP,
                              code);

            y += rowHeight;
        }
    }
}
