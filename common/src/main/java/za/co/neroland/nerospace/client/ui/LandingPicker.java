package za.co.neroland.nerospace.client.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

/**
 * The "Dock at" / "Land at" list shared by the Rocket and Launch Controller screens: a panel of rows drawn over the
 * console, one per candidate (name, the viewer's relation, and the distance from the rocket where it means
 * something). The host screen owns the input and calls in: Up/Down move the keyboard focus, Enter picks the focused
 * row, the wheel scrolls, a click picks a row and a click outside (or Escape) closes it. The host draws the picked row
 * with its optimistic highlight and reconciles it with the server, like the destination list.
 *
 * <p>Procedural and client-only; the rows come from the synced station/pad labels, never from world state.</p>
 */
public final class LandingPicker {

    public static final int ROW_H = 12;
    private static final int HEADER_H = 13;
    private static final int FOOTER_H = 11;
    private static final int BODY = 0xF00A1018;
    private static final int INK = 0xFF05080D;
    private static final int SELECTED = 0xFF123042;
    private static final int HOVER = 0xFF16344A;
    private static final int TEXT = 0xFFD6ECFF;
    private static final int SUBTLE = 0xFF8DA0B4;

    /**
     * One candidate. {@code value} is the slot or pad index the host sends; {@code tag} is the relation word,
     * {@code distance} the distance text (or {@code null}), {@code tooltip} extra help for the row (or {@code null}).
     */
    public record Entry(int value, Component name, @Nullable Component tag, int tagColor, @Nullable Component distance,
            @Nullable Component tooltip) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private boolean open;
    private int scroll;
    private int focus;
    private int x;
    private int y;
    private int w;
    private int rows;
    private Component title = Component.empty();

    /** Opens the list at screen position {@code (x, y)}, {@code w} wide with {@code rows} visible rows. */
    public void open(Component title, List<Entry> list, int selectedValue, int x, int y, int w, int rows) {
        this.title = title;
        this.x = x;
        this.y = y;
        this.w = w;
        this.rows = Math.max(1, rows);
        this.open = true;
        setEntries(list);
        this.focus = Math.max(0, indexOf(selectedValue));
        this.scroll = 0;
        ensureVisible();
    }

    public void close() {
        this.open = false;
    }

    public boolean isOpen() {
        return this.open;
    }

    /** Replaces the rows (the synced lists can change while open), keeping the focus on the same value. */
    public void setEntries(List<Entry> list) {
        int focused = focusedValueOr(Integer.MIN_VALUE);
        this.entries.clear();
        this.entries.addAll(list);
        int at = indexOf(focused);
        this.focus = at >= 0 ? at : Math.min(this.focus, Math.max(0, this.entries.size() - 1));
        this.scroll = DestinationList.clampScroll(this.scroll, this.entries.size(), this.rows);
    }

    /** Total panel height for the current row count. */
    public int height() {
        return HEADER_H + this.rows * ROW_H + FOOTER_H;
    }

    public boolean contains(double mx, double my) {
        return this.open && mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + height();
    }

    /** Moves the keyboard focus by {@code dir} rows (no wrap) and keeps it in view. */
    public void moveFocus(int dir) {
        if (this.entries.isEmpty()) {
            return;
        }
        this.focus = Math.max(0, Math.min(this.entries.size() - 1, this.focus + (dir < 0 ? -1 : 1)));
        ensureVisible();
    }

    public void scrollBy(int delta) {
        this.scroll = DestinationList.clampScroll(this.scroll + delta, this.entries.size(), this.rows);
    }

    /** The focused row, or {@code null} when the list is empty. */
    @Nullable
    public Entry focused() {
        return this.focus >= 0 && this.focus < this.entries.size() ? this.entries.get(this.focus) : null;
    }

    /** The row under the mouse, or {@code null}. */
    @Nullable
    public Entry at(double mx, double my) {
        int row = rowAt(mx, my);
        return row >= 0 ? this.entries.get(row) : null;
    }

