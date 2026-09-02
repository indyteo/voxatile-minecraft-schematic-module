package fr.theoszanto.voxatile.mcschem;

import com.ignfab.minalac.generator.world.VoxelWorldMetadata;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Metadata useful for the schematic.
 */
public class MinecraftSchematicMetadata extends VoxelWorldMetadata {
	private String author = "Voxatile";
	// Using generation start time is an acceptable approximation
	// to allow us to easily provide a default value
	private Instant date = Instant.now();
	private List<String> requiredMods = new ArrayList<>();

	/**
	 * {@return the name of the author}
	 */
	public String getAuthor() {
		return author;
	}

	/**
	 * Sets the name of the author.
	 * @param author the name of the author
	 */
	public void setAuthor(String author) {
		this.author = author;
	}

	/**
	 * {@return the creation date}
	 */
	public Instant getDate() {
		return date;
	}

	/**
	 * Sets the creation date.
	 * @param date the creation date
	 */
	public void setDate(Instant date) {
		this.date = date;
	}

	/**
	 * {@return the list of required mods}
	 */
	public List<String> getRequiredMods() {
		return requiredMods;
	}

	/**
	 * Sets the list of required mods.
	 * @param requiredMods the list of required mods
	 */
	public void setRequiredMods(List<String> requiredMods) {
		this.requiredMods = requiredMods;
	}
}
