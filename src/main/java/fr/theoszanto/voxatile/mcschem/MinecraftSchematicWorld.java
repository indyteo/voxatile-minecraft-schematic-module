package fr.theoszanto.voxatile.mcschem;

import com.ignfab.minalac.generator.utils.FileHelpers;
import com.ignfab.minalac.generator.utils.world2d.WorldBBox2d;
import com.ignfab.minalac.generator.utils.world3d.WorldBBox3d;
import com.ignfab.minalac.generator.utils.world3d.WorldCoords3d;
import com.ignfab.minalac.generator.world.MapWriteException;
import com.ignfab.minalac.generator.world.VoxelTile;
import com.ignfab.minalac.generator.world.VoxelWorld;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of {@link VoxelWorld} that create a schematic
 * that can be imported into Minecraft using external tools.
 * The schematic uses the Sponge Schematic version 3 format.
 * This format does not support tiling. {@link #tiles(int)}
 * will always return a single tile covering the whole area.
 */
public class MinecraftSchematicWorld extends VoxelWorld {
	private final File destination;

	// Technically not hard limits, but realistically no tool can handle
	// such huge schematic with acceptable memory caps.
	// Furthermore, Sponge Schematic version 3 uses unsigned shorts to
	// store width/height/length, making it impossible to represent
	// area bigger than 2^16 blocks along any axis.
	// Offset could be used to make a max-sized schematic not centered in (0, 0).
	private static final WorldBBox3d MAX_LIMIT = new WorldBBox3d(
			new WorldCoords3d(Short.MIN_VALUE, Short.MIN_VALUE, -64),
			new WorldCoords3d(Short.MAX_VALUE, Short.MAX_VALUE, 319)
	);

	/**
	 * Constructs a new {@code MinecraftSchematicWorld}.
	 * The limits of the world have to be set using {@link #setLimits(WorldBBox3d)}
	 *
	 * @param destination Directory where to save schematic to. If null nothing is saved.
	 */
	public MinecraftSchematicWorld(File destination) {
		super(new MinecraftSchematicMetadata());
		this.destination = destination;
	}

	@Override
	public MinecraftSchematicMetadata getMetadata() {
		return (MinecraftSchematicMetadata) super.getMetadata();
	}

	@Override
	public WorldBBox3d maxLimits() {
		return MAX_LIMIT;
	}

	@Override
	public VoxelTile newTile(WorldBBox3d limits) {
		return new MinecraftSchematicTile(limits, generateSchemFileName(), getMetadata());
	}

	@Override
	public void initialize() throws MapWriteException {
		if (destination == null)
			return;
		if (!FileHelpers.isReadableDirectory(destination))
			throw new MapWriteException("Directory %s can not be accessed".formatted(destination));
	}

	@Override
	public void finalizeAndSave() {}

	@Override
	public Collection<WorldBBox2d> tiles(int maxTileSize) {
		// Schematic format does not support tiling!
		return List.of(limits().to2d());
	}

	private File generateSchemFileName() {
		if (destination == null)
			return null;
		return new File(destination, Objects.requireNonNullElse(metadata.getWorldName(), "voxatile")
				.toLowerCase()
				.replaceAll("[^a-z0-9]+", "_") + ".schem");
	}
}
