package com.ikechi.studio.onwa.widgets.recyclerview.v1.model;

/**
 * Immutable-by-default plain data model for a list item.
 * Setters are provided for mutable operations (e.g. favourite toggling).
 */
public class Item {

    private int     mId;
    private String  mTitle;
    private String  mDescription;
    private String  mCategory;
    private boolean mIsFavorite;

    public Item(int id, String title, String description,
                String category, boolean isFavorite) {
        mId          = id;
        mTitle       = title;
        mDescription = description;
        mCategory    = category;
        mIsFavorite  = isFavorite;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int     getId()          { return mId; }
    public String  getTitle()       { return mTitle; }
    public String  getDescription() { return mDescription; }
    public String  getCategory()    { return mCategory; }
    public boolean isFavorite()     { return mIsFavorite; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setId(int id)               { mId = id; }
    public void setTitle(String title)       { mTitle = title; }
    public void setDescription(String desc)  { mDescription = desc; }
    public void setCategory(String category) { mCategory = category; }
    public void setFavorite(boolean value)   { mIsFavorite = value; }

    @Override
    public String toString() {
        return "Item{id=" + mId + ", title='" + mTitle + "'}";
    }
}

