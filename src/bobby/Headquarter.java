package bobby;

import battlecode.common.Anchor;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.ResourceType;
import battlecode.common.RobotController;
import battlecode.common.RobotType;
import battlecode.common.WellInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Headquarter extends RobotPlayer {

    public static final int ACTION_RADIUS = 9;

    private static final int MAX_BUILD_PER_TURN = 5; // 2 action cooldown / 10 per round.
    private static final int SMALL_MAP_SIZE_LIMIT = 28;

    private enum Priority {
        NONE,
        MILITARY,
        ECONOMY;
        // TODO: maybe add ANCHOR_RUSH, if map is small and rc.getIslandCount() is <= 4.
    }

    private static Priority priority = Priority.NONE;

    // TODO: keep track of resource rate over the past 10 turns maybe.
    // TODO: keep track of how many robots of each type we have constructed.

    public static void run(RobotController rc) throws GameActionException {

        // Write down my location and any wells I see when I am born. These
        // things don't really change (well, wells may change in the future).
        if (rc.getRoundNum() == 1) {
            Memory.writeHeadquarter(rc);

            // Write down any islands and wells I see.
            WellInfo[] wells = rc.senseNearbyWells(-1);
            System.out.println(Arrays.asList(wells));
            Memory.maybeWriteWells(rc, wells, false);
            knownWells = Memory.readWells(rc); // wasteful but easy
            updateKnownWells(rc);

            // Update map-based priorities.
            if (rc.getMapWidth() <= SMALL_MAP_SIZE_LIMIT && rc.getMapHeight() <= SMALL_MAP_SIZE_LIMIT) {
                priority = Priority.MILITARY;
            } else {
                priority = Priority.ECONOMY;
            }
        } else if (rc.getRoundNum() == 2) { // let other HQs write first.
            knownHQs = Memory.readHeadquarters(rc);
            knownWells = Memory.readWells(rc);
            updateKnownWells(rc);
        }


        // Build phase.
        if (maxCheapRobots(rc) >= 5 && rc.getActionCooldownTurns() >= 10) {
            // we can build more than 5 robots, so we have to strategically decide which ones we want.
            // For now: "small map" -> max launcher, rest carrier. Prioritize Mana.
            // TODO: decide how much to build of what.
        }

        int i = 0;
        while (rc.isActionReady() && i < MAX_BUILD_PER_TURN) { // failsafe against infinite loops.
            i++;

            // Priorities:
            //
            // TODO: 1) Under attack. Save resources for Launchers. REMEMBER TO BREAK.
            // TODO: 2) Unclaimed islands exist. Build/save res for Anchor. REMEMBER TO BREAK.
            if (rc.getRoundNum() > SAVE_FOR_ANCHORS_ROUND_NUM && rc.getNumAnchors(Anchor.STANDARD) == 0) {
                if (rc.canBuildAnchor(Anchor.STANDARD)) {
                    rc.buildAnchor(Anchor.STANDARD);
                } else {
                    break; // wait until we do have resources.
                }
            }

            // 3) Build as many Carriers and Launchers as possible. This may change later in
            // late-game. Since these use independent resources, let's just go crazy.

            switch (priority) {
                case MILITARY:
                    if (attemptToBuild(rc, RobotType.LAUNCHER)) {
                        continue; // prioritize Launchers by trying again.
                    } else {
                        attemptToBuild(rc, RobotType.CARRIER);
                    }
                    break;
                case ECONOMY:
                default:
                    if (attemptToBuild(rc, RobotType.CARRIER)) {
                        continue; // prioritize Carriers by trying again.
                    } else {
                        attemptToBuild(rc, RobotType.LAUNCHER);
                    }
                    break;
            }
        }
    }

    private static boolean attemptToBuild(RobotController rc, RobotType type) throws GameActionException {
        if (enoughResourcesFor(rc, type)) { // avoid expensive location calculus if possible
            MapLocation buildLoc = pickBuildLocation(rc, type);
            if (rc.canBuildRobot(type, buildLoc)) {
                rc.buildRobot(type, buildLoc);
                return true;
            } // else: can lead to infinite loop if we don't handle bad location.
        }
        return false;
    }

    private static int roundRobin = 0;

    private static MapLocation pickBuildLocation(RobotController rc, RobotType type) throws GameActionException {
        switch (type) {
            case CARRIER:
                if (knownWellsNearMe.size() > 0) {
                    return closestTo(rc, knownWellsNearMe.get(roundRobin++ % knownWellsNearMe.size()).loc);
                } else {
                    return pickRandomBuildLocation(rc);
                }
            case LAUNCHER:
                return closestTo(rc, mapCenter(rc));
            default:
                return pickRandomBuildLocation(rc);
        }
    }

    private static Map<MapLocation, List<MapInfo>> closestToCache = new HashMap<>();
    private static List<MapInfo> randomOrder;

    private static MapLocation closestTo(RobotController rc, MapLocation target) throws GameActionException {
        if (closestToCache.containsKey(target)) {
            return firstAvailable(rc, closestToCache.get(target));
        }

        List<MapInfo> infos = Arrays.asList(rc.senseNearbyMapInfos(rc.getLocation(), ACTION_RADIUS));
        Collections.sort(infos, Comparator.comparingInt(i -> target.distanceSquaredTo(i.getMapLocation())));
        closestToCache.put(target, infos); // save for later

        return firstAvailable(rc, infos);
    }

    private static MapLocation pickRandomBuildLocation(RobotController rc) throws GameActionException {
        if (randomOrder == null) {
            randomOrder = Arrays.asList(rc.senseNearbyMapInfos(ACTION_RADIUS)); // up to 29 locations including center.
            Collections.shuffle(randomOrder, rng);
        }
        return firstAvailable(rc, randomOrder);
    }

    private static MapLocation firstAvailable(RobotController rc, List<MapInfo> infos) throws GameActionException {
        for (int i = 0; i < infos.size(); i++) {
            MapInfo info = infos.get(i);
            if (info.isPassable() && !rc.isLocationOccupied(info.getMapLocation())) {
                return info.getMapLocation();
            }
        }
        return null; // nothing available :(
    }

    private static int maxCheapRobots(RobotController rc) {
        return rc.getResourceAmount(ResourceType.ADAMANTIUM) / CARRIER_AD_COST +
                rc.getResourceAmount(ResourceType.MANA) / LAUNCHER_MN_COST;
    }

    private static boolean enoughResourcesFor(RobotController rc, RobotType type) {
        switch (type) {
            case CARRIER:
                return rc.getResourceAmount(ResourceType.ADAMANTIUM) >= CARRIER_AD_COST;
            case LAUNCHER:
                return rc.getResourceAmount(ResourceType.MANA) >= LAUNCHER_MN_COST;
            default:
                return false;  // TODO: change.
        }
    }

}
