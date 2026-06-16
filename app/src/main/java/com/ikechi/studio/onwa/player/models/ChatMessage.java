package com.ikechi.studio.onwa.player.models;

/**
 * Data model for a single chat message.
 */
public class ChatMessage {

    private long id;                // database row id
    private String username;        // owner's unique username (key for storage)
    private String realName;        // owner's display name
    private String message;
    private long timestamp;
    private boolean isLocal;        // true = sent by this device
    private boolean isPrivate;      // true = @mention DM
    private String targetUsername;  // recipient username, or "" for broadcasts

    public ChatMessage() {}

    /** Broadcast message constructor. */
    public ChatMessage(String username, String realName, String message,
                       long timestamp, boolean isLocal) {
        this(username, realName, message, timestamp, isLocal, false, "");
    }

    /** Full constructor including private-message fields. */
    public ChatMessage(String username, String realName, String message,
                       long timestamp, boolean isLocal,
                       boolean isPrivate, String targetUsername) {
        this.username = username;
        this.realName = realName;
        this.message = message;
        this.timestamp = timestamp;
        this.isLocal = isLocal;
        this.isPrivate = isPrivate;
        this.targetUsername = targetUsername != null ? targetUsername : "";
    }

    // Getters and setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isLocal() { return isLocal; }
    public void setLocal(boolean local) { isLocal = local; }

    public boolean isPrivate() { return isPrivate; }
    public void setPrivate(boolean isPrivate) { this.isPrivate = isPrivate; }

    public String getTargetUsername() { return targetUsername; }
    public void setTargetUsername(String targetUsername) {
        this.targetUsername = targetUsername != null ? targetUsername : "";
    }
}
