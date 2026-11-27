package dev.tlockout.goal;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.structure.Structure;
import org.bukkit.util.BiomeSearchResult;
import org.bukkit.util.StructureSearchResult;

/**
 * Scans the primary overworld around spawn for structures and biomes required by goals, caching
 * results per world until {@link #clearCache()} (called on {@code /lockout reload}).
 */
public final class WorldFeasibilityScanner {

    private static final int BIOME_HORIZONTAL_STEP = 32;
    private static final int BIOME_VERTICAL_STEP = 64;

    public record WorldFacts(
            UUID worldId,
            int radiusBlocks,
            int spawnX,
            int spawnZ,
            Map<String, Boolean> structures,
            Map<String, Boolean> biomes,
            long scanDurationMs) {

        public static final WorldFacts UNRESTRICTED = new WorldFacts(null, 0, 0, 0, Map.of(), Map.of(), 0L);

        public int foundStructures() {
            return (int) structures.values().stream().filter(Boolean::booleanValue).count();
        }

        public int foundBiomes() {
            return (int) biomes.values().stream().filter(Boolean::booleanValue).count();
        }

        /**
         * True when at least one required biome (if any) and at least one required structure
         * (if any) were located within the scan radius. Unknown keys fail open.
         */
        public boolean satisfies(List<String> requiredBiomes, List<String> requiredStructures) {
            if (!requiredBiomes.isEmpty() && !biomes.isEmpty()) {
                boolean anyBiome = false;
                for (String key : requiredBiomes) {
                    if (biomes.getOrDefault(normaliseKey(key), true)) {
                        anyBiome = true;
                        break;
                    }
                }
                if (!anyBiome) {
                    return false;
                }
            }
            if (!requiredStructures.isEmpty() && !structures.isEmpty()) {
                boolean anyStructure = false;
                for (String key : requiredStructures) {
                    if (structures.getOrDefault(normaliseKey(key), true)) {
                        anyStructure = true;
                        break;
                    }
                }
                if (!anyStructure) {
                    return false;
                }
            }
            return true;
        }
    }

    private final Logger logger;
    private boolean enabled;
    private int radiusBlocks;
    private final Map<UUID, WorldFacts> cache = new HashMap<>();

    public WorldFeasibilityScanner(Logger logger, boolean enabled, int radiusBlocks) {
        this.logger = logger;
        this.enabled = enabled;
        this.radiusBlocks = Math.max(64, radiusBlocks);
    }

    public boolean enabled() {
        return enabled;
    }

    public int radiusBlocks() {
        return radiusBlocks;
    }

    public void configure(boolean enabled, int radiusBlocks) {
        this.enabled = enabled;
        this.radiusBlocks = Math.max(64, radiusBlocks);
        clearCache();
    }

    public void clearCache() {
        cache.clear();
    }

    /** True when scanning {@code world} will perform a fresh locate pass instead of hitting cache. */
    public boolean needsScan(World world) {
        if (!enabled || world == null) {
            return false;
        }
        Location spawn = world.getSpawnLocation();
        WorldFacts cached = cache.get(world.getUID());
        return cached == null
                || cached.radiusBlocks() != radiusBlocks
                || cached.spawnX() != spawn.getBlockX()
                || cached.spawnZ() != spawn.getBlockZ();
    }

