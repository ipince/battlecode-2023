package bobby;

import battlecode.common.Anchor;
import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.ResourceType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what we'll call once your robot
 * is created!
 */
public strictfp class RobotPlayer {

    // Constants that aren't defined in GameConstants.
    static final int CARRIER_AD_COST = RobotType.CARRIER.getBuildCost(ResourceType.ADAMANTIUM);
    static final int LAUNCHER_MN_COST = RobotType.LAUNCHER.getBuildCost(ResourceType.MANA);
    static final int ANCHOR_AD_COST = Anchor.STANDARD.getBuildCost(ResourceType.ADAMANTIUM);
    static final int ANCHOR_MN_COST = Anchor.STANDARD.getBuildCost(ResourceType.MANA);
    static final int ANCHOR_HP_STANDARD = 250;
    static final int ANCHOR_HP_ACCELERATING = 750;

    // Configuration params. Play around with these.
    static final int ANCHOR_OVERRIDE_HEALTH_PCT = 40;
    static final int SAVE_FOR_ANCHORS_ROUND_NUM = 400;

    static final boolean DEBUG = true; // set to false before submitting.
    static final boolean PROFILE = false; // print bytecode usage in some places.

    /**
     * We will use this variable to count the number of turns this robot has been alive.
     * You can use static variables like this to save any information you want. Keep in mind that even though
     * these variables are static, in Battlecode they aren't actually shared between your robots.
     */
    static int age = 0;

    /**
     * A random number generator.
     * We will use this RNG to make some random moves. The Random class is provided by the java.util.Random
     * import at the top of this file. Here, we *seed* the RNG with a constant number (6147); this makes sure
     * we get the same sequence of numbers every time this code is run. This is very useful for debugging!
     */
//    static final Random rng = new Random(6147);
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
    static List<Memory.Well> knownWellsNearMe = new ArrayList<>(); // NOT saved
    // Memory: things we know that the rest of the world may not know. Gets flushed when in-range to HQ/Amp/Islands.
    static Set<Memory.Well> memoryWells = new HashSet<>(); // NOT saved

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * It is like the main function for your robot. If this method returns, the robot dies!
     *
     * @param rc The RobotController object. You use it to perform actions from this robot, and to get
     *           information on its current status. Essentially your portal to interacting with the world.
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        rng.setSeed(rc.getID());

        while (true) {
            // This code runs during the entire lifespan of the robot, which is why it is in an infinite
            // loop. If we ever leave this loop and return from run(), the robot dies! At the end of the
            // loop, we call Clock.yield(), signifying that we've done everything we want to do.

            age += 1;  // We have now been alive for one more turn!

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode.
            try {
                // The same run() function is called for every robot on your team, even if they are
                // different types. Here, we separate the control depending on the RobotType, so we can
                // use different strategies on different robots. If you wish, you are free to rewrite
                // this into a different control structure!
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
                    case BOOSTER: // Examplefuncsplayer doesn't use any of these robot types below.
                    case DESTABILIZER: // You might want to give them a try!
                    case AMPLIFIER:
                        break;
                }

            } catch (GameActionException e) {
                // Oh no! It looks like we did something illegal in the Battlecode world. You should
                // handle GameActionExceptions judiciously, in case unexpected events occur in the game
                // world. Remember, uncaught exceptions cause your robot to explode!
                System.out.println(rc.getType() + " Exception");
                e.printStackTrace();

            } catch (Exception e) {
                // Oh no! It looks like our code tried to do something bad. This isn't a
                // GameActionException, so it's more likely to be a bug in our code.
                System.out.println(rc.getType() + " Exception");
                e.printStackTrace();

            } finally {
                // Signify we've done everything we want to do, thereby ending our turn.
                // This will make our code wait until the next turn, and then perform this loop again.
                Clock.yield();
            }
            // End of loop: go back to the top. Clock.yield() has ended, so it's time for another turn!
        }

        // Your code should never reach here (unless it's intentional)! Self-destruction imminent...
    }

    static void updateKnowledge(RobotController rc) throws GameActionException {
        int start = Clock.getBytecodeNum();

        knownHQs = Memory.readHeadquarters(rc, true, true); // TODO skip
        int allyHqsDone = Clock.getBytecodeNum();
        updateEnemyHQs(rc);
        int hqsDone = Clock.getBytecodeNum();
        if (shouldPrint(rc) && PROFILE) {
            System.out.println("read ally hqs took " + (allyHqsDone - start));
            System.out.println("enemy hqs took " + (hqsDone - allyHqsDone));
        }

        knownWells = Memory.readWells(rc);
        // Maybe someone else wrote the wells we've seen, so we don't need to write them anymore.
        // This might be a bit bytecode-intensive, especially because most of the time nothing changed.
        // TODO: consider tracking if knownWells changed.
        memoryWells.removeAll(knownWells.values());

        int wellsDone = Clock.getBytecodeNum();

        int took = Clock.getBytecodeNum() - start;
        if (shouldPrint(rc) && PROFILE)
            System.out.printf("UpdateKnowledge: took %d (hqs = %d, wells = %d)\n", took, hqsDone - start, wellsDone - hqsDone);
    }

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
                potentialEnemyHQs.clear();
            }
        } else { // we know!
            if (inferredSymmetry != Mapping.Symmetry.NOT_DECIPHERABLE_WITH_HQS_ALONE && !hqsAreSet) {
                knownEnemyHQs.clear();
                memoryEnemyHQs.clear();
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

        // Add every potential enemy HQ first.
        potentialEnemyHQs.clear();
        for (MapLocation allyHQ : knownHQs) {
            if (couldBeVerticallySymmetric) {
                MapLocation potential = Mapping.symmetries(rc, allyHQ, Mapping.Symmetry.VERTICAL);
                if (!knownHQs.contains(potential))
                    potentialEnemyHQs.add(Mapping.symmetries(rc, allyHQ, Mapping.Symmetry.VERTICAL));
            }
            if (couldBeHorizontallySymmetric) {
                MapLocation potential = Mapping.symmetries(rc, allyHQ, Mapping.Symmetry.HORIZONTAL);
                if (!knownHQs.contains(potential))
                    potentialEnemyHQs.add(Mapping.symmetries(rc, allyHQ, Mapping.Symmetry.HORIZONTAL));
            }
            if (couldBeRotationallySymmetric) {
                MapLocation potential = Mapping.symmetries(rc, allyHQ, Mapping.Symmetry.ROTATIONAL);
                if (!knownHQs.contains(potential))
                    potentialEnemyHQs.add(Mapping.symmetries(rc, allyHQ, Mapping.Symmetry.ROTATIONAL));
            }
        }
        // Remove the ones that we know ARE enemies (no longer "potential" or undecided).
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
            if (knownNotEnemyHQs.contains(Mapping.symmetries(rc, hq, Mapping.Symmetry.HORIZONTAL))) {
                couldBeHorizontallySymmetric = false;
            }
            if (knownNotEnemyHQs.contains(Mapping.symmetries(rc, hq, Mapping.Symmetry.VERTICAL))) {
                couldBeVerticallySymmetric = false;
            }
            if (knownNotEnemyHQs.contains(Mapping.symmetries(rc, hq, Mapping.Symmetry.ROTATIONAL))) {
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
            if (rc.canSenseLocation(potential)) { // can save bytecode using isWithin
                RobotInfo info = rc.senseRobotAtLocation(potential);
                if (info != null && info.getTeam() == rc.getTeam().opponent() && info.getType() == RobotType.HEADQUARTERS) {
                    memoryEnemyHQs.add(potential);
                } else { // no robot, or it's our team, or it's not an HQ => no enemy HQ here!
                    memoryNotEnemyHQs.add(potential);
                }
                iter.remove(); // now we know the truth, so it's no longer "potential"
            }
        }
    }

    static void maybeFlushEnemyHQs(RobotController rc) throws GameActionException {
        if (rc.canWriteSharedArray(0, 0)) { // within writing distance
            if (memoryEnemyHQs.size() > 0) {
                for (MapLocation enemyHQ : memoryEnemyHQs) {
                    Memory.writeHeadquarter(rc, enemyHQ, false, true);
                }
                memoryEnemyHQs.clear();
            }
            if (memoryNotEnemyHQs.size() > 0) {
                for (MapLocation notEnemyHQ : memoryNotEnemyHQs) {
                    Memory.writeHeadquarter(rc, notEnemyHQ, false, false);
                }
                memoryNotEnemyHQs.clear();
            }
        }
    }

    static void updateKnownWells(RobotController rc) {
        // TODO: move memory stuff here.
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

        // Draw things -- skip if production.
        for (Memory.Well well : memoryWells) {
            rc.setIndicatorDot(well.loc, 120, 120, 120);
        }
        for (Memory.Well well : knownWells.values()) {
            rc.setIndicatorDot(well.loc, well.saturated ? 255 : 0, well.saturated ? 0 : 255, 0);
        }
    }
}
