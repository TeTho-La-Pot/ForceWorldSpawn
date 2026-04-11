package com.github.TeThoLaPot.ForceWorldSpawn.event;

import com.github.TeThoLaPot.ForceWorldSpawn.utils.FWS_Utils;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(modid = "force_world_spawn")
public class SpawnEventHandler {

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            forceWorldSpawn(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (FWS_Utils.FWS_LOGIN == true) {
            if (event.getEntity() instanceof ServerPlayer player) {
                forceWorldSpawn(player);
            }
        }
    }

    private static void forceWorldSpawn(ServerPlayer player) {
        if (player.getServer() != null) {
            ServerLevel level = player.getServer().getLevel(Level.OVERWORLD);
            if (level != null) {
                BlockPos spawnPos = level.getSharedSpawnPos();
                float spawnAngle = level.getSharedSpawnAngle();

                player.setRespawnPosition(Level.OVERWORLD, spawnPos, spawnAngle, true, false);
                player.teleportTo(level, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, spawnAngle, 0.0F);
            }
        }
    }

    @SubscribeEvent
    public static void onBedClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) return;

        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (state.getBlock() instanceof BedBlock) {
            CompoundTag data = event.getEntity().getPersistentData();
            BlockPos pos = event.getPos();

            data.putInt("SavedBedX", pos.getX());
            data.putInt("SavedBedY", pos.getY());
            data.putInt("SavedBedZ", pos.getZ());
            data.putString("SavedBedDim", event.getLevel().dimension().location().toString());
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tpbed")
                .requires(source -> source.hasPermission(2)) // OP権限(レベル2)が必要
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "target");
                            CompoundTag data = target.getPersistentData();

                            if (data.contains("SavedBedX")) {
                                int x = data.getInt("SavedBedX");
                                int y = data.getInt("SavedBedY");
                                int z = data.getInt("SavedBedZ");
                                String dim = data.getString("SavedBedDim");

                                ServerLevel level = context.getSource().getServer().getLevel(
                                        ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dim)));

                                BlockPos bedPos = new BlockPos(x, y, z);

                                if (level != null && level.getBlockState(bedPos).getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
                                    target.teleportTo(level, x + 0.5, y + 1.0, z + 0.5, target.getYRot(), target.getXRot());
                                    context.getSource().sendSuccess(() -> Component.literal(target.getName().getString() + " を保存されたベッドへ飛ばしました"), true);
                                } else {
                                    context.getSource().sendFailure(Component.literal("登録された座標にベッドが存在しません"));
                                }
                            } else {
                                context.getSource().sendFailure(Component.literal("このプレイヤーの座標データが見つかりません"));
                            }
                            return 1;
                        })
                )
        );
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            CompoundTag oldData = event.getOriginal().getPersistentData();
            CompoundTag newData = event.getEntity().getPersistentData();

            if (oldData.contains("SavedBedX")) {
                newData.putInt("SavedBedX", oldData.getInt("SavedBedX"));
                newData.putInt("SavedBedY", oldData.getInt("SavedBedY"));
                newData.putInt("SavedBedZ", oldData.getInt("SavedBedZ"));
                newData.putString("SavedBedDim", oldData.getString("SavedBedDim"));
            }
        }
    }


}