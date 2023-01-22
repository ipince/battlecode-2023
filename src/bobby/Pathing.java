package bobby;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.MapInfo;

public class Pathing {

    static final int NUM_DIRECTIONS = 8;

    static Direction currentDir = null;

    static void moveTowards(RobotController rc, MapLocation target) throws GameActionException {
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

    static int shortestDistance = Integer.MAX_VALUE;

    static void moveTowardsWithBug2(RobotController rc, MapLocation origin, MapLocation target) throws GameActionException {
        if (rc.getLocation().distanceSquaredTo(target) <= 2) {
            shortestDistance = Integer.MAX_VALUE;
            currentDir = null;
            // todo
            return; // we're already there!
        }
        if (!rc.isMovementReady()) {
            rc.setIndicatorString("cant move");
            return; // can't move anyway
        }

        // TODO: save origin/target so we can reset if they are new.
        Direction directDir = rc.getLocation().directionTo(target);

        // Two modes: (1) on line, or (2) wall-following.
        if (onLine(rc, rc.getLocation(), origin, target)) { // try to exit wall-following.
            int currentDist = rc.getLocation().distanceSquaredTo(target);
            if (currentDist < shortestDistance) { // we're getting closer, move towards it.
                if (rc.canMove(directDir)) { // maybe should check if passable
                    rc.move(directDir);
                    shortestDistance = currentDist;
                    currentDir = null;
                    rc.setIndicatorString("BUG2 ON LINE, moved " + directDir);
                    return; // because we moved.
                } else {
                    // can't move on line... so follow wall.
                    rc.setIndicatorString("BUG2 ON LINE can't move, will try " + directDir);
                    currentDir = followWall(rc, directDir); // TODO what if it's null?
                }
            } else {
                rc.setIndicatorString("BUG2 ON LINE farther away, will try " + currentDir);
                currentDir = followWall(rc, currentDir); // TODO what if it's null?
            }
        } else {
            // Not in line. If we're wall-following, continue. else, try to move towards target.
            if (currentDir == null) { // doubles as !isWallFollowing.
                if (rc.canMove(directDir)) { // maybe should check if passable
                    rc.move(directDir);
                    rc.setIndicatorString("BUG2 NOT ON LINE, moved anyway " + directDir);
                    return; // because we moved.
                } else {
                    currentDir = followWall(rc, directDir); // TODO what if it's null?
                }
            } else {
                rc.setIndicatorString("BUG2 WALL, will try " + currentDir);
                currentDir = followWall(rc, currentDir); // TODO what if it's null?
            }
        }
    }


    // Tries to move in the currentDir, and rotates if it can't. If it moved, returns the next
    // direction that should be attempted. If it couldn't move, returns null. // TODO: null?
    static Direction followWall(RobotController rc, Direction currentDir) throws GameActionException {
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
        System.out.println("IM TRAPPED!!!");
        return null;
    }

    static boolean onLine(RobotController rc, MapLocation current, MapLocation origin, MapLocation target) {
        double dist = Math.abs(current.y - (getSlope(origin, target) * current.x + getIntercept(origin, target)));
        if (shouldPrint(rc)) {
            System.out.println("Is " + current + " is on line " + origin + " -> " + target + "? " + dist);
        }
        return dist <= 1.5;
    }

    static double getSlope(MapLocation origin, MapLocation target) {
        return (target.y - origin.y) / (1.0*target.x - origin.x); // TODO: need to round?
    }

    static double getIntercept(MapLocation origin, MapLocation target) {
        return origin.y - getSlope(origin, target) * origin.x;
    }

    static boolean hasCurrent(RobotController rc, Direction d) throws GameActionException {
        return rc.senseMapInfo(rc.adjacentLocation(d)).getCurrentDirection() != Direction.CENTER;

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

    static boolean shouldPrint(RobotController rc) {
        return rc.getID() == 10269;
    }
}
