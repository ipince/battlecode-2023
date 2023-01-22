package bobby;

import battlecode.common.MapLocation;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MemoryTest {

    @Test
    public void testMapEncoding() {
        MapLocation m = new MapLocation(1, 2);
        assertEquals(m, Memory.decodeMapLocation(Memory.encodeMapLocation(m)));

        assertEquals(0b1_000001, Memory.encodeMapLocation(new MapLocation(0, 0)));
        assertEquals(0b111100_000001, Memory.encodeMapLocation(new MapLocation(59, 0)));
        assertEquals(0b1_111100, Memory.encodeMapLocation(new MapLocation(0, 59)));

        assertNull(Memory.decodeMapLocation(0));
        assertEquals(new MapLocation(0, 0), Memory.decodeMapLocation(0b1_000001));
        assertEquals(new MapLocation(59, 0), Memory.decodeMapLocation(0b1_111100_000001));
        assertNull(Memory.decodeMapLocation(0b1_000000_000000));
    }
}
