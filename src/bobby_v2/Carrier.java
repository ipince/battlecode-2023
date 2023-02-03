package bobby_v2;

import battlecode.common.Anchor;
import battlecode.common.Clock;
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
        TO_WELL,
        COLLECTING,
        DROPPING_OFF,
        ANCHORING;
    }

    private static State state = State.UNASSIGNED;

    // General state;
    private static MapLocation homeHQLoc;

    // State used for each state:
    // UNASSIGNED

    // MOVING_TO_WELL
    private static MapLocation collectingAt;

    // DELIVERING_ANCHOR
    private static MapLocation targetIslandLoc;

    public static void run(RobotController rc) throws GameActionException {
        // TODO: After executing the major actions, I should always consider: can i kill a nearby robot?
        // can i sense important information? if so, can i write it back to shared memory?

        // First sense, then act/move. Try to ensure that I both act _and_ move everytime.

        rc.setIndicatorString("START! If seen, we're out of bytecode or exited somewhere weird");

        // Regardless of State
        updateKnowledgeAndSense(rc);

        // State machine
        State startState = state;
        runState(rc, startState);
        if (state != startState && (rc.isMovementReady() || rc.isActionReady())) {
            // We had a state transition. Run states again just in case we can act again.
            // Note that if we moved, then we might have to re-sense stuff, but let's ignore that for now.
            runState(rc, state);
        }
        setIndicator(rc);
    }

    private static void updateKnowledgeAndSense(RobotController rc) throws GameActionException {
        int startCodes = Clock.getBytecodeNum();

        // 1) Update knowledge. We may later do this less often if it takes too much bytecode.
        updateKnowledge(rc);

        // 2) Sense nearby information and communicate it back.
        // 2a) Wells
        // maybe this can be done after the state code is done, if we have enough bytecode left?
        WellInfo[] wellInfos = rc.senseNearbyWells();
        for (WellInfo wi : wellInfos) {
            if (!knownWells.containsKey(wi.getMapLocation())) {
                // Found a new well... keep it in memory so we can write it back when close to comms.
                memoryWells.add(Memory.Well.from(wi, false));
            }
        }

        checkPotentialEnemyHQs(rc);
        maybeFlushEnemyHQs(rc);
//
//        int[] nearbyIslands = rc.senseNearbyIslands();
//        for (int island : nearbyIslands) {
//
//        }

        // Flush memory if we can.
        int flushedWells = 0;
        String toFlush = "flushing: ";
        if (memoryWells.size() > 0 && rc.canWriteSharedArray(0, 0)) { // in-range
            for (Memory.Well well : memoryWells) {
                toFlush += well.loc + " ";
            }
            Memory.maybeWriteWells(rc, memoryWells);
            flushedWells = memoryWells.size();
            memoryWells.clear(); // TODO: do this at the end, or re-read or merge into known. TODO: not always clear.
        }

        int took = Clock.getBytecodeNum() - startCodes;
        if (took > 8000) {
            System.out.println(toFlush);
            System.out.println("knowledge/sensing took " + took + " bytecodes;  knownWells=" + knownWells.size() + ", memWells=" + memoryWells.size() + ", flushedWells=" + flushedWells);
        }

        rc.setIndicatorString("Past sensing..."); // in case we're cut off.
    }

    private static void runState(RobotController rc, State state) throws GameActionException {
        switch (state) {
            case UNASSIGNED:
                runUnassigned(rc);
                break;

            case TO_WELL:
                runToWell(rc);
                break;
            case COLLECTING:
                runCollecting(rc);
                break;
            case DROPPING_OFF:
                runDropoff(rc);
                break;

            case ANCHORING:
                runAnchoring(rc);
                break;

            default:
                // do nothing
        }
    }

    private static void runUnassigned(RobotController rc) throws GameActionException {
        // I was just born.
        for (RobotInfo robot : rc.senseNearbyRobots()) {
            // TODO: what if multiple HQs spawn within sight?
            if (robot.getType() == RobotType.HEADQUARTERS && robot.getTeam() == rc.getTeam()) {
                homeHQLoc = robot.getLocation();
            }
        }
        if (homeHQLoc == null) {
            System.out.println("WARNING: no home. not sure what to do");
            return;
        }

        // If there's an Anchor available, take it to an Island.
        // TODO: pick up anchor here too.

        // Choose a well to mine.
        if (rc.getRoundNum() < 20) {
            // Pick a well nearby in the early game.
            WellInfo[] wells = rc.senseNearbyWells();
            if (wells.length > 0) {
                // Choose a well randomly. TODO: choose more intelligently.
                rng.nextInt(wells.length); // Drop first value, which is always 0 (why?)
                collectingAt = wells[rng.nextInt(wells.length)].getMapLocation();
                state = State.TO_WELL;
                return;
            }
        } else { // pick a known one randomly
            if (knownWells.size() > 0) {
                collectingAt = ((Memory.Well) knownWells.values().toArray()[rng.nextInt(knownWells.size())]).loc;
                state = State.TO_WELL;
                return;
            }
        }

        // Haven't chosen a well yet, let's explore
        Pathing.explore(rc);
        // TODO: consider removing this and just running the state again. Might be expensive due to re-sensing.
        if (rc.isMovementReady()) { // Carriers can move up to twice per turn when unloaded
            Pathing.explore(rc);
        }
    }

    private static void runToWell(RobotController rc) throws GameActionException {
        if (!rc.getLocation().isAdjacentTo(collectingAt)) {
            Pathing.moveTowards(rc, collectingAt);
            if (rc.isMovementReady()) { // Carriers can move up to twice per turn when unloaded
                Pathing.moveTowards(rc, collectingAt);
            }
        } else { // we're close enough to collect!
            state = State.COLLECTING;
            return;
        }
    }

    private static void runCollecting(RobotController rc) throws GameActionException {
        // Invariant: we're adjacent to a well.

        collect(rc);
        if (isFull(rc)) {
            state = State.DROPPING_OFF;
            return;
        } else {
            // Move out of the way, if there's crowding. TODO
            Pathing.moveTowards(rc, collectingAt);
        }
    }

    private static void runDropoff(RobotController rc) throws GameActionException {
        // TODO: check that we still have resources; we may have thrown them to an enemy

        if (!rc.getLocation().isAdjacentTo(homeHQLoc)) {
            Pathing.moveTowards(rc, homeHQLoc);
        }

        if (rc.getLocation().isAdjacentTo(homeHQLoc)) {
            dropOff(rc);
        }

        if (isEmpty(rc) && rc.isActionReady()) { // may need to delay a turn here.
            if (rc.canTakeAnchor(homeHQLoc, Anchor.STANDARD)) {
                rc.takeAnchor(homeHQLoc, Anchor.STANDARD);
                state = State.ANCHORING;
                return;
            } else {
                state = State.TO_WELL;
                return;
            }
        }
    }

    private static void dropOff(RobotController rc) throws GameActionException {
        ResourceType res = ResourceType.ADAMANTIUM;
        if (rc.getResourceAmount(res) > 0) {
            if (rc.canTransferResource(homeHQLoc, res, rc.getResourceAmount(res))) {
                rc.transferResource(homeHQLoc, res, rc.getResourceAmount(res));
            }
        }
        res = ResourceType.MANA;
        if (rc.getResourceAmount(res) > 0) {
            if (rc.canTransferResource(homeHQLoc, res, rc.getResourceAmount(res))) {
                rc.transferResource(homeHQLoc, res, rc.getResourceAmount(res));
            }
        }
    }

    private static void runAnchoring(RobotController rc) throws GameActionException {
        // TODO: fix bug if targetIslandLoc != null but island has already been anchored.
        // If we're in island, drop anchor now (but do not override).
        if (shouldPlaceAnchor(rc, rc.getLocation(), rc.getAnchor()) && rc.canPlaceAnchor()) {
            rc.placeAnchor();
            // TODO: state transition? -> occupy?
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
    }

    // END STATE METHODS

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

    /**
     * Returns true iff the robot has an anchor, AND the given location is on an island,
     * AND (the island is not occupied, OR, if it's occupied by the current team,
     * (the anchor's health is < 40%, OR the new anchor is a better class of anchor)).
     */
    private static boolean shouldPlaceAnchor(RobotController rc, MapLocation loc, Anchor anchor) throws GameActionException {
        if (anchor == null) { // no anchor is being held.
            return false;
        }
        int island = rc.senseIsland(loc);
        if (island != -1) {
            Team team = rc.senseTeamOccupyingIsland(island);
            if (team == Team.NEUTRAL) {
                return true;
            } else if (team == rc.getTeam()) {
                // => if health is less than 40%
                Anchor placedAnchor = rc.senseAnchor(island); // could maybe be null in edge cases?
                if (placedAnchor == Anchor.STANDARD && anchor == Anchor.ACCELERATING) {
                    return true; // override Standard by Accelerating anchor.
                }
                int health = rc.senseAnchorPlantedHealth(island);
                switch (placedAnchor) {
                    case STANDARD:
                        return (100.0 * health / ANCHOR_HP_STANDARD) < ANCHOR_OVERRIDE_HEALTH_PCT;
                    case ACCELERATING:
                        return (100.0 * health / ANCHOR_HP_ACCELERATING) < ANCHOR_OVERRIDE_HEALTH_PCT;
                }
            }
        }
        return false;
    }

    private static void collect(RobotController rc) throws GameActionException {
        if (rc.canCollectResource(collectingAt, -1)) {
            rc.collectResource(collectingAt, -1);
        }
        // do it again in case action cooldown allows for it (once every 4 turns)
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

    private static void setIndicator(RobotController rc) {
        String data = "";
        if (state == State.DROPPING_OFF) { // TODO: move state into states themselves.
            data = homeHQLoc.toString();
        } else if (state == State.TO_WELL) {
            data = collectingAt.toString();
        } else if (state == State.ANCHORING) {
            data = targetIslandLoc != null ? targetIslandLoc.toString() : "null";
        }
        setIndicator(rc, state.toString(), data);
    }

    private static void maybeAttack(RobotController rc) throws GameActionException {
        // If a nearby enemy is weak, kill it.
        // Moreover, if we know other Carriers are around and we can collectively kill it, kill it. how?
        // pick it, then rc.senseNearbyRobots(enemy.loc, carrier.actionRadius) <- friendlies... hmm, but we don't know how much res they have.
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
