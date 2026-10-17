package cn.zedocn.tech;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.entity.trialspawner.*;
import net.minecraft.world.level.Level;

class TrialRoundTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private static TrialSpawner createSpawner() { return new TrialSpawner(TrialSpawner.FullConfig.DEFAULT,new TrialSpawner.StateAccessor() { public TrialSpawnerState getState() { return TrialSpawnerState.WAITING_FOR_PLAYERS; } public void setState(Level level,TrialSpawnerState state) {} public void markUpdated() {} },(level,selector,pos,range,los) -> java.util.List.of(),PlayerDetector.EntitySelector.SELECT_FROM_LEVEL); }
    @Test void roundCountAndParticipantSurviveSerialization() {
        var original=createSpawner(); ((ControlledTrial)(Object)original).techPlayers(6);
        var id=java.util.UUID.randomUUID();
        ((cn.zedocn.tech.mixin.TrialDataAccess)original.getStateData()).techDetectedPlayers().add(id);
        var output=net.minecraft.world.level.storage.TagValueOutput.createWithoutContext(net.minecraft.util.ProblemReporter.DISCARDING); original.store(output);
        var restored=createSpawner();
        restored.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,net.minecraft.core.HolderLookup.Provider.create(java.util.stream.Stream.empty()),output.buildResult()));
        assertEquals(6,((ControlledTrial)(Object)restored).techPlayers());
        assertTrue(((cn.zedocn.tech.mixin.TrialDataAccess)restored.getStateData()).techDetectedPlayers().contains(id));
    }
    @Test void vanillaSpawnerGetsRoundStateWithoutChangingNaturalTrialDefaults() {
        var spawner=new TrialSpawner(TrialSpawner.FullConfig.DEFAULT,new TrialSpawner.StateAccessor() {
            public TrialSpawnerState getState() { return TrialSpawnerState.WAITING_FOR_PLAYERS; }
            public void setState(Level level,TrialSpawnerState state) {}
            public void markUpdated() {}
        },(level,selector,pos,range,lineOfSight) -> java.util.List.of(),PlayerDetector.EntitySelector.SELECT_FROM_LEVEL);
        assertInstanceOf(ControlledTrial.class,(Object)spawner);
        var round=(ControlledTrial)(Object)spawner;
        assertEquals(0,round.techPlayers()); round.techPlayers(4); assertEquals(4,round.techPlayers());
        round.techPlayers(999); assertEquals(8,round.techPlayers()); round.techPlayers(0); assertEquals(0,round.techPlayers());
    }
    @Test void failedSpawnDiagnosticClearsOnSuccessAndNewRound() {
        var round=(ControlledTrial)(Object)createSpawner();
        round.techPlayers(1);
        for (int i=0;i<120;i++) round.techSpawnResult(false);
        assertEquals(100,round.techFailedSpawns());
        round.techSpawnResult(true); assertEquals(0,round.techFailedSpawns());
        round.techSpawnResult(false); round.techPlayers(2); assertEquals(0,round.techFailedSpawns());
    }
}
