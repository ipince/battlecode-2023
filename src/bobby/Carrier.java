package bobby;

import battlecode.common.Anchor;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.ResourceType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Team;
import battlecode.common.WellInfo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Carrier extends RobotPlayer {

    public static final int VISION_RADIUS = 20;

    private static int MAX_LOAD = GameConstants.CARRIER_CAPACITY;

    // States for Carrier state machine.
    private enum State {
        UNASSIGNED, // same as "newborn for now"
        MOVING_TO_WELL,
        COLLECTING,
        DROPPING_OFF,
        DELIVERING_ANCHOR;
    }

    private static State state = State.UNASSIGNED;

    // General state;
    private static MapLocation homeHQLoc;

    // State used for each state:
    // UNASSIGNED

    // MOVING_TO_WELL
    private static MapLocation collectingAt;

    // DELIVERING_ANCHOR
    private static int targetIsland;
    private static MapLocation targetIslandLoc;

    public static void run(RobotController rc) throws GameActionException {
        // TODO: After executing the major actions, I should always consider: can i kill a nearby robot?
        // can i sense important information? if so, can i write it back to shared memory?

        // First sense, then act/move. Try to ensure that I both act _and_ move everytime.

        rc.setIndicatorString("OUT OF BYTECODE; or exited somewhere odd..."); // TODO move to bottom? or to a finally block... and clear at top. so we know.

        // Update knowledge, regardless of state (for now).
        if (rc.getRoundNum() - lastRead > UPDATE_FREQ || age < 2) {
            knownHQs = Memory.readHeadquarters(rc);
            knownWells = Memory.readWells(rc);
        }

        if (RobotPlayer.shouldPrint(rc)) {
//            System.out.println("known hqs: " + knownHQs);
//            System.out.println("known wells: " + knownWells.values());
        }

        if (state == State.UNASSIGNED) {
            // I was just born.
            for (RobotInfo robot : rc.senseNearbyRobots()) {
                // TODO: what if multiple HQs spawn within sight?
                if (robot.getType() == RobotType.HEADQUARTERS && robot.getTeam() == rc.getTeam()) {
                    homeHQLoc = robot.getLocation();
                }
            }
            if (homeHQLoc == null) {
                rc.setIndicatorString("no home. not sure what to do");
                return;
            }

            // If there's an Anchor available, take it to an Island.
            // TODO: pick up anchor here too.

            // TODO: read memory to see if there are any other wells and their saturation.

            // Try to find a well to mine nearby.
            WellInfo[] wells = rc.senseNearbyWells();
            if (wells.length > 0) {
                // Choose a well randomly. TODO: choose more intelligently.
                rng.nextInt(wells.length); // Drop first value, which is always 0 (why?)
                collectingAt = wells[rng.nextInt(wells.length)].getMapLocation();
                state = State.MOVING_TO_WELL;
                setIndicator(rc);
            } else {
                Pathing.explore(rc);
                if (rc.isMovementReady()) { // Carriers can move up to twice per turn when unloaded
                    Pathing.explore(rc);
                }
            }
        }

        if (state == State.MOVING_TO_WELL) {
            if (!rc.getLocation().isAdjacentTo(collectingAt)) {
                Pathing.moveTowards(rc, collectingAt);
                if (rc.isMovementReady()) { // Carriers can move up to twice per turn when unloaded
                    Pathing.moveTowards(rc, collectingAt);
                }
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
                Pathing.moveTowards(rc, collectingAt);
            }
        }

        if (state == State.DROPPING_OFF) {
            // TODO: check that we still have resources; we may have thrown them to an enemy

            if (!rc.getLocation().isAdjacentTo(homeHQLoc)) {
                Pathing.moveTowards(rc, homeHQLoc); // TODO: return here?
            } else {
                for (ResourceType r : ResourceType.values()) {
                    if (rc.getResourceAmount(r) > 0) {
                        if (rc.canTransferResource(homeHQLoc, r, rc.getResourceAmount(r))) {
                            rc.transferResource(homeHQLoc, r, rc.getResourceAmount(r));
                        }
                    }
                }

                // Done dropping off (probably).
                if (isEmpty(rc)) {
                    if (rc.canTakeAnchor(homeHQLoc, Anchor.STANDARD)) {
                        rc.takeAnchor(homeHQLoc, Anchor.STANDARD);
                        state = State.DELIVERING_ANCHOR;
                        setIndicator(rc);
                    } else {
                        state = State.MOVING_TO_WELL;
                        setIndicator(rc);
                    }
                }
            }
        }

        if (state == State.DELIVERING_ANCHOR) {
            // If we're in island, drop anchor now.
            if (rc.canPlaceAnchor()) { // this checks everything for us.
                rc.placeAnchor();
                // TODO: state transition?
                targetIslandLoc = null;
            }

            // If I'm close to unoccupied island, go there.
            Set<MapLocation> nearbyNeutrals = senseNearbyNeutralIslandLocs(rc); // TODO: move this up front and only re-calculate if necessary.
            if (targetIslandLoc != null && nearbyNeutrals.contains(targetIslandLoc)) {
                // We're close to our target!! Stay with the same target (prevent jittering).
                Pathing.moveTowards(rc, targetIslandLoc, 0);
            } else if (nearbyNeutrals.size() > 0) {
                // We're close to a different one. It's nearby, so switch targets!
                targetIslandLoc = nearbyNeutrals.iterator().next(); // TODO: get closest one
                Pathing.moveTowards(rc, targetIslandLoc, 0);
            } else {
                // no nearby islands, continue to target (if known); otherwise, explore.
                if (targetIslandLoc != null) {
                    Pathing.moveTowards(rc, targetIslandLoc, 0);
                } else {
                    Pathing.explore(rc);
                }
            }
            setIndicator(rc);
        }
    }

    private static Set<MapLocation> senseNearbyNeutralIslandLocs(RobotController rc) throws GameActionException {
        int[] islands = rc.senseNearbyIslands();
        Set<MapLocation> neutralIslandLocs = new HashSet<>();
        for (int id : islands) {
            if (rc.senseTeamOccupyingIsland(id) == Team.NEUTRAL) {
                MapLocation[] thisIslandLocs = rc.senseNearbyIslandLocations(id);
                neutralIslandLocs.addAll(Arrays.asList(thisIslandLocs));
            }
        }
        return neutralIslandLocs;
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
}
