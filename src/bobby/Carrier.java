package bobby;

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

public class Carrier extends RobotPlayer {

    public static final int VISION_RADIUS = 20;
    public static final int MAX_DAMAGE = (int) Math.floor(GameConstants.CARRIER_DAMAGE_FACTOR * GameConstants.CARRIER_CAPACITY);

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
    private static int targetIslandId;

    public static void run(RobotController rc) throws GameActionException {
        rc.setIndicatorString("START! If seen, we're out of bytecode or exited somewhere weird");

        // Regardless of State, update information.
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

        updateKnowledge(rc, true);

        senseNearbyWells(rc);
        maybeFlushWells(rc);

        checkPotentialEnemyHQs(rc);
        maybeFlushEnemyHQs(rc);

        int startIslands = Clock.getBytecodeNum();
        updateNearbyIslands(rc);
        int islandsTook = Clock.getBytecodeNum() - startIslands;
        if (RobotPlayer.PROFILE) {
            System.out.println("sensing islands took " + islandsTook);
        }

        int took = Clock.getBytecodeNum() - startCodes;
        if (RobotPlayer.PROFILE) {
            System.out.println("knowledge/sensing took " + took + " bytecodes;  knownWells=" + knownWells.size() + ", memWells=" + memoryWells.size());
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
        if (homeHQLoc == null) {
            for (RobotInfo robot : rc.senseNearbyRobots()) {
                if (robot.getType() == RobotType.HEADQUARTERS && robot.getTeam() == rc.getTeam()) {
                    homeHQLoc = robot.getLocation();
                }
            }
        }
        if (homeHQLoc == null) {
            System.out.println("WARNING: no home. not sure what to do");
            return;
        }

        // If there's an Anchor available, take it to an Island.
        // TODO: pick up anchor here too.

        // Choose a well to mine.
        MapLocation well = pickWell(rc);
        if (well != null) {
            collectingAt = well;
            state = State.TO_WELL;
            return; // attempt to run TO_WELL state, since we haven't acted/moved yet.
        }

        // Haven't chosen a well yet, let's explore
        Pathing.explore(rc);
        if (rc.isMovementReady()) { // Carriers can move up to twice per turn when unloaded
            Pathing.explore(rc);
        }
    }

    private static MapLocation pickWell(RobotController rc) {
        if (rc.getRoundNum() < 60) {
            // Pick a well nearby in the early game.
            WellInfo[] wells = rc.senseNearbyWells();
            if (wells.length > 0) {
                // TODO: prioritize mana?
                // Choose a well randomly.
                return wells[rng.nextInt(wells.length)].getMapLocation();
            }
        }

        // If we're here, we don't have any nearby, or it's not early-game anymore. Spread out.
        MapLocation picked = null;
        if (knownWells.size() > 0) {
            picked = ((Memory.Well) knownWells.values().toArray()[rng.nextInt(knownWells.size())]).loc;
        } else if (memoryWells.size() > 0) {
            picked = ((Memory.Well) memoryWells.toArray()[rng.nextInt(memoryWells.size())]).loc;
        }

        if (picked != null) {
            MapLocation closest = homeHQLoc;
            for (MapLocation hq : knownHQs) {
                if (picked.distanceSquaredTo(hq) < picked.distanceSquaredTo(closest)) {
                    closest = hq;
                }
            }
            homeHQLoc = closest;
        }

        return picked; // can't find any wells
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

        maybeKillNearbyEnemy(rc);

        if (rc.canCollectResource(collectingAt, -1)) {
            rc.collectResource(collectingAt, -1);
        }

        // In the early game, go back to HQ earlier, so we can send reinforcements earlier.
        if (isFull(rc) || (rc.getRoundNum() < CARRIER_EARLY_RETURN_ROUND_NUM &&
                collectingAt.distanceSquaredTo(homeHQLoc) < CARRIER_EARLY_RETURN_WELL_RADIUS &&
                totalResources(rc) >= CARRIER_EARLY_RETURN_RESOURCE_AMOUNT)) {
            state = State.DROPPING_OFF;
            return;
        } else {
            // Move out of the way, in case there's crowding.
            Pathing.makeSpace(rc, collectingAt);
        }
    }

    private static void runDropoff(RobotController rc) throws GameActionException {

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
        // If we're in island, drop anchor now (but do not override).
        if (maybeDropAnchorAndTransition(rc)) {
            return; // continue turn elsewhere.
        }

        // If I'm close to unoccupied island, go there.
        if (nearbyNeutrals.size() > 0) {
            if (targetIslandLoc != null && nearbyNeutrals.contains(targetIslandId)) {
                // We're close to our target!! Stay with the same target (prevent jittering).
                Pathing.moveTowards(rc, targetIslandLoc, 0);
            } else {
                // We're close to a different one. It's nearby, so switch targets!
                targetIslandId = nearbyNeutrals.get(0); // TODO: get closest one. doesn't matter much
                targetIslandLoc = islands.get(targetIslandId).random(rc);
                Pathing.moveTowards(rc, targetIslandLoc, 0);
            }
        } else { // no nearby neutral islands, continue to target (if known)
            if (targetIslandLoc == null || islands.get(targetIslandId).team != Team.NEUTRAL) {
                // TODO: if our best choice is an island occupied by the enemy, try it!
                // First unset the current target, so that we have no target if we fail to select a new one.
                targetIslandId = -1;
                targetIslandLoc = null;

                // Select a new target.
                for (Island island : islands.values()) {
                    if (island.team == Team.NEUTRAL) {
                        targetIslandId = island.id;
                        targetIslandLoc = island.random(rc); // TODO: get closest one. here it matters more.
                        break;
                    }
                }
            }

            if (targetIslandLoc != null) {
                Pathing.moveTowards(rc, targetIslandLoc, 0);
            } else { // we know nothing, Jon sNow.
                Pathing.explore(rc);
            }
        }

        // Try again because we moved.
        if (maybeDropAnchorAndTransition(rc)) {
            return; // continue turn elsewhere.
        }
    }

    /**
     * Returns true if the anchor was dropped. We may or may not transition, but we'll
     * transition to TO_WELL in like 99% of cases.
     */
    private static boolean maybeDropAnchorAndTransition(RobotController rc) throws GameActionException {
        if (rc.canPlaceAnchor() && shouldPlaceAnchor(rc, rc.getLocation(), rc.getAnchor())) {
            rc.placeAnchor();
            targetIslandLoc = null;
            targetIslandId = -1;

            // Transition to collecting again.
            MapLocation well = pickWell(rc);
            if (well != null) {
                collectingAt = well;
                state = State.TO_WELL;
            } else {
                // this should basically never happen... If so, let's just stay here
                // occupying our island.
            }
            return true;
        }
        return false;
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
        int islandId = rc.senseIsland(loc);
        if (islandId == -1) {
            return false;
        }

        Island island = islands.get(islandId);
        if (island == null) {
            // This should never happen, but leaving it here in case we break the invariant
            // of "we have sensed all nearby islands at every turn."
            island = addOrUpdateIsland(rc, islandId);
        }

        return island.shouldAnchor(rc, anchor, ANCHOR_OVERRIDE_HEALTH_PCT);
    }

    // END STATE METHODS

    private static void maybeKillNearbyEnemy(RobotController rc) throws GameActionException {
        // If a nearby enemy is weak, kill it.
        RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            if (enemy.getType() == RobotType.HEADQUARTERS) {
                continue;
            }
            if (enemy.getHealth() <= MAX_DAMAGE /* to shortcut */ && currentDamage(rc) > enemy.getHealth()) {
                if (rc.canAttack(enemy.location)) {
                    rc.attack(enemy.location);
                }
            }
        }
    }

    private static boolean isEmpty(RobotController rc) {
        return totalResources(rc) == 0;
    }

    private static boolean isFull(RobotController rc) {
        return totalResources(rc) >= MAX_LOAD;
    }

    private static int totalResources(RobotController rc) {
        return rc.getResourceAmount(ResourceType.ADAMANTIUM) +
                rc.getResourceAmount(ResourceType.MANA) +
                rc.getResourceAmount(ResourceType.ELIXIR);
    }

    private static int currentDamage(RobotController rc) {
        return (int) Math.floor(5.0 * totalResources(rc) / 4);
    }

    private static void setIndicator(RobotController rc) {
        String data = "";
        if (state == State.DROPPING_OFF) {
            data = homeHQLoc.toString();
        } else if (state == State.TO_WELL || state == State.COLLECTING) {
            data = collectingAt.toString();
        } else if (state == State.ANCHORING) {
            data = targetIslandLoc != null ? targetIslandLoc.toString() : "null";
        }
        setIndicator(rc, state.toString(), data);

        rc.setIndicatorDot(homeHQLoc, 255, 255, 255);
        if (collectingAt != null) {
            rc.setIndicatorDot(collectingAt, 255, 255, 255);
        }
    }
}
