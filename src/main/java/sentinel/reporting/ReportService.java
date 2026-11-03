package sentinel.reporting;
import java.nio.file.*;
import java.util.*;
import sentinel.domain.*;
import sentinel.persistence.AtomicFiles;
/** Deterministic Markdown from accepted values; no inference and no silent overwrite. */
public final class ReportService {
    public Path export(Investigation investigation,Path path){return export(investigation,path,false);}
    public Path export(Investigation investigation,Path path,boolean overwrite){AtomicFiles.write(path,preview(investigation.snapshot()),overwrite);return path.toAbsolutePath();}
    private static String safe(Object s){return String.valueOf(s).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("`","\\`").replace("[","\\[").replace("]","\\]");}
    public String preview(CaseSnapshot s){
        var b=new StringBuilder("# Sentinel investigation report\n\nFormat version: 1\n\n");
        b.append("Case: ").append(s.id()).append(" / revision ").append(s.caseVersion()).append("\n\nStatus: ").append(s.status()).append("\n\nGoal: ").append(safe(s.goal())).append("\n\nScope: ").append(safe(s.scope())).append("\n\nModel: ").append(safe(s.modelVersion())).append(" / Prompt: ").append(safe(s.promptVersion())).append(" / Policy: ").append(safe(s.policyVersion())).append("\n\nFeed: ").append(safe(s.feedVersion()));
        b.append("\n\n## Plan\n\n").append(safe(s.plan()));
        b.append("\n\n## Evidence and provenance\n\n");for(var e:s.evidence())b.append("- ").append(e.id()).append(" / source ").append(safe(e.sourceEventId())).append(" / ").append(safe(e.datasetVersion())).append(" / ").append(safe(e.provenance())).append("\n  ").append(safe(e.payload())).append("\n");
        var a=s.assessment();if(a==null)b.append("\n## Assessment\n\nUnavailable or not yet run; export is partial.\n");else{
            b.append("\n## Timeline\n\n");for(var e:a.timeline())b.append("- ").append(e.timestamp()).append(" / ").append(safe(e.eventId())).append(" / ").append(safe(e.eventType())).append("\n");
            b.append("\nRelated pairs (candidate relationships, not causation; maximum 1000): ").append(safe(new sentinel.analysis.TimelineBuilder().correlate(a.timeline())));
            b.append("\n\n## Severity and completeness\n\n").append(safe(a.severity())).append("\n\n").append(a.completeness());
            b.append("\n\n## Hypotheses\n\n");for(var h:a.hypotheses())b.append("- ").append(safe(h)).append("\n");
            b.append("\n## Recommendations for human review\n\n");for(var r:a.recommendations())b.append("- ").append(safe(r)).append("\n");
        }
        b.append("\n## Conversation\n\n");for(var c:s.conversation())b.append("- ").append(safe(c)).append("\n");
        b.append("\n## History and failures\n\n");for(var h:s.history())b.append("- ").append(safe(h)).append("\n");return b.toString();
    }
}
