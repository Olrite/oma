package dev.oma.addon.util;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChunkUtils {
    protected static final Minecraft mc = Minecraft.getInstance();

    public static List<LevelChunk> getLoadedChunks() {
        assert mc.level != null;
        assert mc.player != null;

        List<LevelChunk> loadedChunks = new ArrayList<>();
        BlockPos bPos = mc.player.blockPosition();
        int playerX = bPos.getX();
        int playerZ = bPos.getZ();
        int renderDistance = mc.options.renderDistance().get() * 16;

        for (int x = playerX - renderDistance; x <= playerX + renderDistance; x += 16) {
            for (int z = playerZ - renderDistance; z <= playerZ + renderDistance; z += 16) {
                ChunkPos chunkPos = new ChunkPos(x >> 4, z >> 4);
                if (mc.level.hasChunk(chunkPos.x(), chunkPos.z())) {
                    loadedChunks.add(mc.level.getChunk(chunkPos.x(), chunkPos.z()));
                }
            }
        }

        return loadedChunks;
    }

    public static int getChestCount(LevelChunk chunk) {
        int count = 0;

        Map<BlockPos, BlockEntity> map = chunk.getBlockEntities();
        for (Map.Entry<BlockPos, BlockEntity> entry : map.entrySet()) {
            if (entry.getValue() instanceof ChestBlockEntity) count++;
        }

        return count;
    }


    public static int getShulkerCount(LevelChunk chunk) {
        int count = 0;

        for (BlockPos pos : chunk.getBlockEntities().keySet()) {
            BlockEntity block = chunk.getBlockEntity(pos);
            if (block instanceof ShulkerBoxBlockEntity) count++;
        }

        return count;
    }

    public static int getChestCount() {
        List<LevelChunk> chunks = getLoadedChunks();
        int count = 0;

        for (LevelChunk chunk : chunks) {
            for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                BlockEntity block = chunk.getBlockEntity(pos);
                if (block instanceof ChestBlockEntity) count++;
            }
        }

        return count;
    }



    public static int getShulkerCount() {
        List<LevelChunk> chunks = getLoadedChunks();
        int count = 0;

        for (LevelChunk chunk : chunks) {
            for (BlockPos pos : chunk.getBlockEntities().keySet()) {
                BlockEntity block = chunk.getBlockEntity(pos);
                if (block instanceof ShulkerBoxBlockEntity) count++;
            }
        }

        return count;
    }

    public static int getBlockCount(Block block) {
        int count = 0;
        List<LevelChunk> loadedChunks = getLoadedChunks();

        for (LevelChunk chunk : loadedChunks) {
            ChunkPos chunkPos = chunk.getPos();
            for (int x = 0; x < 16; x++) {
                for (int y = -64; y < 320; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockPos pos = new BlockPos(chunkPos.x() * 16 + x, y, chunkPos.z() * 16 + z);
                        if (chunk.getBlockState(pos).getBlock() == block) {
                            count++;
                        }
                    }
                }
            }
        }

        return count;
    }
}