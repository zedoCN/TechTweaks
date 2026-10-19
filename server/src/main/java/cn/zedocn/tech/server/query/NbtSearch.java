package cn.zedocn.tech.server.query;

import com.google.gson.*;
import net.minecraft.nbt.*;
import java.util.*;

final class NbtSearch {
    private record Pending(Tag tag,String path,String key,int depth) {}
    private NbtSearch() {}
    static void search(Tag root,String path,String filter,int offset,int limit,JsonObject out) {
        var pending=new ArrayDeque<Pending>();pending.push(new Pending(root,"","",0));
        var rows=new ArrayList<JsonObject>();int scanned=0;boolean complete=true;
        while(!pending.isEmpty()) {
            if(scanned++>=8192){complete=false;break;}
            Pending value=pending.pop();Tag tag=value.tag();String preview;
            if(tag instanceof CompoundTag compound)preview="compound("+compound.size()+")";
            else if(tag instanceof CollectionTag collection)preview="list/array("+collection.size()+")";
            else preview=tag.toString();
            if(WorldQueries.matches(filter.toLowerCase(Locale.ROOT),value.key(),preview)) {
                JsonObject row=new JsonObject();
                row.addProperty("path",value.path());row.addProperty("relative_path",value.path());
                row.addProperty("path_scope","selected_value_relative");row.addProperty("nbt_type",tag.getId());
                row.addProperty("preview",preview.substring(0,Math.min(512,preview.length())));
                row.addProperty("preview_truncated",preview.length()>512);rows.add(row);
            }
            if(value.depth()>=64) {if(tag instanceof CompoundTag || tag instanceof CollectionTag)complete=false;continue;}
            if(tag instanceof CompoundTag compound) {
                var keys=new ArrayList<>(compound.keySet());keys.sort(Comparator.reverseOrder());
                for(String key:keys) {
                    if(pending.size()>=8192){complete=false;break;}
                    String next=(value.path().isEmpty()?"":value.path()+".")+new JsonPrimitive(key);
                    pending.push(new Pending(compound.get(key),next,key,value.depth()+1));
                }
            } else if(tag instanceof CollectionTag collection) {
                int count=Math.min(collection.size(),8192-pending.size());if(count<collection.size())complete=false;
                for(int i=count-1;i>=0;i--)pending.push(new Pending(collection.get(i),value.path()+"["+i+"]",Integer.toString(i),value.depth()+1));
            }
        }
        out.addProperty("selected_path",path);
        if(!out.has("selected_match"))out.addProperty("selected_match",0);
        if(!out.has("selected_matches"))out.addProperty("selected_matches",1);
        out.addProperty("path_semantics","select selected_path at selected_match first; row paths are relative to that value, not unique root addresses");
        if(!complete)out.addProperty("scan_incomplete_reason","NBT node, pending-entry or depth limit reached; narrow a known path, changing filter does not resume traversal");
        out.addProperty("find",filter);out.addProperty("scan_complete",complete);out.addProperty("scanned",Math.min(scanned,8192));
        WorldQueries.page(out,rows,offset,limit);
    }
}
