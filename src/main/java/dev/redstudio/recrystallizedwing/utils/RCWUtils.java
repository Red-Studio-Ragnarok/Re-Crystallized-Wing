package dev.redstudio.recrystallizedwing.utils;

import dev.redstudio.recrystallizedwing.config.RCWConfig;
import dev.redstudio.recrystallizedwing.handlers.NostalgicSoundsHandler;
import dev.redstudio.recrystallizedwing.sounds.NostalgicSound;
import lombok.NoArgsConstructor;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.Random;

import static lombok.AccessLevel.PRIVATE;

/**
 * Utility class containing various utility methods.
 *
 * @author Luna Lage (Desoroxxx)
 * @since 1.0
 */
@NoArgsConstructor(access = PRIVATE)
public final class RCWUtils {

    public static final Random random = new Random();

    /**
     * Spawns an explosion particle effect around the given entity in the world.
     *
     * @param entity The entity around which to spawn the particle effect
     * @param amount The number of particles to spawn
     */
    public static void spawnExplosionParticleAtEntity(final Entity entity, final int amount) {
        final double velocity = random.nextGaussian() / 8;

        final double xOffset = random.nextGaussian() / 12;
        final double yOffset = random.nextGaussian() / 12;
        final double zOffset = random.nextGaussian() / 12;

        ((WorldServer) entity.getEntityWorld()).spawnParticle(EnumParticleTypes.EXPLOSION_NORMAL, entity.posX, entity.posY, entity.posZ, amount, xOffset, yOffset, zOffset, velocity);
    }

    /**
     * Verifies if the given block position is a safe teleport location for players.
     *
     * @param world The world in which to verify the respawn location
     * @param blockPos The block position to verify
     *
     * @return True if the respawn location is safe, false otherwise
     */
    public static boolean verifyTeleportCoordinates(final World world, final BlockPos blockPos) {
        final Material floorBlockMaterial = world.getBlockState(blockPos.down()).getMaterial();
        final Material bottomBlockMaterial = world.getBlockState(blockPos).getMaterial();
        final Material topBlockMaterial = world.getBlockState(blockPos.up()).getMaterial();

        final boolean floorSafe = floorBlockMaterial.isSolid() || floorBlockMaterial.isLiquid();
        final boolean bottomSafe = !bottomBlockMaterial.isSolid() && !bottomBlockMaterial.isLiquid();
        final boolean topSafe = !topBlockMaterial.isSolid() && !topBlockMaterial.isLiquid();

        return floorSafe && bottomSafe && topSafe;
    }

    /**
     * Finds the highest solid block at the given position in the world.
     *
     * @param world The world in which to search for the highest solid block
     * @param mutablePos A mutable block position of the position for the search
     * @param skipNonNormalCube A boolean flag indicating whether to skip non-normal cubes during the search
     *
     * @return The Y coordinate of the highest solid block, or 0 if no solid block is found
     */
    public static int getHighestSolidBlock(final World world, final BlockPos.MutableBlockPos mutablePos, final boolean skipNonNormalCube) {
        mutablePos.setY(world.getActualHeight());

        if (!skipNonNormalCube) {
            while ((mutablePos.getY() > 0) && world.isAirBlock(mutablePos))
                mutablePos.move(EnumFacing.DOWN);
        } else {
            while ((mutablePos.getY() > 0) && (world.isAirBlock(mutablePos) && !world.isBlockNormalCube(mutablePos, true)))
                mutablePos.move(EnumFacing.DOWN);
        }

        return mutablePos.getY();
    }

