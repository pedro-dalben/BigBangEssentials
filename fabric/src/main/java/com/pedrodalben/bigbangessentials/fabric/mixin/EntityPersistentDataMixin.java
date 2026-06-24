package com.pedrodalben.bigbangessentials.fabric.mixin;

import com.pedrodalben.bigbangessentials.fabric.accessor.FabricEntityDataAccessor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityPersistentDataMixin implements FabricEntityDataAccessor {
    @Unique
    private CompoundTag bbEssentials$persistentData;

    @Override
    public CompoundTag bbEssentials$getPersistentData() {
        if (this.bbEssentials$persistentData == null) {
            this.bbEssentials$persistentData = new CompoundTag();
        }
        return this.bbEssentials$persistentData;
    }

    @Inject(method = "saveWithoutId", at = @At("HEAD"))
    private void writeCustomData(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        if (this.bbEssentials$persistentData != null && !this.bbEssentials$persistentData.isEmpty()) {
            tag.put("BigBangEssentialsData", this.bbEssentials$persistentData);
        }
    }

    @Inject(method = "load", at = @At("HEAD"))
    private void readCustomData(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("BigBangEssentialsData", 10)) {
            this.bbEssentials$persistentData = tag.getCompound("BigBangEssentialsData");
        }
    }
}
