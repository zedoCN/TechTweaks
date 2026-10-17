package cn.zedocn.tech;

import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.*;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public final class ControllerBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int SLOTS = 9;
    private final SimpleContainer inventory = new SimpleContainer(SLOTS) { @Override public void setChanged() { super.setChanged(); ControllerBlockEntity.this.setChanged(); } };
    public final SimpleEnergyHandler energy = new SimpleEnergyHandler(Balance.ENERGY_CAPACITY, 100_000, 0) {
        @Override protected void onEnergyChanged(int previous) { setChanged(); }
    };
    public boolean enabled = true, ominous = false, redstoneControl = false;
    private boolean lastSignal;
    public int players = 1, radius = 16, timeMode = 0, weatherMode = 0, status = 0;
    public long protectedUntil;
    public int protectedRadius;
    public final ResourceHandler<ItemResource> externalInventory = new ResourceHandler<>() {
        private ResourceHandler<ItemResource> delegate() { return VanillaContainerWrapper.of(ControllerBlockEntity.this); }
        public int size() { return SLOTS; }
        public ItemResource getResource(int i) { return delegate().getResource(i); }
        public long getAmountAsLong(int i) { return delegate().getAmountAsLong(i); }
        public long getCapacityAsLong(int i, ItemResource r) { return delegate().getCapacityAsLong(i,r); }
        public boolean isValid(int i, ItemResource r) { return i == 0 && validInput(r.toStack()); }
        public int insert(int i, ItemResource r, int n, TransactionContext tx) { return isValid(i,r) ? delegate().insert(i,r,n,tx) : 0; }
        public int extract(int i, ItemResource r, int n, TransactionContext tx) { return i > 0 ? delegate().extract(i,r,n,tx) : 0; }
    };
    public ControllerBlockEntity(BlockPos pos, BlockState state) {
        super(TechTweaks.ENTITY.get(), pos, state);
    }
    public ControllerKind kind() { return ((ControllerBlock)getBlockState().getBlock()).kind; }
    public BlockPos targetPos() { return worldPosition.relative(getBlockState().getValue(ControllerBlock.FACING)); }
    public boolean validInput(ItemStack s) {
        return switch (kind()) { case TRIAL -> s.is(Items.OMINOUS_BOTTLE); case VAULT -> s.is(Items.TRIAL_KEY) || s.is(Items.OMINOUS_TRIAL_KEY); default -> false; };
    }
    public boolean spend(int amount) {
        if (energy.getAmountAsLong() < amount) { status = 2; return false; }
        energy.set((int)energy.getAmountAsLong() - amount); return true;
    }
    public boolean canFit(ItemStack stack) { return OutputBuffer.transfer(this,stack,1,false).isEmpty(); }
    public ItemStack receive(ItemStack stack) { return OutputBuffer.transfer(this,stack,1,true); }
    public static void tick(Level raw, BlockPos pos, BlockState state, ControllerBlockEntity be) {
        if (!(raw instanceof ServerLevel level)) return;
        boolean signal = level.hasNeighborSignal(pos);
        if (signal != be.lastSignal) {
            if (signal && be.enabled && be.kind() == ControllerKind.TRIAL) TrialAutomation.start(level, be);
            be.lastSignal = signal; be.setChanged();
        }
        if ((level.getGameTime() + pos.asLong()) % 20 != 0) return;
        if (!be.enabled || be.redstoneControl && !signal) { be.status = 5; return; }
        switch (be.kind()) {
            case TRIAL -> TrialAutomation.tick(level,be);
            case VAULT -> VaultAutomation.tick(level, be);
            case ENVIRONMENT -> be.environment(level);
            case PROTECTION -> {
                if (be.spend(Balance.protectionCost(be.radius))) { be.protectedRadius = be.radius; be.protectedUntil = level.getGameTime() + 20; be.status = 0; }
            }
        }
    }
    private void environment(ServerLevel level) {
        if (timeMode == 0 && weatherMode == 0) { status = 0; return; }
        if (!level.dimensionType().hasSkyLight()) { status = 6; return; }
        if (!EnvironmentControl.ownsControls(this)) { status=8; return; }
        if (!spend(1_000)) return;
        if (timeMode != 0) level.dimensionType().defaultClock().ifPresent(clock -> {
            long current=level.clockManager().getTotalTicks(clock);
            level.clockManager().setTotalTicks(clock, Math.floorDiv(current,24_000L)*24_000L + (timeMode==1 ? 6_000L : 18_000L));
        });
        if (weatherMode != 0) {
            boolean rain = weatherMode >= 2, thunder = weatherMode == 3;
            level.getServer().setWeatherParameters(rain ? 0 : 40, rain ? 40 : 0, rain, thunder);
        }
        status = 0;
    }
    public void action(int id) {
        if (!(level instanceof ServerLevel server)) return;
        switch (id) {
            case 0 -> { enabled = !enabled; if (!enabled) protectedUntil = 0; }
            case 1 -> { players = Balance.players(players - 1); radius = Balance.radius(radius - 8); }
            case 2 -> { players = Balance.players(players + 1); radius = Balance.radius(radius + 8); }
            case 3 -> timeMode = (timeMode + 1) % 3;
            case 4 -> weatherMode = (weatherMode + 1) % 4;
            case 5 -> { if (kind() == ControllerKind.TRIAL && enabled) TrialAutomation.start(server, this); }
            case 6 -> ominous = !ominous;
            case 7 -> redstoneControl = !redstoneControl;
            default -> { return; }
        }
        setChanged();
    }
    @Override public void onLoad() { super.onLoad(); if (kind() == ControllerKind.PROTECTION && level instanceof ServerLevel) AreaProtection.add(this); if (kind()==ControllerKind.ENVIRONMENT && level instanceof ServerLevel) EnvironmentControl.add(this); }
    @Override public void setRemoved() { AreaProtection.remove(this); EnvironmentControl.remove(this); super.setRemoved(); }
    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) { if (level != null) Containers.dropContents(level, pos, this); super.preRemoveSideEffects(pos,state); }
    @Override protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out); ContainerHelper.saveAllItems(out.child("inventory"), inventory.getItems());
        energy.serialize(out.child("energy"));
        out.putBoolean("enabled", enabled); out.putBoolean("ominous", ominous); out.putBoolean("redstone_control", redstoneControl); out.putBoolean("last_signal", lastSignal);
        out.putInt("players", players); out.putInt("radius", radius); out.putInt("time_mode", timeMode); out.putInt("weather_mode", weatherMode);
    }
    @Override protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in); in.child("inventory").ifPresent(v -> ContainerHelper.loadAllItems(v, inventory.getItems())); in.child("energy").ifPresent(energy::deserialize); energy.set(Math.min(Balance.ENERGY_CAPACITY,(int)energy.getAmountAsLong()));
        enabled = in.getBooleanOr("enabled", true); ominous = in.getBooleanOr("ominous", false); redstoneControl = in.getBooleanOr("redstone_control", false); lastSignal = in.getBooleanOr("last_signal", false);
        players = Balance.players(in.getIntOr("players",1)); radius = Balance.radius(in.getIntOr("radius",16)); timeMode = Math.clamp(in.getIntOr("time_mode",0),0,2); weatherMode = Math.clamp(in.getIntOr("weather_mode",0),0,3);
    }
    public ContainerData menuData() {
        return new ContainerData() {
            public int get(int i) { return switch(i) {
                case 0 -> kind().ordinal(); case 1 -> enabled ? 1 : 0; case 2 -> players; case 3 -> radius; case 4 -> timeMode; case 5 -> weatherMode;
                case 6 -> ominous ? 1 : 0; case 7 -> redstoneControl ? 1 : 0; case 8 -> (int)energy.getAmountAsLong() & 65535; case 9 -> (int)energy.getAmountAsLong() >>> 16; case 10 -> status;
                case 11 -> level!=null && level.hasChunkAt(targetPos()) && level.getBlockEntity(targetPos()) instanceof net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity trial ? ((ControlledTrial)(Object)trial.getTrialSpawner()).techPlayers() : 0;
                case 12 -> level!=null && level.hasChunkAt(targetPos()) && level.getBlockEntity(targetPos()) instanceof net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity trial ? ((cn.zedocn.tech.mixin.TrialDataAccess)trial.getTrialSpawner().getStateData()).techCurrentMobs().size() : 0;
                default -> 0; }; }
            public void set(int i,int v) {} public int getCount() { return 13; }
        };
    }
    @Override public int getContainerSize() { return SLOTS; }
    @Override public boolean isEmpty() { return inventory.isEmpty(); }
    @Override public ItemStack getItem(int i) { return inventory.getItem(i); }
    @Override public ItemStack removeItem(int i,int n) { return inventory.removeItem(i,n); }
    @Override public ItemStack removeItemNoUpdate(int i) { return inventory.removeItemNoUpdate(i); }
    @Override public void setItem(int i,ItemStack s) { inventory.setItem(i,s); }
    @Override public boolean canPlaceItem(int i,ItemStack s) { return i == 0 && validInput(s); }
    @Override public void clearContent() { inventory.clearContent(); }
    @Override public boolean stillValid(Player p) { return Container.stillValidBlockEntity(this,p); }
    @Override public Component getDisplayName() { return Component.translatable(getBlockState().getBlock().getDescriptionId()); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) { return new ControllerMenu(id,inv,this,menuData()); }
}
