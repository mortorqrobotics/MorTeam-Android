package org.team1515.morteam.entities;

public class Subdivision {
    private String name;
    private String id;

    public Subdivision(String name, String id) {
        this.name = name;
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }
}