    /** Draws the panel; {@code shownValue} is the host's optimistic selection, {@code accent} the screen accent. */
    public void draw(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, int shownValue, int accent) {
        if (!this.open) {
            return;
        }
        int h = height();
        g.fill(this.x - 1, this.y - 1, this.x + this.w + 1, this.y + h + 1, accent);
        g.fill(this.x, this.y, this.x + this.w, this.y + h, INK);
        g.fill(this.x + 1, this.y + 1, this.x + this.w - 1, this.y + h - 1, BODY);
        g.text(font, fit(font, this.title, this.w - 6), this.x + 3, this.y + 3, TEXT, false);
        g.fill(this.x + 1, this.y + HEADER_H - 2, this.x + this.w - 1, this.y + HEADER_H - 1, accent);
        int hovered = rowAt(mouseX, mouseY);
        Entry tipEntry = null;
        int listW = this.w - (this.entries.size() > this.rows ? 5 : 0);
        for (int r = 0; r < this.rows; r++) {
            int i = this.scroll + r;
            if (i >= this.entries.size()) {
                break;
            }
            Entry e = this.entries.get(i);
            int ry = this.y + HEADER_H + r * ROW_H;
            boolean selected = e.value() == shownValue;
            if (selected || i == hovered) {
                g.fill(this.x + 2, ry, this.x + listW - 2, ry + ROW_H - 1, selected ? SELECTED : HOVER);
            }
            if (selected) {
                g.fill(this.x + 2, ry, this.x + 4, ry + ROW_H - 1, accent);
            }
            if (i == this.focus) {
                outline(g, this.x + 2, ry, listW - 4, ROW_H - 1, i == hovered || selected ? TEXT : SUBTLE);
            }
            int right = this.x + listW - 4;
            Component tag = e.tag();
            if (tag != null) {
                int tw = font.width(tag);
                g.text(font, tag, right - tw, ry + 2, e.tagColor(), false);
                right -= tw + 6;
            }
            Component d = e.distance();
            if (d != null) {
                int dw = font.width(d);
                g.text(font, d, right - dw, ry + 2, SUBTLE, false);
                right -= dw + 6;
            }
            g.text(font, fit(font, e.name(), right - (this.x + 7)), this.x + 7, ry + 2, selected ? TEXT : 0xFFB9C6D4,
                    false);
            if (i == hovered) {
                tipEntry = e;
            }
        }
        if (this.entries.size() > this.rows) {
            int trackY = this.y + HEADER_H;
            int trackH = this.rows * ROW_H;
            int thumbH = Math.max(6, trackH * this.rows / this.entries.size());
            int maxScroll = Math.max(1, this.entries.size() - this.rows);
            int thumbY = trackY + (trackH - thumbH) * this.scroll / maxScroll;
            g.fill(this.x + this.w - 4, trackY, this.x + this.w - 2, trackY + trackH, INK);
            g.fill(this.x + this.w - 4, thumbY, this.x + this.w - 2, thumbY + thumbH, accent);
        }
        Component hint = Component.translatable("gui.nerospace.picker.hint");
        g.text(font, fit(font, hint, this.w - 6), this.x + 3, this.y + h - FOOTER_H + 2, SUBTLE, false);
        if (tipEntry != null) {
            Component tip = tipEntry.tooltip();
            if (tip != null) {
                g.setTooltipForNextFrame(font.split(tip, 200), mouseX, mouseY);
            }
        }
    }

    private static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    private int rowAt(double mx, double my) {
        if (!this.open || mx < this.x || mx >= this.x + this.w) {
            return -1;
        }
        int top = this.y + HEADER_H;
        if (my < top || my >= top + this.rows * ROW_H) {
            return -1;
        }
        int i = this.scroll + (int) ((my - top) / ROW_H);
        return i >= 0 && i < this.entries.size() ? i : -1;
    }

    private void ensureVisible() {
        if (this.focus < this.scroll) {
            this.scroll = this.focus;
        } else if (this.focus >= this.scroll + this.rows) {
            this.scroll = this.focus - this.rows + 1;
        }
        this.scroll = DestinationList.clampScroll(this.scroll, this.entries.size(), this.rows);
    }

    private int indexOf(int value) {
        for (int i = 0; i < this.entries.size(); i++) {
            if (this.entries.get(i).value() == value) {
                return i;
            }
        }
        return -1;
    }

    private int focusedValueOr(int fallback) {
        Entry e = focused();
        return e != null ? e.value() : fallback;
    }

    private static Component fit(Font font, Component text, int maxWidth) {
        if (maxWidth <= 0) {
            return Component.empty();
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String s = text.getString();
        return Component.literal(font.plainSubstrByWidth(s, Math.max(0, maxWidth - font.width("…"))) + "…");
    }
}
