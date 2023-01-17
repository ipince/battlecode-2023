package bobby;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.ResourceType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.WellInfo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Carrier extends RobotPlayer {

    private static int MAX_LOAD = 40;

    private enum State {
        UNASSIGNED, // same as "newborn for now"
        MOVING_TO_WELL,
        COLLECTING,
        DROPPING_OFF,
        DELIVERING_ANCHOR;
    }
    private static State state = State.UNASSIGNED;
    private static MapLocation collectingAt;
    private static ResourceType preferredResource; // TODO
    private static MapLocation homeHQLoc;

    private static MapLocation[] last3 = new MapLocation[3];

    public static void run(RobotController rc) throws GameActionException {
        // After executing the major actions, I should always consider: can i kill a nearby robot?
        // can i sense important information? if so, can i write it back to shared memory?
        setIndicator(rc);

        if (state == State.UNASSIGNED) {
            // I was just born.
            for (RobotInfo robot : rc.senseNearbyRobots()) {
                if (robot.getType() == RobotType.HEADQUARTERS && robot.getTeam() == rc.getTeam()) {
                    homeHQLoc = robot.getLocation();
                }
            }
            if (homeHQLoc == null) {
                rc.setIndicatorString("no home. not sure what to do");
                return;
            }

            // Try to find a well to mine.
            WellInfo[] wells = rc.senseNearbyWells();
            if (wells.length > 0) {
                // Choose a well randomly. TODO: choose more intelligently.
                rng.nextInt(wells.length); // Drop first value, which is always 0 (why?)
                collectingAt = wells[rng.nextInt(wells.length)].getMapLocation();
                state = State.MOVING_TO_WELL;
                setIndicator(rc);
            } else {
                moveRandomly(rc);
            }
        }

        if (state == State.MOVING_TO_WELL) {
            if (!rc.getLocation().isAdjacentTo(collectingAt)) {
                moveTowards(rc, collectingAt);
            } else { // we're close enough to collect!
                state = State.COLLECTING;
                setIndicator(rc);
            }
        }

        if (state == State.COLLECTING) { // we're adjacent
            collect(rc);
            if (isFull(rc)) {
                state = State.DROPPING_OFF;
                setIndicator(rc);
            } else {
                // Move out of the way, if there's crowding. TODO
                moveTowards(rc, collectingAt);
            }
        }

        if (state == State.DROPPING_OFF) {
            if (!rc.getLocation().isAdjacentTo(homeHQLoc)) {
                moveTowards(rc, homeHQLoc);
            } else {
                for (ResourceType r : ResourceType.values()) {
                    if (rc.getResourceAmount(r) > 0) {
                        if (rc.canTransferResource(homeHQLoc, r, rc.getResourceAmount(r))) {
                            rc.transferResource(homeHQLoc, r, rc.getResourceAmount(r));
                        }
                    }
                }
            }

            // Done dropping off (probably).
            if (isEmpty(rc)) {
                state = State.MOVING_TO_WELL;
                setIndicator(rc);
            }
        }

        if (state == State.DELIVERING_ANCHOR) {
            // If I have an anchor singularly focus on getting it to the first island I see
            int[] islands = rc.senseNearbyIslands();
            Set<MapLocation> islandLocs = new HashSet<>();
            for (int id : islands) {
                MapLocation[] thisIslandLocs = rc.senseNearbyIslandLocations(id);
                islandLocs.addAll(Arrays.asList(thisIslandLocs));
            }
            if (islandLocs.size() > 0) {
                MapLocation islandLocation = islandLocs.iterator().next();
                rc.setIndicatorString("Moving my anchor towards " + islandLocation);
                while (!rc.getLocation().equals(islandLocation)) {
                    Direction dir = rc.getLocation().directionTo(islandLocation);
                    if (rc.canMove(dir)) {
                        rc.move(dir);
                    }
                }
                if (rc.canPlaceAnchor()) {
                    rc.setIndicatorString("Huzzah, placed anchor!");
                    rc.placeAnchor();
                }
            }
        }
    }

    private static void collect(RobotController rc) throws GameActionException {
        if (rc.canCollectResource(collectingAt, -1)) {
            rc.collectResource(collectingAt, -1);
        }
    }

    private static boolean isEmpty(RobotController rc) {
        int total = rc.getResourceAmount(ResourceType.ADAMANTIUM) +
                rc.getResourceAmount(ResourceType.MANA) +
                rc.getResourceAmount(ResourceType.ELIXIR);
        return total == 0;
    }

    private static boolean isFull(RobotController rc) {
        int total = rc.getResourceAmount(ResourceType.ADAMANTIUM) +
                rc.getResourceAmount(ResourceType.MANA) +
                rc.getResourceAmount(ResourceType.ELIXIR);
        return total >= MAX_LOAD;
    }

    private static void setIndicator(RobotController rc) throws GameActionException {
        if (state == State.DROPPING_OFF) {
            rc.setIndicatorString(state + " at " + homeHQLoc.toString());
        } else if (state == State.MOVING_TO_WELL) {
            rc.setIndicatorString(state + " at " + collectingAt.toString());
        } else {
            rc.setIndicatorString(state.toString());
        }
    }

    private static void maybeAttack(RobotController rc) throws GameActionException {
        // Occasionally try out the carriers attack
        if (rng.nextInt(20) == 1) {
            RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
            if (enemyRobots.length > 0) {
                if (rc.canAttack(enemyRobots[0].location)) {
                    rc.attack(enemyRobots[0].location);
                }
            }
        }
    }

    private static boolean canAct(RobotController rc) {
        return rc.isActionReady() || rc.isMovementReady();
    }
}
