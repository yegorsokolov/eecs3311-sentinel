package sentinel.presentation;

import java.awt.GraphicsEnvironment;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.swing.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import sentinel.application.*;
import sentinel.analysis.*;
import sentinel.data.*;
import sentinel.domain.*;
import sentinel.domain.Action;
import sentinel.events.*;
import sentinel.persistence.*;
import sentinel.planning.*;
import sentinel.reporting.*;
import sentinel.tools.*;
import static org.junit.jupiter.api.Assertions.*;

abstract class SwingTestSupport {
    @TempDir Path temp;
    protected DatasetStore store;protected ToolExecutor tools;protected AgentController controller;protected SentinelGUI gui;
    protected final Instant start=Instant.parse("2026-10-22T12:00:00Z");
    @BeforeEach void setup()throws Exception {
        store=new DatasetStore("demo");var rows=new ArrayList<Event>();for(int n=0;n<6;n++)rows.add(new Event("event-"+n,start.plusSeconds(n),"login","alice",null,"host","success","fixture#/"+n,"pending"));store.ingest(new ImportBatch(rows));tools=ToolExecutor.local(store);
        usePlanner(c->null);
    }
    protected void usePlanner(PlanningStrategy planner)throws Exception {
        var bus=new EventBus();var analysis=new AnalysisService(p->{throw new AssertionError("View test must not infer");},new TimelineBuilder(),new RuleSeverityPolicy(),store);
        var engine=new AgentEngine(planner,tools,analysis,bus,store);controller=new AgentController(engine,new JsonCaseRepository(temp.resolve("cases")),new LogImporter(store),new ReportService(),bus);
        SwingUtilities.invokeAndWait(()->gui=new SentinelGUI(controller));bus.subscribe(gui);
    }
    @AfterEach void close()throws Exception{SwingUtilities.invokeAndWait(()->{try{JFrame frame=(JFrame)field("frame");if(frame!=null)frame.dispose();}catch(Exception ex){throw new RuntimeException(ex);}});tools.close();}
    protected Object field(String name)throws Exception{Field f=SentinelGUI.class.getDeclaredField(name);f.setAccessible(true);return f.get(gui);}
    protected Map<String,String> newArgs(){return Map.of("dataset","demo","version","v1","target","alice","start",start.toString(),"end",start.plusSeconds(60).toString(),"request","Investigate synthetic alice","rule","true");}
    protected Map<String,String> queryArgs(int limit){return Map.of("datasetId","demo","datasetVersion","v1","accountId","alice","start",start.toString(),"end",start.plusSeconds(60).toString(),"limit",String.valueOf(limit));}
    protected void show()throws Exception{Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());SwingUtilities.invokeAndWait(()->gui.show("demo",()->{}));}
    protected void perform(String action,Map<String,String> args)throws Exception{SwingUtilities.invokeAndWait(()->{try{Method m=SentinelGUI.class.getDeclaredMethod("perform",String.class,Map.class);m.setAccessible(true);m.invoke(gui,action,args);}catch(Exception ex){throw new RuntimeException(ex);}});}
    @SuppressWarnings("unchecked") protected void awaitWorker()throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);var ready=new AtomicBoolean();while(System.nanoTime()<end){SwingUtilities.invokeAndWait(()->{try{ready.set(((List<JButton>)field("buttons")).stream().allMatch(JButton::isEnabled));}catch(Exception ex){throw new RuntimeException(ex);}});if(ready.get())return;Thread.sleep(20);}fail("GUI worker did not complete");}
    protected String text(String tab)throws Exception{var out=new AtomicReference<String>();SwingUtilities.invokeAndWait(()->{try{@SuppressWarnings("unchecked")var views=(Map<String,JTextArea>)field("views");out.set(views.get(tab).getText());}catch(Exception ex){throw new RuntimeException(ex);}});return out.get();}
}
