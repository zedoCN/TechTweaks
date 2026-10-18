package cn.zedocn.tech.server.query;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.*;

/** Read only the requested saved record, never open a world or acquire a chunk ticket. */
final class SavedNbt {
    static final int MAX_BYTES=8*1024*1024;
    private SavedNbt() {}

    static Tag read(ServerLevel level,JsonObject request,JsonObject out,boolean chunk,BlockPos pos) throws Exception {
        Path root=level.getServer().getWorldPath(LevelResource.ROOT).toRealPath();
        Path file;
        if(chunk) {
            var id=level.dimension().identifier();
            file=root.resolve("dimensions").resolve(id.getNamespace()).resolve(id.getPath()).resolve("region")
                    .resolve("r."+((pos.getX()>>4)>>5)+"."+((pos.getZ()>>4)>>5)+".mca");
        } else {
            String name=WorldQueries.text(request,"file","");
            if(name.isEmpty() || Path.of(name).isAbsolute() || !name.endsWith(".dat"))throw new IllegalArgumentException("file must name one world-relative .dat file");
            file=root.resolve(name);
        }
        file=confined(root,file);
        BasicFileAttributes before=Files.readAttributes(file,BasicFileAttributes.class);
        byte[] bytes;
        Tag tag;
        if(chunk) {
            int cx=pos.getX()>>4,cz=pos.getZ()>>4;
            int index=Math.floorMod(cx,32)+Math.floorMod(cz,32)*32;
            try(RandomAccessFile region=new RandomAccessFile(file.toFile(),"r")) {
                if(region.length()<8192)throw new IOException("Truncated region header");
                region.seek(index*4L);int location=region.readInt(),sector=location>>>8,sectors=location&255;
                if(sector==0 || sectors==0)throw new IOException("Chunk has no saved record");
                if(sector<2 || (long)sector*4096+5>region.length())throw new IOException("Invalid region sector range");
                region.seek(4096L+index*4L);out.addProperty("chunk_saved_epoch_seconds",Integer.toUnsignedLong(region.readInt()));
                region.seek(sector*4096L);int length=region.readInt(),compression=region.readUnsignedByte();
                if(length<1 || length>sectors*4096-4 || length>MAX_BYTES || (long)sector*4096+4+length>region.length())throw new IOException("Invalid or oversized saved chunk");
                if((compression&128)!=0) {
                    if(length!=1)throw new IOException("Invalid external chunk record");
                    Path external=confined(root,file.resolveSibling("c."+cx+"."+cz+".mcc"));
                    var externalBefore=Files.readAttributes(external,BasicFileAttributes.class);
                    bytes=bounded(external);tag=decode(bytes,compression&127);
                    unchanged(external,externalBefore);
                } else {bytes=new byte[length-1];region.readFully(bytes);tag=decode(bytes,compression);}
                out.addProperty("chunk_x",cx);out.addProperty("chunk_z",cz);
            }
        } else {
            bytes=bounded(file);
            int compression=bytes.length>=2 && (bytes[0]&255)==31 && (bytes[1]&255)==139 ? 1:3;
            tag=decode(bytes,compression);
        }
        unchanged(file,before);
        out.addProperty("source","saved_world_file");out.addProperty("file",root.relativize(file).toString());
        out.addProperty("file_modified_at",before.lastModifiedTime().toInstant().toString());
        out.addProperty("record_sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        out.addProperty("consistency","saved_disk_record_may_lag_live_state_not_an_atomic_world_snapshot");
        return tag;
    }

    static Path confined(Path root,Path file) throws IOException {
        Path normalized=file.toAbsolutePath().normalize();
        if(!normalized.startsWith(root.toAbsolutePath().normalize()))throw new IOException("Path is outside world root");
        Path real=normalized.toRealPath();
        if(!real.startsWith(root.toRealPath()) || !Files.isRegularFile(real))throw new IOException("Not a regular file inside world root");
        return real;
    }
    private static byte[] bounded(Path file) throws IOException {
        if(Files.size(file)>MAX_BYTES)throw new IOException("Saved NBT record exceeds 8 MiB");
        try(InputStream input=Files.newInputStream(file)) {
            byte[] bytes=input.readNBytes(MAX_BYTES+1);
            if(bytes.length>MAX_BYTES)throw new IOException("Saved NBT record exceeds 8 MiB");return bytes;
        }
    }
    static CompoundTag decode(byte[] bytes,int compression) throws IOException {
        InputStream input=new ByteArrayInputStream(bytes);
        input=switch(compression) {
            case 1 -> new GZIPInputStream(input);
            case 2 -> new InflaterInputStream(input);
            case 3 -> input;
            default -> throw new IOException("Unsupported saved chunk compression "+compression);
        };
        try(var data=new DataInputStream(new BufferedInputStream(input))) {
            return NbtIo.read(data,NbtAccounter.create(MAX_BYTES));
        }
    }
    private static void unchanged(Path file,BasicFileAttributes before) throws IOException {
        var after=Files.readAttributes(file,BasicFileAttributes.class);
        if(before.size()!=after.size() || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !java.util.Objects.equals(before.fileKey(),after.fileKey()))
            throw new IOException("Saved file changed during read; use live NBT or retry a later read");
    }
}
