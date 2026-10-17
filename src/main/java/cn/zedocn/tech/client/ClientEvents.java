package cn.zedocn.tech.client;
import cn.zedocn.tech.TechTweaks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
@EventBusSubscriber(modid=TechTweaks.ID,value=Dist.CLIENT)
public final class ClientEvents {
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent e) { e.register(TechTweaks.MENU.get(),ControllerScreen::new); }
}
