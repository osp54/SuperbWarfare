package com.atsuishio.superbwarfare.mixins;

import com.atsuishio.superbwarfare.entity.OBBEntity;
import com.atsuishio.superbwarfare.tools.OBB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Predicate;

@Mixin(Level.class)
public abstract class LevelMixin {

    @Shadow
    protected abstract <T extends Entity> void getEntities(net.minecraft.world.level.entity.EntityTypeTest<Entity, T> pEntityTypeTest, AABB pBounds, Predicate<? super T> pPredicate, List<? super T> pOutput, int pMaxResults);

    @Inject(method = "getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
            at = @At("RETURN"))
    public void getEntities(Entity pEntity, AABB pBoundingBox, Predicate<? super Entity> pPredicate, CallbackInfoReturnable<List<Entity>> cir) {
        if (!(pEntity instanceof Projectile)) return;

        List<Entity> nearby = new java.util.ArrayList<>();
        this.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Entity.class), pBoundingBox, entity -> entity != pEntity && pPredicate.test(entity), nearby, Integer.MAX_VALUE);
        for (Entity entity : nearby) {
            if (!(entity instanceof OBBEntity obbEntity) || obbEntity.enableAABB()) {
                continue;
            }

            if (cir.getReturnValue().contains(entity)) {
                continue;
            }

            for (OBB obb : obbEntity.getOBBs()) {
                if (OBB.isColliding(obb, pBoundingBox)) {
                    cir.getReturnValue().add(entity);
                    break;
                }
            }
        }
    }
}
