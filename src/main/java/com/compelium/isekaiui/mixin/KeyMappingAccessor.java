package com.compelium.isekaiui.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** "Aperta" uma tecla de outro mod (ex.: a tecla G do CPM) sem precisar saber em que tecla ela esta. */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
	@Accessor("clickCount")
	int isekai$getClickCount();

	@Accessor("clickCount")
	void isekai$setClickCount(int count);
}
