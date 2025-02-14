package yesman.epicfight.api.animation.types.procedural;

import java.util.Map;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import yesman.epicfight.api.animation.JointTransform;
import yesman.epicfight.api.animation.Keyframe;
import yesman.epicfight.api.animation.Pose;
import yesman.epicfight.api.animation.TransformSheet;
import yesman.epicfight.api.animation.types.ActionAnimation;
import yesman.epicfight.api.model.Model;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.utils.math.Vec3f;
import yesman.epicfight.client.renderer.EpicFightRenderTypes;
import yesman.epicfight.client.renderer.RenderingTool;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.entitypatch.boss.enderdragon.EnderDragonPatch;

public class EnderDragonDynamicActionAnimation extends ActionAnimation implements ProceduralAnimation {
	private final IKSetter[] ikSetters;
	private Map<String, TransformSheet> tipPointTransform;
	
	public EnderDragonDynamicActionAnimation(float convertTime, String path, Model model, IKSetter[] ikSetters) {
		super(convertTime, path, model);
		this.ikSetters = ikSetters;
	}
	
	@Override
	public void loadAnimation(ResourceManager resourceManager) {
		loadBothSide(resourceManager, this);
		this.tipPointTransform = Maps.newHashMap();
		this.setIKData(this.ikSetters, this.getTransfroms(), this.tipPointTransform, this.getModel().getArmature(), true, true);
	}
	
	@Override
	public Pose getPoseByTime(LivingEntityPatch<?> entitypatch, float time, float partialTicks) {
		Pose pose = super.getPoseByTime(entitypatch, time, partialTicks);
		
		if (entitypatch instanceof EnderDragonPatch) {
			EnderDragonPatch enderdragonpatch = (EnderDragonPatch)entitypatch;
	    	float x = (float)entitypatch.getOriginal().getX();
	    	float y = (float)entitypatch.getOriginal().getY();
	    	float z = (float)entitypatch.getOriginal().getZ();
	    	float xo = (float)entitypatch.getOriginal().xo;
	    	float yo = (float)entitypatch.getOriginal().yo;
	    	float zo = (float)entitypatch.getOriginal().zo;
	    	OpenMatrix4f toModelPos = OpenMatrix4f.mul(OpenMatrix4f.translate(new Vec3f(xo + (x - xo) * partialTicks, yo + (y - yo) * partialTicks, zo + (z - zo) * partialTicks), new OpenMatrix4f(), null), entitypatch.getModelMatrix(partialTicks), null).invert();
	    	this.correctRootRotation(pose.getJointTransformData().get("Root"), enderdragonpatch, partialTicks);
	    	
	    	for (IKSetter ikSetter : this.ikSetters) {
		    	TipPointAnimation tipAnim = enderdragonpatch.getTipPointAnimation(ikSetter.endJoint);
	    		JointTransform jt = tipAnim.getTipTransform(partialTicks);
		    	Vec3f jointModelpos = OpenMatrix4f.transform3v(toModelPos, jt.translation(), null);
		    	this.applyFabrikToJoint(jointModelpos.multiply(-1.0F, 1.0F, -1.0F), pose, this.getModel().getArmature(), ikSetter.startJoint, ikSetter.endJoint, jt.rotation());
	    	}
		}
		
		return pose;
	}
	
	@Override
	public void begin(LivingEntityPatch<?> entitypatch) {
		super.begin(entitypatch);
		
		if (entitypatch instanceof EnderDragonPatch) {
			EnderDragonPatch enderdragonpatch = (EnderDragonPatch)entitypatch;
			Vec3 entitypos = enderdragonpatch.getOriginal().position();
			OpenMatrix4f toWorld = OpenMatrix4f.mul(OpenMatrix4f.createTranslation((float)entitypos.x, (float)entitypos.y, (float)entitypos.z), enderdragonpatch.getModelMatrix(1.0F), null);
			TransformSheet movementAnimation = enderdragonpatch.getAnimator().getPlayerFor(this).getActionAnimationCoord();
			
			for (IKSetter ikSetter : this.ikSetters) {
				TransformSheet tipAnim = this.toPartAnimation(this.tipPointTransform.get(ikSetter.endJoint), ikSetter);
				Keyframe[] keyframes = tipAnim.getKeyframes();
				Vec3f startpos = movementAnimation.getInterpolatedTranslation(0.0F);
				
				for (int i = 0; i < keyframes.length; i++) {
					Keyframe kf = keyframes[i];
					Vec3f dynamicpos = movementAnimation.getInterpolatedTranslation(kf.time()).sub(startpos);
					OpenMatrix4f.transform3v(OpenMatrix4f.createRotatorDeg(-90.0F, Vec3f.X_AXIS), dynamicpos, dynamicpos).multiply(-1.0F, 1.0F, -1.0F);
					Vec3f finalTargetpos;
					
					if (!ikSetter.hasPartAnimation || ikSetter.touchingGround[i]) {
						Vec3f clipStart = kf.transform().translation().copy().multiply(-1.0F, 1.0F, -1.0F).add(dynamicpos).add(0.0F, 2.5F, 0.0F);
						finalTargetpos = this.getRayCastedTipPosition(clipStart, toWorld, enderdragonpatch, 2.5F, ikSetter.rayLeastHeight);
					} else {
						Vec3f start = kf.transform().translation().copy().multiply(-1.0F, 1.0F, -1.0F).add(dynamicpos);
						finalTargetpos = OpenMatrix4f.transform3v(toWorld, start, null);
					}
					
					kf.transform().translation().set(finalTargetpos);
				}
				
				enderdragonpatch.addTipPointAnimation(ikSetter.endJoint, keyframes[0].transform().translation(), tipAnim, ikSetter);
			}
		}
	}
	
