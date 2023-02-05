package bobby;

import battlecode.common.Anchor;
import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.ResourceType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Team;
import battlecode.common.WellInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public strictfp class RobotPlayer {

    // Constants that aren't defined in GameConstants.
    static final int CARRIER_AD_COST = RobotType.CARRIER.getBuildCost(ResourceType.ADAMANTIUM);
    static final int LAUNCHER_MN_COST = RobotType.LAUNCHER.getBuildCost(ResourceType.MANA);
    static final int ANCHOR_AD_COST = Anchor.STANDARD.getBuildCost(ResourceType.ADAMANTIUM);
    static final int ANCHOR_MN_COST = Anchor.STANDARD.getBuildCost(ResourceType.MANA);
    static final int CLOUD_VISION_RADIUS = 4;

    // Configuration params. Play around with these.
    static final int ANCHOR_OVERRIDE_HEALTH_PCT = 40;
    static final int SAVE_FOR_ANCHORS_ROUND_NUM = 400;
    static final int CARRIER_EARLY_RETURN_ROUND_NUM = 60;
    static final int CARRIER_EARLY_RETURN_WELL_RADIUS = 40;
    static final int CARRIER_EARLY_RETURN_RESOURCE_AMOUNT = 25;
    static final int NUMBER_ENEMIES_FOR_SIEGE = 7;

    static final boolean DEBUG = false; // set to false before submitting.
    static final boolean PROFILE = false; // print bytecode usage in some places.
    static List<CommandTime> profilingInfo = new ArrayList<>();

    static class CommandTime {
        String cmd;
        int took;

        public CommandTime(String cmd, int took) {
            this.cmd = cmd;
            this.took = took;
        }

        public String over(int total) {
            return String.format("\n  %s took %d (%.2f%% of total %d)", cmd, took, 100.0 * took / total, total);
        }
    }

    static final Random rng = new Random();

    // Knowledge
    static List<MapLocation> knownHQs = new ArrayList<>(); // saved in array
    static List<MapLocation> knownEnemyHQs = new ArrayList<>(); // saved in array
    static List<MapLocation> knownNotEnemyHQs = new ArrayList<>(); // saved in array

    static Set<MapLocation> memoryEnemyHQs = new HashSet<>(); // NOT saved in array
    static Set<MapLocation> memoryNotEnemyHQs = new HashSet<>(); // NOT saved in array
    static Set<MapLocation> potentialEnemyHQs = new HashSet<>(); // NOT saved; does not include confirmed ones.

    static boolean couldBeVerticallySymmetric = true;
    static boolean couldBeHorizontallySymmetric = true;
    static boolean couldBeRotationallySymmetric = true;
    static Mapping.Symmetry inferredSymmetry;
    static boolean hqsAreSet = false;

    static Map<MapLocation, Memory.Well> knownWells = new HashMap<>(); // saved in array
    static Set<Memory.Well> memoryWells = new HashSet<>(); // NOT saved

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * It is like the main function for your robot. If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        rng.setSeed(rc.getID());

        while (true) {
            // This code runs during the entire lifespan of the robot, which is why it is in an infinite
            // loop. If we ever leave this loop and return from run(), the robot dies! At the end of the
            // loop, we call Clock.yield(), signifying that we've done everything we want to do.

            int startRound = rc.getRoundNum();
            int start = Clock.getBytecodeNum();

            try {
                switch (rc.getType()) {
                    case HEADQUARTERS:
                        Headquarter.run(rc);
                        break;
                    case CARRIER:
                        Carrier.run(rc);
                        break;
                    case LAUNCHER:
                        Launcher.run(rc);
                        break;
                    case BOOSTER:
                    case DESTABILIZER:
                    case AMPLIFIER:
                        break;
                }
            } catch (GameActionException e) {
                System.out.println(rc.getType() + " Exception");
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println(rc.getType() + " Exception");
                e.printStackTrace();
            } finally {
                int endRound = rc.getRoundNum();
                int took = Clock.getBytecodeNum() - start;
                if (startRound != endRound) {
                    took = 12500;
                }
                if (took > 12000) {
                    StringBuilder sb = new StringBuilder("Ran (or almost ran) out of bytecode on round " + startRound + "!");
                    for (CommandTime ct : profilingInfo) {
                        sb.append(ct.over(took));
                    }
                    System.out.println(sb);
                }
                profilingInfo.clear(); // clear for next round.

                // Signify we've done everything we want to do, thereby ending our turn.
                // This will make our code wait until the next turn, and then perform this loop again.
                Clock.yield();
            }
            // End of loop: go back to the top. Clock.yield() has ended, so it's time for another turn!
        }
        // Code should never reach here! Self-destruction imminent...
    }

    static void updateKnowledge(RobotController rc, boolean includeWells) throws GameActionException {
        // before: 944k, 50k
        // after removing ally hq: 771k, 54k.
        int start = Clock.getBytecodeNum();

        if (knownHQs.isEmpty()) {
            knownHQs = Memory.readHeadquarters(rc, true, true);
        }
        int allyHqsDone = Clock.getBytecodeNum();
        updateEnemyHQs(rc);
        int hqsDone = Clock.getBytecodeNum();
        if (shouldPrint(rc) && PROFILE) {
            System.out.println("enemy hqs took " + (hqsDone - allyHqsDone));
        }

        if (includeWells) {
            knownWells = Memory.readWells(rc);
            // Maybe someone else wrote the wells we've seen, so we don't need to write them anymore.
            // This might be a bit bytecode-intensive, especially because most of the time nothing changed.
            // TODO: consider tracking if knownWells changed.
            memoryWells.removeAll(knownWells.values());
        }

        int wellsDone = Clock.getBytecodeNum();

        int took = Clock.getBytecodeNum() - start;
        if (shouldPrint(rc) && PROFILE)
            System.out.printf("UpdateKnowledge: took %d (hqs = %d, wells = %d)\n", took, hqsDone - start, wellsDone - hqsDone);
    }

    // ENEMY HQs and Map symmetry.

    private static void updateEnemyHQs(RobotController rc) throws GameActionException {
        if (inferredSymmetry == null) {
            knownEnemyHQs = Memory.readHeadquarters(rc, false, true);
            knownNotEnemyHQs = Memory.readHeadquarters(rc, false, false);
            inferSymmetry(rc);
        }
        if (inferredSymmetry == null) { // still not known? ok lets try potential locations
            updatePotentialEnemyHQs(rc);
            if (knownEnemyHQs.size() + memoryEnemyHQs.size() + potentialEnemyHQs.size() == knownHQs.size()) {
                // then all potentials must be enemy HQs!
                memoryEnemyHQs.addAll(potentialEnemyHQs);
                potentialEnemyHQs.clear(); // we're done.
            }
        } else { // we know!
            if (inferredSymmetry != Mapping.Symmetry.NOT_DECIPHERABLE_WITH_HQS_ALONE && !hqsAreSet) {
                knownEnemyHQs.clear();
                memoryEnemyHQs.clear(); // TODO: we inferred it, but we'll never write it back.
                memoryNotEnemyHQs.clear();
                potentialEnemyHQs.clear();
                for (MapLocation hq : knownHQs) {
                    knownEnemyHQs.add(Mapping.symmetries(rc, hq, inferredSymmetry));
                }
                hqsAreSet = true;
            } // if undecipherable, it must be because we already know all HQs, so we can skip (do nothing).
        }
    }

    private static void updatePotentialEnemyHQs(RobotController rc) {
        // This method might not seem necessary. If we add the potentials once, and then we are
        // disciplined about removing potentials when we confirm or deny a potential HQ, then we
        // shouldn't have to rebuild the set. However, if we rule out a particular map symmetry,
        // we do want to remove those points from the potential list, so we save some compute.
        // NOTE: we could be a bit better about this, and instead clear the potentials when we
        // infer the symmetry. TODO.

        // Add every potential enemy HQ first.
        potentialEnemyHQs.clear();
        for (MapLocation allyHQ : knownHQs) {
            if (couldBeVerticallySymmetric) {
                MapLocation potential = Mapping.symmetries(rc, allyHQ, Mapping.Symmetry.VERTICAL);
                if (!knownHQs.contains(potential))
                    potentialEnemyHQs.add(potential);
            }
            if (couldBeHorizontallySymmetric) {
                MapLocation potential = Mapping.symmetries(rc, allyHQ, Mapping.Symmetry.HORIZONTAL);
                if (!knownHQs.contains(potential))
                    potentialEnemyHQs.add(potential);//Mapping.symmetries(rc, allyHQ, Mapping.Symmetry.HORIZONTAL));
            }
            if (couldBeRotationallySymmetric) {
                MapLocation potential = Mapping.symmetries(rc, allyHQ, Mapping.Symmetry.ROTATIONAL);
                if (!knownHQs.contains(potential))
                    potentialEnemyHQs.add(potential);//Mapping.symmetries(rc, allyHQ, Mapping.Symmetry.ROTATIONAL));
            }
        }
        // Remove the ones that we know ARE enemies (no longer "potential" or undecided).
        // NOTE: this way of iteration is more efficient than .removeAll().
        for (MapLocation enemyHQ : knownEnemyHQs) {
            potentialEnemyHQs.remove(enemyHQ);
        }
        for (MapLocation enemyHQ : memoryEnemyHQs) {
            potentialEnemyHQs.remove(enemyHQ);
        }
        // Remove the ones that we know are NOT enemies.
        for (MapLocation enemyHQ : knownNotEnemyHQs) {
            potentialEnemyHQs.remove(enemyHQ);
        }
        for (MapLocation enemyHQ : memoryNotEnemyHQs) {
            potentialEnemyHQs.remove(enemyHQ);
        }
    }

    private static void inferSymmetry(RobotController rc) {
        if (inferredSymmetry != null) return;

        if (knownEnemyHQs.size() == knownHQs.size()) {
            inferSymmetryWithAllKnownHQs(rc);
        } else {
            inferSymmetryWithPartialHQs(rc);
        }
    }

    // Precondition: knownHQs.size() == knownEnemyHQs.size()
    private static void inferSymmetryWithAllKnownHQs(RobotController rc) {
        // We know all HQs, so we can probably deduce symmetry. If not, then symmetry is not
        // determinable from HQ locations alone.

        // Try each symmetry. If any doesn't match, rule it out.
        if (couldBeHorizontallySymmetric) {
            for (MapLocation hq : knownHQs) {
                if (!knownEnemyHQs.contains(Mapping.symmetries(rc, hq, Mapping.Symmetry.HORIZONTAL))) {
                    couldBeHorizontallySymmetric = false;
                }
            }
        }
        if (couldBeVerticallySymmetric) {
            for (MapLocation hq : knownHQs) {
                if (!knownEnemyHQs.contains(Mapping.symmetries(rc, hq, Mapping.Symmetry.VERTICAL))) {
                    couldBeVerticallySymmetric = false;
                }
            }
        }
        if (couldBeRotationallySymmetric) {
            for (MapLocation hq : knownHQs) {
                if (!knownEnemyHQs.contains(Mapping.symmetries(rc, hq, Mapping.Symmetry.ROTATIONAL))) {
                    couldBeRotationallySymmetric = false;
                }
            }
        }

        if (!couldBeHorizontallySymmetric && !couldBeVerticallySymmetric) {
            inferredSymmetry = Mapping.Symmetry.ROTATIONAL;
        } else if (!couldBeHorizontallySymmetric && !couldBeRotationallySymmetric) {
            inferredSymmetry = Mapping.Symmetry.VERTICAL;
        } else if (!couldBeVerticallySymmetric && !couldBeRotationallySymmetric) {
            inferredSymmetry = Mapping.Symmetry.HORIZONTAL;
        } else {
            // Sometimes we can't know based on HQ locations alone, but this can help us
            // save bytecode in the future.
            inferredSymmetry = Mapping.Symmetry.NOT_DECIPHERABLE_WITH_HQS_ALONE;
        }
    }

    private static void inferSymmetryWithPartialHQs(RobotController rc) {
        for (MapLocation hq : knownHQs) {
            // Add the couldBes in the if-clause to short-circuit and reduce bytecode (from ~114k -> 98k in test game).
            if (couldBeHorizontallySymmetric && knownNotEnemyHQs.contains(Mapping.symmetries(rc, hq, Mapping.Symmetry.HORIZONTAL))) {
                couldBeHorizontallySymmetric = false;
                // TODO: remove all horizontal points from potential
            }
            if (couldBeVerticallySymmetric && knownNotEnemyHQs.contains(Mapping.symmetries(rc, hq, Mapping.Symmetry.VERTICAL))) {
                couldBeVerticallySymmetric = false;
            }
            if (couldBeRotationallySymmetric && knownNotEnemyHQs.contains(Mapping.symmetries(rc, hq, Mapping.Symmetry.ROTATIONAL))) {
                couldBeRotationallySymmetric = false;
            }
        }

        if (!couldBeHorizontallySymmetric && !couldBeVerticallySymmetric) {
            inferredSymmetry = Mapping.Symmetry.ROTATIONAL;
        } else if (!couldBeHorizontallySymmetric && !couldBeRotationallySymmetric) {
            inferredSymmetry = Mapping.Symmetry.VERTICAL;
        } else if (!couldBeVerticallySymmetric && !couldBeRotationallySymmetric) {
            inferredSymmetry = Mapping.Symmetry.HORIZONTAL;
        }
    }

    static void checkPotentialEnemyHQs(RobotController rc) throws GameActionException {
        Iterator<MapLocation> iter = potentialEnemyHQs.iterator();
        while (iter.hasNext()) {
            MapLocation potential = iter.next();
            if (rc.getLocation().isWithinDistanceSquared(potential, rc.getType().visionRadiusSquared)) {
                if (rc.canSenseLocation(potential)) {
                    RobotInfo info = rc.senseRobotAtLocation(potential);
                    if (info != null && info.getTeam() == rc.getTeam().opponent() && info.getType() == RobotType.HEADQUARTERS) {
                        memoryEnemyHQs.add(potential);
                    } else { // no robot, or it's our team, or it's not an HQ => no enemy HQ here!
                        memoryNotEnemyHQs.add(potential);
                    }
                    iter.remove(); // now we know the truth, so it's no longer "potential"
                } else {
                    // Either they are in a cloud, or we are in a cloud. Either way, we need to get
                    // closer. Let the Pathing get closer. Eventually, we will be close enough (hopefully).
                    // When we do, we'll hit the if-branch, so here we do nothing.
                }
            }
        }
    }

    static void maybeFlushEnemyHQs(RobotController rc) throws GameActionException {
        if (rc.canWriteSharedArray(0, 0)) { // within writing distance
            if (memoryEnemyHQs.size() > 0) {
                for (Iterator<MapLocation> i = memoryEnemyHQs.iterator(); i.hasNext(); ) {
                    MapLocation enemyHQ = i.next();
                    boolean written = Memory.writeHeadquarter(rc, enemyHQ, false, true);
                    if (written) {
                        i.remove();
                    }
                }
            }
            if (memoryNotEnemyHQs.size() > 0) {
                for (Iterator<MapLocation> i = memoryNotEnemyHQs.iterator(); i.hasNext(); ) {
                    MapLocation notEnemyHQ = i.next();
                    boolean written = Memory.writeHeadquarter(rc, notEnemyHQ, false, false);
                    if (written) {
                        i.remove();
                    }
                }
            }
        }
    }

    // WELLS

    static void senseNearbyWells(RobotController rc) {
        WellInfo[] wellInfos = rc.senseNearbyWells();
        for (WellInfo wi : wellInfos) {
            if (!knownWells.containsKey(wi.getMapLocation())) {
                // Found a new well... keep it in memory so we can write it back when close to comms.
                memoryWells.add(Memory.Well.from(wi, false));
            }
        }
    }

    static void maybeFlushWells(RobotController rc) throws GameActionException {
        // Flush memory if we can.
        if (memoryWells.size() > 0 && rc.canWriteSharedArray(0, 0)) { // in-range
            knownWells = Memory.maybeWriteWells(rc, memoryWells);
            // Don't clear all, because maybe we failed to write some. If we succeeded, we'll read next round
            memoryWells.removeAll(knownWells.values());
        }
    }

    // ISLANDS
    static Map<Integer, Island> islands = new HashMap<>();
    static List<Integer> nearbyNeutrals = new ArrayList<>();

    static void updateNearbyIslands(RobotController rc) throws GameActionException {
        int[] nearbyIslands = rc.senseNearbyIslands();
        nearbyNeutrals.clear();
        for (int id : nearbyIslands) {
            Island island = addOrUpdateIsland(rc, id);
            if (island.team == Team.NEUTRAL) {
                nearbyNeutrals.add(id);
            }
        }
    }

    // Precondition: id must be in sensing range!
    static Island addOrUpdateIsland(RobotController rc, int id) throws GameActionException {
        Team team = rc.senseTeamOccupyingIsland(id);
        MapLocation[] locs = rc.senseNearbyIslandLocations(id);
        Island island = islands.get(id);
        if (island == null) { // add new
            island = new Island(id, Arrays.asList(locs), team, rc.getRoundNum());
            islands.put(id, island);
        } else { // update existing
            island.locations.addAll(Arrays.asList(locs));
            island.asOf = rc.getRoundNum();
            if (team == Team.NEUTRAL) {
                island.clearOccupier();
            }
        }
        if (team != Team.NEUTRAL) {
            island.setOccupier(team, rc.senseAnchor(id), rc.senseAnchorPlantedHealth(id));
        }
        return island;
    }

    // DEBUGGING methods below.

    static boolean shouldPrint(RobotController rc) {
        return DEBUG;
    }

    static void setIndicator(RobotController rc, String state, String data) {
        if (!DEBUG) return;

        // String: STATE <data> | PathingString
        StringBuilder sb = new StringBuilder(state);
        if (data != null && data != "") {
            sb.append(" " + data);
        }
        if (Pathing.indicatorString != null && Pathing.indicatorString != "") {
            sb.append(" | " + Pathing.indicatorString);
        }
        rc.setIndicatorString(sb.toString());
        if (Pathing.dest != null) {
            rc.setIndicatorLine(rc.getLocation(), Pathing.dest, 0, 0, 255);
            if (Pathing.start != null) {
                rc.setIndicatorLine(Pathing.start, Pathing.dest, 120, 120, 120);
            }
        }

        // HQs: known committed red, known in-memory pink; known not committed black, known in-memory dark gray, potential light gray.
        for (MapLocation loc : knownHQs) {
            rc.setIndicatorDot(loc, 0, 0, 255);
        }
        for (MapLocation loc : knownEnemyHQs) {
            rc.setIndicatorDot(loc, 255, 0, 0);
        }
        for (MapLocation loc : memoryEnemyHQs) {
            rc.setIndicatorDot(loc, 255, 150, 150);
        }
        for (MapLocation loc : knownNotEnemyHQs) {
            rc.setIndicatorDot(loc, 0, 0, 0);
        }
        for (MapLocation loc : memoryNotEnemyHQs) {
            rc.setIndicatorDot(loc, 100, 100, 100);
        }
        for (MapLocation loc : potentialEnemyHQs) {
            rc.setIndicatorDot(loc, 200, 200, 200);
        }

        // Wells
        for (Memory.Well well : memoryWells) {
            rc.setIndicatorDot(well.loc, 120, 120, 120);
        }
        for (Memory.Well well : knownWells.values()) {
            rc.setIndicatorDot(well.loc, well.saturated ? 255 : 0, well.saturated ? 0 : 255, 0);
        }

        // Islands
        for (Island island : islands.values()) {
            for (MapLocation loc : island.locations) {
                int rgb = island.team == Team.NEUTRAL ? 255 : (int) 245.0 * island.health / island.anchor.totalHealth + 10;
                rc.setIndicatorDot(loc,
                        island.team == Team.A ? rgb : island.team == Team.NEUTRAL ? rgb : 0,
                        island.team == Team.A ? 0 : island.team == Team.NEUTRAL ? 0 : 0,
                        island.team == Team.A ? 0 : island.team == Team.NEUTRAL ? rgb : rgb);
            }
        }
    }

    static void profile(String cmd, int took) {
        profilingInfo.add(new CommandTime(cmd, took));
    }
}
