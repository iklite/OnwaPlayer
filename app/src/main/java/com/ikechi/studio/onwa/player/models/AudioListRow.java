package com.ikechi.studio.onwa.player.models;


/**
 * Wrapper for a row in the audio library list.
 * Can be a section header or an audio item.
 */
public class AudioListRow {
    public static final int TYPE_HEADER = 0;
    public static final int TYPE_ITEM   = 1;

    private final int    type;
    private final String header;      // non-null if TYPE_HEADER
    private final AudioItem item;      // non-null if TYPE_ITEM

    private AudioListRow(int type, String header, AudioItem item) {
        this.type = type;
        this.header = header;
        this.item = item;
    }

    public static AudioListRow header(String header) {
        return new AudioListRow(TYPE_HEADER, header, null);
    }

    public static AudioListRow item(AudioItem item) {
        return new AudioListRow(TYPE_ITEM, null, item);
    }

    public int getType() { return type; }
    public String getHeader() { return header; }
    public AudioItem getItem() { return item; }
	
}
