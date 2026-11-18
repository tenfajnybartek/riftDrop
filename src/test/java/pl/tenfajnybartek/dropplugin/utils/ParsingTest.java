package pl.tenfajnybartek.dropplugin.utils;

import org.junit.jupiter.api.Test;
import pl.tenfajnybartek.dropplugin.objects.Count;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ParsingTest {
    @Test
    void dropKeysSurviveSerializationAndLegacyNamesRemainSeparate() throws IOException {
        Set<String> disabled = Set.of("id:diamond", "legacy:żółć=1@2");
        Map<String, Integer> counts = Map.of("id:diamond", 5, "legacy:żółć=1@2", 10);
        assertEquals(disabled, MapUtils.deserializeList(MapUtils.serializeList(disabled)));
        assertEquals(counts, MapUtils.deserializeMap(MapUtils.serializeMap(counts)));
        assertEquals(Set.of("legacy:v2:any"), MapUtils.legacyList("v2:any"));
        assertEquals(Map.of("legacy:test=name", 12), MapUtils.legacyMap("test=name=12"));
        assertThrows(IOException.class, () -> MapUtils.deserializeMap("v2:!"));
    }

    @Test
    void signedRangesAndIntegerMaxAreHandled() {
        assertEquals(new Count(-64, -16), Count.parse("-64--16"));
        assertEquals(new Count(-4, -4), Count.parse("-4"));
        assertEquals(new Count(-64, 320), Count.parse("-64 - 320"));
        assertTrue(new Count(Integer.MAX_VALUE - 1, Integer.MAX_VALUE).random() >= Integer.MAX_VALUE - 1);
        assertThrows(IllegalArgumentException.class, () -> Count.parse("9-2"));
    }

    @Test
    void turboDurationMustMatchTheWholeInput() {
        assertTrue(DataUtils.parseDateDiff("2h30min15s", true) > System.currentTimeMillis());
        assertTrue(DataUtils.parseDateDiff("1mo2d", true) > System.currentTimeMillis());
        assertEquals(-1, DataUtils.parseDateDiff("5m?", true));
        assertEquals(-1, DataUtils.parseDateDiff("0s", true));
    }
}
