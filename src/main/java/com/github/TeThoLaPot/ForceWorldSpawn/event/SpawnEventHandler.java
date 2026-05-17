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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;


@Mod.EventBusSubscriber(modid = "force_world_spawn")
public class SpawnEventHandler {

    private static final String KEY_SAVED_BED_X = "SavedBedX";
    private static final String KEY_SAVED_BED_Y = "SavedBedY";
    private static final String KEY_SAVED_BED_Z = "SavedBedZ";
    private static final String KEY_SAVED_BED_DIM = "SavedBedDim";

    private static final String KEY_PENDING_BED_X = "FwsPendingBedX";
    private static final String KEY_PENDING_BED_Y = "FwsPendingBedY";
    private static final String KEY_PENDING_BED_Z = "FwsPendingBedZ";
    private static final String KEY_PENDING_BED_DIM = "FwsPendingBedDim";
    private static final String KEY_PENDING_BED_TIME = "FwsPendingBedTime";

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
            if (!(state.getBlock() instanceof BedBlock)) return;

            // Right-clickだけでは確定保存しない:
            // まず候補として座標を保持し、実際にスポーンが更新された(PlayerSetSpawnEvent)時だけ確定する。
            CompoundTag data = event.getEntity().getPersistentData();
            BlockPos pos = event.getPos();

            data.putInt(KEY_PENDING_BED_X, pos.getX());
            data.putInt(KEY_PENDING_BED_Y, pos.getY());
            data.putInt(KEY_PENDING_BED_Z, pos.getZ());
            data.putString(KEY_PENDING_BED_DIM, event.getLevel().dimension().location().toString());
            data.putLong(KEY_PENDING_BED_TIME, event.getLevel().getGameTime());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerSetSpawn(PlayerSetSpawnEvent event) {
        if (!FWS_Utils.FWS_ENABLE) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        CompoundTag data = player.getPersistentData();

        // スポーンが「解除」された場合は、保存済みベッド座標も解除する
        if (event.getNewSpawn() == null) {
            clearSavedBed(data);
            clearPendingBed(data);
            return;
        }

        // スポーンが設定された場合は、その座標をベッド座標として確定保存する。
        // 寝袋のようにスポーンが変わらないケースは、このイベント自体が発火しない想定。
        ResourceKey<Level> spawnLevel = event.getSpawnLevel();
        ServerLevel level = player.getServer() == null ? null : player.getServer().getLevel(spawnLevel);
        BlockPos newSpawn = event.getNewSpawn();

        if (level != null && level.getBlockState(newSpawn).getBlock() instanceof BedBlock) {
            data.putInt(KEY_SAVED_BED_X, newSpawn.getX());
            data.putInt(KEY_SAVED_BED_Y, newSpawn.getY());
            data.putInt(KEY_SAVED_BED_Z, newSpawn.getZ());
            data.putString(KEY_SAVED_BED_DIM, spawnLevel.location().toString());
        } else if (!isModForcedWorldSpawn(level, newSpawn)) {
            // リスポーンアンカー等へ切り替えた場合のみクリア（MODのワールドスポーン固定は対象外）
            clearSavedBed(data);
        }

        clearPendingBed(data);
    }

    /**
     * forceWorldSpawn が設定するワールドスポーンと同じ座標か。
     * 死亡リスポーン時に PlayerSetSpawnEvent が複数回飛んでも tpbed 用データを消さない。
     */
    private static boolean isModForcedWorldSpawn(ServerLevel level, BlockPos spawnPos) {
        if (level == null || spawnPos == null) {
            return false;
        }
        return level.dimension() == Level.OVERWORLD && spawnPos.equals(level.getSharedSpawnPos());
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

        if (data.contains(KEY_SAVED_BED_X)) {
            int x = data.getInt(KEY_SAVED_BED_X);
            int y = data.getInt(KEY_SAVED_BED_Y);
            int z = data.getInt(KEY_SAVED_BED_Z);
            String dim = data.getString(KEY_SAVED_BED_DIM);

            ServerLevel level = source.getServer().getLevel(
                    ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dim)));

            BlockPos bedPos = new BlockPos(x, y, z);

            if (level == null || !(level.getBlockState(bedPos).getBlock() instanceof BedBlock)) {
                source.sendFailure(Component.literal("登録された座標にベッドが存在しません (壊された可能性があります)"));
                return 0;
            }

            // バニラと同じ「安全なリスポーン位置」を計算してテレポートする
            var safe = Player.findRespawnPositionAndUseSpawnBlock(level, bedPos, 0.0F, true, false);
            if (safe.isPresent()) {
                var pos = safe.get();
                target.teleportTo(level, pos.x, pos.y, pos.z, target.getYRot(), target.getXRot());
                source.sendSuccess(() -> Component.literal(target.getName().getString() + " を保存されたベッドへ飛ばしました"), true);
                return 1;
            }

            source.sendFailure(Component.literal("ベッド周辺が塞がっていて安全なテレポート位置が見つかりません"));
            return 0;
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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        CompoundTag oldData = event.getOriginal().getPersistentData();
        CompoundTag newData = event.getEntity().getPersistentData();

        if (oldData.contains(KEY_SAVED_BED_X)) {
            newData.putInt(KEY_SAVED_BED_X, oldData.getInt(KEY_SAVED_BED_X));
            newData.putInt(KEY_SAVED_BED_Y, oldData.getInt(KEY_SAVED_BED_Y));
            newData.putInt(KEY_SAVED_BED_Z, oldData.getInt(KEY_SAVED_BED_Z));
            newData.putString(KEY_SAVED_BED_DIM, oldData.getString(KEY_SAVED_BED_DIM));
        }
    }

    private static void clearPendingBed(CompoundTag data) {
        data.remove(KEY_PENDING_BED_X);
        data.remove(KEY_PENDING_BED_Y);
        data.remove(KEY_PENDING_BED_Z);
        data.remove(KEY_PENDING_BED_DIM);
        data.remove(KEY_PENDING_BED_TIME);
    }

    private static void clearSavedBed(CompoundTag data) {
        data.remove(KEY_SAVED_BED_X);
        data.remove(KEY_SAVED_BED_Y);
        data.remove(KEY_SAVED_BED_Z);
        data.remove(KEY_SAVED_BED_DIM);
    }


}