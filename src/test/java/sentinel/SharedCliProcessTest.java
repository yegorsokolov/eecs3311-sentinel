package sentinel;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import sentinel.domain.*;
import sentinel.persistence.JsonCodec;
import sentinel.presentation.SentinelCLI;
import static org.junit.jupiter.api.Assertions.*;

class SharedCliProcessTest {
    @TempDir Path temp;
    private HttpServer server;private AtomicInteger modelCalls;
    record Output(int code,String stdout,String stderr) {}
    @BeforeEach void start()throws Exception {
        modelCalls=new AtomicInteger();server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/generate",exchange->{modelCalls.incrementAndGet();var input=(Map<?,?>)JsonCodec.tree(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));String instructions=(String)input.get("system");String response;
            if(instructions.contains("purpose: planning"))response="{\"version\":\"planning-v1\",\"steps\":[{\"id\":\"collect\",\"tool\":\"logSearch\",\"args\":{\"datasetId\":\"local\",\"datasetVersion\":\"v0\",\"accountId\":\"alice\",\"start\":\"2026-10-22T12:00:00Z\",\"end\":\"2026-10-22T13:00:00Z\",\"limit\":\"100\"},\"dependsOn\":[]}]}";
            else if(instructions.contains("purpose: answer"))response="{\"text\":\"Review the cited login.\",\"evidenceIds\":[\"E1\"],\"limitations\":[\"Synthetic fixture\"]}";
            else response="{\"version\":\"analysis-v1\",\"hypothesis\":{\"text\":\"Review login\",\"ref\":\"E1\",\"against\":[],\"confidence\":\"LOW\",\"limitation\":\"Synthetic\"},\"recommendation\":{\"action\":\"REVIEW\",\"reason\":\"Review source login\",\"ref\":\"E1\",\"priority\":\"LOW\",\"prerequisite\":\"Approval\"}}";
            byte[] body=JsonCodec.encode(Map.of("model","qwen2.5:7b","response",response,"done",true)).getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);try(var out=exchange.getResponseBody()){out.write(body);}});server.start();
    }
    @AfterEach void stop(){server.stop(0);}
    private Output invoke(String... args)throws Exception {
        var command=new ArrayList<String>();command.add(Path.of(System.getProperty("java.home"),"bin","java").toString());command.add("-cp");command.add(System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")));command.add("sentinel.Main");command.addAll(List.of(args));
        // A Java launcher argument file preserves JSON quotes across Windows process creation.
        Path argumentFile=temp.resolve(UUID.randomUUID()+".args");
        var lines=new ArrayList<String>();for(String value:command.subList(1,command.size()))lines.add("\""+value.replace("\\","\\\\").replace("\"","\\\"")+"\"");
        Files.writeString(argumentFile,String.join("\n",lines),StandardCharsets.UTF_8);
        Path stdout=temp.resolve(UUID.randomUUID()+".stdout"),stderr=temp.resolve(UUID.randomUUID()+".stderr");
        var pb=new ProcessBuilder(command.get(0),"@"+argumentFile).directory(temp.toFile()).redirectOutput(stdout.toFile()).redirectError(stderr.toFile());pb.environment().keySet().removeIf(k->k.startsWith("SENTINEL_"));pb.environment().put("SENTINEL_OLLAMA_URL","http://127.0.0.1:"+server.getAddress().getPort());var process=pb.start();
        if(!process.waitFor(20,TimeUnit.SECONDS)){process.destroyForcibly();fail("CLI fixture exceeded process deadline");}return new Output(process.exitValue(),Files.readString(stdout,StandardCharsets.UTF_8),Files.readString(stderr,StandardCharsets.UTF_8));
    }
    private Result success(String... args)throws Exception{var output=invoke(args);assertEquals(0,output.code(),output.stderr());return JsonCodec.decode(output.stdout(),Result.class);}
    private CaseSnapshot create(boolean rule)throws Exception{var args=new ArrayList<>(List.of("new","--dataset","local","--request","Investigate synthetic alice","--target","alice","--start","2026-10-22T12:00:00Z","--end","2026-10-22T13:00:00Z"));if(rule)args.add("--rule");return JsonCodec.decode(success(args.toArray(String[]::new)).message(),CaseSnapshot.class);}
    @Test void completeWorkflowPersistsBetweenCliProcessesAndUsesFakeModelOnlyForAnalysisAndAnswer()throws Exception {
        var initial=create(true);String id=initial.id().toString();assertEquals(CaseStatus.READY,initial.status());assertEquals(0,modelCalls.get());
        Path input=Files.writeString(temp.resolve("events.json"),"[{\"eventId\":\"one\",\"timestamp\":\"2026-10-22T12:00:00Z\",\"eventType\":\"login\",\"accountId\":\"alice\",\"outcome\":\"success\"}]");
        assertEquals("v1",JsonCodec.decode(success("import","--case",id,"--file",input.toString()).message(),Summary.class).datasetVersion());
        String args=JsonCodec.encode(Map.of("datasetId","local","datasetVersion","v1","accountId","alice","start","2026-10-22T12:00:00Z","end","2026-10-22T13:00:00Z","limit","100")).replace("{","{ ");
        assertEquals(1,JsonCodec.decode(success("query","--case",id,"--tool","logSearch","--args",args).message(),ToolResult.class).evidence().size());
        success("rule","--case",id);var result=JsonCodec.decode(success("run","--case",id).message(),Assessment.class);assertEquals(Completeness.COMPLETE,result.completeness());assertEquals(0,result.severity().score());
        assertFalse(JsonCodec.decode(success("ask","--case",id,"--question","What supports the result?").message(),Answer.class).evidenceIds().isEmpty());assertEquals(2,modelCalls.get());
        var opened=JsonCodec.decode(success("open","--case",id).message(),CaseSnapshot.class);assertEquals(CaseStatus.COMPLETED,opened.status());assertEquals(1,opened.conversation().size());
        assertEquals(opened,JsonCodec.decode(success("show","--case",id).message(),CaseSnapshot.class));assertTrue(success("list").message().contains(id));assertTrue(success("preview","--case",id).message().contains("## Conversation"));
        Path report=temp.resolve("case.md");success("export","--case",id,"--out",report.toString());String before=Files.readString(report);assertEquals(3,invoke("export","--case",id,"--out",report.toString()).code());assertEquals(before,Files.readString(report));success("export","--case",id,"--out",report.toString(),"--overwrite");assertEquals(2,modelCalls.get());
    }
    @Test void nativeAdapterModelPlanningReachesReadyWithoutRetrieval()throws Exception{var snapshot=create(false);assertEquals(Plan.Origin.MODEL,snapshot.plan().origin());assertEquals(CaseStatus.READY,snapshot.status());assertTrue(snapshot.evidence().isEmpty());assertEquals("planning-v1",snapshot.promptVersion());assertEquals(1,modelCalls.get());}
    @Test void queryJsonMustBeObjectOfStringsAndReturnInvalidInputExitCode()throws Exception{String id=create(true).id().toString();for(String args:List.of("[]","null","{ \"limit\":1}")){var output=invoke("query","--case",id,"--tool","logSearch","--args",args);assertEquals(2,output.code(),output.stderr());assertTrue(output.stdout().isEmpty());}assertEquals(0,modelCalls.get());}
    @Test void namedArgumentParserRejectsDuplicatesMissingValuesAndPositionalArguments(){assertThrows(IllegalArgumentException.class,()->SentinelCLI.parse(new String[]{"new","--request","one","--request","two"}));assertThrows(IllegalArgumentException.class,()->SentinelCLI.parse(new String[]{"query","--args"}));assertThrows(IllegalArgumentException.class,()->SentinelCLI.parse(new String[]{"new","positional"}));var action=SentinelCLI.parse(new String[]{"new","--request","synthetic","--rule"});assertEquals("true",action.arguments().get("rule"));assertThrows(UnsupportedOperationException.class,()->action.arguments().clear());}
}
