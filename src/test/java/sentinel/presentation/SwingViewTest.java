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

class SwingViewTest extends SwingTestSupport {
    @Test void guiAndCliUseSameDispatcherAndPersistAcceptedCase()throws Exception{var output=new AtomicReference<Result>();SwingUtilities.invokeAndWait(()->output.set(gui.submit(new Action("new",newArgs()))));var saved=JsonCodec.decode(output.get().message(),CaseSnapshot.class);assertEquals(CaseStatus.READY,saved.status());assertEquals(saved,controller.current());assertEquals(saved,new JsonCaseRepository(temp.resolve("cases")).load(saved.id()).snapshot());var cli=new SentinelCLI(controller);assertEquals(gui.submit(new Action("show",Map.of())),cli.submit(new Action("show",Map.of())));}
    @Test void backgroundProgressIsDeliveredOnEdt()throws Exception{Thread other=new Thread(()->gui.update(new ProgressEvent("RETRIEVAL","SUCCESS",List.of())));other.start();other.join();var result=new AtomicReference<String>();SwingUtilities.invokeAndWait(()->{try{result.set(((JTextArea)field("progress")).getText());}catch(Exception ex){throw new RuntimeException(ex);}});assertTrue(result.get().contains("RETRIEVAL: SUCCESS"));}
    @Test void constructionRejectsOffEdtShow(){assertThrows(IllegalStateException.class,()->gui.show("demo",()->{}));}
    @Test void workerKeepsEdtResponsiveAndRendersAcceptedSnapshot()throws Exception{
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var onEdt=new AtomicBoolean(true);
        usePlanner(c->{onEdt.set(SwingUtilities.isEventDispatchThread());entered.countDown();try{if(!release.await(3,TimeUnit.SECONDS))throw new IllegalStateException("fixture release timeout");}catch(InterruptedException ex){Thread.currentThread().interrupt();throw new IllegalStateException(ex);}var rule=new RulePlanningStrategy(new PlanValidator(tools,store),store).createPlan(new CaseContext(c.snapshot(),true));return new Plan(rule.id(),rule.revision(),rule.steps(),rule.dependencies(),Plan.Origin.MODEL);});
        show();var args=new HashMap<>(newArgs());args.put("rule","false");try{perform("new",args);assertTrue(entered.await(2,TimeUnit.SECONDS));var responded=new AtomicBoolean();SwingUtilities.invokeAndWait(()->responded.set(true));assertTrue(responded.get());assertFalse(onEdt.get());}finally{release.countDown();}awaitWorker();assertEquals(CaseStatus.READY,controller.current().status());assertTrue(text("Plan").contains("MODEL"));
    }
}
