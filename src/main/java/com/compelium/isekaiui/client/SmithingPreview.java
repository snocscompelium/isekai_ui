package com.compelium.isekaiui.client;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

/** "Provador" da mesa de ferraria: o personagem local aparece vestindo o resultado (so no desenho). */
public final class SmithingPreview {
	private static ItemStack preview;

	private SmithingPreview() {
	}

	public static void set(ItemStack stack) {
		preview = stack == null || stack.isEmpty() || !isArmor(stack) ? null : stack.copy();
	}

	public static boolean isArmor(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return false;
		Equippable eq = stack.get(DataComponents.EQUIPPABLE);
		if (eq == null) return false;
		return switch (eq.slot()) {
			case HEAD, CHEST, LEGS, FEET -> true;
			default -> false;
		};
	}

	/** Chamado ao montar o "estado de desenho" do jogador local. */
	public static void apply(HumanoidRenderState state) {
		ItemStack p = preview;
		if (p == null) return;
		Equippable eq = p.get(DataComponents.EQUIPPABLE);
		if (eq == null) return;
		switch (eq.slot()) {
			case HEAD -> state.headEquipment = p;
			case CHEST -> state.chestEquipment = p;
			case LEGS -> state.legsEquipment = p;
			case FEET -> state.feetEquipment = p;
			default -> {
			}
		}
	}
}
