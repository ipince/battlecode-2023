package bobby;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class PathingTest {

    @Test
    public void testBadCurrent() {
        assertTrue(Pathing.hasBadCurrent(infoWithCurrent(Direction.SOUTH), Direction.NORTH));
        assertTrue(Pathing.hasBadCurrent(infoWithCurrent(Direction.SOUTH), Direction.NORTHEAST));
        assertTrue(Pathing.hasBadCurrent(infoWithCurrent(Direction.SOUTH), Direction.NORTHWEST));
    }

    private MapInfo infoWithCurrent(Direction d) {
        return new MapInfo(
                new MapLocation(1, 1),
                false,
               true,
                new double[]{0, 0},
                d,
                new int[][]{{0, 0}, {0, 0}},
                new int[][]{{0, 0}, {0, 0}});
    }
}
