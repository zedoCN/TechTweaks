package cn.zedocn.tech.server.query;

import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.*;

final class MapQueries {
    private MapQueries() {}
    static JsonObject read(ServerLevel level,BlockPos center,JsonObject request,JsonObject out,String query,String filter,int offset,int limit) throws Exception {
        if(query.equals("map"))return surface(level,center,request,out,offset,limit);
        JsonObject properties=request.has("properties")?request.getAsJsonObject("properties"):new JsonObject();
        for(var entry:properties.entrySet())WorldQueries.text(properties,entry.getKey(),"");
        if(properties.size()>32)throw new IllegalArgumentException("At most 32 state properties");
        if(filter.isEmpty() && properties.isEmpty())throw new IllegalArgumentException("Block search requires filter or properties");
        int budget=WorldQueries.integer(request,"scan_budget",8192,1,32768);
        int minY=WorldQueries.integer(request,"min_y",Math.max(level.getMinY(),center.getY()-32),level.getMinY(),level.getMaxY());
        int maxY=WorldQueries.integer(request,"max_y",Math.min(level.getMaxY(),center.getY()+32),minY,level.getMaxY());
        JsonArray results=new JsonArray();int work=0,scanned=0,skipped=0,cursor,end,start;
        if(query.equals("blocks.saved")) {
            CompoundTag chunk=(CompoundTag)SavedNbt.read(level,request,out,true,center);
            var sections=new ArrayList<CompoundTag>();
            for(Tag tag:chunk.getListOrEmpty("sections"))if(tag instanceof CompoundTag section)sections.add(section);
            sections.sort(Comparator.comparingInt(section->section.getIntOr("Y",0)));
            end=Math.multiplyExact(sections.size(),4096);cursor=WorldQueries.integer(request,"cursor",0,0,end);start=cursor;
            int unsupported=0;
            while(cursor<end && work<budget && results.size()<limit) {
                int sectionIndex=cursor/4096,index=cursor%4096;
                CompoundTag section=sections.get(sectionIndex),states=section.getCompoundOrEmpty("block_states");
                ListTag palette=states.getListOrEmpty("palette");
                int sy=section.getIntOr("Y",0)*16;
                if(sy>maxY || sy+15<minY){work++;cursor=(sectionIndex+1)*4096;continue;}
                boolean[] matching=new boolean[palette.size()];boolean any=false;
                for(int p=0;p<palette.size();p++) {
                    CompoundTag state=palette.getCompoundOrEmpty(p);
                    matching[p]=matches(state,filter,properties);any|=matching[p];
                }
                if(palette.isEmpty()){work++;unsupported++;cursor=(sectionIndex+1)*4096;continue;}
                if(!any){work++;skipped++;cursor=(sectionIndex+1)*4096;continue;}
                long[] data=states.getLongArray("data").orElseGet(()->new long[0]);
                while(index<4096 && work<budget && results.size()<limit) {
                    work++;scanned++;int y=sy+(index>>8);
                    int paletteIndex=palette.size()==1?0:paletteIndex(data,palette.size(),index);
                    if(paletteIndex>=palette.size())throw new IllegalArgumentException("Invalid saved palette index");
                    if(y>=minY && y<=maxY && matching[paletteIndex]) {
                        BlockPos pos=new BlockPos((center.getX()>>4)*16+(index&15),y,(center.getZ()>>4)*16+((index>>4)&15));
                        CompoundTag state=palette.getCompoundOrEmpty(paletteIndex);JsonObject row=WorldQueries.position(pos);
                        row.addProperty("id",state.getStringOr("Name",""));row.add("properties",stateProperties(state));results.add(row);
                    }
                    index++;cursor++;
                }
            }
            out.addProperty("unsupported_sections",unsupported);
            out.addProperty("scope","one_saved_chunk");
        } else {
            int radius=WorldQueries.integer(request,"radius",16,1,256),width=radius*2+1,height=maxY-minY+1;
            end=Math.multiplyExact(Math.multiplyExact(width,width),height);cursor=WorldQueries.integer(request,"cursor",0,0,end);start=cursor;
            var candidates=new IdentityHashMap<LevelChunkSection,Boolean>();int unloaded=0;
            while(cursor<end && work<budget && results.size()<limit) {
                work++;int x=center.getX()-radius+(cursor/height)/width,z=center.getZ()-radius+(cursor/height)%width,y=minY+cursor%height;
                var chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);
                if(chunk==null){unloaded++;cursor+=height-cursor%height;continue;}
                var section=chunk.getSection(chunk.getSectionIndex(y));
                boolean any=candidates.computeIfAbsent(section,s->s.getStates().maybeHas(state->matches(state,filter,properties)));
                if(!any){skipped++;cursor+=Math.min(16-(y&15),maxY-y+1);continue;}
                BlockState state=section.getBlockState(x&15,y&15,z&15);scanned++;cursor++;
                if(matches(state,filter,properties)) {
                    JsonObject row=WorldQueries.position(new BlockPos(x,y,z));row.addProperty("id",BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                    JsonObject props=new JsonObject();state.getValues().forEach(v->props.addProperty(v.property().getName(),v.valueName()));row.add("properties",props);results.add(row);
                }
            }
            out.addProperty("unloaded_columns_skipped",unloaded);out.addProperty("scope","live_loaded_blocks_in_requested_box");
            out.addProperty("radius",radius);out.addProperty("source","live_server_blocks");
        }
        out.addProperty("min_y",minY);out.addProperty("max_y",maxY);out.add("results",results);out.addProperty("returned",results.size());
        out.addProperty("cursor",start);out.addProperty("scan_complete",cursor>=end);out.addProperty("total_positions",end);
        out.addProperty("visited_positions",cursor-start);out.addProperty("scanned_blocks",scanned);out.addProperty("work_units",work);
        out.addProperty("palette_sections_skipped",skipped);if(cursor<end)out.addProperty("next_cursor",cursor);
        out.addProperty("pagination_consistency","live_or_saved_record_can_change_compare_tick_or_record_sha256");return out;
    }
    private static JsonObject surface(ServerLevel level,BlockPos center,JsonObject request,JsonObject out,int offset,int limit) {
        int radius=WorldQueries.integer(request,"radius",64,1,256),step=WorldQueries.integer(request,"step",16,1,64),width=radius*2/step+1;
        int count=width*width;JsonArray rows=new JsonArray();int end=Math.min(count,offset+limit);
        for(int i=offset;i<end;i++) {
            int x=center.getX()-radius+(i/width)*step,z=center.getZ()-radius+(i%width)*step;
            JsonObject row=new JsonObject();row.addProperty("x",x);row.addProperty("z",z);
            var chunk=level.getChunkSource().getChunkNow(x>>4,z>>4);row.addProperty("loaded",chunk!=null);
            if(chunk!=null) {
                int y=chunk.getHeight(Heightmap.Types.WORLD_SURFACE,x,z);row.addProperty("surface_y",y);
                BlockPos pos=new BlockPos(x,y,z);row.addProperty("surface_block",BuiltInRegistries.BLOCK.getKey(chunk.getBlockState(pos).getBlock()).toString());
                row.addProperty("biome",level.getBiome(pos).unwrapKey().map(key->key.identifier().toString()).orElse(null));
            }
            rows.add(row);
        }
        out.add("results",rows);out.addProperty("total_samples",count);out.addProperty("offset",offset);out.addProperty("step",step);
        out.addProperty("truncated",end<count);if(end<count)out.addProperty("next_offset",end);out.addProperty("source","live_loaded_surface_samples");return out;
    }
    static int paletteIndex(long[] data,int paletteSize,int index) {
        int bits=Math.max(4,32-Integer.numberOfLeadingZeros(paletteSize-1)),perLong=64/bits;
        int word=index/perLong;if(word>=data.length)throw new IllegalArgumentException("Truncated saved block state array");
        return (int)((data[word]>>>((index%perLong)*bits))&((1L<<bits)-1));
    }
    private static boolean matches(BlockState state,String filter,JsonObject properties) {
        if(!WorldQueries.matches(filter,BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()))return false;
        for(var wanted:properties.entrySet()) {
            if(state.getValues().noneMatch(value->value.property().getName().equals(wanted.getKey())
                    && value.valueName().equals(wanted.getValue().getAsString())))return false;
        }return true;
    }
    private static boolean matches(CompoundTag state,String filter,JsonObject properties) {
        if(!WorldQueries.matches(filter,state.getStringOr("Name","")))return false;
        var props=state.getCompoundOrEmpty("Properties");
        for(var value:properties.entrySet())if(!props.getStringOr(value.getKey(),"").equals(value.getValue().getAsString()))return false;
        return true;
    }
    private static JsonObject stateProperties(CompoundTag state) {
        JsonObject props=new JsonObject();var tag=state.getCompoundOrEmpty("Properties");for(String key:tag.keySet())props.addProperty(key,tag.getStringOr(key,""));return props;
    }
}
