package fr.theoszanto.minalac.mcschem;

import com.ignfab.minalac.generator.modules.minecraft.MinecraftVoxel;
import com.ignfab.minalac.generator.placeables.Placeable;
import com.ignfab.minalac.generator.world.VoxelTile;

/**
 * Wrapper around {@link MinecraftVoxel} that is placed in {@link MinecraftSchematicTile}s.
 * @param block the block
 */
public record MinecraftSchematicVoxel(MinecraftVoxel block) implements Placeable {
	/**
	 * Default voxel wrapping {@link MinecraftVoxel#DEFAULT_VOXEL}.
	 */
	public static final MinecraftSchematicVoxel DEFAULT_VOXEL = new MinecraftSchematicVoxel(MinecraftVoxel.DEFAULT_VOXEL);

	@Override
	public void place(VoxelTile tile, int x, int y, int z) {
		if (!(tile instanceof MinecraftSchematicTile schem))
			throw new IllegalArgumentException("Minecraft schematic voxel can only be placed in a Minecraft schematic");
		schem.setVoxel(x, z, -y - 1, block); // X/Y/Z => X/Z/-Y
	}
}
