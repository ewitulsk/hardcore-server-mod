package xyz.hardcoreserver;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Builds Respawn Shrines out of vanilla blocks only, so unmodded clients can see and use them.
 *
 * <pre>
 *   y+1:  lantern  .  lantern          (on top of the corner walls)
 *   y  :  wall   air  wall
 *         air  ANCHOR air              (charged respawn anchor = the "Respawn block")
 *         wall   air  wall
 *   y-1:  3x3 polished blackstone brick platform, chiseled center
 * </pre>
 */
public final class ShrineBuilder {
    public static final String DISPLAY_TAG = "hardcoreserver_shrine_label";
    private static final int MIN_RADIUS = 6;
    private static final int MAX_RADIUS = 22;

    /** Horizontal radius of chunks that must be loaded around a village center before building. */
    public static final int REQUIRED_RADIUS = MAX_RADIUS + 2;

    /** Finds a good spot near {@code center} and builds a shrine there. Returns the anchor position. */
    public static BlockPos buildNear(ServerLevel level, BlockPos center) {
        BlockPos ground = findSite(level, center, true);
        if (ground == null) ground = findSite(level, center, false);
        if (ground == null) {
            ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.offset(MIN_RADIUS, 0, 0)).below();
        }
        return build(level, ground);
    }

    /** Builds a shrine whose platform center replaces {@code ground}. Returns the anchor position. */
    public static BlockPos build(ServerLevel level, BlockPos ground) {
        BlockState brick = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState wall = Blocks.POLISHED_BLACKSTONE_BRICK_WALL.defaultBlockState();
        BlockState lantern = Blocks.SOUL_LANTERN.defaultBlockState();
        int flags = Block.UPDATE_ALL;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos base = ground.offset(dx, 0, dz);
                level.setBlock(base, dx == 0 && dz == 0 ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState() : brick, flags);
                // Support the platform so it never floats.
                for (int dy = 1; dy <= 4; dy++) {
                    BlockPos below = base.below(dy);
                    BlockState s = level.getBlockState(below);
                    if (!s.canBeReplaced() && s.getFluidState().isEmpty()) break;
                    level.setBlock(below, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), flags);
                }
                // Clear the space above.
                for (int dy = 1; dy <= 3; dy++) {
                    level.setBlock(base.above(dy), Blocks.AIR.defaultBlockState(), flags);
                }
            }
        }

        BlockPos anchor = ground.above();
        level.setBlock(anchor, Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE, RespawnAnchorBlock.MAX_CHARGES), flags);
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                level.setBlock(anchor.offset(dx, 0, dz), wall, flags);
                level.setBlock(anchor.offset(dx, 1, dz), lantern, flags);
            }
        }
        // Let the walls connect / update shapes properly.
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                BlockPos p = anchor.offset(dx, 0, dz);
                level.setBlock(p, Block.updateFromNeighbourShapes(level.getBlockState(p), level, p), flags);
            }
        }

        spawnLabel(level, anchor);
        HardcoreData.get(level.getServer()).addShrine(GlobalPos.of(level.dimension(), anchor));
        HardcoreServer.LOGGER.info("Built Respawn Shrine at {} in {}", anchor, level.dimension().location());
        return anchor;
    }

    private static void spawnLabel(ServerLevel level, BlockPos anchor) {
        removeLabel(level, anchor);
        Display.TextDisplay display = EntityType.TEXT_DISPLAY.create(level);
        if (display == null) return;
        Component text = Component.literal("Respawn Shrine").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD)
                .append(Component.literal("\nRight-click the anchor to buy back").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("\nfallen players with diamonds").withStyle(ChatFormatting.AQUA));
        CompoundTag tag = new CompoundTag();
        tag.putString("text", Component.Serializer.toJson(text, level.registryAccess()));
        tag.putString("billboard", "center");
        display.load(tag);
        display.moveTo(anchor.getX() + 0.5, anchor.getY() + 2.1, anchor.getZ() + 0.5, 0, 0);
        display.addTag(DISPLAY_TAG);
        level.addFreshEntity(display);
    }

    public static void removeLabel(ServerLevel level, BlockPos anchor) {
        AABB box = new AABB(anchor).inflate(1, 3, 1);
        for (Display.TextDisplay d : level.getEntitiesOfClass(Display.TextDisplay.class, box, e -> e.getTags().contains(DISPLAY_TAG))) {
            d.discard();
        }
    }

    /** Where a revived player should appear for a shrine anchor. */
    public static BlockPos standPos(BlockPos anchor) {
        return anchor.north();
    }

    // ---------------------------------------------------------------- site selection

    private static BlockPos findSite(ServerLevel level, BlockPos center, boolean strict) {
        for (int r = MIN_RADIUS; r <= MAX_RADIUS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue; // ring only
                    BlockPos ground = checkSite(level, center.getX() + dx, center.getZ() + dz, strict);
                    if (ground != null) return ground;
                }
            }
        }
        return null;
    }

    /** Returns the center ground block if the 3x3 area at (x,z) is a flat, open, natural surface. */
    private static BlockPos checkSite(ServerLevel level, int x, int z, boolean strict) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (Math.abs(y - level.getSeaLevel()) > 40) return null;
        // In strict mode also keep a free ring around it so we don't block doors/paths.
        int reach = strict ? 2 : 1;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz);
                boolean inner = Math.abs(dx) <= 1 && Math.abs(dz) <= 1;
                if (inner ? h != y : Math.abs(h - y) > 1) return null;
                BlockPos g = new BlockPos(x + dx, h - 1, z + dz);
                BlockState ground = level.getBlockState(g);
                if (!isNaturalGround(ground, strict)) return null;
                for (int up = 0; up < 4; up++) {
                    BlockState above = level.getBlockState(g.above(1 + up));
                    if (!above.canBeReplaced() || !above.getFluidState().isEmpty()) return null;
                }
            }
        }
        return new BlockPos(x, y - 1, z);
    }

    private static boolean isNaturalGround(BlockState s, boolean strict) {
        if (!s.getFluidState().isEmpty()) return false;
        if (s.is(Blocks.DIRT_PATH)) return !strict;
        return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.GRAVEL)
                || s.is(Blocks.STONE) || s.is(BlockTags.TERRACOTTA) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE)
                || (!strict && s.isSolid());
    }

    private ShrineBuilder() {}
}
