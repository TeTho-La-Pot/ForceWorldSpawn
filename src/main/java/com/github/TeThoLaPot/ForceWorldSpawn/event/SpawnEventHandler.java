package com.github.TeThoLaPot.ForceWorldSpawn.event;

import com.github.TeThoLaPot.ForceWorldSpawn.utils.FWS_Utils;
import net.minecraft.commands.CommandSourceStack;
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
        if (FWS_Utils.FWS_ENABLE) {
            if (event.getEntity() instanceof ServerPlayer player) {
                forceWorldSpawn(player);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (FWS_Utils.FWS_LOGIN) {
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

                if (FWS_Utils.FWS_ENABLE) {
                    player.setRespawnPosition(Level.OVERWORLD, spawnPos, spawnAngle, true, false);
                }

                player.teleportTo(level, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, spawnAngle, 0.0F);
            }
        }
    }

    @SubscribeEvent
    public static void onBedClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) return;

        if (FWS_Utils.FWS_ENABLE) {
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
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tpbed")
                .requires(source -> source.hasPermission(2)) // 管理者権限
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    return executeBedTeleport(context.getSource(), player);
                })
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "target");
                            return executeBedTeleport(context.getSource(), target);
                        })
                )
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "target");
                            return executeBedTeleport(context.getSource(), target);
                        })
                )
        );

        event.getDispatcher().register(Commands.literal("worldspawn")
                .requires(source -> source.hasPermission(2)) // 必要に応じて権限レベルを変更
                .executes(context -> {
                    if (context.getSource().getEntity() instanceof ServerPlayer player) {
                        ServerLevel level = player.getServer().getLevel(Level.OVERWORLD);
                        if (level != null) {
                            BlockPos spawnPos = level.getSharedSpawnPos();
                            float spawnAngle = level.getSharedSpawnAngle();

                            // 実装済みの forceWorldSpawn ロジックを流用
                            player.teleportTo(level, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, spawnAngle, 0.0F);

                            context.getSource().sendSuccess(() -> Component.literal("ワールドスポーンにテレポートしました"), false);
                            return 1;
                        }
                    }
                    return 0;
                })

                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> {
                            ServerPlayer target = EntityArgument.getPlayer(context, "target");
                            return teleportToWorldSpawn(context.getSource(), target);
                        })
                )
        );
    }

    private static int executeBedTeleport(CommandSourceStack source, ServerPlayer target) {
        CompoundTag data = target.getPersistentData();

        if (data.contains("SavedBedX")) {
            int x = data.getInt("SavedBedX");
            int y = data.getInt("SavedBedY");
            int z = data.getInt("SavedBedZ");
            String dim = data.getString("SavedBedDim");

            ServerLevel level = source.getServer().getLevel(
                    ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dim)));

            BlockPos bedPos = new BlockPos(x, y, z);

            if (level != null && level.getBlockState(bedPos).getBlock() instanceof BedBlock) {
                target.teleportTo(level, x + 0.5, y + 1.0, z + 0.5, target.getYRot(), target.getXRot());
                source.sendSuccess(() -> Component.literal(target.getName().getString() + " を保存されたベッドへ飛ばしました"), true);
                return 1;
            } else {
                source.sendFailure(Component.literal("登録された座標にベッドが存在しません (壊された可能性があります)"));
                return 0;
            }
        } else {
            source.sendFailure(Component.literal(target.getName().getString() + " のベッド座標データが見つかりません"));
            return 0;
        }
    }

    private static int teleportToWorldSpawn(CommandSourceStack source, ServerPlayer target) {
        ServerLevel level = target.getServer().getLevel(Level.OVERWORLD);
        if (level != null) {
            BlockPos spawnPos = level.getSharedSpawnPos();
            float spawnAngle = level.getSharedSpawnAngle();

            target.teleportTo(level, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, spawnAngle, 0.0F);

            source.sendSuccess(() -> Component.literal(target.getName().getString() + " をワールドスポーンにテレポートしました"), true);
            return 1;
        }
        return 0;
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