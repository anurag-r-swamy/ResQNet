package com.example.myapplication;

public class Message {
    private String senderId;
    private String text;
    private boolean isMe;
    private long timestamp;

    public Message(String senderId, String text, boolean isMe) {
        this(senderId, text, isMe, System.currentTimeMillis());
    }

    public Message(String senderId, String text, boolean isMe, long timestamp) {
        this.senderId = senderId;
        this.text = text;
        this.isMe = isMe;
        this.timestamp = timestamp;
    }

    public String getSenderId() { return senderId; }
    public String getText() { return text; }
    public boolean isMe() { return isMe; }
    public long getTimestamp() { return timestamp; }
}