package bobby;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.ResourceType;
import battlecode.common.RobotController;
import battlecode.common.WellInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Memory {

    // INDECES
    // Up to 4 HQ locations
    static int HQ_BEGIN = 0; // inclusive
    static int HQ_END = HQ_BEGIN + GameConstants.MAX_STARTING_HEADQUARTERS; // exclusive

    // Wells: 4-13 (inclusive)
    static int WELLS_SIZE = 10;
    static int WELLS_BEGIN = HQ_END;
    static int WELLS_END = WELLS_BEGIN + WELLS_SIZE;

    // Island locations
    static int ISLANDS_SIZE = 10;
    static int ISLAND_BEGIN = WELLS_END;
    static int ISLAND_END = ISLAND_BEGIN + ISLANDS_SIZE;

    // Enemy HQs (includes confirmed and unconfirmed)
    static int ENEMY_HQ_SIZE = GameConstants.MAX_STARTING_HEADQUARTERS * 3; // for each HQ, there's at most 3 locations for enemy HQs.
    static int ENEMY_HQ_BEGIN = ISLAND_END;
    static int ENEMY_HQ_END = ENEMY_HQ_BEGIN + ENEMY_HQ_SIZE;

    // ...up to 63 (i think -- doublecheck)

    // HQs (ally and enemy)

    public static boolean writeHeadquarter(RobotController rc, MapLocation hqLoc, boolean ally, boolean confirmed) throws GameActionException {
        int begin = HQ_BEGIN;
        int end = HQ_END;
        if (!ally) {
            begin = ENEMY_HQ_BEGIN;
            end = ENEMY_HQ_END;
        }
        // Find a space to write to first
        for (int i = begin; i < end; i++) {
            if (rc.readSharedArray(i) == 0) { // first empty slot
                int data = (encodeMapLocation(hqLoc) << 1) + (confirmed ? 1 : 0);
                if (rc.canWriteSharedArray(i, data)) {
                    rc.writeSharedArray(i, data);
                    return true;
                }
            }
        }
        return false;
    }

    public static List<MapLocation> readHeadquarters(RobotController rc, boolean ally, boolean confirmed) throws GameActionException {
        int begin = HQ_BEGIN;
        int end = HQ_END;
        if (!ally) {
            begin = ENEMY_HQ_BEGIN;
            end = ENEMY_HQ_END;
        }
        List<MapLocation> hqLocs = new ArrayList<>();
        for (int i = begin; i < end; i++) {
            int data = rc.readSharedArray(i);
            boolean readConfirmed = (data & 0b1) == 1;
            MapLocation loc = decodeMapLocation(data >> 1);
            if (loc == null) {
                break; // done reading
            }
            if (confirmed == readConfirmed) {
                hqLocs.add(loc);
            }
        }
        return hqLocs;
    }

    // Wells

    public static class Well {
        MapLocation loc;
        ResourceType res;
        boolean upgraded;
        Boolean saturated; // null is unknown

        int idx; // where it is stored, or -1 if not stored/unknown. Makes it easier to overwrite/update.

        public Well(MapLocation loc, ResourceType res, boolean upgraded, Boolean saturated, int idx) {
            this.loc = loc;
            this.res = res;
            this.upgraded = upgraded;
            this.saturated = saturated;
            this.idx = idx;
        }

        public static Well from(WellInfo info, Boolean saturated) {
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

        @Override
        public int hashCode() {
            return loc.hashCode(); // NOTE: this is a hack. Probably better to use a Map<MapLocation, Well>
        }

        @Override
        public String toString() {
            return String.format("(%s, %s, upgr=%b, sat=%b, idx=%s)", loc, res, upgraded, saturated, idx);
        }
    }

    // for now, we assume wells don't change, so we don't re-read already read wells.
    private static Map<MapLocation, Well> wells = new HashMap<>();
    private static int lastReadWell = WELLS_BEGIN;

    public static Map<MapLocation, Well> readWells(RobotController rc) throws GameActionException {
        int start = Clock.getBytecodeNum();
        for (; lastReadWell < WELLS_END; lastReadWell++) {
            Well well = decodeWell(rc.readSharedArray(lastReadWell), lastReadWell);
            if (well != null) {
                wells.put(well.loc, well);
            } else {
                break;
            }
        }
        if (RobotPlayer.PROFILE) System.out.println("readWells took " + (Clock.getBytecodeNum() - start));
        return wells;
    }

    /**
     * Returns all known wells after writing. Maybe we failed to write some wells.
     */
    public static Map<MapLocation, Well> maybeWriteWells(RobotController rc, Set<Well> newWells) throws GameActionException {
        // Read existing wells first, since we may have to overwrite.
        int idx = lastReadWell;
        for (Well newWell : newWells) {
            if (idx >= WELLS_END) { // no more space.
                break;
            }
            int encoded = encodeWell(newWell);
            if (wells.containsKey(newWell.loc)) { // update (if changed)
                if (!newWell.equals(wells.get(newWell.loc))) {
                    if (rc.canWriteSharedArray(newWell.idx, encoded)) {
                        rc.writeSharedArray(newWell.idx, encoded);
                        wells.put(newWell.loc, newWell);
                    }
                }
            } else { // new well
                if (rc.canWriteSharedArray(idx, encoded)) {
                    rc.writeSharedArray(idx, encoded);
                    newWell.idx = idx;
                    wells.put(newWell.loc, newWell);
                    idx++;
                }
            }
        }
        return wells;
    }

    // Only have plural version since each write involves reading all existing wells, so this prevents
    // too much reading.
    public static void maybeWriteWells(RobotController rc, WellInfo[] infos) throws GameActionException {
        Set<Well> wells = new HashSet<>(infos.length);
        for (WellInfo info : infos) {
            wells.add(Well.from(info, null));
        }
        maybeWriteWells(rc, wells);
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
        data += (well.saturated != null ? (well.saturated ? 1 : 0) : 0); // 1 bit // TODO: we'll need another bit to save unknown, ugh
        return data;
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
