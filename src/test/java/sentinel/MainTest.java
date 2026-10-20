package sentinel;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MainTest {
    @Test void helpDoesNotStartInvestigation() {
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        int code = Main.run(new String[]{"--help"}, new PrintStream(output), new PrintStream(errors));
        assertEquals(0, code);
        assertTrue(output.toString().contains("--check-config"));
        assertTrue(errors.toString().isEmpty());
    }

    @Test void unsupportedCommandReturnsInvalidInput() {
        var output = new ByteArrayOutputStream();
        var errors = new ByteArrayOutputStream();
        int code = Main.run(new String[]{"--gui"}, new PrintStream(output), new PrintStream(errors));
        assertEquals(2, code);
        assertTrue(output.toString().isEmpty());
        assertTrue(errors.toString().contains("Unsupported command"));
    }
}
