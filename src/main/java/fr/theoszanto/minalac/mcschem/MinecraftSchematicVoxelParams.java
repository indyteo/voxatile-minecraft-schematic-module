package fr.theoszanto.minalac.mcschem;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.ignfab.minalac.generator.modules.minecraft.MinecraftVoxelParams;
import com.ignfab.minalac.generator.parameters.JsonWrapper;
import com.ignfab.minalac.generator.parameters.placeables.PlaceableParams;
import com.ignfab.minalac.generator.utils.random.Seed;

import java.beans.ConstructorProperties;

/**
 * Parameters for {@link MinecraftSchematicVoxel}.
 */
@JsonWrapper
public class MinecraftSchematicVoxelParams extends PlaceableParams {
	/**
	 * Wrapped block params (required).
	 */
	@JsonSetter(nulls = Nulls.FAIL)
	public MinecraftVoxelParams params;

	/**
	 * Creates a new {@link MinecraftSchematicVoxelParams}.
	 * @param params wrapped block params
	 */
	@ConstructorProperties("params")
	public MinecraftSchematicVoxelParams(MinecraftVoxelParams params) {
		this.params = params;
	}

	@Override
	public void validate() throws IllegalArgumentException {
		params.validate();
	}

	@Override
	public MinecraftSchematicVoxel create(Seed seed) {
		return new MinecraftSchematicVoxel(params.create(seed));
	}

	/**
	 * Creates a params from the block in packed form.
	 * @param block block in packed form
	 * @return the created params
	 * @see MinecraftVoxelParams#packed(String)
	 */
	public static MinecraftSchematicVoxelParams packed(String block) {
		return new MinecraftSchematicVoxelParams(MinecraftVoxelParams.packed(block));
	}
}
