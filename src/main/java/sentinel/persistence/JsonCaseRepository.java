package sentinel.persistence;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.*;
import sentinel.domain.*;
/** Atomic bounded schema-v1 files, monotonic revisions and cross-process write exclusion. */
public final class JsonCaseRepository implements CaseRepository {
    private final Path root;
    public JsonCaseRepository(Path root){this.root=Objects.requireNonNull(root).toAbsolutePath().normalize();}
    private Path path(UUID id){return root.resolve(Objects.requireNonNull(id)+".json");}
    public synchronized int save(CaseSnapshot snapshot){
        try{Files.createDirectories(root);try(var channel=FileChannel.open(root.resolve(snapshot.id()+".lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.tryLock()){
            if(lock==null)throw new IllegalStateException("Case is being saved by another process.");
            Path p=path(snapshot.id());String json=JsonCodec.encode(snapshot);
            if(Files.exists(p)){CaseSnapshot old=read(p);if(snapshot.caseVersion()<old.caseVersion() || snapshot.caseVersion()==old.caseVersion()&&!json.equals(JsonCodec.encode(old)))throw new IllegalStateException("Stale/conflicting case revision.");}
            AtomicFiles.write(p,json,true);return snapshot.caseVersion();
        }}catch(java.io.IOException|OverlappingFileLockException ex){throw new IllegalStateException("Case save unavailable.");}
    }
    private CaseSnapshot read(Path p){CaseSnapshot s=JsonCodec.decode(AtomicFiles.read(p),CaseSnapshot.class);if(!p.getFileName().toString().equals(s.id()+".json"))throw new IllegalArgumentException("Case identity mismatch.");return s;}
    public Investigation load(UUID id){return Investigation.restore(read(path(id)));}
    public List<CaseSummary> list(){if(!Files.exists(root))return List.of();try(var paths=Files.list(root)){return paths.filter(p->p.getFileName().toString().endsWith(".json")).sorted().map(this::read).map(s->new CaseSummary(s.id(),s.caseVersion(),s.status(),s.goal())).toList();}catch(java.io.IOException ex){throw new IllegalStateException("Case listing unavailable.");}}
}
