package com.bigbangcraft.regions.protection;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import java.util.Objects;

public class ProtectionContext {
    private final RegionAction action;
    private final ActorType actorType;
    private final ServerPlayer player;
    private final Entity sourceEntity;
    private final BlockPos sourceBlock;
    private final BlockPos targetPosition;
    private final Entity targetEntity;
    private final Level world;
    private final String cause;

    public ProtectionContext(RegionAction action, ActorType actorType, ServerPlayer player,
                             Entity sourceEntity, BlockPos sourceBlock, BlockPos targetPosition,
                             Entity targetEntity, Level world, String cause) {
        this.action = Objects.requireNonNull(action, "Action cannot be null");
        this.actorType = Objects.requireNonNull(actorType, "ActorType cannot be null");
        this.targetPosition = Objects.requireNonNull(targetPosition, "TargetPosition cannot be null");
        this.world = Objects.requireNonNull(world, "World cannot be null");
        this.player = player;
        this.sourceEntity = sourceEntity;
        this.sourceBlock = sourceBlock;
        this.targetEntity = targetEntity;
        this.cause = cause;
    }

    public RegionAction getAction() {
        return action;
    }

    public ActorType getActorType() {
        return actorType;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public Entity getSourceEntity() {
        return sourceEntity;
    }

    public BlockPos getSourceBlock() {
        return sourceBlock;
    }

    public BlockPos getTargetPosition() {
        return targetPosition;
    }

    public Entity getTargetEntity() {
        return targetEntity;
    }

    public Level getWorld() {
        return world;
    }

    public String getCause() {
        return cause;
    }

    public static class Builder {
        private final RegionAction action;
        private final Level world;
        private final BlockPos targetPosition;
        private ActorType actorType = ActorType.UNKNOWN;
        private ServerPlayer player;
        private Entity sourceEntity;
        private BlockPos sourceBlock;
        private Entity targetEntity;
        private String cause;

        public Builder(RegionAction action, Level world, BlockPos targetPosition) {
            this.action = action;
            this.world = world;
            this.targetPosition = targetPosition;
        }

        public Builder actor(ActorType actorType) {
            this.actorType = actorType;
            return this;
        }

        public Builder player(ServerPlayer player) {
            this.player = player;
            if (player != null) {
                this.actorType = ActorType.PLAYER;
            }
            return this;
        }

        public Builder sourceEntity(Entity sourceEntity) {
            this.sourceEntity = sourceEntity;
            return this;
        }

        public Builder sourceBlock(BlockPos sourceBlock) {
            this.sourceBlock = sourceBlock;
            return this;
        }

        public Builder targetEntity(Entity targetEntity) {
            this.targetEntity = targetEntity;
            return this;
        }

        public Builder cause(String cause) {
            this.cause = cause;
            return this;
        }

        public ProtectionContext build() {
            return new ProtectionContext(action, actorType, player, sourceEntity, sourceBlock, targetPosition, targetEntity, world, cause);
        }
    }
}
