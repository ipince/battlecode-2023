package bobby;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;

public class Memory {

    // INDECES

    // 0-9 HQ locations

    // 10-19 Island locations
    static int ISLAND_BEGIN = 11;
    static int ISLAND_END = 20;

    // ...up to 63 (i think -- doublecheck)

    public class Island {
        int size;
        int x, y; // one of its locations, anyone.
    }

    public static void readIslands(RobotController rc) throws GameActionException {
        for (int i = ISLAND_BEGIN; i < ISLAND_END; i++) {
            int saved = rc.readSharedArray(i);
            if (saved == 0) {
                // we're done with islands.
            } else {
                // take bits out. first 12 bits, are location. next 4 bits are... size?, owner (2)
            }
        }
    }
}
