package sentinel.presentation;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import sentinel.domain.Action;
import sentinel.persistence.JsonCodec;
import static org.junit.jupiter.api.Assertions.*;

class SwingQueryDisplayTest extends SwingTestSupport {
    private void create()throws Exception{SwingUtilities.invokeAndWait(()->gui.submit(new Action("new",newArgs())));show();}
    private void query(Map<String,String> args)throws Exception{perform("query",Map.of("tool","logSearch","args",JsonCodec.encode(args)));awaitWorker();}
    private int displayed()throws Exception{return text("Evidence").split("\\\"sourceEventId\\\"",-1).length-1;}
    @Test void smallerQueryReplacesDisplayButKeepsAccumulatedCaseEvidence()throws Exception{create();query(queryArgs(6));assertEquals(6,displayed());query(queryArgs(2));assertEquals(2,displayed());assertEquals(6,controller.current().evidence().size());assertEquals(6,new sentinel.persistence.JsonCaseRepository(temp.resolve("cases")).load(controller.current().id()).snapshot().evidence().size());}
    @Test void emptyAndRejectedQueryDoNotRestoreOldDisplay()throws Exception{create();query(queryArgs(6));var empty=new HashMap<>(queryArgs(6));empty.put("eventType","absent");query(empty);assertEquals(0,displayed());assertEquals(6,controller.current().evidence().size());query(queryArgs(0));assertEquals(0,displayed());var progress=new AtomicReference<String>();SwingUtilities.invokeAndWait(()->{try{progress.set(((JTextArea)field("progress")).getText());}catch(Exception ex){throw new RuntimeException(ex);}});assertTrue(progress.get().contains("INVALID_ARGUMENT"));assertEquals(6,controller.current().evidence().size());}
    @Test void noCaseAndMalformedArgsAreRejectedBeforeWorker()throws Exception{show();perform("query",Map.of("tool","logSearch","args","{}"));var progress=new AtomicReference<String>();SwingUtilities.invokeAndWait(()->{try{progress.set(((JTextArea)field("progress")).getText());}catch(Exception ex){throw new RuntimeException(ex);}});assertTrue(progress.get().contains("Open or create a case"));SwingUtilities.invokeAndWait(()->gui.submit(new Action("new",newArgs())));int revision=controller.current().caseVersion();perform("query",Map.of("tool","logSearch","args","[]"));assertEquals(revision,controller.current().caseVersion());SwingUtilities.invokeAndWait(()->{try{progress.set(((JTextArea)field("progress")).getText());}catch(Exception ex){throw new RuntimeException(ex);}});assertTrue(progress.get().contains("JSON object with string values"));}
}
