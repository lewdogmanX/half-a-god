package folk.sisby.surveyor.client;

import com.google.common.collect.Multimap;
import folk.sisby.surveyor.WorldSummary;
import folk.sisby.surveyor.util.RegionPos;

import java.util.BitSet;
import java.util.Map;

/** Compile-time stand-in for Surveyor's class (never packaged). */
public class SurveyorClientEvents {
    public static class Invoke {
        public static void terrainUpdated(WorldSummary summary, Map<RegionPos, BitSet> chunks) {
        }

        public static void structuresAdded(WorldSummary summary, Multimap<?, ?> starts) {
        }

        public static void landmarksAdded(WorldSummary summary, Multimap<?, ?> landmarks) {
        }

        public static void landmarksRemoved(WorldSummary summary, Multimap<?, ?> landmarks) {
        }
    }
}
