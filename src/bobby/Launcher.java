package bobby;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.Team;

import java.util.Arrays;
import java.util.Comparator;

public class Launcher extends RobotPlayer {

    public static void run(RobotController rc) throws GameActionException {

        // Try to attack someone
        int radius = rc.getType().actionRadiusSquared;
        RobotInfo[] enemies = rc.senseNearbyRobots(radius, rc.getTeam().opponent());
        if (enemies.length > 0) {
            RobotInfo enemy = pickEnemy(enemies);
            if (rc.canAttack(enemy.getLocation())) {
                rc.attack(enemy.getLocation());
            }
        }

        // Also try to move randomly.
        moveRandomly(rc);
    }

    private static RobotInfo pickEnemy(RobotInfo[] enemies) { // len(enemies) > 0
        RobotInfo picked = enemies[0];
        for (RobotInfo e : enemies) {
            if (e.getHealth() < picked.getHealth()) { // Prefer weakest
                picked = e;
            }
        }
        return picked;
    }
}
