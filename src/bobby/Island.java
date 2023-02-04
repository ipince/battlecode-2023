package bobby;

import battlecode.common.Anchor;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.Team;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class Island {

    private static final int ANCHOR_HP_STANDARD = 250;
    private static final int ANCHOR_HP_ACCELERATING = 750;

    int id;
    Set<MapLocation> locations;
    Team team;

    // Only set if occupied (i.e. team != Team.NEUTRAL)
    Anchor anchor = null;
    int health = 0;

    int asOf;

    public Island(int id, Collection<MapLocation> locs, Team team, int asOf) {
        this.id = id;
        this.locations = new HashSet<>(locs);
        this.team = team;
        this.asOf = asOf;
    }

    public void clearOccupier() {
        this.team = Team.NEUTRAL;
        this.anchor = null;
        this.health = 0;
    }

    public void setOccupier(Team team, Anchor anchor, int health) {
        this.team = team;
        this.anchor = anchor;
        this.health = health;
    }

    public MapLocation random(RobotController rc) {
        return locations.iterator().next(); // TODO: pick better
    }

    public boolean shouldAnchor(RobotController rc, Anchor newAnchor, int healthLimitPct) {
        if (team == Team.NEUTRAL) {
            return true;
        } else if (team == rc.getTeam()) {
            if (anchor == Anchor.STANDARD && newAnchor == Anchor.ACCELERATING) {
                return true; // override Standard by Accelerating anchor.
            }
            switch (anchor) {
                case STANDARD:
                    return (100.0 * health / ANCHOR_HP_STANDARD) < healthLimitPct;
                case ACCELERATING:
                    return (100.0 * health / ANCHOR_HP_ACCELERATING) < healthLimitPct;
            }
        }
        return false;
    }
}
