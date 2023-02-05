package bobby;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;

import java.util.HashSet;
import java.util.Set;

public class Pathing {

    static final int NUM_DIRECTIONS = 8;

    // PARAMETERS to adjust/tune.
    static final double ONLINE_THRESHOLD = 1.5;

    enum Algo {
        BUG0, BUG2;
    }

    static MapLocation start = null; // used for bug2
    static MapLocation dest = null;
    static Algo preferred = null;
    static Set<MapLocation> path = new HashSet<>();

    static Direction currentDir = null; // used for bug0 and bug2; aka isWallFollowing
    static int shortestDistance = Integer.MAX_VALUE; // used for bug2

    static String indicatorString = "";

    static Boolean rotateRight; // TODO: instead of it being fixed; choose based on how close we get to target.

    static void moveTowards(RobotController rc, MapLocation target) throws GameActionException {
        moveTowards(rc, Algo.BUG2, target, 2, 0);
    }

    static void moveTowards(RobotController rc, MapLocation target, int radius) throws GameActionException {
        moveTowards(rc, Algo.BUG2, target, radius, 0);
    }

    // MAIN entry point to pathing.
    static void moveTowards(RobotController rc, Algo algo, MapLocation target, int radius, int avoidRadius) throws GameActionException {
        if (rotateRight == null) {
            rotateRight = rc.getID() % 2 == 1;
        }

        if (!target.equals(dest)) {
            // Moving to a new place! Reset values.
            start = rc.getLocation();
            dest = target;
            preferred = algo;
            path.clear();
            currentDir = null;
            shortestDistance = Integer.MAX_VALUE;
            indicatorString = "";
        }

        MapLocation before = rc.getLocation();
        switch (preferred) {
            case BUG0:
                moveTowardsWithBug0(rc, dest, radius);
                break;
            case BUG2:
            default:
                moveTowardsWithBug2(rc, start, dest, radius, avoidRadius);
        }
        MapLocation after = rc.getLocation();

        if (after.distanceSquaredTo(target) <= radius) {
            setIndicatorString(algo.name(), target, "arrived (within " + radius + ")!", null);
        }

        if (!before.equals(after) && path.contains(after)) { // careful if we didn't move.
            // we're stuck in a loop or something! reset by unsetting dest
            dest = null; // next time we're called, we'll start over.
            indicatorString = "STUCK in loop; resetting...";
            // Robot may still get stuck if the start/end yield same results... im a bit confused here.
            // try changing the robot's direction.
            rotateRight = !rotateRight;
//            preferred = preferred == Algo.BUG0 ? Algo.BUG2 : Algo.BUG0;
            // TODO: maybe try a diff algorithm?
        }
        path.add(after); // If not, then add new location to path (for now add them all--infinite path memory).
    }

    static MapLocation randomLoc = null;

    // explore is like a random walk, but "with purpose". We pick a random target and go to it,
    // and then we pick another one and go to it.
    static void explore(RobotController rc) throws GameActionException {
        if (randomLoc != null && // we're already going somewhere and it's far
                !rc.getLocation().isWithinDistanceSquared(randomLoc, 3)) {
            Pathing.moveTowards(rc, randomLoc); // continue going
        } else {
            randomLoc = Pathing.randomLocWithin(rc, 10, 30);
            Pathing.moveTowards(rc, randomLoc); // go to new random location
        }
    }
    // TODO: stop exploring?

    static void moveAway(RobotController rc, MapLocation away, boolean perpendicularOk) throws GameActionException {
        Direction opposite = rc.getLocation().directionTo(away).opposite();
        if (rc.canMove(opposite)) {
            rc.move(opposite);
            return;
        } else if (rc.canMove(opposite.rotateLeft())) {
            rc.move(opposite.rotateLeft());
            return;
        } else if (rc.canMove(opposite.rotateRight())) {
            rc.move(opposite.rotateRight());
            return;
        }
        if (perpendicularOk) { // try double rotations
            if (rc.canMove(opposite.rotateLeft().rotateLeft())) {
                rc.move(opposite.rotateLeft().rotateLeft());
                return;
            } else if (rc.canMove(opposite.rotateRight().rotateRight())) {
                rc.move(opposite.rotateRight().rotateRight());
                return;
            }
        }
    }

    static void makeSpace(RobotController rc, MapLocation target) throws GameActionException {
        // Go to another adjacent location, if possible.
        for (MapLocation adjacent : rc.getAllLocationsWithinRadiusSquared(target, 2)) {
            if (rc.canMove(rc.getLocation().directionTo(adjacent))) {
                rc.move(rc.getLocation().directionTo((adjacent)));
            }
        }
    }

