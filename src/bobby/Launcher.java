package bobby;

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
    private static MapLocation targetHQ = null; // It might not be an actual HQ (for both lead/followers).
    private static boolean targetHQConfirmed = false;
    private static int knownEnemiesAtTargetSelection;

    public static void run(RobotController rc) throws GameActionException {
        rc.setIndicatorString("START");

        updateKnowledge(rc, false);

        electLeader(rc);

        // Attacking takes priority. If attacked, move away.
        boolean attackedEnemy = maybeAttackEnemy(rc);
        if (attackedEnemy) { // move back
            Pathing.moveAway(rc, target.getLocation(), false);
        }

        // Learn if potential enemy HQ is actually an HQ or not.
        if (amLeader) {
            // followers are just following leader, so no need for them to check too.
            checkPotentialEnemyHQs(rc);
        }

        // Main moving logic
        if (rc.isMovementReady()) { // save bytecode if we already moved.
            moveTowardsEnemy(rc);
        }

        // Move away from enemy HQ if we're too close
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
        if (amLeader) {
            leader = rc.senseRobotAtLocation(rc.getLocation());
        }
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

    private static void moveTowardsEnemy(RobotController rc) throws GameActionException {
        if (rc.getRoundNum() < 50) { // rendezvous in the middle at first.
            Pathing.moveTowards(rc, Mapping.mapCenter(rc), 4);
            return;
        }

        refreshTargetHQ(rc); // targetHQ cannot be null after this.
        if (amLeader) {
            int radius = CLOUD_VISION_RADIUS; // get closer to unknown locations.
            if (targetHQConfirmed || memoryEnemyHQs.contains(targetHQ)) {
                // Stay afar from enemy HQ's damage radius.
                radius = OUTSIDE_HQ_ACTION_RADIUS;
            }
            Pathing.moveTowards(rc, Pathing.Algo.BUG0, targetHQ, radius, 0);
        } else { // follower
            if (targetHQConfirmed) {
                // Go towards same target as leader, instead of following leader.
                Pathing.moveTowards(rc, Pathing.Algo.BUG0, targetHQ, OUTSIDE_HQ_ACTION_RADIUS, 0);
            } else {
                Pathing.moveTowards(rc, Pathing.Algo.BUG0, leader.getLocation(), 4, 0);
            }
        }
    }

    private static void refreshTargetHQ(RobotController rc) {
        if (memoryNotEnemyHQs.contains(targetHQ) || knownNotEnemyHQs.contains(targetHQ)) {
            targetHQ = null; // unset so we choose a new target.
        }
        if (knownEnemyHQs.size() != knownEnemiesAtTargetSelection) {
            targetHQ = null; // force target reselection if information changes.
        }

        // TODO: if we see an enemy HQ nearby, just go to it instead.
        if (targetHQ == null) {
            // If we pick a known HQ, target is relevant for both leaders and followers.
            if (!knownEnemyHQs.isEmpty()) { // pick known at random.
                targetHQ = knownEnemyHQs.get(leader.getID() % knownEnemyHQs.size());
                targetHQConfirmed = true;
                knownEnemiesAtTargetSelection = knownEnemyHQs.size();
                return;
            }
            // Otherwise, target is only relevant for leaders.
            if (!memoryEnemyHQs.isEmpty()) {
                targetHQ = memoryEnemyHQs.iterator().next(); // TODO: randomize
            } else { // pick potential at random
                targetHQ = Utils.pickRandom(potentialEnemyHQs, rng);
            }
        }
    }
}
