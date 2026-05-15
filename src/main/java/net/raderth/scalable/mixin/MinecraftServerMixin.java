package net.raderth.scalable.mixin;

import net.minecraft.server.MinecraftServer;
import net.raderth.scalable.ScalableMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "tickServer", at = @At("HEAD"))
    private void scalable$startBudget(BooleanSupplier haveTime, CallbackInfo ci) {
        ScalableMod.BUDGET.startServerTick(System.nanoTime());
    }
}