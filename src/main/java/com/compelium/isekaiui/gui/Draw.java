package com.compelium.isekaiui.gui;

import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * Todas as chamadas de desenho passam por aqui. Quando o mod for portado para outras versoes
 * do Minecraft, praticamente so este arquivo muda.
 */
public final class Draw {
	private Draw() {
	}

	public static int alpha(int argb, float a) {
		if (a >= 1f) return argb;
		if (a <= 0f) return argb & 0x00FFFFFF;
		int al = Math.round(((argb >>> 24) & 0xFF) * a);
		return (al << 24) | (argb & 0x00FFFFFF);
	}

	public static int white(float a) {
		return alpha(0xFFFFFFFF, a);
	}

	public static int rgb(int rgb, float a) {
		return alpha(0xFF000000 | (rgb & 0xFFFFFF), a);
	}

	public static void sprite(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h, float a) {
		sprite(g, sprite, x, y, w, h, white(a));
	}

	public static void sprite(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h, int argb) {
		if (((argb >>> 24) & 0xFF) < 3 || w <= 0 || h <= 0) return;
		g.blitSprite(RenderType::guiTextured, sprite, x, y, w, h, argb);
	}

	/** Desenha uma regiao (u,v,uw,vh) de uma textura solta esticada em (x,y,w,h). */
	public static void texture(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h,
							   float u, float v, int uw, int vh, int texW, int texH, int argb) {
		if (((argb >>> 24) & 0xFF) < 3 || w <= 0 || h <= 0) return;
		g.blit(RenderType::guiTextured, tex, x, y, u, v, w, h, uw, vh, texW, texH, argb);
	}

	public static void fill(GuiGraphics g, int x1, int y1, int x2, int y2, int argb) {
		if (((argb >>> 24) & 0xFF) == 0 || x2 <= x1 || y2 <= y1) return;
		g.fill(x1, y1, x2, y2, argb);
	}

	public static void gradient(GuiGraphics g, int x1, int y1, int x2, int y2, int top, int bottom) {
		if (x2 <= x1 || y2 <= y1) return;
		g.fillGradient(x1, y1, x2, y2, top, bottom);
	}

	public static void text(GuiGraphics g, Font font, Component text, int x, int y, int argb, boolean shadow) {
		if (((argb >>> 24) & 0xFF) < 6) return;
		g.drawString(font, text, x, y, argb, shadow);
	}

	public static void textCentered(GuiGraphics g, Font font, Component text, int cx, int y, int argb, boolean shadow) {
		text(g, font, text, cx - font.width(text) / 2, y, argb, shadow);
	}

	/** Texto centralizado que encolhe (ate 60%) para caber na largura; se ainda nao couber, e cortado. */
	public static void textFit(GuiGraphics g, Font font, Component text, int cx, int y, int maxW, int argb, boolean shadow) {
		if (((argb >>> 24) & 0xFF) < 6 || maxW <= 4) return;
		int w = font.width(text);
		if (w <= maxW) {
			textCentered(g, font, text, cx, y, argb, shadow);
			return;
		}
		float scale = Math.max(0.6f, (float) maxW / w);
		push(g);
		translate(g, cx, y + 1);
		scale(g, scale);
		FormattedCharSequence line = text.getVisualOrderText();
		if (w * scale > maxW) line = Language.getInstance().getVisualOrder(font.substrByWidth(text, (int) (maxW / scale)));
		g.drawString(font, line, -font.width(line) / 2, 0, argb, shadow);
		pop(g);
	}

	public static void push(GuiGraphics g) {
		g.pose().pushPose();
	}

	public static void pop(GuiGraphics g) {
		g.pose().popPose();
	}

	public static void translate(GuiGraphics g, double x, double y) {
		g.pose().translate(x, y, 0.0);
	}

	/** Sobe a camada de desenho (para ficar por cima de itens). */
	public static void layer(GuiGraphics g, float z) {
		g.pose().translate(0f, 0f, z);
	}

	public static void scale(GuiGraphics g, float s) {
		g.pose().scale(s, s, 1f);
	}

	public static void rotate(GuiGraphics g, float degrees) {
		g.pose().mulPose(Axis.ZP.rotationDegrees(degrees));
	}

	public static void scissor(GuiGraphics g, Rect r) {
		g.enableScissor(r.x(), r.y(), r.right(), r.bottom());
	}

	public static void endScissor(GuiGraphics g) {
		g.disableScissor();
	}
}
