package bobby;

import battlecode.common.Anchor;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.ResourceType;
import battlecode.common.RobotController;
import battlecode.common.RobotType;

public class Headquarter extends RobotPlayer {

    static int LAUNCHER_MANA_COST = 60;
    static int READ_WINDOW = 10;

    static int roundsSinceLastReading = 999999999;

    /**
     * Run a single turn for a Headquarters.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void run(RobotController rc) throws GameActionException {
        if (roundsSinceLastReading > READ_WINDOW) {
        }

        // Write down my location when I am born.
        if (rc.getRoundNum() == 1) {
            Memory.writeHeadquarter(rc);
            rc.setIndicatorString("Wrote myself to memory");
        }

        // In the beginning, start building carriers, until we have "enough".
        // Write game age.

        // Read any info.
//        rc.getID()

        // Pick unoccupied direction.
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
