package cn.zedocn.tech.server.query;

import com.google.gson.*;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class SavedNbtTest {
    @TempDir Path root;
    private byte[] encode() throws IOException {
        var tag=new CompoundTag();tag.putString("item.id","minecraft:diamond");tag.putLong("energy",Long.MAX_VALUE);
        var list=new ListTag();var entry=new CompoundTag();entry.putString("id","ae2:encoded_pattern");list.add(entry);tag.put("items",list);
        var bytes=new ByteArrayOutputStream();NbtIo.write(tag,new DataOutputStream(bytes));return bytes.toByteArray();
    }
    @Test void readsGzipZlibAndRawWithoutLosingLongsOrNestedTags() throws Exception {
        byte[] raw=encode();
        for(int compression:new int[]{1,2,3}) {
            var bytes=new ByteArrayOutputStream();
            if(compression==3)bytes.write(raw);
            else try(var zip=compression==1?new GZIPOutputStream(bytes):new DeflaterOutputStream(bytes)){zip.write(raw);}
            var decoded=SavedNbt.decode(bytes.toByteArray(),compression);
            assertEquals(Long.MAX_VALUE,decoded.getLongOr("energy",0));assertEquals("minecraft:diamond",decoded.getStringOr("item.id",""));
            assertEquals("ae2:encoded_pattern",decoded.getListOrEmpty("items").getCompoundOrEmpty(0).getStringOr("id",""));
        }
    }
    @Test void rejectsUnknownCompressionAndTruncatedPayloads() {
        assertThrows(IOException.class,()->SavedNbt.decode(new byte[]{10},4));
        assertThrows(IOException.class,()->SavedNbt.decode(new byte[]{10,0},3));
    }
    @Test void rejectsTraversalAndEscapingSymlinks() throws Exception {
        Path inside=root.resolve("world");Files.createDirectories(inside);Path outside=root.resolve("secret.dat");Files.write(outside,new byte[]{1});
        assertThrows(IOException.class,()->SavedNbt.confined(inside,inside.resolve("../secret.dat")));
        Files.createSymbolicLink(inside.resolve("escape.dat"),outside);
        assertThrows(IOException.class,()->SavedNbt.confined(inside,inside.resolve("escape.dat")));
        Path valid=inside.resolve("level.dat");Files.write(valid,encode());assertEquals(valid.toRealPath(),SavedNbt.confined(inside,valid));
    }
    @Test void rejectsDecompressionAllocationBomb() throws Exception {
        var bytes=new ByteArrayOutputStream();
        try(var data=new DataOutputStream(new GZIPOutputStream(bytes))) {
            data.writeByte(10);data.writeUTF("");data.writeByte(7);data.writeUTF("huge");data.writeInt(Integer.MAX_VALUE);
        }
        assertThrows(RuntimeException.class,()->SavedNbt.decode(bytes.toByteArray(),1));
    }
    @Test void searchesActualNbtPathsAndPreservesMatchAndPagingBoundaries() throws Exception {
        var tag=SavedNbt.decode(encode(),3);JsonObject result=new JsonObject();
        NbtSearch.search(tag,"","encoded_pattern",0,1,result);
        assertTrue(result.get("scan_complete").getAsBoolean());assertEquals(1,result.get("matched").getAsInt());
        assertEquals("\"items\"[0].\"id\"",result.getAsJsonArray("results").get(0).getAsJsonObject().get("path").getAsString());
        result=new JsonObject();NbtSearch.search(tag,"","",0,1,result);assertTrue(result.get("truncated").getAsBoolean());assertEquals(1,result.get("next_offset").getAsInt());
    }
    @Test void readsVanillaPackedPalettesAcrossLongBoundariesAndPadding() {
        var random=new java.util.Random(20261018);
        for(int size:new int[]{2,16,17,32,33,1024,4096}) {
            int bits=Math.max(4,32-Integer.numberOfLeadingZeros(size-1));
            var storage=new net.minecraft.util.SimpleBitStorage(bits,4096);
            for(int i=0;i<4096;i++)storage.set(i,random.nextInt(size));
            for(int i=0;i<4096;i++)assertEquals(storage.get(i),MapQueries.paletteIndex(storage.getRaw(),size,i));
        }
        assertThrows(IllegalArgumentException.class,()->MapQueries.paletteIndex(new long[0],2,0));
    }
    @Test void readsStorageBeforeCheckingLazyNamespaceKeys() {
        var id=net.minecraft.resources.Identifier.parse("test:stored");
        var tag=new CompoundTag();tag.putLong("value",Long.MAX_VALUE);
        var storage=new net.minecraft.world.level.storage.CommandStorage(null) {
            boolean loaded;
            @Override public CompoundTag get(net.minecraft.resources.Identifier key){loaded=true;return tag;}
            @Override public java.util.stream.Stream<net.minecraft.resources.Identifier> keys(){return loaded?java.util.stream.Stream.of(id):java.util.stream.Stream.empty();}
        };
        assertSame(tag,WorldQueries.readStorage(storage,id));
        assertThrows(IllegalArgumentException.class,()->WorldQueries.readStorage(storage,net.minecraft.resources.Identifier.parse("test:missing")));
    }
    @Test void nbtPathMustConsumeTheWholeRequest() throws Exception {
        var tag=new CompoundTag();tag.putString("with space","value");
        assertEquals("value",WorldQueries.selectPath(tag,"\"with space\"").getFirst().asString().orElseThrow());
        assertThrows(IllegalArgumentException.class,()->WorldQueries.selectPath(tag,"\"with space\" trailing"));
    }
    @Test void rejectsNonIntegralAndOverflowingRequestValues() {
        for(String bad:new String[]{"1.2","2147483648","true","\"1\""}) {
            var request=JsonParser.parseString("{\"radius\":"+bad+"}").getAsJsonObject();
            assertThrows(IllegalArgumentException.class,()->WorldQueries.integer(request,"radius",64,1,256));
        }
    }
}