    static void moveTowardsWithBug0(RobotController rc, MapLocation target, int radius) throws GameActionException {
        if (rc.getLocation().distanceSquaredTo(target) <= radius) {
            setIndicatorString("BUG0", target, "arrived! (within " + radius + ")", null);
            return; // we're already there!
        }
        if (!rc.isMovementReady()) {
            setIndicatorString("BUG0", target, "can't move! next: ", currentDir);
            return; // can't move anyway
        }

        Direction dir = rc.getLocation().directionTo(target);
        if (rc.canMove(dir)) { // will exit wall-crawling
            rc.move(dir);
            currentDir = null; // we were able to move, so stop going around
            setIndicatorString("BUG0", target, "DIR", dir);
            return;
        } else {
            // Go around: keep obstacle on RHS
            if (currentDir == null) {
                currentDir = dir;
            }
            for (int i = 0; i < NUM_DIRECTIONS; i++) {
                if (rc.canMove(currentDir)) {
                    rc.move(currentDir);
                    if (rotateRight) {
                        currentDir = currentDir.rotateLeft().rotateLeft();
                    } else {
                        currentDir = currentDir.rotateRight().rotateRight();
                    }
                    setIndicatorString("BUG0", target, "WALL next: ", currentDir);
                    break;
                } else {
                    if (rotateRight) {
                        currentDir = currentDir.rotateRight();
                    } else {
                        currentDir = currentDir.rotateLeft();
                    }
                }
            }
        }

        // TODO: can't move
        if (currentDir == null) {
            setIndicatorString("BUG0", target, "STUCK!", null);
        }
    }

    static void moveTowardsWithBug2(RobotController rc, MapLocation origin, MapLocation target, int radius, int avoid) throws GameActionException {
        if (rc.getLocation().distanceSquaredTo(target) <= radius) {
            shortestDistance = Integer.MAX_VALUE;
            currentDir = null;
            setIndicatorString("BUG2", target, "arrived! (within " + radius + ")", null);
            // todo: do we really need this? i think not.
            return; // we're already there!
        }
        if (!rc.isMovementReady()) {
            setIndicatorString("BUG2", target, "can't move! next: ", currentDir);
            return; // can't move anyway
        }

        // TODO: save origin/target so we can reset if they are new.
        Direction directDir = rc.getLocation().directionTo(target);

        if (currentDir != null) { // Wall-following
            double dist = lineDist(rc, rc.getLocation(), origin, target);
            if (dist < ONLINE_THRESHOLD) { // try to exit wall-following.
                int currentDist = rc.getLocation().distanceSquaredTo(target);
                if (currentDist < shortestDistance && rc.canMove(directDir)) { // exit wall-following
                    rc.move(directDir);
                    shortestDistance = currentDist;
                    currentDir = null;
                    setIndicatorString("BUG2", target, "EXIT wall (d=" + dist + ")", directDir);
                    return; // because we moved.
                } else {
                    currentDir = followWall(rc, currentDir, target);
                    setIndicatorString("BUG2", target, "ON LINE (d=" + dist + ", " + currentDist + " > " + shortestDistance + ") CONT wall " + (rotateRight ? "R " : "L ") + "next: ", currentDir);
                }
            } else {
                currentDir = followWall(rc, currentDir, target);
                setIndicatorString("BUG2", target, "OFF LINE (d=" + dist + ") CONT wall " + (rotateRight ? "R " : "L ") + "next: ", currentDir);
            }
        } else {
            // Not wall-following. Try to keep going.
            if (rc.canMove(directDir) && !tooClose(rc, target, directDir, avoid) && !hasCurrent(rc, directDir)) { // maybe should check if passable // TODO: check not stuck
                rc.move(directDir);
                setIndicatorString("BUG2", target, "DIRECT", directDir); // what we did
                return; // because we moved.
            } else {
                shortestDistance = Math.min(shortestDistance, rc.getLocation().distanceSquaredTo(target));
                currentDir = followWall(rc, directDir, target);
                setIndicatorString("BUG2", target, "ENTER wall (sd=" + shortestDistance + ") " + (rotateRight ? "R " : "L ") + "next: ", currentDir);
            }
        }

        if (currentDir == null) { // we tried to follow wall, but failed.
            indicatorString = ("BUG2 " + target + " STUCK!!");
        }
    }

    static void setIndicatorString(String algo, MapLocation target, String branch, Direction dir) {
        indicatorString = String.format("%s %s; %s %s", algo, target, branch, dir != null ? dir : "--");
    }

