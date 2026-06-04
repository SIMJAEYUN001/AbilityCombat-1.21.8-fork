package com.abilitycombat.game;

public enum MatchMode {
    SOLO("개인전", false),
    DUO("2인전", true),
    TEAM("팀전", true);

    private final String displayName;
    private final boolean teamBased;

    MatchMode(String displayName, boolean teamBased) {
        this.displayName = displayName;
        this.teamBased = teamBased;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isTeamBased() {
        return teamBased;
    }
}
