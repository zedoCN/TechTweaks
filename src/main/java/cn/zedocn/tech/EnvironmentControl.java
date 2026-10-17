package cn.zedocn.tech;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.level.LevelEvent;
public final class EnvironmentControl {
    private static final Set<ControllerBlockEntity> loaded=new HashSet<>();
    public static void add(ControllerBlockEntity be) { loaded.add(be); }
    public static void remove(ControllerBlockEntity be) { loaded.remove(be); }
    public static void unload(LevelEvent.Unload e) { loaded.removeIf(be -> be.getLevel()==e.getLevel()); }
    private static boolean running(ControllerBlockEntity be) {
        return !be.isRemoved() && be.enabled && be.energy.getAmountAsLong()>=1_000 && (!be.redstoneControl || be.getLevel().hasNeighborSignal(be.getBlockPos()));
    }
    public static boolean ownsControls(ControllerBlockEntity be) {
        var level=(ServerLevel)be.getLevel();
        for(var other:loaded) {
            if(other==be || !running(other) || !(other.getLevel() instanceof ServerLevel otherLevel) || otherLevel.getServer()!=level.getServer()) continue;
            boolean timeConflict=be.timeMode!=0 && other.timeMode!=0 && level.dimensionType().defaultClock().isPresent() && level.dimensionType().defaultClock().equals(otherLevel.dimensionType().defaultClock());
            boolean weatherConflict=be.weatherMode!=0 && other.weatherMode!=0 && level.getWeatherData()==otherLevel.getWeatherData();
            if(!timeConflict && !weatherConflict) continue;
            int dimensionOrder=otherLevel.dimension().identifier().toString().compareTo(level.dimension().identifier().toString());
            if(dimensionOrder<0 || dimensionOrder==0 && other.getBlockPos().asLong()<be.getBlockPos().asLong()) return false;
        }
        return true;
    }
}
