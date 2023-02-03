package bobby_v1;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what we'll call once your robot
 * is created!
 */
public strictfp class RobotPlayer {

    // Constants that aren't defined in GameConstants.
    static final int CARRIER_AD_COST = 40; // See RobotType.CARRIER.getBuildCost(ResourceType);
    static final int LAUNCHER_MN_COST = 60;
    static final int ANCHOR_HP_STANDARD = 250;
    static final int ANCHOR_HP_ACCELERATING = 750;

    // Configuration params. Play around with these.
    static final int ANCHOR_OVERRIDE_HEALTH_PCT = 40;
    static final int SAVE_FOR_ANCHORS_ROUND_NUM = 150;

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

    /**
     * Array containing all the possible movement directions.
     */
    static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };

    // Knowledge
    static List<MapLocation> knownHQs = new ArrayList<>();
    static Map<MapLocation, Memory.Well> knownWells = new HashMap<>();
    static List<Memory.Well> knownWellsNearMe = new ArrayList<>();
    static int lastRead; // round number when we last updated shared knowledge.
    static int UPDATE_FREQ = 10; // rounds. High because HQs and Wells don't change often.

    static List<MapLocation> knownEnemyHQs;
    static List<MapLocation> unverifiedEnemyHQs;

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

    static boolean shouldPrint(RobotController rc) {
        return rc.getTeam() == Team.A && rc.getRoundNum() < 10;
    }

    static MapLocation mapCenter(RobotController rc) {
        // TODO: memoize
        return new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
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

    static void setIndicator(RobotController rc, String state, String data) {
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
    }

    static boolean isEarlyGame(RobotController rc) {
        // In early game, we might want to _heavily_ prioritize military (rush).
        return rc.getRoundNum() <= 100;
    }
}
