package bobby;

import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.ResourceType;
import battlecode.common.RobotController;
import battlecode.common.WellInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Memory {

    // INDECES
    // 0-3: Up to 4 HQ locations
    static int HQ_BEGIN = 0;
    static int HQ_END = HQ_BEGIN + GameConstants.MAX_STARTING_HEADQUARTERS - 1;

    // 4-9: Up to 6 wells
    static int WELLS_BEGIN = HQ_END + 1;
    static int WELLS_END = WELLS_BEGIN + 6 - 1;

    // 10-19 Island locations
    static int ISLAND_BEGIN = 11;
    static int ISLAND_END = 20;

    // ...up to 63 (i think -- doublecheck)

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

    public static List<MapLocation> readHeadquarters(RobotController rc) throws GameActionException {
        List<MapLocation> hqLocs = new ArrayList<>();
        for (int i = WELLS_BEGIN; i < WELLS_END; i++) {
            MapLocation loc = decodeMapLocation(rc.readSharedArray(i));
            if (loc != null) {
                hqLocs.add(loc);
            } else {
                break;
            }
        }
        return hqLocs;
    }

    public static class Well {
        MapLocation loc;
        ResourceType res;
        boolean upgraded;
        boolean saturated;
        int idx; // where it is stored, or -1 if not stored/unknown.

        public Well(MapLocation loc, ResourceType res, boolean upgraded, boolean saturated, int idx) {
            this.loc = loc;
            this.res = res;
            this.upgraded = upgraded;
            this.saturated = saturated;
            this.idx = idx;
        }

        public static Well from(WellInfo info, boolean saturated) {
            return new Well(info.getMapLocation(), info.getResourceType(), info.isUpgraded(), saturated, -1);
        }

        @Override
        public boolean equals(Object that) {
            // NOTE: does NOT care about idx.
            if (!(that instanceof Well)) {
                return false;
            }
            Well well = (Well) that;
            return this.loc.equals(well.loc) && this.res == well.res && this.upgraded == well.upgraded && this.saturated == well.saturated;
        }
    }

    public static Map<MapLocation, Well> readWells(RobotController rc) throws GameActionException {
        Map<MapLocation, Well> wells = new HashMap<>();
        Well well;
        for (int i = WELLS_BEGIN; i < WELLS_END; i++) {
            well = decodeWell(rc.readSharedArray(i), i);
            if (well != null) {
                wells.put(well.loc, well);
            } else {
                break;
            }
        }
        return wells;
    }

    public static void maybeWriteWell(RobotController rc, WellInfo info, boolean saturated) throws GameActionException {
        // Read existing wells first, since we may have to overwrite.
        Map<MapLocation, Well> wells = readWells(rc);
        Well newWell = Well.from(info, saturated);
        int encoded = encodeWell(newWell);
        if (wells.containsKey(newWell.loc) && !newWell.equals(wells.get(newWell.loc))) {
            // we should update!
            if (rc.canWriteSharedArray(newWell.idx, encoded)) {
                rc.writeSharedArray(newWell.idx, encoded);
            }
        } else { // new well
            int idx = WELLS_BEGIN + wells.size();
            if (rc.canWriteSharedArray(idx, encoded)) {
                rc.writeSharedArray(idx, encoded);
            }
        }
    }

    static Well decodeWell(int encoded, int idx) {
        boolean saturated = (encoded & 0b1) == 1;
        boolean upgraded = ((encoded & 10) >> 1) == 1;
        ResourceType res = ResourceType.values()[(encoded & 0b1100) >> 2];
        MapLocation loc = decodeMapLocation(encoded >> 4);
        if (loc != null) {
            return new Well(loc, res, upgraded, saturated, idx);
        } else {
            return null; // nothing here.
        }
    }

    static int encodeWell(Well well) {
        int data = encodeMapLocation(well.loc) << 4; // 12 bits
        data += well.res.ordinal() << 2; // 2 bits
        data += (well.upgraded ? 1 : 0) << 1; // 1 bit
        data += (well.saturated ? 1 : 0); // 1 bit
        return data;
    }

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

    public static int encodeMapLocation(MapLocation loc) {
        // NOTE: 0 <= x and y are <= 60, so each int has at most 6 bits.
        return ((loc.x + 1) << 6) + (loc.y + 1);
    }

    public static MapLocation decodeMapLocation(int encoded) {
        if ((encoded << 20) >> 12 == 0) {
            return null;
        }
        int y = (encoded & 0b111111) - 1;
        int x = ((encoded >> 6) & 0b111111) - 1;
        return new MapLocation(x, y);
    }
}
