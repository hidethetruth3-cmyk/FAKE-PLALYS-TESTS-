package com.fakeplayermod;

public enum Rank {
    NORMAL("normal"),
    GENERAL("general"),
    COMMANDER("commander");

    public final String id;

    Rank(String id) {
        this.id = id;
    }
}
