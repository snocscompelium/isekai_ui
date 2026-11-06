package com.compelium.isekaiui.mixin;

import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ContainerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractFurnaceMenu.class)
public interface AbstractFurnaceMenuAccessor {
	/** 0 = combustivel restante, 1 = duracao do combustivel, 2 = progresso, 3 = tempo total por item (ticks). */
	@Accessor("data")
	ContainerData isekai$data();
}