    /**
     * Returns cached feasibility facts for {@code world}, scanning once around world spawn for all
     * structure and biome keys referenced by {@code templates}.
     */
    public WorldFacts factsFor(World world, List<GoalSpec> templates) {
        if (!enabled || world == null) {
            return WorldFacts.UNRESTRICTED;
        }
        Location spawn = world.getSpawnLocation();
        WorldFacts cached = cache.get(world.getUID());
        if (cached != null
                && cached.radiusBlocks() == radiusBlocks
                && cached.spawnX() == spawn.getBlockX()
                && cached.spawnZ() == spawn.getBlockZ()) {
            return cached;
        }

        Set<String> structureKeys = new LinkedHashSet<>();
        Set<String> biomeKeys = new LinkedHashSet<>();
        for (GoalSpec spec : templates) {
            for (String key : spec.requiredStructures()) {
                structureKeys.add(normaliseKey(key));
            }
            for (String key : spec.requiredBiomes()) {
                biomeKeys.add(normaliseKey(key));
            }
        }

        long started = System.currentTimeMillis();
        Map<String, Boolean> structureResults = scanStructures(world, spawn, structureKeys);
        Map<String, Boolean> biomeResults = scanBiomes(world, spawn, biomeKeys);
        long elapsed = System.currentTimeMillis() - started;

        long foundStructures = structureResults.values().stream().filter(Boolean::booleanValue).count();
        long foundBiomes = biomeResults.values().stream().filter(Boolean::booleanValue).count();
        logger.info("World scan for '" + world.getName() + "' (radius " + radiusBlocks + " blocks): "
                + foundStructures + "/" + structureResults.size() + " structures, "
                + foundBiomes + "/" + biomeResults.size() + " biomes located in " + elapsed + "ms.");

        WorldFacts facts = new WorldFacts(world.getUID(), radiusBlocks,
                spawn.getBlockX(), spawn.getBlockZ(),
                Map.copyOf(structureResults), Map.copyOf(biomeResults), elapsed);
        cache.put(world.getUID(), facts);
        return facts;
    }

    private Map<String, Boolean> scanStructures(World world, Location spawn, Set<String> keys) {
        Map<String, Boolean> results = new HashMap<>();
        if (keys.isEmpty()) {
            return results;
        }
        Registry<Structure> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);
        int radiusChunks = Math.max(1, (radiusBlocks + 15) / 16);
        long maxDistSq = (long) radiusBlocks * radiusBlocks;
        for (String key : keys) {
            NamespacedKey nsKey = NamespacedKey.minecraft(key);
            Structure structure = registry.get(nsKey);
            if (structure == null) {
                logger.warning("Unknown required structure '" + key + "' in goals.yml; ignoring gate.");
                results.put(key, true);
                continue;
            }
            try {
                StructureSearchResult found = world.locateNearestStructure(spawn, structure, radiusChunks, false);
                results.put(key, found != null && withinHorizontalRadius(spawn, found.getLocation(), maxDistSq));
            } catch (RuntimeException ex) {
                logger.warning("Failed locating structure '" + key + "': " + ex.getMessage());
                results.put(key, true);
            }
        }
        return results;
    }

    private Map<String, Boolean> scanBiomes(World world, Location spawn, Set<String> keys) {
        Map<String, Boolean> results = new HashMap<>();
        if (keys.isEmpty()) {
            return results;
        }
        Registry<Biome> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
        long maxDistSq = (long) radiusBlocks * radiusBlocks;
        for (String key : keys) {
            NamespacedKey nsKey = NamespacedKey.minecraft(key);
            Biome biome = registry.get(nsKey);
            if (biome == null) {
                logger.warning("Unknown required biome '" + key + "' in goals.yml; ignoring gate.");
                results.put(key, true);
                continue;
            }
            try {
                BiomeSearchResult found = world.locateNearestBiome(
                        spawn, radiusBlocks, BIOME_HORIZONTAL_STEP, BIOME_VERTICAL_STEP, biome);
                results.put(key, found != null && withinHorizontalRadius(spawn, found.getLocation(), maxDistSq));
            } catch (RuntimeException ex) {
                logger.warning("Failed locating biome '" + key + "': " + ex.getMessage());
                results.put(key, true);
            }
        }
        return results;
    }

    private static boolean withinHorizontalRadius(Location origin, Location target, long maxDistSq) {
        if (target == null) {
            return false;
        }
        double dx = target.getX() - origin.getX();
        double dz = target.getZ() - origin.getZ();
        return (dx * dx + dz * dz) <= maxDistSq;
    }

    public static String normaliseKey(String raw) {
        String key = raw.trim().toLowerCase(Locale.ROOT);
        int colon = key.indexOf(':');
        return colon >= 0 ? key.substring(colon + 1) : key;
    }
}
