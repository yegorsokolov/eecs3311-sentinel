package sentinel;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import sentinel.domain.*;
import sentinel.persistence.JsonCodec;
/** Actual CLI processes, isolated from user .env and without model inference. */
class MainCliTest {
    @TempDir Path temp;
    record Output(int code,String stdout,String stderr) { }
    Output invoke(String... args)throws Exception{
        var command=new ArrayList<String>();command.add(Path.of(System.getProperty("java.home"),"bin","java").toString());command.add("-cp");command.add(System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")));command.add("sentinel.Main");command.addAll(List.of(args));
        var pb=new ProcessBuilder(command).directory(temp.toFile());pb.environment().keySet().removeIf(k->k.startsWith("SENTINEL_"));var process=pb.start();assertTrue(process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS));return new Output(process.exitValue(),new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8),new String(process.getErrorStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
    }
    @Test void actualCliKeepsJsonOnStdoutAndUsesInputAndOperationalExitCodes()throws Exception{
        var created=invoke("new","--dataset","local","--request","Investigate synthetic user","--target","user","--start","2026-10-22T12:00:00Z","--end","2026-10-22T13:00:00Z","--rule");assertEquals(0,created.code(),created.stderr());var envelope=JsonCodec.decode(created.stdout(),Result.class);var snapshot=JsonCodec.decode(envelope.message(),CaseSnapshot.class);assertEquals(CaseStatus.READY,snapshot.status());assertTrue(created.stderr().contains("PLANNING"));
        Path invalid=temp.resolve("invalid.json");Files.writeString(invalid,"{invalid");var rejected=invoke("import","--case",snapshot.id().toString(),"--file",invalid.toString());assertEquals(2,rejected.code());assertEquals("REJECTED",JsonCodec.decode(rejected.stdout(),Result.class).status());
        var invalidTool=invoke("query","--case",snapshot.id().toString(),"--tool","logSearch","--args","{}");assertEquals(2,invalidTool.code());assertTrue(invalidTool.stdout().isEmpty());assertTrue(invalidTool.stderr().contains("INVALID_ARGUMENT"));
        var failedWrite=invoke("export","--case",snapshot.id().toString(),"--out",temp.toString());assertEquals(3,failedWrite.code());assertTrue(failedWrite.stdout().isEmpty());
    }
}
