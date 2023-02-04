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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private static int targetIslandId;

    static class IslandInfo {
        int id;
        Set<MapLocation> locations;
        Team team;

        // Only set if occupied (i.e. team != Team.NEUTRAL)
        Anchor anchor;
        int health;

        int asOf;

        public IslandInfo(int id, Collection<MapLocation> locs, Team team, int asOf) {
            this.id = id;
            this.locations = new HashSet<>(locs);
            this.team = team;
            this.asOf = asOf;
        }

        public void setOccupier(Team team, Anchor anchor, int health) {
            this.team = team;
            this.anchor = anchor;
            this.health = health;
        }

        public MapLocation random(RobotController rc) {
            return locations.iterator().next(); // TODO: pick better
        }

        public boolean shouldAnchor(RobotController rc, Anchor newAnchor) {
            if (team == Team.NEUTRAL) {
                return true;
            } else if (team == rc.getTeam()) {
                // => if health is less than 40%
                if (anchor == Anchor.STANDARD && newAnchor == Anchor.ACCELERATING) {
                    return true; // override Standard by Accelerating anchor.
                }
                switch (anchor) {
                    case STANDARD:
                        return (100.0 * health / ANCHOR_HP_STANDARD) < ANCHOR_OVERRIDE_HEALTH_PCT;
                    case ACCELERATING:
                        return (100.0 * health / ANCHOR_HP_ACCELERATING) < ANCHOR_OVERRIDE_HEALTH_PCT;
                }
            }
            return false;
        }
    }

    private static Map<Integer, IslandInfo> islands = new HashMap<>();
    private static List<Integer> nearbyNeutrals = new ArrayList<>();

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

        updateKnowledge(rc, true);

        senseNearbyWells(rc);
        maybeFlushWells(rc);

        checkPotentialEnemyHQs(rc);
        maybeFlushEnemyHQs(rc);

        // TODO: islands
        int startIslands = Clock.getBytecodeNum();
        int[] nearbyIslands = rc.senseNearbyIslands();
        nearbyNeutrals.clear();
        for (int id : nearbyIslands) {
            // TODO: move to own function.
            Team team = rc.senseTeamOccupyingIsland(id);
            MapLocation[] locs = rc.senseNearbyIslandLocations(id);
            IslandInfo island = islands.get(id);
            if (island == null) {
                island = new IslandInfo(id, Arrays.asList(locs), team, rc.getRoundNum());
                islands.put(id, island);
            } else { // update
                island.locations.addAll(Arrays.asList(locs));
                island.asOf = rc.getRoundNum();
            }
            if (team != Team.NEUTRAL) {
                island.setOccupier(team, rc.senseAnchor(id), rc.senseAnchorPlantedHealth(id));
            } else {
                nearbyNeutrals.add(id);
            }
        }
        int islandsTook = Clock.getBytecodeNum() - startIslands;
        if (islandsTook > 1000) {
            System.out.println("sensing islands took " + (Clock.getBytecodeNum() - startIslands));
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
        MapLocation well = pickWell(rc);
        if (well != null) {
            collectingAt = well;
            state = State.TO_WELL;
            return; // attempt to run TO_WELL state, since we haven't acted/moved yet.
        }

        // Haven't chosen a well yet, let's explore
        Pathing.explore(rc);
        // TODO: consider removing this and just running the state again. Might be expensive due to re-sensing.
        if (rc.isMovementReady()) { // Carriers can move up to twice per turn when unloaded
            Pathing.explore(rc);
        }
    }

    private static MapLocation pickWell(RobotController rc) {
        if (rc.getRoundNum() < 20) {
            // Pick a well nearby in the early game.
            WellInfo[] wells = rc.senseNearbyWells(); // TODO: check knownWells or memoryWells?
            if (wells.length > 0) {
                // Choose a well randomly. TODO: choose more intelligently. -> choose mana
                rng.nextInt(wells.length); // Drop first value, which is always 0 (why?) TODO
                return wells[rng.nextInt(wells.length)].getMapLocation();
            }
        } else { // pick a known one randomly
            if (knownWells.size() > 0) { // TODO: check memory too.
                return ((Memory.Well) knownWells.values().toArray()[rng.nextInt(knownWells.size())]).loc;
            }
        }
        // TODO: change hqLoc to HQ closest to our well, to avoid being stupidly inefficient.
        return null; // can't find any wells
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

    private static void collect(RobotController rc) throws GameActionException {
        if (rc.canCollectResource(collectingAt, -1)) {
            rc.collectResource(collectingAt, -1);
        }
        // do it again in case action cooldown allows for it (once every 4 turns)
        if (rc.canCollectResource(collectingAt, -1)) {
            rc.collectResource(collectingAt, -1);
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
                for (IslandInfo island : islands.values()) {
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

        IslandInfo island = islands.get(islandId);
        if (island == null) {
            // This should never happen, but leaving it here in case we break the invariant
            // of "we have sensed all nearby islands at every turn."
            // TODO: build island and add to map.
        }

        return island.shouldAnchor(rc, anchor);
    }

    // END STATE METHODS

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
        if (state == Carrier.State.DROPPING_OFF) { // TODO: move state into states themselves.
            data = homeHQLoc.toString();
        } else if (state == Carrier.State.TO_WELL) {
            data = collectingAt.toString();
        } else if (state == Carrier.State.ANCHORING) {
            data = targetIslandLoc != null ? targetIslandLoc.toString() : "null";
        }
        setIndicator(rc, state.toString(), data);

        // Islands
        for (IslandInfo island : islands.values()) {
            for (MapLocation loc : island.locations) {
                int rgb = island.team == Team.NEUTRAL ? 255 : (int) 255.0 * island.health / island.anchor.totalHealth;
                rc.setIndicatorDot(loc,
                        island.team == Team.A ? rgb : island.team == Team.NEUTRAL ? rgb : 0,
                        island.team == Team.A ? 0 : island.team == Team.NEUTRAL ? 0 : 0,
                        island.team == Team.A ? 0 : island.team == Team.NEUTRAL ? rgb : rgb);
            }
        }
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
