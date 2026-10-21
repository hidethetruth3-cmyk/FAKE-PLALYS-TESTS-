package com.fakeplayermod.mixin;

import com.fakeplayermod.BlackSkin;
import com.fakeplayermod.FakeIds;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {
    @Shadow
    public abstract GameProfile getProfile();

    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void fakeplayer$blackSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        if (FakeIds.isFake(getProfile().id())) {
            cir.setReturnValue(BlackSkin.get());
        }
    }
}
