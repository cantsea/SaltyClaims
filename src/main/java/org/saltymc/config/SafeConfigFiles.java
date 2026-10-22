package org.saltymc.config;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/** Injectable filesystem boundary: exact backups, compare-before-replace, and same-directory staging. */
public class SafeConfigFiles {
    public record Snapshot(byte[] bytes,FileTime modified,Object fileKey) {
        public Snapshot { bytes=bytes==null?null:bytes.clone(); }
        @Override public byte[] bytes() { return bytes==null?null:bytes.clone(); }
        public boolean exists() { return bytes!=null; }
    }
    public Snapshot read(Path path) throws IOException {
        if(!Files.exists(path,LinkOption.NOFOLLOW_LINKS)) return new Snapshot(null,null,null);
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)) throw new IOException("Editable YAML must be a regular file");
        BasicFileAttributes before=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(before.size()>8_000_000) throw new IOException("Editable YAML exceeds size limit");
        byte[] bytes=Files.readAllBytes(path);
        BasicFileAttributes after=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(before.size()!=after.size()||!before.lastModifiedTime().equals(after.lastModifiedTime())||!Objects.equals(before.fileKey(),after.fileKey()))
            throw new IOException("File changed during preparation");
        return new Snapshot(bytes,after.lastModifiedTime(),after.fileKey());
    }
    public void check(Path path,Snapshot expected) throws IOException {
        Snapshot current=read(path);
        if(!Arrays.equals(expected.bytes(),current.bytes())||!Objects.equals(expected.modified(),current.modified())||!Objects.equals(expected.fileKey(),current.fileKey()))
            throw new IOException("File changed since preparation");
    }
    public Path backup(Path original,Snapshot snapshot,int from,int to) throws IOException {
        Path directory=original.getParent().resolve("config-backups");
        if(Files.exists(directory,LinkOption.NOFOLLOW_LINKS)&&(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(directory)))
            throw new IOException("Backup directory must be a real directory");
        Files.createDirectories(directory); restrict(directory,true);
        Path backup=directory.resolve(original.getFileName()+".v"+from+"-to-v"+to+"."+UUID.randomUUID()+".bak");
        writePrivate(backup,snapshot.bytes()); return backup;
    }
    public Path stage(Path destination,byte[] bytes) throws IOException {
        Path temporary=Files.createTempFile(destination.getParent(),"."+destination.getFileName()+".",".tmp");
        try { restrict(temporary,false); write(temporary,bytes,StandardOpenOption.TRUNCATE_EXISTING); return temporary; }
        catch(IOException e) { Files.deleteIfExists(temporary); throw e; }
    }
    public void replace(Path temporary,Path destination,boolean existed) throws IOException {
        try { moveAtomically(temporary,destination); }
        catch(AtomicMoveNotSupportedException e) {
            if(existed) Files.move(temporary,destination,StandardCopyOption.REPLACE_EXISTING);
            else Files.move(temporary,destination);
        }
    }
    protected void moveAtomically(Path temporary,Path destination) throws IOException {
        Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    private void writePrivate(Path path,byte[] bytes) throws IOException {
        Files.createFile(path);
        try { restrict(path,false); write(path,bytes,StandardOpenOption.TRUNCATE_EXISTING); }
        catch(IOException e) { Files.deleteIfExists(path); throw e; }
    }
    private static void write(Path path,byte[] bytes,StandardOpenOption option) throws IOException {
        try(FileChannel channel=FileChannel.open(path,StandardOpenOption.WRITE,option,LinkOption.NOFOLLOW_LINKS)) {
            ByteBuffer buffer=ByteBuffer.wrap(bytes); while(buffer.hasRemaining()) channel.write(buffer); channel.force(true);
        }
    }
    private static void restrict(Path path,boolean directory) throws IOException {
        PosixFileAttributeView posix=Files.getFileAttributeView(path,PosixFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
        if(posix!=null) {
            posix.setPermissions(directory?PosixFilePermissions.fromString("rwx------"):PosixFilePermissions.fromString("rw-------")); return;
        }
        AclFileAttributeView acl=Files.getFileAttributeView(path,AclFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);
        if(acl!=null) {
            AclEntry.Builder entry=AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(path))
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class));
            if(directory) entry.setFlags(AclEntryFlag.DIRECTORY_INHERIT,AclEntryFlag.FILE_INHERIT);
            acl.setAcl(List.of(entry.build()));
        }
        // Filesystems without POSIX/ACL support inherit directory access; recovery docs explain the limit.
    }
}
