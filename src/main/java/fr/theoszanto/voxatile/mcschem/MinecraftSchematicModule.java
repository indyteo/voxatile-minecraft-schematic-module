package fr.theoszanto.voxatile.mcschem;

import com.ignfab.minalac.generator.parameters.OutputFormat;
import com.ignfab.minalac.generator.parameters.ParamsParser;
import com.ignfab.minalac.generator.utils.modules.Module;

/**
 * Minecraft schematic (Sponge Schematic version 3) module.
 * @see <a href="https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-3.md">Sponge Schematic Specification version 3</a>
 */
public class MinecraftSchematicModule extends Module {
	@Override
	public void registerParams(ParamsParser parser) {
		parser.registerFormat("minecraftSchematic", new OutputFormat(MinecraftSchematicWorld::new, MinecraftSchematicVoxelParams.class, MinecraftSchematicVoxelParams::packed));
		// TODO Probably handle schematic placeable as well
		// (requires better definition of placeable parameters to allow registration of new types from modules)
	}
}
