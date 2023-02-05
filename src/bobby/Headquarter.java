package bobby;

import battlecode.common.Anchor;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.ResourceType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.WellInfo;

import java.util.ArrayList;
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
    }

    private static Priority priority = Priority.NONE;

    private static int currentAd = 0;
    private static int currentMana = 0;

    // Resource rates. How to measure last 5 rounds?
    private static int endingAd = 0;
    private static MovingAvgLastN adamantiumRate = new MovingAvgLastN(10);
    private static int endingMana = 0;
    private static MovingAvgLastN manaRate = new MovingAvgLastN(10);

    static List<Memory.Well> knownWellsNearMe = new ArrayList<>(); // NOT saved

    public static void run(RobotController rc) throws GameActionException {

        currentAd = rc.getResourceAmount(ResourceType.ADAMANTIUM);
        currentMana = rc.getResourceAmount(ResourceType.MANA);

        adamantiumRate.add(currentAd - endingAd);
        manaRate.add(currentMana - endingMana);

        // Write down my location and any wells I see when I am born. These
        // things don't really change (well, wells may change in the future).
        if (rc.getRoundNum() == 1) {
            Memory.writeHeadquarter(rc, rc.getLocation(), true, true);

            // Write down any wells I see.
            WellInfo[] wellInfos = rc.senseNearbyWells();
            Memory.maybeWriteWells(rc, wellInfos);
            knownWells = Memory.readWells(rc); // wasteful but easy
            updateWellsNearMe(rc);

            // Update map-based priorities.
            if (rc.getMapWidth() <= SMALL_MAP_SIZE_LIMIT && rc.getMapHeight() <= SMALL_MAP_SIZE_LIMIT) {
                priority = Priority.MILITARY;
            } else {
                priority = Priority.ECONOMY;
            }
        } else if (rc.getRoundNum() == 2) { // let other HQs write first.
            knownHQs = Memory.readHeadquarters(rc, true, true);
            knownWells = Memory.readWells(rc);
            updateWellsNearMe(rc);
        }

        int i = 0;
        while (rc.isActionReady() && i < MAX_BUILD_PER_TURN * 2) { // failsafe against infinite loops. times 2 in case previous turn ran over
            i++;

            // 1) If under attack, save for launchers and built all at once.
            if (underSiege(rc)) {
                if (enoughResourcesFor(rc, RobotType.LAUNCHER, MAX_BUILD_PER_TURN)) {
                    // Build all we can!
                    while (attemptToBuild(rc, RobotType.LAUNCHER)) {
                    }
                }
                // TODO: should we also build carriers?
                break;
            }

            // 2) Build (or save for) an anchor.
            if (rc.getRoundNum() > SAVE_FOR_ANCHORS_ROUND_NUM && rc.getNumAnchors(Anchor.STANDARD) == 0 &&
                    adamantiumRate.getAvg() + manaRate.getAvg() > 10) {
                if (currentAd < ANCHOR_AD_COST || currentMana < ANCHOR_MN_COST) {
                    rc.setIndicatorString("SAVING FOR ANCHOR");
                    // save up, but still carry on, just pretend we have less resources.
                    currentAd = Math.max(0, currentAd - ANCHOR_AD_COST);
                    currentMana = Math.max(0, currentMana - ANCHOR_MN_COST);
                } else {
                    if (rc.canBuildAnchor(Anchor.STANDARD)) {
                        rc.buildAnchor(Anchor.STANDARD);
                        currentAd -= ANCHOR_AD_COST;
                        currentMana -= ANCHOR_MN_COST;
                        rc.setIndicatorString("BUILT ANCHOR");
                    }
                }
            } else {
                rc.setIndicatorString("");
            }

            // 3) Build as many Carriers and Launchers as possible. This may change later in
            // late-game. Since these use independent resources, let's just go crazy.
            switch (priority) {
                case MILITARY:
                    while (attemptToBuild(rc, RobotType.LAUNCHER)) {
                    }
                    attemptToBuild(rc, RobotType.CARRIER);
                    break;
                case ECONOMY:
                default:
                    while (attemptToBuild(rc, RobotType.CARRIER)) {
                    }
                    attemptToBuild(rc, RobotType.LAUNCHER);
                    break;
            }
        }

        endingAd = rc.getResourceAmount(ResourceType.ADAMANTIUM);
        endingMana = rc.getResourceAmount(ResourceType.MANA);
    }

    private static boolean attemptToBuild(RobotController rc, RobotType type) throws GameActionException {
        if (enoughResourcesFor(rc, type, 1)) { // avoid expensive location calculus if possible
            MapLocation buildLoc = pickBuildLocation(rc, type);
            if (buildLoc == null) {
                System.out.println("WARNING: no location to build!!"); // TODO: optimize a bit.
                return false;
            }
            if (rc.canBuildRobot(type, buildLoc)) {
                rc.buildRobot(type, buildLoc);
                currentAd -= type.getBuildCost(ResourceType.ADAMANTIUM);
                currentMana -= type.getBuildCost(ResourceType.MANA);
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
                return closestTo(rc, Mapping.mapCenter(rc));
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

        List<MapInfo> infos = Arrays.asList(rc.senseNearbyMapInfos(ACTION_RADIUS));
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

    private static boolean enoughResourcesFor(RobotController rc, RobotType type, int num) {
        switch (type) {
            case CARRIER:
                return currentAd >= CARRIER_AD_COST * num;
            case LAUNCHER:
                return currentMana >= LAUNCHER_MN_COST * num;
            default:
                return false;  // TODO: change.
        }
    }

    private static boolean underSiege(RobotController rc) throws GameActionException {
        if (rc.getRoundNum() < 20) {
            return false;
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        return enemies.length >= NUMBER_ENEMIES_FOR_SIEGE;
    }

    static void updateWellsNearMe(RobotController rc) {
        MapLocation me = rc.getLocation();
        int nearMeDistance = Headquarter.ACTION_RADIUS + Carrier.VISION_RADIUS; // 29
        List<Memory.Well> updated = new ArrayList<>();
        for (Memory.Well w : knownWells.values()) {
            if (me.distanceSquaredTo(w.loc) <= nearMeDistance) {
                updated.add(w);
            }
        }
        knownWellsNearMe = updated;
    }
}
