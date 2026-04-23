package com.example.universalattributes.mixin;

import com.example.universalattributes.UniversalAttributesMod;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public class LivingEntityDamageMixin {
    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float universalattributes$modifyDamage(float amount, DamageSource source) {
        if ((Object) this instanceof ServerPlayerEntity player) {
            return UniversalAttributesMod.scaleIncomingDamage(player, source, amount);
        }
        return amount;
    }
}