    // Tries to move in the currentDir, and rotates if it can't. If it moved, returns the next
    // direction that should be attempted. If it couldn't move, returns null.
    // currentDir is the direction we WANT to go in.
    // @returns the NEXT direction to try.
    static Direction followWall(RobotController rc, Direction currentDir, MapLocation target) throws GameActionException {
        for (int i = 0; i < NUM_DIRECTIONS; i++) {
            // Avoid currents for now...
            if (rc.canMove(currentDir) && !hasCurrent(rc, currentDir)) {
                rc.move(currentDir);
                if (rotateRight) {
                    return currentDir.rotateLeft().rotateLeft();
                } else {
                    return currentDir.rotateRight().rotateRight();
                }
            } else {
                if (rotateRight) {
                    currentDir = currentDir.rotateRight();
                } else {
                    currentDir = currentDir.rotateLeft();
                }
            }
        }
        return null; // we're trapped!!
    }

    private static boolean tooClose(RobotController rc, MapLocation target, Direction dir, int avoid) {
        return rc.getLocation().add(dir).distanceSquaredTo(target) <= avoid;
    }

    static double pointDist(MapLocation loc, double x, double y) {
        double dx = x - loc.x;
        double dy = y - loc.y;
        return 1.0 * Math.round((dx * dx + dy * dy) * 100) / 100;
    }

    static double lineDist(RobotController rc, MapLocation current, MapLocation origin, MapLocation target) {
        // Handle edge cases: vertical and horizonal lines, with Infinity and 0 slopes, respectively.
        if (origin.x == target.x) { // vertical line
            return (current.x - origin.x) * (current.x - origin.x);
        } else if (origin.y == target.y) {
            return (current.y - origin.y) * (current.y - origin.y);
        }

        double m = getSlope(origin, target);
        double b = getIntercept(origin, m);
        double mInv = -1.0 / m; // slope of perpendicular line
        // Find point in mLine which is closest to current (it's also on perpendicular line).
        // Math here for the future:
        //   mLine: y = mx + b
        //   perp line: y = (-1/m)x + b'   note m' = (-1/m)
        // take two points in the perp line and subtract, to get (can also think as rise/run):
        //   y - y1 = m'(x - x1)   note (x1, y1) is our current location (known)
        //   => y = m'(x - x1) + y1
        //   => mx + b = m'x - m'x1 + y1
        //   => mx - m'x = -m'x1 + y1 - b
        //   => x = (-m'x1 + y1 - b) / (m - m')
        double closestX = (-mInv * current.x + current.y - b) / (m - mInv);
        double closestY = m * closestX + b;

        return pointDist(current, closestX, closestY);
    }

    static double getSlope(MapLocation origin, MapLocation target) {
        // NOTE: if x1 == x2, then this will return Infinity (or -Infinity).
        return (1.0 * target.y - origin.y) / (1.0 * target.x - origin.x);
    }

    static double getIntercept(MapLocation origin, double slope) {
        return origin.y - slope * origin.x;
    }

    static boolean hasCurrent(RobotController rc, Direction d) throws GameActionException {
        return rc.senseMapInfo(rc.adjacentLocation(d)).getCurrentDirection() != Direction.CENTER;
    }

    static boolean isCurrentSafe(RobotController rc, Direction d, MapLocation target) throws GameActionException {
        Direction current = rc.senseMapInfo(rc.adjacentLocation(d)).getCurrentDirection();
        if (current == Direction.CENTER) {
            return true; // ok to step into locations with NO currents.
        } else if (current.opposite() == d) {
            // always avoid stepping into an opposite current
            return false;
        }

        // We have a current, let's follow it and see where it leads.
        // If the end is better than where we are now, then follow it.
        Set<MapLocation> seen = new HashSet<>();
        MapLocation next = rc.adjacentLocation(d);
        while (!seen.contains(next) && rc.canSenseLocation(next)) {
            MapInfo info = rc.senseMapInfo(next);
            seen.add(next);
            next = info.getMapLocation().add(info.getCurrentDirection());
        }
        // next is the endpoint, or it's as far as we can see.
        return next.distanceSquaredTo(target) < rc.getLocation().distanceSquaredTo(target);
    }

    static boolean hasBadCurrent(MapInfo info, Direction d) {
//        System.out.println(info.getMapLocation() + " current: " + info.getCurrentDirection() + ", opp ord " + info.getCurrentDirection().opposite().ordinal());
        System.out.println("dir " + d + ", ord " + d.ordinal());
        int diff = d.ordinal() - info.getCurrentDirection().opposite().ordinal();
        // diff can be small, or it can be large (e.g. 7).
        System.out.println("diff " + diff);
        return info.getCurrentDirection() != Direction.CENTER && info.getCurrentDirection() != d;
//                && (Math.abs(8 - diff) <= 2 || Math.abs(diff) <= 2);
    }

    static MapLocation randomLocWithin(RobotController rc, int min, int max) {
        // TODO: implement.
        return new MapLocation(
                RobotPlayer.rng.nextInt(rc.getMapWidth()),
                RobotPlayer.rng.nextInt(rc.getMapHeight()));
    }
}
