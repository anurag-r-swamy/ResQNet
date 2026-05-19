package com.example.myapplication;

public class Message {
    private String senderId;
    private String text;
    private boolean isMe;

    public Message(String senderId, String text, boolean isMe) {
        this.senderId = senderId;
        this.text = text;
        this.isMe = isMe;
    }

    public String getSenderId() { return senderId; }
    public String getText() { return text; }
    public boolean isMe() { return isMe; }
}