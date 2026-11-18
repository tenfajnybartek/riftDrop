package pl.tenfajnybartek.riftdrop.storage;

import org.junit.jupiter.api.Test;
import pl.tenfajnybartek.riftdrop.mining.IntRange;
import pl.tenfajnybartek.riftdrop.text.BoostTime;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ParsingTest {
    @Test
    void dropKeysSurviveSerializationAndLegacyNamesRemainSeparate() throws IOException {
        Set<String> disabled = Set.of("id:diamond", "legacy:żółć=1@2");
        Map<String, Integer> counts = Map.of("id:diamond", 5, "legacy:żółć=1@2", 10);
        assertEquals(disabled, ProfileCodec.deserializeList(ProfileCodec.serializeList(disabled)));
        assertEquals(counts, ProfileCodec.deserializeMap(ProfileCodec.serializeMap(counts)));
        assertEquals(Set.of("legacy:v2:any"), ProfileCodec.legacyList("v2:any"));
        assertEquals(Map.of("legacy:test=name", 12), ProfileCodec.legacyMap("test=name=12"));
        assertThrows(IOException.class, () -> ProfileCodec.deserializeMap("v2:!"));
    }

    @Test
    void signedRangesAndIntegerMaxAreHandled() {
        assertEquals(new IntRange(-64, -16), IntRange.parse("-64--16"));
        assertEquals(new IntRange(-4, -4), IntRange.parse("-4"));
        assertEquals(new IntRange(-64, 320), IntRange.parse("-64 - 320"));
        assertTrue(new IntRange(Integer.MAX_VALUE - 1, Integer.MAX_VALUE).random() >= Integer.MAX_VALUE - 1);
        assertThrows(IllegalArgumentException.class, () -> IntRange.parse("9-2"));
    }

    @Test
    void turboDurationMustMatchTheWholeInput() {
        assertTrue(BoostTime.parseDateDiff("2h30min15s", true) > System.currentTimeMillis());
        assertTrue(BoostTime.parseDateDiff("1mo2d", true) > System.currentTimeMillis());
        assertEquals(-1, BoostTime.parseDateDiff("5m?", true));
        assertEquals(-1, BoostTime.parseDateDiff("0s", true));
    }
}
