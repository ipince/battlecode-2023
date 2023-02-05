package bobby;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Launcher extends RobotPlayer {

    // 18 (3^2 + 3^2). if it's less, then they enter  radius 9 :facepalm:
    private static int OUTSIDE_HQ_ACTION_RADIUS = RobotType.HEADQUARTERS.actionRadiusSquared + 9;

    private static RobotInfo leader = null;
    private static boolean amLeader = false;

    private static RobotInfo target = null;
    private static MapLocation targetHQ = null; // It might not be an actual HQ. For Leaders only.
    private static int numEnemyHQsAtSelection;

    public static void run(RobotController rc) throws GameActionException {
        rc.setIndicatorString("START");

        int knownEnemyStart = knownEnemyHQs.size();
        updateKnowledge(rc, false);

        electLeader(rc);

        // Attacking takes priority.
        boolean attackedEnemy = maybeAttackEnemy(rc);

        // Learn if potential enemy HQ is actually an HQ or not.
        if (targetHQ != null) {
            checkPotentialEnemyHQs(rc);
            if (memoryNotEnemyHQs.contains(targetHQ) || knownNotEnemyHQs.contains(targetHQ)) {
                targetHQ = null; // unset so we choose a new target.
            }
            if (knownEnemyHQs.size() != knownEnemyStart) {
                targetHQ = null; // force leaders to reselect target if information changes.
            }
        }

        // Moving logic.
        if (attackedEnemy) { // move back
            Direction opposite = target.getLocation().directionTo(rc.getLocation());
            if (rc.canMove(opposite)) {
                rc.move(opposite);
            } else if (rc.canMove(opposite.rotateLeft())) {
                rc.move(opposite.rotateLeft());
            } else if (rc.canMove(opposite.rotateRight())) {
                rc.move(opposite.rotateRight());
            }
        } else {
            if (rc.getRoundNum() < 50) { // rendezvous in the middle at first.
                Pathing.moveTowards(rc, Mapping.mapCenter(rc), 4);
            }

            // TODO: if we see an enemy HQ nearby, just go to it instead.
            if (amLeader) {
                if (targetHQ == null) {// || numEnemyHQsAtSelection != knownEnemyHQs.size()) {
                    pickTargetHQ(rc.getID());
                }
                if (targetHQ != null) {
                    int radius = CLOUD_VISION_RADIUS; // get closer to unknown locations.
                    if (knownEnemyHQs.contains(targetHQ) || memoryEnemyHQs.contains(targetHQ)) {
                        // Stay afar from enemy HQ's damage radius.
                        radius = OUTSIDE_HQ_ACTION_RADIUS;
                    }
                    Pathing.moveTowards(rc, Pathing.Algo.BUG0, targetHQ, radius, 0);
                } else {
                    Pathing.explore(rc);
                }
            } else if (leader != null) {
                boolean pickedBasedOnLeader = pickTargetHQ(leader.getID());
                if (pickedBasedOnLeader && targetHQ != null) {
                    // Go towards same target as leader, instead of following leader.
                    Pathing.moveTowards(rc, Pathing.Algo.BUG0, targetHQ, OUTSIDE_HQ_ACTION_RADIUS, 0);
                } else {
                    Pathing.moveTowards(rc, Pathing.Algo.BUG0, leader.getLocation(), 4, 0);
                }
            } else {
                Pathing.explore(rc);
            }
        }

        if (rc.isMovementReady() && targetHQ != null) {
            if (rc.getLocation().isWithinDistanceSquared(targetHQ, RobotType.HEADQUARTERS.actionRadiusSquared)) {
                // Move away from enemy HQs
                Pathing.moveAway(rc, targetHQ, false); // TODO: perp ok?
            }
        }

        maybeAttackEnemy(rc); // in case an enemy became visible after we moved.

        // Maybe flush to shared memory.
        maybeFlushEnemyHQs(rc);

        // Extra attack, if possible.
        if (!attackedEnemy) {
            maybeAttackCloud(rc);
        }

        setIndicator(rc, amLeader ? "LEADING" : leader != null ? "FOLLOW " + leader.getID() : "NONE", "");
        if (amLeader) {
            rc.setIndicatorDot(rc.getLocation(), 255, 200, 0);
        } else if (leader != null) {
            rc.setIndicatorDot(leader.getLocation(), 0, 0, 255);
        }
    }

    private static void electLeader(RobotController rc) throws GameActionException {
        RobotInfo[] allies = rc.senseNearbyRobots(rc.getType().visionRadiusSquared, rc.getTeam());
        leader = Arrays.stream(allies)
                .filter((r) -> r.getType() == RobotType.LAUNCHER)
                .min(Comparator.comparingInt(RobotInfo::getID))
                .orElse(null);
        amLeader = leader == null || rc.getID() < leader.getID();
    }

    private static boolean maybeAttackEnemy(RobotController rc) throws GameActionException {
        int radius = rc.getType().actionRadiusSquared;
        RobotInfo[] enemies = rc.senseNearbyRobots(radius, rc.getTeam().opponent());
        target = pickEnemy(enemies);
        if (target != null) {
            if (rc.canAttack(target.getLocation())) {
                rc.attack(target.getLocation());
                return true;
            }
        }
        return false;
    }

    private static void maybeAttackCloud(RobotController rc) throws GameActionException {
        MapLocation[] clouds = rc.senseNearbyCloudLocations(rc.getType().actionRadiusSquared);
        if (clouds.length > 0) {
            MapLocation cloud = clouds[rng.nextInt(clouds.length)];
            if (rc.canAttack(cloud)) {
                rc.attack(cloud);
            }
        }
    }

    private static final Map<RobotType, Integer> killPriorities = killPriorities();

    private static Map<RobotType, Integer> killPriorities() {
        Map<RobotType, Integer> m = new HashMap<>();
        m.put(RobotType.LAUNCHER, 1);
        m.put(RobotType.CARRIER, 2);
        m.put(RobotType.AMPLIFIER, 3);
        m.put(RobotType.DESTABILIZER, 4);
        m.put(RobotType.BOOSTER, 5);
        m.put(RobotType.HEADQUARTERS, 6);
        return m;
    }

    private static RobotInfo pickEnemy(RobotInfo[] enemies) {
        if (enemies.length == 0) {
            return null;
        }
        int minShotsToKill = Integer.MAX_VALUE;
        List<RobotInfo> picked = new ArrayList<>();
        for (RobotInfo e : enemies) {
            if (e.getType() == RobotType.HEADQUARTERS) { // immortal, skip.
                continue;
            }
            int shotsToKill = e.getHealth() / RobotType.LAUNCHER.damage;
            if (e.getHealth() - shotsToKill * RobotType.LAUNCHER.damage > 0) {
                shotsToKill++;
            }
            if (shotsToKill < minShotsToKill) {
                picked = new ArrayList<>();
                minShotsToKill = shotsToKill;
            }
            if (shotsToKill == minShotsToKill) {
                picked.add(e);
            }
        }
        if (picked.size() == 0) {
            return null;
        } else if (picked.size() == 1) { // save some compute
            return picked.get(0);
        } else {
            Collections.sort(picked, Comparator.comparingInt(o -> killPriorities.get(o.getType())));
            // TODO: if first few are carriers, pick one with most anchors, or most resources.
            return picked.get(0);
        }
    }

    private static boolean pickTargetHQ(int leaderId) {
        if (!knownEnemyHQs.isEmpty()) { // pick known at random.
            targetHQ = knownEnemyHQs.get(leaderId % knownEnemyHQs.size());
            numEnemyHQsAtSelection = knownEnemyHQs.size();
            return true;
        } else if (!memoryEnemyHQs.isEmpty()) {
            // Not shared information, so just follow leader.
            targetHQ = memoryEnemyHQs.iterator().next(); // TODO: randomize
        } else { // pick potential at random
            // TODO: they always go to the same one... which isn't great.
            targetHQ = Utils.pickRandom(potentialEnemyHQs, rng);
        }
        return false;
    }
}
