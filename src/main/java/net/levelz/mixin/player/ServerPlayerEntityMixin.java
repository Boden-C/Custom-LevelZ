package net.levelz.mixin.player;

import com.mojang.authlib.GameProfile;

import net.levelz.network.PlayerStatsServerPacket;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributes;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;

import net.levelz.access.PlayerStatsManagerAccess;
import net.levelz.stats.PlayerStatsManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {
    PlayerStatsManager playerStatsManager = ((PlayerStatsManagerAccess) this).getPlayerStatsManager((PlayerEntity) (Object) this);
    private int syncedLevelExperience = -99999999;

    @Inject(method = "<init>", at = @At(value = "TAIL"))
    private void initMixin(MinecraftServer server, ServerWorld world, GameProfile profile, CallbackInfo info) {
        ServerPlayerEntity serverPlayerEntity = (ServerPlayerEntity) (Object) this;
        serverPlayerEntity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(6D + playerStatsManager.getLevel("health"));
        serverPlayerEntity.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED).setBaseValue(0.09D + (double) playerStatsManager.getLevel("agility") / 1000D);
        serverPlayerEntity.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE).setBaseValue(1D + (double) playerStatsManager.getLevel("strength") / 5D);
        serverPlayerEntity.getAttributeInstance(EntityAttributes.GENERIC_ARMOR).setBaseValue((double) playerStatsManager.getLevel("defense") / 5D);
        serverPlayerEntity.getAttributeInstance(EntityAttributes.GENERIC_LUCK).setBaseValue((double) playerStatsManager.getLevel("luck") / 20D);
        // Init stats - Can't send to client cause network hander is null -> onSpawn packet
    }

    @Inject(method = "addExperience", at = @At(value = "TAIL"))
    private void addExperienceMixin(int experience, CallbackInfo info) {
        this.syncedLevelExperience = -1;
    }

    @Inject(method = "playerTick", at = @At(value = "FIELD", target = "Lnet/minecraft/server/network/ServerPlayerEntity;totalExperience:I", ordinal = 0, shift = At.Shift.BEFORE))
    private void playerTickMixin(CallbackInfo info) {
        if (playerStatsManager.totalLevelExperience != this.syncedLevelExperience) {
            this.syncedLevelExperience = playerStatsManager.totalLevelExperience;
            PlayerStatsServerPacket.writeS2CXPPacket(playerStatsManager, ((ServerPlayerEntity) (Object) this));
        }
    }

    @Nullable
    @Inject(method = "moveToWorld", at = @At(value = "FIELD", target = "Lnet/minecraft/server/network/ServerPlayerEntity;syncedExperience:I", ordinal = 0))
    private void moveToWorldMixin(ServerWorld destination, CallbackInfoReturnable<Entity> info) {
        this.syncedLevelExperience = -1;
    }

    @Inject(method = "onSpawn", at = @At(value = "TAIL"))
    private void onSpawnMixin(CallbackInfo info) {
        PlayerStatsServerPacket.writeS2CSkillPacket(playerStatsManager, (ServerPlayerEntity) (Object) this);
    }

}