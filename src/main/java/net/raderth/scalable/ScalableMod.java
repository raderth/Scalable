package net.raderth.scalable;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.raderth.scalable.tick.WorldTickQueues;
import net.raderth.scalable.util.TickBudget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScalableMod implements ModInitializer {

    public static final String MOD_ID = "scalable";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final TickBudget BUDGET = new TickBudget();

    @Override
    public void onInitialize() {
        // Load config as soon as we have the server directory
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            ScalableConfig.init(server.getServerDirectory());
            // Apply loaded values
            if (ScalableConfig.budgetMs < 0) {
                BUDGET.setAutomatic();
            } else {
                BUDGET.setFixed(ScalableConfig.budgetMs);
            }
            WorldTickQueues.setMaxDeferred(ScalableConfig.maxDeferred);
            LOGGER.info("[Scalable] Config loaded. Budget={}ms, maxDeferred={}",
                    ScalableConfig.budgetMs, ScalableConfig.maxDeferred);
        });

        // Register /scalable commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, env) -> {
            dispatcher.register(
                    net.minecraft.commands.Commands.literal("scalable")
                            .requires(src -> src.permissions().hasPermission(
                                    new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                            .then(net.minecraft.commands.Commands.literal("budget")
                                    .then(net.minecraft.commands.Commands.literal("auto").executes(ctx -> {
                                        BUDGET.setAutomatic();
                                        ScalableConfig.budgetMs = -1;
                                        ScalableConfig.save();
                                        ctx.getSource().sendSuccess(
                                                () -> Component.literal("[Scalable] Budget set to automatic"), true);
                                        return 1;
                                    }))
                                    .then(net.minecraft.commands.Commands.argument("ms", LongArgumentType.longArg(1, 50))
                                            .executes(ctx -> {
                                                long ms = LongArgumentType.getLong(ctx, "ms");
                                                BUDGET.setFixed(ms);
                                                ScalableConfig.budgetMs = ms;
                                                ScalableConfig.save();
                                                ctx.getSource().sendSuccess(
                                                        () -> Component.literal("[Scalable] Budget set to " + ms + "ms"), true);
                                                return 1;
                                            }))
                                    .then(net.minecraft.commands.Commands.literal("query").executes(ctx -> {
                                        MinecraftServer server = ctx.getSource().getServer();
                                        float mspt = server.getCurrentSmoothedTickTime();
                                        float tps  = Math.min(20f, 1000f / mspt);
                                        String mode = BUDGET.isAutomatic() ? "auto" :
                                                BUDGET.getFixedBudgetMs() + "ms";
                                        ctx.getSource().sendSuccess(
                                                () -> Component.literal(
                                                        "[Scalable] Budget=" + mode +
                                                                " | MSPT=" + String.format("%.1f", mspt) +
                                                                " | TPS=" + String.format("%.1f", tps)), false);
                                        return 1;
                                    }))
                            )
                            .then(net.minecraft.commands.Commands.literal("deferred")
                                    .then(net.minecraft.commands.Commands.argument("max", IntegerArgumentType.integer(0, 65536))
                                            .executes(ctx -> {
                                                int max = IntegerArgumentType.getInteger(ctx, "max");
                                                ScalableConfig.maxDeferred = max;
                                                ScalableConfig.save();
                                                WorldTickQueues.setMaxDeferred(max);
                                                ctx.getSource().sendSuccess(
                                                        () -> Component.literal("[Scalable] Max deferred set to " + max), true);
                                                return 1;
                                            }))
                            )
            );
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            WorldTickQueues.clear();
            LOGGER.info("[Scalable] Server stopping - cleared world tick queues");
        });
    }
}