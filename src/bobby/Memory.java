package bobby;

import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

public class Memory {

    // INDECES

    // 0-9 HQ locations
    static int HQ_BEGIN = 0;
    static int HQ_END = GameConstants.MAX_STARTING_HEADQUARTERS;

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

    public static void writeHeadquarter(RobotController rc) throws GameActionException {
        // Find a space to write to first
        for (int i = HQ_BEGIN; i < HQ_END; i++) {
            if (rc.readSharedArray(i) == 0) { // first empty slot
                int loc = encodeMapLocation(rc.getLocation());
                if (rc.canWriteSharedArray(i, loc)) {
                    rc.writeSharedArray(i, loc);
                    return;
                }
            }
        }
    }

    public static int encodeMapLocation(MapLocation loc) {
        // NOTE: 0 <= x and y are <= 60, so each int has at most 6 bits.
        return ((loc.x+1) << 6) + (loc.y+1);
    }

    public static MapLocation decodeMapLocation(int encoded) {
        if ((encoded << 20) >> 12 == 0) {
            return  null;
        }
        int y = (encoded & 0b111111) - 1;
        int x = ((encoded >> 6) & 0b111111) - 1;
        return new MapLocation(x, y);
    }
}
