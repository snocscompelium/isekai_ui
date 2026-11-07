package com.compelium.isekaiui.gui;

import com.compelium.isekaiui.client.HudOverlay;
import com.compelium.isekaiui.client.UiSounds;
import com.compelium.isekaiui.config.IsekaiConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Tela para posicionar o minimapa que aparece durante o jogo (com bussola): arrastar move, a rodinha do
 * mouse muda o tamanho, botao direito volta para o canto. O jogo continua visivel atras.
 */
public class HudLayoutScreen extends Screen {
	private static final int BUTTON_H = 16;
	private final Screen parent;
	private boolean dragging;
	private double grabX;
	private double grabY;
	private int hoveredButton = -1;

	public HudLayoutScreen(Screen parent) {
		super(Component.translatable("isekai_ui.hud_editor.title"));
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private Rect mapRect() {
		return HudOverlay.minimapRect(this.width, this.height);
	}

	/** Area agarravel: mapa + moldura + textos de baixo. */
	private Rect grabRect() {
		Rect r = mapRect();
		return new Rect(r.x() - 7, r.y() - 7, r.w() + 14, r.h() + 14 + HudOverlay.LABELS_H);
	}

	private Rect button(int i) {
		int w = 112;
		int gap = 8;
		int total = 2 * w + gap;
		int x = (this.width - total) / 2 + i * (w + gap);
		int y = this.height / 2 + 34;
		return new Rect(x, y, w, BUTTON_H);
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		if (this.minecraft != null && this.minecraft.level == null) {
			super.renderBackground(g, mouseX, mouseY, partialTick);
			return;
		}
		Draw.fill(g, 0, 0, this.width, this.height, 0x60000000);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		super.render(g, mouseX, mouseY, partialTick);
		Rect r = mapRect();
		HudOverlay.drawMinimap(g, this.minecraft, this.minecraft.player, r, partialTick, 1f);
		Rect box = grabRect();
		boolean hover = box.contains(mouseX, mouseY) || this.dragging;
		dashed(g, box, hover ? Theme.GOLD : 0xC0F2E6C8);

		// explicacao no meio da tela
		int cx = this.width / 2;
		int cy = this.height / 2 - 26;
		Component[] lines = {
				Component.translatable("isekai_ui.hud_editor.hint1"),
				Component.translatable("isekai_ui.hud_editor.hint2"),
				Component.translatable("isekai_ui.hud_editor.hint3")};
		int w = 0;
		for (Component c : lines) w = Math.max(w, this.font.width(c));
		w = Math.min(this.width - 20, w + 24);
		Rect info = new Rect(cx - w / 2, cy - 18, w, 18 + lines.length * 11 + 8);
		Panels.panel(g, this.font, info, 1f, 1f, this.title, 0f);
		for (int i = 0; i < lines.length; i++) {
			Draw.textFit(g, this.font, lines[i], cx, info.y() + 9 + i * 11, w - 12, i == 0 ? Theme.TEXT : Theme.TEXT_DIM, false);
		}

		this.hoveredButton = -1;
		String[] keys = {"isekai_ui.hud_editor.reset", "isekai_ui.hud_editor.done"};
		for (int i = 0; i < 2; i++) {
			Rect b = button(i);
			boolean h = b.contains(mouseX, mouseY) && !this.dragging;
			if (h) this.hoveredButton = i;
			pill(g, b, Component.translatable(keys[i]), h, i == 1);
		}
	}

	/** Botao em forma de pilula no estilo do mod. */
	static void pill(GuiGraphics g, Rect b, Component text, boolean hover, boolean primary) {
		net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
		Draw.fill(g, b.x() + 1, b.y(), b.right() - 1, b.bottom(), hover ? 0xF0473A63 : (primary ? 0xE0322943 : 0xD01A1622));
		int edge = hover ? Theme.GOLD : (primary ? 0xFFB89552 : 0xFF6E5B3A);
		Draw.fill(g, b.x() + 1, b.y(), b.right() - 1, b.y() + 1, edge);
		Draw.fill(g, b.x() + 1, b.bottom() - 1, b.right() - 1, b.bottom(), edge);
		Draw.fill(g, b.x(), b.y() + 1, b.x() + 1, b.bottom() - 1, edge);
		Draw.fill(g, b.right() - 1, b.y() + 1, b.right(), b.bottom() - 1, edge);
		Draw.textFit(g, font, text, b.cx(), b.y() + (b.h() - 8) / 2, b.w() - 8, hover ? Theme.TEXT_GOLD : Theme.TEXT, false);
	}

	/** Contorno tracejado dourado. */
	static void dashed(GuiGraphics g, Rect r, int color) {
		for (int x = r.x(); x < r.right(); x += 6) {
			int e = Math.min(r.right(), x + 3);
			Draw.fill(g, x, r.y(), e, r.y() + 1, color);
			Draw.fill(g, x, r.bottom() - 1, e, r.bottom(), color);
		}
		for (int y = r.y(); y < r.bottom(); y += 6) {
			int e = Math.min(r.bottom(), y + 3);
			Draw.fill(g, r.x(), y, r.x() + 1, e, color);
			Draw.fill(g, r.right() - 1, y, r.right(), e, color);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0 && this.hoveredButton == 0) {
			resetToCorner();
			return true;
		}
		if (button == 0 && this.hoveredButton == 1) {
			UiSounds.click();
			onClose();
			return true;
		}
		if (grabRect().contains(mouseX, mouseY)) {
			if (button == 1) {
				resetToCorner();
				return true;
			}
			if (button == 0) {
				Rect r = mapRect();
				this.dragging = true;
				this.grabX = mouseX - r.x();
				this.grabY = mouseY - r.y();
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private void resetToCorner() {
		IsekaiConfig.InstrumentSettings cfg = IsekaiConfig.get().instruments;
		cfg.hudX = -1f;
		cfg.hudY = -1f;
		IsekaiConfig.save();
		UiSounds.toggle();
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
		if (this.dragging) {
			HudOverlay.savePosition((int) Math.round(mouseX - this.grabX), (int) Math.round(mouseY - this.grabY), this.width, this.height);
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (this.dragging && button == 0) {
			this.dragging = false;
			IsekaiConfig.save();
			UiSounds.click();
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		IsekaiConfig.InstrumentSettings cfg = IsekaiConfig.get().instruments;
		Rect before = mapRect();
		int size = Mth.clamp(cfg.hudSize + (int) Math.signum(scrollY) * 4, 48, 160);
		if (size == cfg.hudSize) return true;
		cfg.hudSize = size;
		// mantem o centro no mesmo lugar ao mudar de tamanho (quando a posicao e livre)
		if (cfg.hudX >= 0f && cfg.hudY >= 0f) {
			HudOverlay.savePosition(before.cx() - size / 2, before.cy() - size / 2, this.width, this.height);
		}
		IsekaiConfig.save();
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			onClose();
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void onClose() {
		IsekaiConfig.save();
		this.minecraft.setScreen(this.parent);
	}
}
