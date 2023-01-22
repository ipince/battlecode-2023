package bobby;

import battlecode.common.Anchor;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.ResourceType;
import battlecode.common.RobotController;
import battlecode.common.RobotType;

public class Headquarter extends RobotPlayer {

    public static void run(RobotController rc) throws GameActionException {

        // Write down my location when I am born.
        if (rc.getRoundNum() == 1) {
            Memory.writeHeadquarter(rc);
            rc.setIndicatorString("Wrote myself to memory");
        }

        // Pick unoccupied direction to build.
        Direction dir = directions[rng.nextInt(directions.length)];
        MapLocation buildLoc = rc.getLocation().add(dir);
        for (int i = 0; i < directions.length; i++) {
            if (!rc.isLocationOccupied(buildLoc)) {
                break;
            } else {
                dir = dir.rotateRight();
                buildLoc = rc.getLocation().add(dir);
            }
        }
        if (rc.isLocationOccupied(buildLoc)) {
            // can't build...
            return;
        }

        if (rc.canBuildRobot(RobotType.LAUNCHER, buildLoc)) {
            rc.buildRobot(RobotType.LAUNCHER, buildLoc);
        }

        if (rc.canBuildRobot(RobotType.CARRIER, buildLoc)) {
            rc.buildRobot(RobotType.CARRIER, buildLoc);
        }

        if (rc.canBuildAnchor(Anchor.STANDARD)) {
            // If we can build an anchor do it!
            rc.buildAnchor(Anchor.STANDARD);
        }
    }
}
