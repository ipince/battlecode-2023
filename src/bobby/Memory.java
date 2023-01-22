package bobby;

import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.ResourceType;
import battlecode.common.RobotController;
import battlecode.common.WellInfo;
import battlecode.world.Inventory;
import org.apache.commons.lang3.StringUtils;

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

    public static class Well {
        MapLocation loc;
        ResourceType res;
        boolean upgraded;
        boolean saturated;

        public Well(MapLocation loc, ResourceType res, boolean upgraded, boolean saturated) {
            this.loc = loc;
            this.res = res;
            this.upgraded = upgraded;
            this.saturated = saturated;
        }

        public static Well from(WellInfo info, boolean saturated) {
            return new Well(info.getMapLocation(), info.getResourceType(), info.isUpgraded(), saturated);
        }

        @Override
        public boolean equals(Object that) {
            if (!(that instanceof Well)) {
                return false;
            }
            Well well = (Well) that;
            return this.loc.equals(well.loc) && this.res == well.res && this.upgraded == well.upgraded && this.saturated == well.saturated;
        }
    }

    public static Well decodeWell(int encoded) {
        boolean saturated = (encoded & 0b1) == 1; // TODO: return this in my own struct
        boolean upgraded = ((encoded & 10) >> 1) == 1;
        ResourceType res = ResourceType.values()[(encoded & 0b1100) >> 2];
        MapLocation loc = decodeMapLocation(encoded >> 4);
        return new Well(loc, res, upgraded, saturated);
    }

    public static int encodeWell(Well well) {
        int data = encodeMapLocation(well.loc) << 4; // 12 bits
        data += well.res.ordinal() << 2; // 2 bits
        data += (well.upgraded ? 1 : 0) << 1; // 1 bit
        data += (well.saturated ? 1 : 0); // 1 bit
        System.out.println(Utils.padLeft(Integer.toBinaryString(data), 16));
        return data;
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
