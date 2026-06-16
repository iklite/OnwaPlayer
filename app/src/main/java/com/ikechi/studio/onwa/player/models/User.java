package com.ikechi.studio.onwa.player.models;

public class User {
    private String username;
    private String realName;
    private String deviceAddress; // WiFi Direct MAC address (optional)

    public User(String username, String realName) {
        this.username = username;
        this.realName = realName;
    }

    public User(String username, String realName, String deviceAddress) {
        this.username = username;
        this.realName = realName;
        this.deviceAddress = deviceAddress;
    }

    public String getUsername() { return username; }
    public String getRealName() { return realName; }
    public String getDeviceAddress() { return deviceAddress; }
    public void setDeviceAddress(String addr) { this.deviceAddress = addr; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return username.equals(user.username);
    }

    @Override
    public int hashCode() {
        return username.hashCode();
    }
}
