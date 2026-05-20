package com.axa.ch.project.phoenix.model;

import java.util.ArrayList;
import java.util.List;

public class Group {
    private String id;
    private String name;
    private String room;
    private List<String> userIds = new ArrayList<>();

    public Group() {}

    public Group(String id, String name, String room) {
        this.id = id;
        this.name = name;
        this.room = room;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }
    public List<String> getUserIds() { return userIds; }
    public void setUserIds(List<String> userIds) { this.userIds = userIds; }
}