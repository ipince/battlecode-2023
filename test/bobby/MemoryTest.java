package bobby;

import battlecode.common.MapLocation;
import battlecode.common.ResourceType;
import org.junit.Test;

import static bobby.Memory.Well;
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

    @Test
    public void testWellEncoding() {
        Well well;

        well = new Well(new MapLocation(0, 0), ResourceType.NO_RESOURCE, false, false, -1);
        assertEquals(0b000001_000001_00_0_0, Memory.encodeWell(well));
        assertEquals(well, Memory.decodeWell(Memory.encodeWell(well), -1));

        well = new Well(new MapLocation(0, 0), ResourceType.ADAMANTIUM, false, true, -1);
        assertEquals(0b000001_000001_01_0_1, Memory.encodeWell(well));

        well = new Well(new MapLocation(0, 0), ResourceType.MANA, true, false, -1);
        assertEquals(0b000001_000001_10_1_0, Memory.encodeWell(well));

        well = new Well(new MapLocation(0, 0), ResourceType.ELIXIR, true, true, -1);
        assertEquals(0b000001_000001_11_1_1, Memory.encodeWell(well));

        assertNull(Memory.decodeWell(0, -1));
    }
}
