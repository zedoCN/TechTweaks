package cn.zedocn.tech.server;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.anvilcraft.rg.sd.SiliconeDolls;
import dev.anvilcraft.rg.sd.SiliconeDollsServerRules;
import dev.anvilcraft.rg.sd.entity.FakePlayer;
import dev.anvilcraft.rg.sd.tool.FakePlayerResident;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Capture SiliconeDolls' native restart data in the same server task as world flushing. */
@Mod("tech_tweaks_server")
public final class TechTweaksServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TechTweaksServer.class);

    public TechTweaksServer() {
        NeoForge.EVENT_BUS.addListener(TechTweaksServer::commands);
        NeoForge.EVENT_BUS.addListener(cn.zedocn.tech.server.performance.PerformanceFixes::commands);
    }

    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("techbackup")
                .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
                .then(Commands.literal("snapshot")
                        .then(Commands.argument("nonce", StringArgumentType.word())
                                .executes(context -> capture(context.getSource(),
                                        StringArgumentType.getString(context, "nonce"))))));
    }

    private static int capture(CommandSourceStack source, String nonce) {
        try {
            MinecraftServer server = source.getServer();
            if (!server.isSameThread() || !nonce.matches("[0-9a-f]{32}")) {
                throw new IllegalStateException("Invalid snapshot request");
            }
            if (!SiliconeDollsServerRules.fakePlayerResident
                    || !SiliconeDollsServerRules.fakePlayerReloadAction
                    || !SiliconeDollsServerRules.allowSpawningOfflinePlayers) {
                throw new IllegalStateException("Fake player residency, action reload and offline spawning must be enabled");
            }
            JsonObject residents = new JsonObject();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!(player instanceof FakePlayer)) continue;
                // Match SiliconeDolls' own opt-out handling, including presence of a false-valued tag.
                try (var problems = new ProblemReporter.ScopedCollector(player.problemPath(), LOGGER)) {
                    var output = TagValueOutput.createWithContext(problems, player.registryAccess());
                    player.saveWithoutId(output);
                    if (output.buildResult().contains("rolling_gate.NoResident")) continue;
                }
                JsonObject state = FakePlayerResident.save(player);
                if (!state.has("actions") || !state.get("actions").isJsonObject()) {
                    throw new IllegalStateException("Missing fake player actions");
                }
                residents.add(player.getGameProfile().name(), state);
            }
            // No tick, join or action execution can run between the capture and this flush.
            if (!server.saveEverything(true, true, true)) {
                throw new IllegalStateException("World flush failed");
            }
            byte[] bytes = SiliconeDolls.GSON.toJson(residents).getBytes(StandardCharsets.UTF_8);
            Path world = server.getWorldPath(LevelResource.ROOT);
            Path target = world.resolve("fake_player.rg.json");
            if (Files.isSymbolicLink(target)) throw new IllegalStateException("Unsafe fake player manifest");
            Path temporary = Files.createTempFile(world, ".fake-player-", ".tmp");
            try {
                Files.write(temporary, bytes);
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temporary);
            }
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            source.sendSuccess(() -> Component.literal("TECH_SNAPSHOT_OK " + nonce + " "
                    + residents.size() + " " + hash), false);
            return 1;
        } catch (Exception error) {
            LOGGER.error("Online world/fake-player snapshot failed", error);
            source.sendFailure(Component.literal("TECH_SNAPSHOT_FAILED; inspect server log"));
            return 0;
        }
    }
}
