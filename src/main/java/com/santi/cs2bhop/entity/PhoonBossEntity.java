package com.santi.cs2bhop.entity;

import com.geckolib.animatable.GeoEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.util.GeckoLibUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * PHOON.
 *
 * <p>A GeckoLib animatable so the model and its animations can be authored in Blockbench and
 * dropped in without touching Java — see {@code assets/cs2bhop/geo/phoon.geo.json} and
 * {@code assets/cs2bhop/animations/phoon.animation.json}.
 *
 * <p>Two animation controllers, deliberately separate: one plays the locomotion state (idle, run,
 * tired), the other only fires one-shot attack swings. Keeping them apart means a swing does not
 * cancel the run cycle and vice versa, which is the usual reason a boss looks like it is stuttering.
 *
 * <p>The tired state is synched to the client so the animation can react to it — the client
 * otherwise has no idea the fight has opened a window.
 */
public class PhoonBossEntity extends Monster implements GeoEntity {

    private static final EntityDataAccessor<Boolean> TIRED =
            SynchedEntityData.defineId(PhoonBossEntity.class, EntityDataSerializers.BOOLEAN);

    /** Animation names must match the keys in phoon.animation.json. */
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation TIRED_ANIM = RawAnimation.begin().thenLoop("tired");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public PhoonBossEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.ATTACK_DAMAGE, 5.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 128.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.STEP_HEIGHT, 1.5);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.4, true));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 32.0F));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TIRED, false);
    }

    public boolean isTired() {
        return this.entityData.get(TIRED);
    }

    public void setTired(boolean tired) {
        this.entityData.set(TIRED, tired);
    }

    /** Triggers the one-shot swing, played on the attack controller. */
    public void playAttackAnimation() {
        triggerAnim("attack", "swing");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<PhoonBossEntity>("locomotion", 5, test -> {
            if (test.animatable().isTired()) {
                return test.setAndContinue(TIRED_ANIM);
            }
            return test.setAndContinue(test.isMoving() ? RUN : IDLE);
        }));

        controllers.add(new AnimationController<PhoonBossEntity>("attack", 0, test -> PlayState.STOP)
                .triggerableAnim("swing", ATTACK));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
