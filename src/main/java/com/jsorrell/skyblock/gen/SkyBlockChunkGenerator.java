package com.jsorrell.skyblock.gen;

import java.util.Arrays;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.inventory.Inventory;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Items;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.ChunkRandom;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// 世界 方块生成
public class SkyBlockChunkGenerator extends NoiseChunkGenerator {
public static final Codec<SkyBlockChunkGenerator> CODEC =
    RecordCodecBuilder.create(
        (instance) ->
            instance
                .group(
                    BiomeSource.CODEC
                        .fieldOf("biome_source")
                        .forGetter(SkyBlockChunkGenerator::getBiomeSource),
                    Codec.LONG
                        .fieldOf("seed")
                        .stable()
                        .forGetter(SkyBlockChunkGenerator::getSeed),
                    ChunkGeneratorSettings.REGISTRY_CODEC
                        .fieldOf("settings")
                        .forGetter(SkyBlockChunkGenerator::getSettings))
                .apply(instance, instance.stable(SkyBlockChunkGenerator::new)));
private final long seed;

private static final Logger LOGGER = LogManager.getLogger();

public SkyBlockChunkGenerator(BiomeSource biomeSource, long seed, Supplier<ChunkGeneratorSettings> settings) {
    // 父类处理
    super(biomeSource, seed, settings);
    this.seed = seed;
}


protected static void placeRelativeBlockInBox(
    WorldAccess world,
    BlockState block,
    BlockPos referencePos,
    int x,
    int y,
    int z,
    BlockBox box) {
    BlockPos blockPos =
        new BlockPos(referencePos.getX() + x, referencePos.getY() + y, referencePos.getZ() + z);
    if (box.contains(blockPos)) {
        world.setBlockState(blockPos, block, 2);

    }
}

protected static void fillRelativeBlockInBox(
    WorldAccess world,
    BlockState block,
    BlockPos referencePos,
    int startX,
    int startY,
    int startZ,
    int endX,
    int endY,
    int endZ,
    BlockBox box) {
    for (int x = startX; x <= endX; x++) {
        for (int y = startY; y <= endY; y++) {
            for (int z = startZ; z <= endZ; z++) {
                placeRelativeBlockInBox(world, block, referencePos, x, y, z, box);
            }
        }
    }
}

private static boolean generateStrongholdPortalFrameSideInBox(
    ServerWorldAccess world, BlockPos centerPos, Random random, Direction side, BlockBox box) {
    BlockState portalFrameBlock =
        Blocks.END_PORTAL_FRAME
            .getDefaultState()
            .with(EndPortalFrameBlock.FACING, side.getOpposite());

    boolean completePortal = true;

    for (int i = -1; i < 2; ++i) {
        boolean hasEye = random.nextFloat() > 0.9F;
        completePortal &= hasEye;

        portalFrameBlock = portalFrameBlock.with(EndPortalFrameBlock.EYE, hasEye);
        Vec3i offset =
            side.getVector().multiply(2).add(side.rotateClockwise(Axis.Y).getVector().multiply(i));
        placeRelativeBlockInBox(
            world, portalFrameBlock, centerPos, offset.getX(), 0, offset.getZ(), box);
    }

    return completePortal;
}

// 末地门生成
private static void generateStrongholdPortalInBox(
    ServerWorldAccess world,
    BlockPos northWestCornerPos,
    Random random,
    Direction facing,
    BlockBox box) {

    BlockPos portalCenterPosition;
    switch (facing) {
        case EAST:
            portalCenterPosition = northWestCornerPos.add(10, 3, 5);
            break;
        case SOUTH:
            portalCenterPosition = northWestCornerPos.add(5, 3, 10);
            break;
        case WEST:
        case NORTH:
        default:
            portalCenterPosition = northWestCornerPos.add(5, 3, 5);
    }

    boolean completePortal = true;

    for (Direction d : Direction.values()) {
        if (d.getAxis().isHorizontal()) {
            completePortal &=
                generateStrongholdPortalFrameSideInBox(world, portalCenterPosition, random, d, box);
        }
    }

    if (completePortal) {
        BlockState endPortal = Blocks.END_PORTAL.getDefaultState();
        fillRelativeBlockInBox(world, endPortal, portalCenterPosition, -1, 0, 1, -1, 0, 1, box);
    }
    BlockPos spawnerPos = portalCenterPosition.subtract(facing.getVector().multiply(4));
    world.setBlockState(spawnerPos, Blocks.SPAWNER.getDefaultState(), 2);
    BlockEntity spawnerEntity = world.getBlockEntity(spawnerPos);
    if (spawnerEntity instanceof MobSpawnerBlockEntity) {
        ((MobSpawnerBlockEntity) spawnerEntity).getLogic().setEntityId(EntityType.SILVERFISH);
    }
}

protected static void generateSpawnPlatformInBox(ServerWorldAccess world, BlockPos spawnpoint, BlockBox box) {

    placeRelativeBlockInBox(world, Blocks.CHEST.getDefaultState(), spawnpoint, -1, 0, 0, box);

    BlockPos pos = new BlockPos(spawnpoint.getX() - 1, spawnpoint.getY(), spawnpoint.getZ());
    BlockEntity blockEntity = world.getBlockEntity(pos);
    if (blockEntity instanceof Inventory inventory){
        inventory.setStack(0, Items.APPLE.getDefaultStack());
    }
    else{
        LOGGER.info("blockEntity is not Inventory");
    }


    placeRelativeBlockInBox(world, Blocks.MYCELIUM.getDefaultState(), spawnpoint, 0, 0, 0, box);
    placeRelativeBlockInBox(world, Blocks.GRASS_BLOCK.getDefaultState(), spawnpoint, 1, 0, 0, box);
}

private static void generatorNetherPlatformInBox(ServerWorldAccess world, BlockPos spawnpoint, BlockBox box) {
    placeRelativeBlockInBox(world, Blocks.CRIMSON_NYLIUM.getDefaultState(), spawnpoint, 0, -1, 0, box);
    placeRelativeBlockInBox(world, Blocks.CRIMSON_FUNGUS.getDefaultState(), spawnpoint, 0, 0, 0, box);
}

public long getSeed() {
    return this.seed;
}

public Supplier<ChunkGeneratorSettings> getSettings() {
    return this.settings;
}

@Override
protected Codec<? extends ChunkGenerator> getCodec() {
    return CODEC;
}

@Override
@Environment(EnvType.CLIENT)
public ChunkGenerator withSeed(long seed) {
    return new SkyBlockChunkGenerator(this.biomeSource.withSeed(seed), seed, this.settings);
}


// 生成区块
@Override
public void buildSurface(ChunkRegion region, Chunk chunk) {
    Arrays.fill(chunk.getSectionArray(), WorldChunk.EMPTY_SECTION);



    if (region.getDimension().isNatural()) {
        BlockPos spawn =
            new BlockPos(
                region.getLevelProperties().getSpawnX(),
                region.getLevelProperties().getSpawnY(),
                region.getLevelProperties().getSpawnZ());
        if (chunk.getPos().getStartX() <= spawn.getX()
            && spawn.getX() <= chunk.getPos().getEndX()
            && chunk.getPos().getStartZ() <= spawn.getZ()
            && spawn.getZ() <= chunk.getPos().getEndZ()) {
            generateSpawnPlatformInBox(region, spawn,
                new BlockBox(chunk.getPos().getStartX(),
                    0, chunk.getPos().getStartZ(), chunk.getPos().getStartX() + 15,
                    region.getHeight(),
                    chunk.getPos().getStartZ() + 15));
        }
    } else if (region.getDimension().isUltrawarm()) {
        BlockPos spawn =
            new BlockPos(
                region.getLevelProperties().getSpawnX() / 8,
                region.getLevelProperties().getSpawnY(),
                region.getLevelProperties().getSpawnZ() / 8);
        if (chunk.getPos().getStartX() <= spawn.getX()
            && spawn.getX() <= chunk.getPos().getEndX()
            && chunk.getPos().getStartZ() <= spawn.getZ()
            && spawn.getZ() <= chunk.getPos().getEndZ()) {
            generatorNetherPlatformInBox(region, spawn,
                new BlockBox(
                    chunk.getPos().getStartX(),
                    0,
                    chunk.getPos().getStartZ(),
                    chunk.getPos().getStartX() + 15,
                    region.getHeight(),
                    chunk.getPos().getStartZ() + 15));
        }
    }
}

// TODO: Does this hurt something
@Override
public CompletableFuture<Chunk> populateNoise(
    Executor executor, StructureAccessor accessor, Chunk chunk) {
    return CompletableFuture.completedFuture(chunk);
}

@Override
public void carve(long seed, BiomeAccess access, Chunk chunk, GenerationStep.Carver carver) {
}

@Override
public void generateFeatures(ChunkRegion region, StructureAccessor accessor) {
    ChunkPos chunkPos = region.getCenterPos();
    BlockPos pos = new BlockPos(chunkPos.getStartX(), region.getBottomY(), chunkPos.getStartZ());
    int startX = chunkPos.getStartX();
    int startZ = chunkPos.getStartZ();
    BlockBox box = new BlockBox(startX, 0, startZ, startX + 15, region.getHeight(), startZ + 15);

    accessor
        .getStructuresWithChildren(
            ChunkSectionPos.from(pos),
            Registry.STRUCTURE_FEATURE.get(new Identifier("minecraft:stronghold")))
        .forEach(
            (structureStart) -> {
                for (StructurePiece piece : structureStart.getChildren()) {
                    if (piece.getType() == StructurePieceType.STRONGHOLD_PORTAL_ROOM) {
                        BlockPos portalPos =
                            new BlockPos(
                                piece.getBoundingBox().getMinX(),
                                piece.getBoundingBox().getMinY(),
                                piece.getBoundingBox().getMinZ());
                        if (piece.intersectsChunk(chunkPos, 0)) {
                            ChunkRandom random = new ChunkRandom();
                            random.setCarverSeed(seed, chunkPos.x, chunkPos.z);
                            generateStrongholdPortalInBox(
                                region, portalPos, random, Objects.requireNonNull(piece.getFacing()), box);
                        }
                    }
                }
            });
}
}


