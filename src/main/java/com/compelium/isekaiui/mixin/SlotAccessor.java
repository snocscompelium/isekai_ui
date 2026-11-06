package com.compelium.isekaiui.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Permite reposicionar os slots reais na tela (o servidor continua vendo os mesmos slots). */
@Mixin(Slot.class)
public interface SlotAccessor {
	@Mutable
	@Accessor("x")
	void isekai$setX(int x);

	@Mutable
	@Accessor("y")
	void isekai$setY(int y);
}
