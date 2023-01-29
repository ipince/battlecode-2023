package bobby;

import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public class Mapping {

    public enum Symmetry {
        VERTICAL, HORIZONTAL, ROTATIONAL;
    }

    public static MapLocation symmetries(RobotController rc, MapLocation loc, Symmetry sym) {
        switch (sym) {
            case VERTICAL:
                return new MapLocation(rc.getMapWidth() - loc.x - 1, loc.y);
            case HORIZONTAL:
                return new MapLocation(loc.x, rc.getMapHeight() - loc.y - 1);
            case ROTATIONAL:
            default:
                return new MapLocation(rc.getMapWidth() - loc.x - 1, rc.getMapHeight() - loc.y - 1);
        }
    }

    static MapLocation mapCenter(RobotController rc) {
        // TODO: memoize
        return new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
    }
}
