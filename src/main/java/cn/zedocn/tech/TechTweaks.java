package cn.zedocn.tech;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.*;
import cn.zedocn.tech.integration.FoundryAutomation;

@Mod(TechTweaks.ID)
public final class TechTweaks {
    public static final String ID = "tech_tweaks";
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    public static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    public static final DeferredBlock<ControllerBlock> TRIAL = block("trial_controller", ControllerKind.TRIAL);
    public static final DeferredBlock<ControllerBlock> VAULT = block("vault_controller", ControllerKind.VAULT);
    public static final DeferredBlock<ControllerBlock> ENVIRONMENT = block("environment_controller", ControllerKind.ENVIRONMENT);
    public static final DeferredBlock<ControllerBlock> PROTECTION = block("protection_controller", ControllerKind.PROTECTION);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ControllerBlockEntity>> ENTITY = ENTITIES.register("controller", () -> new BlockEntityType<>(ControllerBlockEntity::new, TRIAL.get(), VAULT.get(), ENVIRONMENT.get(), PROTECTION.get()));
    public static final DeferredHolder<MenuType<?>, MenuType<ControllerMenu>> MENU = MENUS.register("controller", () -> new MenuType<>(ControllerMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredBlock<MeteorGuideRodBlock> METEOR_ROD = BLOCKS.registerBlock("meteor_guide_rod", MeteorGuideRodBlock::new, props -> props.noOcclusion().strength(3F,6F).sound(net.minecraft.world.level.block.SoundType.COPPER).requiresCorrectToolForDrops());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MeteorGuideRodBlockEntity>> METEOR_ROD_ENTITY = ENTITIES.register("meteor_guide_rod", () -> new BlockEntityType<>(MeteorGuideRodBlockEntity::new, METEOR_ROD.get()));
    static {
        ITEMS.registerSimpleBlockItem(METEOR_ROD);
        TABS.register("controllers", () -> CreativeModeTab.builder().title(Component.literal("TECH 自动化")).icon(() -> TRIAL.get().asItem().getDefaultInstance()).displayItems((p,o) -> { o.accept(TRIAL.get()); o.accept(VAULT.get()); o.accept(ENVIRONMENT.get()); o.accept(PROTECTION.get()); o.accept(METEOR_ROD.get()); }).build());
    }
    private static DeferredBlock<ControllerBlock> block(String name, ControllerKind kind) {
        var b = BLOCKS.registerBlock(name, props -> new ControllerBlock(kind, props), props -> props.strength(3.5F).requiresCorrectToolForDrops());
        ITEMS.registerSimpleBlockItem(b);
        return b;
    }
    public TechTweaks(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); MENUS.register(bus); TABS.register(bus);
        bus.addListener(this::capabilities);
        NeoForge.EVENT_BUS.addListener(cn.zedocn.tech.integration.CuriosSupport::commands);
        NeoForge.EVENT_BUS.addListener(AreaProtection::checkSpawn);
        NeoForge.EVENT_BUS.addListener(AreaProtection::levelUnload);
        NeoForge.EVENT_BUS.addListener(EnvironmentControl::unload);
        NeoForge.EVENT_BUS.addListener(cn.zedocn.tech.performance.PerformanceFixes::commands);
    }
    private void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, ENTITY.get(), (be, side) -> be.externalInventory);
        event.registerBlockEntity(Capabilities.Energy.BLOCK, ENTITY.get(), (be, side) -> be.energy);
        FoundryAutomation.register(event);
    }
}
