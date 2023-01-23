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

    static MapLocation start = null; // used for bug2
    static MapLocation dest = null;
    static Direction currentDir = null; // used for bug0 and bug2
    static int shortestDistance = Integer.MAX_VALUE; // used for bug2

    static String indicatorString = "";

    static void moveTowards(RobotController rc, MapLocation target) throws GameActionException {
        moveTowards(rc, target, 2);
    }

    static void moveTowards(RobotController rc, MapLocation target, int radius) throws GameActionException {
        if (!target.equals(dest)) {
            // Moving to a new place!
            start = rc.getLocation();
            dest = target;
            currentDir = null;
            shortestDistance = Integer.MAX_VALUE;
        }
        moveTowardsWithBug2(rc, start, dest, radius);
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

    static void moveTowardsWithBug0(RobotController rc, MapLocation target) throws GameActionException {
        if (rc.getLocation().equals(target)) {
            return; // we're already there!
        }
        if (!rc.isMovementReady()) {
            return; // can't move anyway
        }

        Direction dir = rc.getLocation().directionTo(target);
        if (rc.canMove(dir)) { // will exit wall-crawling
            rc.move(dir);
            currentDir = null; // we were able to move, so stop going around
        } else {
            // Go around: keep obstacle on RHS
            System.out.println("Cant go directly, so i'll do bug-0 to the left");
            if (currentDir == null) {
                currentDir = dir;
            }
            for (int i = 0; i < NUM_DIRECTIONS; i++) {
                if (rc.canMove(currentDir)) {
                    System.out.println("I found a direction to move, will move and unwind");
                    rc.move(currentDir);
                    currentDir = currentDir.rotateRight(); // to undo the previous rotation
                    break;
                } else {
                    System.out.println("turning left to try to move...");
                    currentDir = currentDir.rotateLeft();
                }
            }
        }
    }

    static void moveTowardsWithBug2(RobotController rc, MapLocation origin, MapLocation target, int radius) throws GameActionException {
        if (rc.getLocation().distanceSquaredTo(target) <= radius) {
            shortestDistance = Integer.MAX_VALUE;
            currentDir = null;
            // todo
            return; // we're already there!
        }
        if (!rc.isMovementReady()) {
            indicatorString = "cant move";
            return; // can't move anyway
        }

        // TODO: save origin/target so we can reset if they are new.
        Direction directDir = rc.getLocation().directionTo(target);

        if (currentDir != null) { // Wall-following
            if (onLine(rc, rc.getLocation(), origin, target)) { // try to exit wall-following.
                int currentDist = rc.getLocation().distanceSquaredTo(target);
                if (currentDist < shortestDistance && rc.canMove(directDir)) {
                    rc.move(directDir);
                    shortestDistance = currentDist;
                    currentDir = null; // exit wall-following
                    indicatorString = ("BUG2 " + target + "; EXIT wall " + directDir);
                    return; // because we moved.
                } else {
                    indicatorString = ("BUG2 " + target + "; CONT wall " + currentDir);
                    currentDir = followWall(rc, currentDir, target);
                }
            } else {
                indicatorString = ("BUG2 " + target + "; CONT wall " + currentDir);
                currentDir = followWall(rc, currentDir, target);
            }
        } else {
            // Not wall-following. Try to keep going.
            // TODO: should we check we're on the line? let's skip for now.
            if (rc.canMove(directDir) && !hasCurrent(rc, directDir)) { // maybe should check if passable // TODO: check not stuck
                rc.move(directDir);
                indicatorString = ("BUG2 " + target + "; DIRECT " + directDir);
                return; // because we moved.
            } else {
                indicatorString = ("BUG2 " + target + "; ENTER wall " + directDir);
                shortestDistance = Math.min(shortestDistance, rc.getLocation().distanceSquaredTo(target));
                currentDir = followWall(rc, directDir, target);
            }
        }

        if (currentDir == null) { // we tried to follow wall, but failed.
            indicatorString = ("BUG2 " + target + " STUCK!!");
        }
    }

    // Tries to move in the currentDir, and rotates if it can't. If it moved, returns the next
    // direction that should be attempted. If it couldn't move, returns null.
    static Direction followWall(RobotController rc, Direction currentDir, MapLocation target) throws GameActionException {
        boolean rotateRight = rc.getID() % 2 == 1;
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

    static boolean onLine(RobotController rc, MapLocation current, MapLocation origin, MapLocation target) {
        double dist = Math.abs(current.y - (getSlope(origin, target) * current.x + getIntercept(origin, target)));
        return dist <= 1.5;
    }

    static double getSlope(MapLocation origin, MapLocation target) {
        return (target.y - origin.y) / (1.0 * target.x - origin.x); // TODO: need to round?
    }

    static double getIntercept(MapLocation origin, MapLocation target) {
        return origin.y - getSlope(origin, target) * origin.x;
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
