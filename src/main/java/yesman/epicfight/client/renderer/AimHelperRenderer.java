package yesman.epicfight.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Matrix4f;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import yesman.epicfight.api.utils.math.Vec3f;
import yesman.epicfight.main.EpicFightMod;

@OnlyIn(Dist.CLIENT)
public class AimHelperRenderer {
	public void doRender(PoseStack matStackIn, float partialTicks) {
		if (!EpicFightMod.CLIENT_INGAME_CONFIG.enableAimHelperPointer.getValue()) {
			return;
		}
		
		Minecraft minecraft = Minecraft.getInstance();
		Entity entity = minecraft.player;
		HitResult ray = entity.pick(200.D, partialTicks, false);
		Vec3 vec3 = ray.getLocation();
		Vec3f pos1 = new Vec3f((float) Mth.lerp((double)partialTicks, entity.xOld, entity.getX()),
							   (float) Mth.lerp((double)partialTicks, entity.yOld, entity.getY()) + entity.getEyeHeight() - 0.15F,
							   (float) Mth.lerp((double)partialTicks, entity.zOld, entity.getZ()));
		Vec3f pos2 = new Vec3f((float) vec3.x, (float) vec3.y, (float) vec3.z);
		
		Camera renderInfo = minecraft.gameRenderer.getMainCamera();
		Vec3 projectedView = renderInfo.getPosition();
		matStackIn.pushPose();
		matStackIn.translate(-projectedView.x, -projectedView.y, -projectedView.z);
		Matrix4f matrix = matStackIn.last().pose();
		
		int color = EpicFightMod.CLIENT_INGAME_CONFIG.aimHelperRealColor;
		float f1 = (float)(color >> 16 & 255) / 255.0F;
		float f2 = (float)(color >> 8 & 255) / 255.0F;
		float f3 = (float)(color & 255) / 255.0F;
		
		Tesselator tesselator = Tesselator.getInstance();
		BufferBuilder bufferBuilder = tesselator.getBuilder();
		
		RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
		RenderSystem.disableTexture();
		RenderSystem.enableBlend();
		RenderSystem.lineWidth(3.0F);
		bufferBuilder.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR);
		bufferBuilder.vertex(matrix, pos1.x, pos1.y, pos1.z).color(f1, f2, f3, 0.5F).endVertex();
		bufferBuilder.vertex(matrix, pos2.x, pos2.y, pos2.z).color(f1, f2, f3, 0.5F).endVertex();
		tesselator.end();
		
		float length = Vec3f.sub(pos2, pos1, null).length();
		float ratio = Math.min(50.0F, length);
		ratio = (51.0F - ratio) / 50.0F;
		matStackIn.popPose();
	}
}