    /**
     * Teleports the given player to a new, randomly selected location within a specified distance from their current location the distance is specified via the config.
     *
     * @param world The world in which to teleport the player
     * @param player The player to teleport
     */
    public static void randomTeleport(final World world, final EntityPlayer player) {
        int randomX = 0;
        int randomZ = 0;

        final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos((int) player.posX, (int) player.posY, (int) player.posZ);

        while (!verifyTeleportCoordinates(world, mutablePos.add(0, 1, 0))) {
            randomX = (int) ((player.posX + random.nextInt(RCWConfig.common.randomTeleportationDistance * 2)) - RCWConfig.common.randomTeleportationDistance);
            randomZ = (int) ((player.posZ + random.nextInt(RCWConfig.common.randomTeleportationDistance * 2)) - RCWConfig.common.randomTeleportationDistance);

            mutablePos.setPos(randomX, 0, randomZ);
            mutablePos.setY(getHighestSolidBlock(world, mutablePos, false));
        }

        teleportPlayer(world, player, randomX + 0.5, mutablePos.getY(), randomZ + 0.5, 80);
    }

    /**
     * Teleports the given player to the specified coordinates in the given world and spawns explosion particle effects at both the old and new locations.
     *
     * @param world The world in which to teleport the player
     * @param player The player to teleport
     * @param x The X coordinate of the new location
     * @param y The Y coordinate of the new location
     * @param z The Z coordinate of the new location
     * @param particleAmount The number of explosion particles to spawn
     */
    public static void teleportPlayer(final World world, final EntityPlayer player, final double x, final double y, final double z, final int particleAmount) {
        spawnExplosionParticleAtEntity(player, particleAmount);

        player.setPositionAndUpdate(x + 0.5, y, z + 0.5);

        while (!world.getCollisionBoxes(player, player.getEntityBoundingBox()).isEmpty())
            player.setPositionAndUpdate(player.posX, player.posY + 1, player.posZ);

        spawnExplosionParticleAtEntity(player, particleAmount);

        playTeleportSound(player);
    }

    /**
     * Teleports the given player to the specified coordinates in the given world and spawns explosion particle effects at both the old and new locations.
     *
     * @param world The world in which to teleport the player
     * @param player The player to teleport
     * @param blockPos The coordinates of the new location
     * @param particleAmount The number of explosion particles to spawn
     */
    public static void teleportPlayer(final World world, final EntityPlayer player, final BlockPos blockPos, final int particleAmount) {
        teleportPlayer(world, player, blockPos.getX(), blockPos.getY(), blockPos.getZ(), particleAmount);
    }

    /**
     * Performs a ray trace with extended reach in the world using the player's position, rotation and by calculating the reach with the config options.
     *
     * @param world The world in which to perform the ray trace
     * @param player The player whose position and rotation to use for the ray trace
     *
     * @return A RayTraceResult object containing information about the block that was hit (if any)
     */
    public static RayTraceResult rayTraceWithExtendedReach(final World world, final EntityPlayer player) {
        final Vec3d startPosition = new Vec3d(player.posX, player.posY + player.getEyeHeight(), player.posZ);

        final float yaw = player.rotationYaw;
        final float cosYaw = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        final float sinYaw = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);

        final float pitch = player.rotationPitch;
        final float cosPitch = -MathHelper.cos(-pitch * 0.017453292F);
        final float sinPitch = MathHelper.sin(-pitch * 0.017453292F);

        final float reachMultiplier = RCWConfig.common.enderScepterReach * (player.isCreative() ? RCWConfig.common.enderScepterCreativeReachMult : 1);

        final Vec3d endPosition = startPosition.add((sinYaw * cosPitch) * reachMultiplier, sinPitch * reachMultiplier, (cosYaw * cosPitch) * reachMultiplier);

        return world.rayTraceBlocks(startPosition, endPosition, false, true, false);
    }

    /**
     * Plays the teleport sound effect for the given player.
     *
     * @param player The player for whom to play the sound
     */
    private static void playTeleportSound(final EntityPlayer player) {
        if (RCWConfig.common.nostalgicSounds) {
            NostalgicSoundsHandler.NOSTALGIC_SOUNDS.add(new NostalgicSound(player));
        } else {
            player.world.playSound(null, player.getPosition(), SoundEvents.ENTITY_ENDERMEN_TELEPORT, SoundCategory.MASTER, 1, 1);
        }
    }
}
