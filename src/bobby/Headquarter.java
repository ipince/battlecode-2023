package bobby;

import battlecode.common.Anchor;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotType;

public class Headquarter extends RobotPlayer {

    static int READ_WINDOW = 10;

    static int roundsSinceLastReading = 999999999;

    /**
     * Run a single turn for a Headquarters.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void run(RobotController rc) throws GameActionException {
        if (roundsSinceLastReading > READ_WINDOW) {
        }

        // In the beginning, start building carriers, until we have "enough".
        // Write down my location when I am born.
        // Write game age.

        // Read any info.
//        rc.getID()

        // Pick a direction to build in.
        Direction dir = directions[rng.nextInt(directions.length)];
        MapLocation newLoc = rc.getLocation().add(dir);

        // Let's try to build a carrier.
        rc.setIndicatorString("Trying to build a carrier");
        if (rc.canBuildRobot(RobotType.CARRIER, newLoc)) {
            rc.buildRobot(RobotType.CARRIER, newLoc);
        }

        if (rc.canBuildAnchor(Anchor.STANDARD)) {
            // If we can build an anchor do it!
            rc.buildAnchor(Anchor.STANDARD);
            rc.setIndicatorString("Building anchor!");
        }
    }
}
