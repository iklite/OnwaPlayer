package com.ikechi.studio.onwa.widgets.recyclerview.v1.model;

/**
 * A flat "list row" that is either a section header or a data item.
 * Constructed via the static factory methods {@link #header(String)} and
 * {@link #item(Item)}.
 */
public class ListRow {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_ITEM   = 1;

    private final String mTitle; // non-null when this is a header
    private final Item   mItem;  // non-null when this is a data item

    private ListRow(String title, Item item) {
        mTitle = title;
        mItem  = item;
    }

    /** Creates a header row with the given title. */
    public static ListRow header(String title) {
        return new ListRow(title, null);
    }

    /** Creates a data-item row. */
    public static ListRow item(Item item) {
        return new ListRow(null, item);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** Returns {@code true} if this row is a section header. */
    public boolean isHeader()  { return mItem == null; }

    /** Returns the header title, or {@code null} if this is not a header. */
    public String  getTitle()  { return mTitle; }

    /** Returns the {@link Item}, or {@code null} if this is a header. */
    public Item    getItem()   { return mItem; }

    /** Returns {@link #TYPE_HEADER} or {@link #TYPE_ITEM}. */
    public int     getViewType() { return isHeader() ? TYPE_HEADER : TYPE_ITEM; }
}

