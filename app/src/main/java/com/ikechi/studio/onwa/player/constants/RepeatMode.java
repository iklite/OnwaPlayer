package com.ikechi.studio.onwa.player.constants;

public class RepeatMode {
    public static final int REPEAT_MODE_NONE = 0;
    public static final int REPEAT_MODE_ALL  = 1;
    public static final int REPEAT_MODE_ONE  = 2;

    private int value;

    public RepeatMode(int value) { this.value = value; }

    public int getMode() { return value; }
    public void setMode(int value) { this.value = value; }

    public void cycle() { value = (value + 1) % 3; }
}