	@Override
	public void tick(LivingEntityPatch<?> entitypatch) {
		super.tick(entitypatch);
		
		if (entitypatch instanceof EnderDragonPatch) {
			EnderDragonPatch enderdragonpatch = (EnderDragonPatch)entitypatch;
			float elapsedTime = entitypatch.getAnimator().getPlayerFor(this).getElapsedTime();
			
			for (IKSetter ikSetter : this.ikSetters) {
				if (ikSetter.hasPartAnimation) {
					Keyframe[] keyframes = this.getTransfroms().get(ikSetter.endJoint).getKeyframes();
					float startTime = keyframes[ikSetter.startFrame].time();
					float endTime = keyframes[ikSetter.endFrame - 1].time();
					
					if (startTime <= elapsedTime && elapsedTime < endTime) {
						TipPointAnimation tipAnim = enderdragonpatch.getTipPointAnimation(ikSetter.endJoint);
						
						if (!tipAnim.isOnWorking()) {
							this.startSimple(ikSetter, tipAnim);
						}
					}
				}
			}
		}
	}
	
	@Override
	@OnlyIn(Dist.CLIENT)
	public void renderDebugging(PoseStack poseStack, MultiBufferSource buffer, LivingEntityPatch<?> entitypatch, float playTime, float partialTicks) {
		if (entitypatch instanceof EnderDragonPatch) {
			EnderDragonPatch enderdragonpatch = ((EnderDragonPatch)entitypatch);
			OpenMatrix4f modelmat = enderdragonpatch.getModelMatrix(partialTicks);
			LivingEntity originalEntity = entitypatch.getOriginal();
			Vec3 entitypos = originalEntity.position();
			float x = (float)entitypos.x;
	       	float y = (float)entitypos.y;
	       	float z = (float)entitypos.z;
	       	float xo = (float)originalEntity.xo;
	       	float yo = (float)originalEntity.yo;
	       	float zo = (float)originalEntity.zo;
	       	OpenMatrix4f toModelPos = OpenMatrix4f.mul(OpenMatrix4f.createTranslation(xo + (x - xo) * partialTicks, yo + (y - yo) * partialTicks, zo + (z - zo) * partialTicks), modelmat, null).invert();
	       	
			for (IKSetter ikSetter : this.ikSetters) {
				VertexConsumer vertexBuilder = buffer.getBuffer(EpicFightRenderTypes.debugQuads());
				Vec3f worldtargetpos = enderdragonpatch.getTipPointAnimation(ikSetter.endJoint).getTargetPosition();
				Vec3f modeltargetpos = OpenMatrix4f.transform3v(toModelPos, worldtargetpos, null).multiply(-1.0F, 1.0F, -1.0F);
				RenderingTool.drawQuad(poseStack, vertexBuilder, modeltargetpos, 0.5F, 1.0F, 0.0F, 0.0F);
		       	Vec3f jointWorldPos = enderdragonpatch.getTipPointAnimation(ikSetter.endJoint).getTipPosition(partialTicks);
		       	Vec3f jointModelpos = OpenMatrix4f.transform3v(toModelPos, jointWorldPos, null);
		       	RenderingTool.drawQuad(poseStack, vertexBuilder, jointModelpos.multiply(-1.0F, 1.0F, -1.0F), 0.4F, 0.0F, 0.0F, 1.0F);
		       	
		       	Pose pose = new Pose();
		       	
				for (String jointName : this.jointTransforms.keySet()) {
					pose.putJointData(jointName, this.jointTransforms.get(jointName).getInterpolatedTransform(playTime));
				}
			}
		}
	}
}