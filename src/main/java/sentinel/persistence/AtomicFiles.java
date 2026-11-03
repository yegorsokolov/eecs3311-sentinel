package sentinel.persistence;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.charset.*;
import java.io.IOException;
public final class AtomicFiles {
    private AtomicFiles() { }
    public static String read(Path p) {
        try(var in=Files.newInputStream(p)){byte[] b=in.readNBytes(20*1024*1024+1);if(b.length>20*1024*1024)throw new IOException();return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(b)).toString();}
        catch(IOException ex){throw new IllegalStateException("Cannot read bounded UTF-8 file.");}
    }
    public static void write(Path path,String content,boolean overwrite) {
        if(content.getBytes(StandardCharsets.UTF_8).length>20*1024*1024)throw new IllegalStateException("Output size limit exceeded.");
        Path p=path.toAbsolutePath().normalize(),tmp=null;
        try {Files.createDirectories(p.getParent()); if(!overwrite && Files.exists(p))throw new IOException();
            tmp=Files.createTempFile(p.getParent(),".sentinel-",".tmp");
            Files.writeString(tmp,content,StandardCharsets.UTF_8);
            try(var c=FileChannel.open(tmp,StandardOpenOption.WRITE)){c.force(true);}
            if(overwrite)Files.move(tmp,p,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
            else { // Create destination without overwrite; a competing writer must not be replaced.
                Files.createLink(p,tmp);Files.delete(tmp);
            }
        } catch(IOException ex){throw new IllegalStateException("Atomic write failed or destination already exists.");}
        finally{if(tmp!=null)try{Files.deleteIfExists(tmp);}catch(IOException ignored){}}
    }
}
