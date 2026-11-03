package sentinel.persistence;
import org.junit.jupiter.api.Test;
import sentinel.domain.*;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class JsonCodecToolResultTest {
    @Test void queryResultsRoundTripWithActivitySummaryAndLongDuration() {
        var start=Instant.parse("2026-10-22T12:00:00Z");
        var summary=new ActivitySummary("demo-analyst",start,start,0,0,0,0,0,0,0,null,null);
        for(long duration:new long[]{0,17,(long)Integer.MAX_VALUE+1,Long.MAX_VALUE}) {
            var result=new ToolResult("EMPTY",List.of(),"v1",duration,false,summary,null);
            assertEquals(result,JsonCodec.decode(JsonCodec.encode(result),ToolResult.class));
        }
    }
    @Test void integerFieldsStillRejectOverflowAndFractionalValues() {
        record Counts(int count) {}
        assertThrows(IllegalArgumentException.class,()->JsonCodec.decode("{\"count\":2147483648}",Counts.class));
        assertThrows(IllegalArgumentException.class,()->JsonCodec.decode("{\"count\":1.5}",Counts.class));
    }
}
