package bobby;

import battlecode.common.GameActionException;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Launcher extends RobotPlayer {

    public static void run(RobotController rc) throws GameActionException {

        // Try to attack someone
        int radius = rc.getType().actionRadiusSquared;

        // get friends, get enemies.
        // for each enemy, see which friends are around. pick weakest enemy given total attack.

        RobotInfo[] enemies = rc.senseNearbyRobots(radius, rc.getTeam().opponent());
        RobotInfo enemy = pickEnemy(enemies);
        if (enemy != null) {
            Pathing.moveTowards(rc, enemy.getLocation(), 4);
            if (rc.canAttack(enemy.getLocation())) {
                rc.attack(enemy.getLocation());
            }
        } else {
            // Also try to move randomly.
            Pathing.explore(rc);
        }

        setIndicator(rc, "NONE", "");
    }

    static void electLeader(RobotController rc) throws GameActionException {
        // TODO: change distance
        RobotInfo[] allies = rc.senseNearbyRobots(9, rc.getTeam());

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
            if (e.getType() == RobotType.HEADQUARTERS) { // immortal
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
            Collections.sort(picked, new Comparator<RobotInfo>() {
                @Override
                public int compare(RobotInfo o1, RobotInfo o2) {
                    // TODO: if two carriers, pick one with higher resources.
                    return killPriorities.get(o1.getType()) - killPriorities.get(o2.getType());
                }
            });
            System.out.println("many to choose, ordered: " + picked);
            return picked.get(0);
        }
    }
}
