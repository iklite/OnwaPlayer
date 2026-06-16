package com.ikechi.studio.onwa.player.models;

import java.util.List;

public class Category {
    private final int id;               // unique identifier
    private final String name;           // displayed name
    private final int iconResId;         // drawable resource for the tile
    private final List<AudioItem> items; // pre‑filtered list (or you can lazy‑load)

    public Category(int id, String name, int iconResId, List<AudioItem> items) {
        this.id = id;
        this.name = name;
        this.iconResId = iconResId;
        this.items = items;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public int getIconResId() { return iconResId; }
    public List<AudioItem> getItems() { return items; }
}
