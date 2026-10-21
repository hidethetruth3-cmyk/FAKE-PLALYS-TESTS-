package com.fakeplayermod;

import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/** Client-side: the bundled all-black skin. */
public final class BlackSkin {
    private static PlayerSkin skin;

    public static PlayerSkin get() {
        if (skin == null) {
            Identifier id = Identifier.fromNamespaceAndPath("fakeplayer", "entity/black_skin");
            Identifier path = Identifier.fromNamespaceAndPath("fakeplayer", "textures/entity/black_skin.png");
            skin = new PlayerSkin(new ClientAsset.ResourceTexture(id, path), null, null, PlayerModelType.WIDE, true);
        }
        return skin;
    }
    private BlackSkin() {}
}
