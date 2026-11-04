package sentinel.application;
import java.nio.file.*;
import sentinel.analysis.*;
import sentinel.config.*;
import sentinel.data.*;
import sentinel.events.*;
import sentinel.llm.*;
import sentinel.persistence.*;
import sentinel.planning.*;
import sentinel.reporting.*;
import sentinel.tools.*;
/** Composition root: one dataset, controller and executor per single-user process. */
public final class ApplicationSession implements AutoCloseable {
    public final AgentController controller;public final EventBus events;private final ToolExecutor tools;
    public ApplicationSession(AppConfig config,String datasetId){
        if(datasetId==null||!datasetId.matches("[A-Za-z0-9_-]{1,64}"))throw new IllegalArgumentException("Dataset ID requires 1-64 letters, digits, underscores or hyphens.");
        var store=new DatasetStore(datasetId);Path state=config.datasetDirectory().resolve(datasetId+".json");if(Files.exists(state))store.restore(state);store.durable(state);
        Path feed=Path.of("data/indicators/threat-feed.json");if(Files.exists(feed))store.loadIndicatorFeed(feed);
        tools=new ToolExecutor(java.util.Map.of("logSearch",new LogSearchCommand(store),"userActivity",new UserActivityCommand(store),"indicatorLookup",new IndicatorLookupCommand(store)),config.limits());
        var llm=ModelGatewayFactory.create(config);events=new EventBus();
        var engine=new AgentEngine(new LLMPlanningStrategy(llm,new PlanValidator(tools,store)),tools,new AnalysisService(llm,new TimelineBuilder(),new RuleSeverityPolicy(),store),events,store);
        controller=new AgentController(engine,new JsonCaseRepository(config.caseDirectory()),new LogImporter(store),new ReportService(),events);
    }
    public void close(){tools.close();}
}
