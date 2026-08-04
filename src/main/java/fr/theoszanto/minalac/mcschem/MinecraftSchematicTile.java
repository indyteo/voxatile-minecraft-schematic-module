package fr.theoszanto.minalac.mcschem;

import com.ignfab.minalac.generator.modules.minecraft.MinecraftBlockEntityVoxel;
import com.ignfab.minalac.generator.modules.minecraft.MinecraftVoxel;
import com.ignfab.minalac.generator.utils.world3d.WorldBBox3d;
import com.ignfab.minalac.generator.utils.world3d.WorldCoords3d;
import com.ignfab.minalac.generator.world.MapWriteException;
import com.ignfab.minalac.generator.world.VoxelTile;
import io.github.ensgijs.nbt.io.BinaryNbtHelpers;
import io.github.ensgijs.nbt.io.CompressionType;
import io.github.ensgijs.nbt.mca.DataVersion;
import io.github.ensgijs.nbt.tag.CompoundTag;
import io.github.ensgijs.nbt.tag.ListTag;
import io.github.ensgijs.nbt.tag.StringTag;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Minecraft schematic output implementation.
 * Note that the format used (Sponge Schematic version 3) does not support
 * tiling, so this implementation creates a standalone schematic file.
 */
public class MinecraftSchematicTile extends VoxelTile {
	private final File schemFile;
	private final MinecraftSchematicMetadata metadata;
	// Data is split into sections to reduce memory footprint as much as possible
	private final Int2ObjectMap<int[]> dataSections = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());
	// But the palette is global to simplify saving
	private final Palette palette = new Palette();
	private final Map<WorldCoords3d, MinecraftBlockEntityVoxel> blockEntities = new HashMap<>(); // In-Game coords

	// Sections are similar to vanilla Minecraft chunk sections
	private static final int SECTION_SIZE = 16 * 16 * 16;

	/**
	 * Creates a new {@code MinecraftSchematicTile}.
	 *
	 * @param limits Limits of this tile (must be contained in world limits)
	 * @param schemFile Destination file (will be overwritten by the save)
	 * @param metadata Schematic metadata information
	 */
	public MinecraftSchematicTile(WorldBBox3d limits, File schemFile, MinecraftSchematicMetadata metadata) {
		super(limits);
		this.schemFile = schemFile;
		this.metadata = metadata;
	}

	@Override
	public void save() throws MapWriteException {
		if (schemFile == null)
			return;

		CompoundTag schematic = new CompoundTag();
		schematic.putInt("Version", 3);
		schematic.putInt("DataVersion", DataVersion.JAVA_1_21_11.id());

		CompoundTag metadataTag = new CompoundTag();
		metadataTag.putString("Name", metadata.getWorldName());
		metadataTag.putString("Author", metadata.getAuthor());
		metadataTag.putLong("Date", metadata.getDate().toEpochMilli());
		ListTag<StringTag> requiredMods = new ListTag<>(StringTag.class);
		metadata.getRequiredMods().forEach(requiredMods::addString);
		metadataTag.put("RequiredMods", requiredMods);
		schematic.put("Metadata", metadataTag);

		// Area is already in game coords
		WorldBBox3d area = computeArea();
		schematic.putShort("Width", (short) area.sizeX());
		schematic.putShort("Height", (short) area.sizeY());
		schematic.putShort("Length", (short) area.sizeZ());

		// X/Y/Z => X/Z/-Y (spawn point only)
		schematic.putIntArray("Offset", new int[] { // TODO Check spawn point
				area.minX() - metadata.getSpawn().x(),
				area.minY() - metadata.getSpawn().z(),
				area.minZ() + metadata.getSpawn().y() - 1
		});

		CompoundTag blocks = new CompoundTag();
		blocks.put("Palette", palette.toTag());
		blocks.putByteArray("Data", encodeData(area));
		blocks.put("BlockEntities", encodeBlockEntities(area));
		schematic.put("Blocks", blocks);

		CompoundTag root = new CompoundTag();
		root.put("Schematic", schematic);
		try {
			BinaryNbtHelpers.write(root, schemFile, CompressionType.GZIP);
		} catch (IOException e) {
			throw new MapWriteException("Unable to save schematic file %s".formatted(schemFile), e);
		}
	}

	// In-Game coords
	/* package-private */ void setVoxel(int blockX, int blockY, int blockZ, MinecraftVoxel block) {
		if (isOutOfLimits(blockX, blockY, blockZ))
			return;
		updateHeightmaps(blockX, -blockZ - 1, blockY); // X/Z/-Y => X/Y/Z

		WorldCoords3d pos = new WorldCoords3d(blockX, blockY, blockZ);
		if (block instanceof MinecraftBlockEntityVoxel blockEntity) {
			blockEntities.put(pos, blockEntity);
			block = blockEntity.stripBlockEntity();
		} else
			blockEntities.remove(pos);

		int[] data = dataSections.computeIfAbsent(sectionKey(blockX, blockY, blockZ), k -> new int[SECTION_SIZE]);
		data[inSectionKey(blockX, blockY, blockZ)] = palette.getOrInsert(block);
	}

	@Override
	public MinecraftSchematicVoxel getVoxel(int x, int y, int z) {
		WorldCoords3d pos = new WorldCoords3d(x, z, -y - 1); // X/Y/Z => X/Z/-Y
		MinecraftBlockEntityVoxel blockEntity = blockEntities.get(pos);
		if (blockEntity != null)
			return new MinecraftSchematicVoxel(blockEntity);

		int[] data = dataSections.get(sectionKey(pos.x(), pos.y(), pos.z()));
		if (data == null)
			return MinecraftSchematicVoxel.DEFAULT_VOXEL;

		int id = data[inSectionKey(pos.x(), pos.y(), pos.z())];
		MinecraftVoxel block = palette.get(id);
		if (block == null)
			throw new IllegalStateException("Corrupted palette and/or data: No block with id " + id);
		return new MinecraftSchematicVoxel(block);
	}

	// In-Game coords
	private boolean isOutOfLimits(int blockX, int blockY, int blockZ) {
		return !limits().contains(blockX, -blockZ - 1, blockY); // X/Z/-Y => X/Y/Z
	}

	// In-Game coords
	private WorldBBox3d computeArea() {
		if (dataSections.isEmpty())
			return WorldBBox3d.EMPTY;

		int minSectionX = Integer.MAX_VALUE;
		int minSectionY = Integer.MAX_VALUE;
		int minSectionZ = Integer.MAX_VALUE;
		int maxSectionX = Integer.MIN_VALUE;
		int maxSectionY = Integer.MIN_VALUE;
		int maxSectionZ = Integer.MIN_VALUE;

		for (IntIterator it = dataSections.keySet().iterator(); it.hasNext(); ) {
			int key = it.nextInt();
			int sectionX = (key >> 20) & 0xFFF;
			int sectionY = key & 0xFF;
			int sectionZ = (key >> 8) & 0xFFF;
			minSectionX = Math.min(minSectionX, sectionX);
			minSectionY = Math.min(minSectionY, sectionY);
			minSectionZ = Math.min(minSectionZ, sectionZ);
			maxSectionX = Math.max(maxSectionX, sectionX);
			maxSectionY = Math.max(maxSectionY, sectionY);
			maxSectionZ = Math.max(maxSectionZ, sectionZ);
		}

		// TODO Trim even more by inspecting edge sections!

		return new WorldBBox3d(
				new WorldCoords3d(
						minSectionX << 4,
						minSectionY << 4,
						minSectionZ << 4
				),
				new WorldCoords3d(
						((maxSectionX + 1) << 4) - 1,
						((maxSectionY + 1) << 4) - 1,
						((maxSectionZ + 1) << 4) - 1
				)
		);
	}

	// In-Game coords
	private byte[] encodeData(WorldBBox3d area) {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream(area.size().volume());

		for (int y = 0; y < area.sizeY(); y++) {
			int y0 = area.minY() + y;

			for (int z = 0; z < area.sizeZ(); z++) {
				int z0 = area.minZ() + z;

				int currentSectionX = Integer.MIN_VALUE;
				int[] currentSection = null;

				for (int x = 0; x < area.sizeX(); x++) {
					int x0 = area.minX() + x;

					int sectionX = x0 >> 4;
					if (sectionX != currentSectionX) {
						currentSectionX = sectionX;
						currentSection = dataSections.get(sectionKey(x0, y0, z0));
					}

					int id = currentSection == null ? 0 : currentSection[inSectionKey(x0, y0, z0)];
					writeVarInt(buffer, id);
				}
			}
		}

		return buffer.toByteArray();
	}

	// In-Game coords
	private ListTag<CompoundTag> encodeBlockEntities(WorldBBox3d area) {
		ListTag<CompoundTag> result = new ListTag<>(CompoundTag.class);
		blockEntities.forEach((pos, blockEntity) -> {
			CompoundTag tag = new CompoundTag();
			tag.putIntArray("Pos", new int[] {
					pos.x() - area.minX(),
					pos.y() - area.minY(),
					pos.z() - area.minZ()
			});
			tag.putString("Id", blockEntity.id());
			CompoundTag data = blockEntity.data();
			data.putString("id", blockEntity.id());
			tag.put("Data", data);
			result.add(tag);
		});
		return result;
	}

	// https://minecraft.wiki/w/Java_Edition_protocol/Data_types#VarInt_and_VarLong
	private static void writeVarInt(ByteArrayOutputStream buffer, int value) {
		while ((value & ~0x7F) != 0) {
			buffer.write((value & 0x7F) | 0x80);
			value >>>= 7;
		}
		buffer.write(value);
	}

	// In-Game coords
	private static int sectionKey(int blockX, int blockY, int blockZ) {
		return ((blockX & 0xFFF0) << 16) | ((blockZ & 0xFFF0) << 4) | ((blockY >> 4) & 0xFF);
	}

	// In-Game coords
	private static int inSectionKey(int blockX, int blockY, int blockZ) {
		return schemKey(blockX & 0xF, blockY & 0xF, blockZ & 0xF, 16, 16);
	}

	// In-Game coords
	private static int schemKey(int blockX, int blockY, int blockZ, int width, int length) {
		return blockX + (blockY * length + blockZ) * width;
	}

	private static class Palette {
		// Symmetric mapping to allow bidirectional quering
		private final Object2IntMap<MinecraftVoxel> block2id;
		private final Int2ObjectMap<MinecraftVoxel> id2block;

		public Palette() {
			// Both maps are synchronized on the same reference to avoid out-of-sync!
			block2id = Object2IntMaps.synchronize(new Object2IntOpenHashMap<>());
			id2block = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>(), block2id);
			insert(MinecraftVoxel.DEFAULT_VOXEL, 0);
		}

		private void insert(MinecraftVoxel block, int id) {
			block2id.put(block, id);
			id2block.put(id, block);
		}

		public int getOrInsert(MinecraftVoxel block) {
			int id = get(block);
			if (id != -1)
				return id;

			synchronized (this) {
				id = get(block);
				if (id != -1)
					return id;

				id = size();
				insert(block, id);
				return id;
			}
		}

		public int get(MinecraftVoxel block) {
			return block2id.getOrDefault(block, -1);
		}

		public MinecraftVoxel get(int id) {
			return id2block.get(id);
		}

		public int size() {
			return block2id.size();
		}

		public CompoundTag toTag() {
			CompoundTag tag = new CompoundTag();
			Object2IntMaps.fastForEach(block2id, entry -> tag.putInt(entry.getKey().toString(), entry.getIntValue()));
			return tag;
		}
	}
}
