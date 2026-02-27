package link.botwmcs.ltsxlogica.heat;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import link.botwmcs.ltsxlogica.data.HeatDataRegistry;
import link.botwmcs.ltsxlogica.data.HeatModelData;
import link.botwmcs.ltsxlogica.data.persistence.FeatureDataStore;
import link.botwmcs.ltsxlogica.heat.activities.IgnitionManager;
import link.botwmcs.ltsxlogica.heat.activities.PhaseChangeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;

/**
 * Per-ServerLevel heat system state.
 *
 * Design summary:
 * - Sparse activation (only sections with dirty/active heat are queued)
 * - Fixed per-tick budget with queue rotation
 * - Section-level lazy allocation and eviction
 */
public final class HeatManager {
    public static final int FIXED_SCALE = 16; // T_fixed = round(T_celsius * 16)

    // Numerical controls (safe defaults; tune in profiling).
    public static final float DT = 1.0f;
    public static final float UNDER_RELAX_ALPHA = 0.35f;
    public static final int EPSILON_FIXED = 1; // 1/16 C

    // Hard clamps to keep explicit integration stable.
    public static final int MIN_TEMP_FIXED = -200 * FIXED_SCALE;
    public static final int MAX_TEMP_FIXED = 2000 * FIXED_SCALE;

    // Tick budgets and queue controls.
    public static final int BUDGET_CELLS_PER_TICK = 8192;
    public static final int MAX_CELLS_PER_SECTION_ROUND = 512;
    public static final int MAX_SECTION_ROUNDS_PER_TICK = 256;
    public static final int WAKE_SCAN_PER_TICK = 64;
    public static final int STABLE_SLEEP_TICKS = 8;
    public static final int SECTION_EVICT_IDLE_TICKS = 20 * 30;

    // Secondary managers (also budgeted).
    public static final int PHASE_CHANGE_BUDGET_PER_TICK = 64;
    public static final int IGNITION_BUDGET_PER_TICK = 32;
    private static final int ENCLOSURE_SCAN_STEPS = 4;
    private static final int ROOF_SCAN_STEPS = 16;
    private static final int Q8_MAX = 255;
    private static final int DIURNAL_DIRECT_MAX_FIXED = toFixed(5.0f);
    private static final int DIURNAL_STORAGE_DAY_TARGET_FIXED = toFixed(4.0f);
    private static final int DIURNAL_STORAGE_NIGHT_TARGET_FIXED = toFixed(-3.0f);
    private static final int DIURNAL_STORAGE_LIMIT_FIXED = toFixed(8.0f);
    private static final int GREENHOUSE_BONUS_MAX_FIXED = toFixed(4.0f);
    private static final float DIURNAL_STORAGE_CHARGE_RATE = 0.028f;
    private static final float DIURNAL_STORAGE_RELEASE_RATE = 0.010f;
    private static final int[] FACE_DX = { 1, -1, 0, 0, 0, 0 };
    private static final int[] FACE_DY = { 0, 0, 1, -1, 0, 0 };
    private static final int[] FACE_DZ = { 0, 0, 0, 0, 1, -1 };
    private static final int STARTUP_MICROCLIMATE_SKIP_TICKS = 80;

    private static final int SERIALIZED_SECTION_BYTES = HeatSection.VOLUME * Short.BYTES;

    // Persistence payload under generic FeatureSavedData.
    private static final String FEATURE_KEY = "heat";
    private static final String TAG_VERSION = "version";
    private static final String TAG_SECTIONS = "sections";
    private static final String TAG_SECTION_KEY = "k";
    private static final String TAG_SECTION_TEMP = "t";
    private static final int PERSISTENCE_VERSION = 1;

    private final HeatPropsResolver propsResolver = new HeatPropsResolver();
    private final Long2ObjectOpenHashMap<HeatSection> sections = new Long2ObjectOpenHashMap<>();
    // Sections not live in memory but persisted in SavedData (lazy restore on access).
    private final Long2ObjectOpenHashMap<byte[]> persistedSections = new Long2ObjectOpenHashMap<>();
    private final ActiveSectionQueue activeSections = new ActiveSectionQueue();
    private final PhaseChangeManager phaseChangeManager = new PhaseChangeManager();
    private final IgnitionManager ignitionManager = new IgnitionManager();
    private final EntityThermalEffect entityThermalEffect = new EntityThermalEffect();
    private final BlockPos.MutableBlockPos biomeProbePos = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos waterProbePos = new BlockPos.MutableBlockPos();
    private final Long2IntOpenHashMap biomeAmbientByQuart = new Long2IntOpenHashMap();
    private final Long2LongOpenHashMap microClimateByQuart = new Long2LongOpenHashMap();
    private final Long2IntOpenHashMap waterInertiaOffsetByQuart = new Long2IntOpenHashMap();
    private final Long2IntOpenHashMap waterInertiaFluidIdByQuart = new Long2IntOpenHashMap();
    private final Long2LongOpenHashMap waterInertiaUpdatedTickByQuart = new Long2LongOpenHashMap();
    private final Long2LongOpenHashMap waterInertiaTouchedTickByQuart = new Long2LongOpenHashMap();

    private long tickCounter = 0L;
    private long ambientCacheTick = Long.MIN_VALUE;
    private long diurnalStateTick = Long.MIN_VALUE;
    private int chunkBootstrapDepth = 0;
    private boolean persistenceLoaded = false;
    private boolean persistenceDirty = false;
    private float diurnalSolarIntensity = 0.0f;
    private int diurnalDirectSolarFixed = 0;
    private int diurnalGroundStorageFixed = 0;
    private long waterInertiaEvictTick = Long.MIN_VALUE;
    private int appliedModelRevision = Integer.MIN_VALUE;

    public HeatManager() {
        this.biomeAmbientByQuart.defaultReturnValue(Integer.MIN_VALUE);
        this.microClimateByQuart.defaultReturnValue(Long.MIN_VALUE);
        this.waterInertiaOffsetByQuart.defaultReturnValue(Integer.MIN_VALUE);
        this.waterInertiaFluidIdByQuart.defaultReturnValue(Integer.MIN_VALUE);
        this.waterInertiaUpdatedTickByQuart.defaultReturnValue(Long.MIN_VALUE);
        this.waterInertiaTouchedTickByQuart.defaultReturnValue(Long.MIN_VALUE);
    }

    public void tick(ServerLevel level) {
        ensurePersistenceLoaded(level);
        refreshDataModelIfNeeded();
        this.tickCounter = level.getGameTime();
        evictWaterInertiaCache(this.tickCounter);
        this.activeSections.wakeExpired(this.tickCounter, WAKE_SCAN_PER_TICK);

        int budgetLeft = BUDGET_CELLS_PER_TICK;
        int sectionRounds = 0;

        while (budgetLeft > 0 && sectionRounds < MAX_SECTION_ROUNDS_PER_TICK) {
            long sectionKey = this.activeSections.poll(this.tickCounter);
            if (sectionKey == ActiveSectionQueue.NO_SECTION) {
                break;
            }

            HeatSection section = this.sections.get(sectionKey);
            if (section == null) {
                continue;
            }

            SectionPos secPos = SectionPos.of(sectionKey);
            int perSectionBudget = Math.min(budgetLeft, MAX_CELLS_PER_SECTION_ROUND);
            int spent = section.step(level, secPos, this, perSectionBudget);
            sectionRounds++;

            if (spent <= 0) {
                this.activeSections.sleep(sectionKey, this.tickCounter, STABLE_SLEEP_TICKS);
                continue;
            }

            budgetLeft -= spent;

            if (section.hasDirty()) {
                this.activeSections.requeue(sectionKey, this.tickCounter);
            } else {
                int ambient = getAmbientFixed(level, secPos.minBlockX(), secPos.minBlockY(), secPos.minBlockZ());
                if (section.isEvictable(this.tickCounter, SECTION_EVICT_IDLE_TICKS, ambient, EPSILON_FIXED)) {
                    this.sections.remove(sectionKey);
                    this.persistedSections.remove(sectionKey);
                    this.activeSections.evict(sectionKey);
                    markPersistenceDirty();
                } else {
                    this.activeSections.sleep(sectionKey, this.tickCounter, STABLE_SLEEP_TICKS);
                }
            }
        }

        this.phaseChangeManager.tick(level, this, PHASE_CHANGE_BUDGET_PER_TICK);
        this.ignitionManager.tick(level, this, IGNITION_BUDGET_PER_TICK);
        this.entityThermalEffect.tick(level, this);
    }

    public void onBlockChanged(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
        ensurePersistenceLoaded(level);
        if (oldState == newState) {
            return;
        }

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();

        markBlockAndNeighborsDirty(level, x, y, z);
        applyHeatState(level, x, y, z, resolvePropsAt(level, x, y, z, newState, null));
    }

    public void onChunkLoad(ServerLevel level, int chunkX, int chunkZ) {
        ensurePersistenceLoaded(level);
        if (isStartupPhase(level)) {
            return;
        }
        this.chunkBootstrapDepth++;
        try {
            // Bootstrap thermal field from existing persistent heat sources in this chunk.
            // Without this, naturally generated lava/fire never enters the simulation until a block update happens.
            int baseX = chunkX << 4;
            int baseZ = chunkZ << 4;
            int minY = level.getMinBuildHeight();
            int maxY = level.getMaxBuildHeight();

            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos belowCursor = new BlockPos.MutableBlockPos();
            for (int dx = 0; dx < 16; dx++) {
                int x = baseX + dx;
                for (int dz = 0; dz < 16; dz++) {
                    int z = baseZ + dz;
                    for (int y = minY; y < maxY; y++) {
                        cursor.set(x, y, z);
                        BlockState state = level.getBlockState(cursor);
                        HeatProps props = resolvePropsAt(level, x, y, z, state, belowCursor);
                        if (!props.isPersistentSource()) {
                            continue;
                        }

                        markBlockAndNeighborsDirty(level, x, y, z);
                        applyHeatState(level, x, y, z, props);
                    }
                }
            }
        } finally {
            this.chunkBootstrapDepth--;
        }
    }

    public void onPotentialStateUpdate(ServerLevel level, BlockPos pos, BlockState state) {
        ensurePersistenceLoaded(level);
        if (!isThermallyRelevantState(state)) {
            return;
        }
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        markBlockAndNeighborsDirty(level, x, y, z);
        applyHeatState(level, x, y, z, resolvePropsAt(level, x, y, z, state, null));
    }

    public void onChunkUnload(ServerLevel level, int chunkX, int chunkZ) {
        ensurePersistenceLoaded(level);
        int minSection = level.getMinSection();
        int maxSection = level.getMaxSection();
        boolean changed = false;
        for (int sy = minSection; sy < maxSection; sy++) {
            long key = SectionPos.asLong(chunkX, sy, chunkZ);
            HeatSection removed = this.sections.remove(key);
            if (removed != null) {
                this.persistedSections.put(key, encodeTempArray(removed.temp));
                changed = true;
            }
            this.activeSections.evict(key);
        }
        if (changed) {
            markPersistenceDirty();
        }
    }

    public void onPlayerLoggedOut(ServerPlayer player) {
        this.entityThermalEffect.onPlayerLoggedOut(player);
    }

    public float getTemperature(ServerLevel level, BlockPos pos) {
        return toCelsius(getTemperatureFixed(level, pos.getX(), pos.getY(), pos.getZ()));
    }

    public int getTemperatureFixed(ServerLevel level, int x, int y, int z) {
        ensurePersistenceLoaded(level);
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
            return getAmbientFixed(level, x, y, z);
        }
        long key = sectionKeyFromBlock(x, y, z);
        HeatSection section = getSection(level, key, x, y, z, false);
        if (section == null) {
            return getAmbientFixed(level, x, y, z);
        }
        return section.get(x & 15, y & 15, z & 15);
    }

    public void setTemperature(ServerLevel level, BlockPos pos, float celsius) {
        setTemperatureFixed(level, pos.getX(), pos.getY(), pos.getZ(), toFixed(celsius));
    }

    public void setTemperatureFixed(ServerLevel level, int x, int y, int z, int fixedTemp) {
        ensurePersistenceLoaded(level);
        long key = sectionKeyFromBlock(x, y, z);
        HeatSection section = getSection(level, key, x, y, z, true);
        section.set(x & 15, y & 15, z & 15, clampFixed(fixedTemp));
        section.markDirty(x & 15, y & 15, z & 15);
        this.activeSections.enqueue(key, this.tickCounter);
        markPersistenceDirty();
    }

    public void markBlockAndNeighborsDirty(ServerLevel level, int x, int y, int z) {
        markDirtyCell(level, x, y, z, true);
        markDirtyCell(level, x + 1, y, z, true);
        markDirtyCell(level, x - 1, y, z, true);
        markDirtyCell(level, x, y + 1, z, true);
        markDirtyCell(level, x, y - 1, z, true);
        markDirtyCell(level, x, y, z + 1, true);
        markDirtyCell(level, x, y, z - 1, true);
    }

    public void onCellTemperatureChanged(
            ServerLevel level,
            int x,
            int y,
            int z,
            int oldTempFixed,
            int newTempFixed,
            BlockState currentState
    ) {
        long packedPos = BlockPos.asLong(x, y, z);
        this.phaseChangeManager.onTemperatureCrossing(packedPos, oldTempFixed, newTempFixed);
        if (currentState.isAir()) {
            this.ignitionManager.onHotAirCandidate(packedPos);
        }
        markPersistenceDirty();
    }

    public boolean shouldUpdateCell(HeatProps props, int sectionPhase, int cellIndex) {
        int period = props.updatePeriodTicks;
        if (period <= 1) {
            return true;
        }
        long lane = (long) sectionPhase + (cellIndex & 3);
        return ((this.tickCounter + lane) % period) == 0L;
    }

    public int getAmbientFixed(ServerLevel level, int x, int y, int z) {
        // Ambient field model:
        // - trilinear-smoothed biome base to avoid hard biome-edge jumps
        // - heuristic indoor / greenhouse micro-climate
        // - day-night solar + ground storage cycle
        // - weak height gradient for vertical variation
        long gameTime = level.getGameTime();
        ensureAmbientCacheTick(gameTime);
        ensureDiurnalState(level, gameTime);

        int sampleY = Mth.clamp(y, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        int biomeBase = blendedBiomeBaseFixed(level, x, sampleY, z);
        int seaLevel = level.getSeaLevel();
        int gradient = (seaLevel - y) >> 3;

        if (isStartupPhase(level) || this.chunkBootstrapDepth > 0) {
            return clampFixed(biomeBase + gradient);
        }

        long microPacked = sampleMicroClimatePacked(level, x, sampleY, z);
        float enclosure = unpackQ8(microPacked, 0) / 255.0f;
        float solarExposure = unpackQ8(microPacked, 8) / 255.0f;
        float greenhouse = unpackQ8(microPacked, 16) / 255.0f;
        float insulation = unpackQ8(microPacked, 24) / 255.0f;
        int materialBiasFixed = unpackSigned16(microPacked, 32);

        int directSolar = Math.round(this.diurnalDirectSolarFixed * solarExposure);
        float indoorSolarDamping = 0.35f + (1.0f - enclosure) * 0.65f;
        directSolar = Math.round(directSolar * indoorSolarDamping);

        int greenhouseBonus = Math.round(GREENHOUSE_BONUS_MAX_FIXED * this.diurnalSolarIntensity * greenhouse);

        float storageCoupling = Mth.clamp(
                0.20f + (1.0f - enclosure) * 0.65f + (1.0f - insulation) * 0.15f,
                0.12f,
                1.0f
        );
        int storage = Math.round(this.diurnalGroundStorageFixed * storageCoupling);
        if (storage < 0) {
            storage = Math.round(storage * (1.0f - 0.55f * insulation * enclosure));
        }

        int materialOffset = Math.round(materialBiasFixed * enclosure);

        int ambient = biomeBase + gradient + directSolar + greenhouseBonus + storage + materialOffset;
        ambient += sampleLiquidAmbientOffsetFixed(level, x, sampleY, z, biomeBase);
        return clampFixed(ambient);
    }

    public HeatPropsResolver getPropsResolver() {
        return this.propsResolver;
    }

    public long getGameTick() {
        return this.tickCounter;
    }

    public float dt() {
        return DT;
    }

    public int epsilonFixed() {
        return EPSILON_FIXED;
    }

    public int clampFixed(int value) {
        return Mth.clamp(value, MIN_TEMP_FIXED, MAX_TEMP_FIXED);
    }

    public int mixUnderRelaxation(int currentFixed, int computedFixed) {
        int mixed = currentFixed + Math.round((computedFixed - currentFixed) * UNDER_RELAX_ALPHA);
        return clampFixed(mixed);
    }

    public static int toFixed(float celsius) {
        return Math.round(celsius * FIXED_SCALE);
    }

    public static float toCelsius(int fixed) {
        return fixed / (float) FIXED_SCALE;
    }

    public static long sectionKeyFromBlock(int x, int y, int z) {
        return SectionPos.asLong(
                SectionPos.blockToSectionCoord(x),
                SectionPos.blockToSectionCoord(y),
                SectionPos.blockToSectionCoord(z)
        );
    }

    public void ensurePersistenceLoaded(ServerLevel level) {
        if (this.persistenceLoaded) {
            return;
        }

        this.tickCounter = level.getGameTime();
        CompoundTag payload = FeatureDataStore.loadFeatureTag(level, FEATURE_KEY);
        readPersistedPayload(level, payload);

        this.persistenceLoaded = true;
        this.persistenceDirty = false;
    }

    public void flushPersistence(ServerLevel level, boolean force) {
        ensurePersistenceLoaded(level);
        if (!force && !this.persistenceDirty) {
            return;
        }

        CompoundTag payload = buildPersistedPayload(level);
        ListTag sectionList = payload.getList(TAG_SECTIONS, Tag.TAG_COMPOUND);
        if (sectionList.isEmpty()) {
            FeatureDataStore.removeFeatureTag(level, FEATURE_KEY);
        } else {
            FeatureDataStore.saveFeatureTag(level, FEATURE_KEY, payload);
        }
        this.persistenceDirty = false;
    }

    private void readPersistedPayload(ServerLevel level, CompoundTag payload) {
        this.persistedSections.clear();
        if (!payload.contains(TAG_SECTIONS, Tag.TAG_LIST)) {
            return;
        }

        int version = payload.getInt(TAG_VERSION);
        if (version > PERSISTENCE_VERSION) {
            // Unknown future schema: ignore gracefully.
            return;
        }

        ListTag sectionList = payload.getList(TAG_SECTIONS, Tag.TAG_COMPOUND);
        for (int i = 0; i < sectionList.size(); i++) {
            CompoundTag secTag = sectionList.getCompound(i);
            if (!secTag.contains(TAG_SECTION_KEY, Tag.TAG_LONG) || !secTag.contains(TAG_SECTION_TEMP, Tag.TAG_BYTE_ARRAY)) {
                continue;
            }

            long sectionKey = secTag.getLong(TAG_SECTION_KEY);
            int secY = SectionPos.y(sectionKey);
            if (secY < level.getMinSection() || secY >= level.getMaxSection()) {
                continue;
            }

            byte[] raw = secTag.getByteArray(TAG_SECTION_TEMP);
            if (raw.length != SERIALIZED_SECTION_BYTES) {
                continue;
            }
            this.persistedSections.put(sectionKey, raw);
        }
    }

    private CompoundTag buildPersistedPayload(ServerLevel level) {
        long nowTick = level.getGameTime();
        Long2ObjectOpenHashMap<byte[]> merged = new Long2ObjectOpenHashMap<>(this.persistedSections);

        for (Long2ObjectMap.Entry<HeatSection> entry : this.sections.long2ObjectEntrySet()) {
            long sectionKey = entry.getLongKey();
            HeatSection section = entry.getValue();

            SectionPos secPos = SectionPos.of(sectionKey);
            int ambient = getAmbientFixed(level, secPos.minBlockX(), secPos.minBlockY(), secPos.minBlockZ());
            if (section.isEvictable(nowTick, SECTION_EVICT_IDLE_TICKS, ambient, EPSILON_FIXED)) {
                merged.remove(sectionKey);
                continue;
            }
            merged.put(sectionKey, encodeTempArray(section.temp));
        }

        CompoundTag payload = new CompoundTag();
        payload.putInt(TAG_VERSION, PERSISTENCE_VERSION);

        ListTag sectionList = new ListTag(merged.size());
        for (Long2ObjectMap.Entry<byte[]> entry : merged.long2ObjectEntrySet()) {
            byte[] raw = entry.getValue();
            if (raw == null || raw.length != SERIALIZED_SECTION_BYTES) {
                continue;
            }
            CompoundTag secTag = new CompoundTag(2);
            secTag.putLong(TAG_SECTION_KEY, entry.getLongKey());
            secTag.putByteArray(TAG_SECTION_TEMP, raw);
            sectionList.add(secTag);
        }

        payload.put(TAG_SECTIONS, sectionList);
        return payload;
    }

    private static byte[] encodeTempArray(short[] tempArray) {
        byte[] out = new byte[SERIALIZED_SECTION_BYTES];
        for (int i = 0, j = 0; i < HeatSection.VOLUME; i++, j += 2) {
            int value = tempArray[i];
            out[j] = (byte) (value & 0xFF);
            out[j + 1] = (byte) ((value >>> 8) & 0xFF);
        }
        return out;
    }

    private static void decodeTempArray(byte[] encoded, short[] out) {
        int maxCells = Math.min(HeatSection.VOLUME, encoded.length >> 1);
        for (int i = 0, j = 0; i < maxCells; i++, j += 2) {
            int lo = encoded[j] & 0xFF;
            int hi = encoded[j + 1] & 0xFF;
            out[i] = (short) ((hi << 8) | lo);
        }
    }

    private void markPersistenceDirty() {
        this.persistenceDirty = true;
    }

    private void refreshDataModelIfNeeded() {
        int revision = HeatDataRegistry.revision();
        if (this.appliedModelRevision == revision) {
            return;
        }

        this.appliedModelRevision = revision;
        this.propsResolver.clearCache();
        this.phaseChangeManager.clear();
        this.ignitionManager.clear();

        this.ambientCacheTick = Long.MIN_VALUE;
        this.biomeAmbientByQuart.clear();
        this.microClimateByQuart.clear();

        this.waterInertiaOffsetByQuart.clear();
        this.waterInertiaFluidIdByQuart.clear();
        this.waterInertiaUpdatedTickByQuart.clear();
        this.waterInertiaTouchedTickByQuart.clear();
        this.waterInertiaEvictTick = Long.MIN_VALUE;
    }

    private void ensureAmbientCacheTick(long gameTime) {
        if (this.ambientCacheTick == gameTime) {
            return;
        }
        this.ambientCacheTick = gameTime;
        this.biomeAmbientByQuart.clear();
        this.microClimateByQuart.clear();
    }

    HeatModelData.LiquidProfile resolveLiquidProfile(BlockState state) {
        return HeatDataRegistry.model().liquidModel().resolveProfile(state.getFluidState());
    }

    boolean isModeledLiquidState(BlockState state) {
        return resolveLiquidProfile(state) != null;
    }

    private int sampleLiquidAmbientOffsetFixed(ServerLevel level, int x, int y, int z, int biomeBaseFixed) {
        this.waterProbePos.set(x, y, z);
        if (!level.hasChunkAt(this.waterProbePos)) {
            return 0;
        }
        BlockState here = level.getBlockState(this.waterProbePos);
        HeatModelData.LiquidProfile liquidProfile = resolveLiquidProfile(here);
        if (liquidProfile == null) {
            return 0;
        }
        Fluid fluidType = here.getFluidState().getType();
        int fluidId = BuiltInRegistries.FLUID.getId(fluidType);

        long quartKey = quartCacheKeyFromBlock(x, y, z);
        long gameTime = level.getGameTime();

        int cachedFluidId = this.waterInertiaFluidIdByQuart.get(quartKey);
        int current = this.waterInertiaOffsetByQuart.get(quartKey);
        if (current == Integer.MIN_VALUE || cachedFluidId != fluidId) {
            current = liquidProfile.baseCoolOffsetFixed();
            this.waterInertiaFluidIdByQuart.put(quartKey, fluidId);
            this.waterInertiaOffsetByQuart.put(quartKey, current);
            this.waterInertiaUpdatedTickByQuart.put(quartKey, Long.MIN_VALUE);
        }

        long updatedTick = this.waterInertiaUpdatedTickByQuart.get(quartKey);
        if (updatedTick != gameTime) {
            int target = computeLiquidTargetOffsetFixed(level, x, y, z, biomeBaseFixed, liquidProfile, fluidType);
            float rate = target > current ? liquidProfile.inertiaWarmRate() : liquidProfile.inertiaCoolRate();
            int next = current + Math.round((target - current) * rate);
            if (Math.abs(target - next) <= EPSILON_FIXED) {
                next = target;
            }
            current = Mth.clamp(next, liquidProfile.targetMinFixed(), liquidProfile.targetMaxFixed());
            this.waterInertiaOffsetByQuart.put(quartKey, current);
            this.waterInertiaUpdatedTickByQuart.put(quartKey, gameTime);
        }

        this.waterInertiaTouchedTickByQuart.put(quartKey, gameTime);
        return current;
    }

    private int computeLiquidTargetOffsetFixed(
            ServerLevel level,
            int x,
            int y,
            int z,
            int biomeBaseFixed,
            HeatModelData.LiquidProfile liquidProfile,
            Fluid fluidType
    ) {
        int maxY = level.getMaxBuildHeight();
        int depthFromSurface = 0;
        int surfaceY = y;

        for (int dy = 1; dy <= liquidProfile.surfaceScanSteps(); dy++) {
            int sy = y + dy;
            if (sy >= maxY) {
                break;
            }
            this.waterProbePos.set(x, sy, z);
            if (!level.hasChunkAt(this.waterProbePos)) {
                break;
            }
            BlockState upState = level.getBlockState(this.waterProbePos);
            Fluid upFluid = upState.getFluidState().getType();
            if (upState.getFluidState().isEmpty() || upFluid != fluidType) {
                surfaceY = sy - 1;
                break;
            }
            depthFromSurface = dy;
            surfaceY = sy;
        }

        int surfaceSolarGain = 0;
        int aboveY = surfaceY + 1;
        if (aboveY < maxY) {
            this.waterProbePos.set(x, aboveY, z);
            if (level.hasChunkAt(this.waterProbePos)) {
                BlockState above = level.getBlockState(this.waterProbePos);
                if (!isModeledLiquidState(above) && above.getFluidState().isEmpty()) {
                    float skyExposure = level.canSeeSky(this.waterProbePos) ? 1.0f : 0.35f;
                    float depthAttenuation = Mth.clamp(1.0f - depthFromSurface / 14.0f, 0.0f, 1.0f);
                    surfaceSolarGain = Math.round(
                            liquidProfile.surfaceSolarMaxFixed() * this.diurnalSolarIntensity * skyExposure * depthAttenuation
                    );
                }
            }
        }

        int seaDepth = Math.max(0, level.getSeaLevel() - y);
        int depthMetric = seaDepth + (depthFromSurface >> 1);
        int deepCooling = -Math.min(liquidProfile.deepCoolMaxFixed(), depthMetric * liquidProfile.deepCoolPerBlockFixed());

        int climateComp = Math.round((biomeBaseFixed - toFixed(14.0f)) * 0.08f);
        int sourceBoost = sampleLiquidHeatSourceBoostFixed(level, x, y, z, liquidProfile);

        int target = liquidProfile.baseCoolOffsetFixed() + climateComp + surfaceSolarGain + deepCooling + sourceBoost;
        return Mth.clamp(target, liquidProfile.targetMinFixed(), liquidProfile.targetMaxFixed());
    }

    private int sampleLiquidHeatSourceBoostFixed(
            ServerLevel level,
            int x,
            int y,
            int z,
            HeatModelData.LiquidProfile liquidProfile
    ) {
        BlockPos.MutableBlockPos nearPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos farPos = new BlockPos.MutableBlockPos();
        int boost = 0;

        for (int d = 0; d < 6; d++) {
            int nx = x + FACE_DX[d];
            int ny = y + FACE_DY[d];
            int nz = z + FACE_DZ[d];
            nearPos.set(nx, ny, nz);
            if (!level.hasChunkAt(nearPos)) {
                continue;
            }

            BlockState near = level.getBlockState(nearPos);
            int direct = liquidHeatingSourceContributionFixed(liquidProfile, near);
            boost += direct;
            if (direct > 0) {
                continue;
            }

            if (!near.isAir() && !isModeledLiquidState(near)) {
                continue;
            }

            int fx = nx + FACE_DX[d];
            int fy = ny + FACE_DY[d];
            int fz = nz + FACE_DZ[d];
            farPos.set(fx, fy, fz);
            if (!level.hasChunkAt(farPos)) {
                continue;
            }

            int indirect = liquidHeatingSourceContributionFixed(liquidProfile, level.getBlockState(farPos));
            if (indirect > 0) {
                boost += Math.round(indirect * 0.45f);
            }
        }

        return Mth.clamp(boost, 0, liquidProfile.heatSourceMaxFixed());
    }

    int liquidHeatingSourceContributionFixed(HeatModelData.LiquidProfile liquidProfile, BlockState state) {
        for (HeatModelData.LiquidSourceRule rule : liquidProfile.sourceRules()) {
            if (rule.matches(state)) {
                return rule.contributionFixed();
            }
        }
        return 0;
    }

    int liquidHeatingSourceClampFixed(HeatModelData.LiquidProfile liquidProfile) {
        return liquidProfile.heatSourceMaxFixed();
    }

    private static long quartCacheKeyFromBlock(int x, int y, int z) {
        return BlockPos.asLong(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
    }

    private void evictWaterInertiaCache(long gameTime) {
        HeatModelData.LiquidProfile defaultLiquid = HeatDataRegistry.model().liquidModel().defaultLiquid();
        if (this.waterInertiaEvictTick != Long.MIN_VALUE
                && gameTime - this.waterInertiaEvictTick < defaultLiquid.inertiaEvictIntervalTicks()) {
            return;
        }
        this.waterInertiaEvictTick = gameTime;

        LongIterator it = this.waterInertiaTouchedTickByQuart.keySet().iterator();
        while (it.hasNext()) {
            long key = it.nextLong();
            long touched = this.waterInertiaTouchedTickByQuart.get(key);
            if (gameTime - touched <= defaultLiquid.inertiaEvictIdleTicks()) {
                continue;
            }
            it.remove();
            this.waterInertiaOffsetByQuart.remove(key);
            this.waterInertiaFluidIdByQuart.remove(key);
            this.waterInertiaUpdatedTickByQuart.remove(key);
        }
    }

    private int blendedBiomeBaseFixed(ServerLevel level, int x, int y, int z) {
        int shiftedX = x - 2;
        int shiftedY = y - 2;
        int shiftedZ = z - 2;

        int qx0 = Math.floorDiv(shiftedX, 4);
        int qy0 = Math.floorDiv(shiftedY, 4);
        int qz0 = Math.floorDiv(shiftedZ, 4);
        int qx1 = qx0 + 1;
        int qy1 = qy0 + 1;
        int qz1 = qz0 + 1;

        int fx = Math.floorMod(shiftedX, 4);
        int fy = Math.floorMod(shiftedY, 4);
        int fz = Math.floorMod(shiftedZ, 4);
        int wx0 = 4 - fx;
        int wy0 = 4 - fy;
        int wz0 = 4 - fz;
        int wx1 = fx;
        int wy1 = fy;
        int wz1 = fz;

        long sum = 0L;
        sum += (long) sampleBiomeBaseFixedAtQuart(level, qx0, qy0, qz0) * wx0 * wy0 * wz0;
        sum += (long) sampleBiomeBaseFixedAtQuart(level, qx1, qy0, qz0) * wx1 * wy0 * wz0;
        sum += (long) sampleBiomeBaseFixedAtQuart(level, qx0, qy1, qz0) * wx0 * wy1 * wz0;
        sum += (long) sampleBiomeBaseFixedAtQuart(level, qx1, qy1, qz0) * wx1 * wy1 * wz0;
        sum += (long) sampleBiomeBaseFixedAtQuart(level, qx0, qy0, qz1) * wx0 * wy0 * wz1;
        sum += (long) sampleBiomeBaseFixedAtQuart(level, qx1, qy0, qz1) * wx1 * wy0 * wz1;
        sum += (long) sampleBiomeBaseFixedAtQuart(level, qx0, qy1, qz1) * wx0 * wy1 * wz1;
        sum += (long) sampleBiomeBaseFixedAtQuart(level, qx1, qy1, qz1) * wx1 * wy1 * wz1;
        return (int) Math.round(sum / 64.0d);
    }

    private int sampleBiomeBaseFixedAtQuart(ServerLevel level, int quartX, int quartY, int quartZ) {
        int sampleX = QuartPos.toBlock(quartX) + 2;
        int sampleY = QuartPos.toBlock(quartY) + 2;
        int sampleZ = QuartPos.toBlock(quartZ) + 2;
        int clampedY = Mth.clamp(sampleY, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        long cacheKey = BlockPos.asLong(sampleX, clampedY, sampleZ);

        int cached = this.biomeAmbientByQuart.get(cacheKey);
        if (cached != Integer.MIN_VALUE) {
            return cached;
        }

        this.biomeProbePos.set(sampleX, clampedY, sampleZ);
        int resolved = BiomeAmbientModel.resolveAmbientFixed(level, level.getBiome(this.biomeProbePos));
        this.biomeAmbientByQuart.put(cacheKey, resolved);
        return resolved;
    }

    private void ensureDiurnalState(ServerLevel level, long gameTime) {
        if (this.diurnalStateTick == gameTime) {
            return;
        }
        this.diurnalStateTick = gameTime;

        if (!level.dimensionType().hasSkyLight()) {
            this.diurnalSolarIntensity = 0.0f;
            this.diurnalDirectSolarFixed = 0;
            this.diurnalGroundStorageFixed = 0;
            return;
        }

        float solar = computeSolarIntensity(level);
        this.diurnalSolarIntensity = solar;
        this.diurnalDirectSolarFixed = Math.round(DIURNAL_DIRECT_MAX_FIXED * solar);

        int storageTarget = Math.round(Mth.lerp(solar, DIURNAL_STORAGE_NIGHT_TARGET_FIXED, DIURNAL_STORAGE_DAY_TARGET_FIXED));
        float rate = solar > 0.04f ? DIURNAL_STORAGE_CHARGE_RATE : DIURNAL_STORAGE_RELEASE_RATE;
        int next = this.diurnalGroundStorageFixed + Math.round((storageTarget - this.diurnalGroundStorageFixed) * rate);
        this.diurnalGroundStorageFixed = Mth.clamp(next, -DIURNAL_STORAGE_LIMIT_FIXED, DIURNAL_STORAGE_LIMIT_FIXED);
    }

    private static float computeSolarIntensity(ServerLevel level) {
        long dayTime = level.getDayTime() % 24000L;
        if (dayTime < 0) {
            dayTime += 24000L;
        }
        float angle = (float) (dayTime * (Math.PI * 2.0d / 24000.0d));
        float solar = Math.max(0.0f, (float) Math.sin(angle));
        if (level.isThundering()) {
            solar *= 0.35f;
        } else if (level.isRaining()) {
            solar *= 0.60f;
        }
        return Mth.clamp(solar, 0.0f, 1.0f);
    }

    private long sampleMicroClimatePacked(ServerLevel level, int x, int y, int z) {
        int qx = QuartPos.fromBlock(x);
        int qy = QuartPos.fromBlock(y);
        int qz = QuartPos.fromBlock(z);
        int sampleX = QuartPos.toBlock(qx) + 2;
        int sampleY = Mth.clamp(QuartPos.toBlock(qy) + 2, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        int sampleZ = QuartPos.toBlock(qz) + 2;
        long cacheKey = BlockPos.asLong(sampleX, sampleY, sampleZ);

        long cached = this.microClimateByQuart.get(cacheKey);
        if (cached != Long.MIN_VALUE) {
            return cached;
        }

        long computed = computeMicroClimatePacked(level, sampleX, sampleY, sampleZ);
        this.microClimateByQuart.put(cacheKey, computed);
        return computed;
    }

    private static boolean isStartupPhase(ServerLevel level) {
        return level.getServer().getTickCount() < STARTUP_MICROCLIMATE_SKIP_TICKS;
    }

    private long computeMicroClimatePacked(ServerLevel level, int x, int y, int z) {
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();

        int enclosureAccum = 0;
        int barrierHits = 0;
        int insulationAccum = 0;
        int materialBiasAccum = 0;

        for (int d = 0; d < 6; d++) {
            for (int step = 1; step <= ENCLOSURE_SCAN_STEPS; step++) {
                int sx = x + FACE_DX[d] * step;
                int sy = y + FACE_DY[d] * step;
                int sz = z + FACE_DZ[d] * step;
                if (sy < minY || sy >= maxY) {
                    break;
                }

                this.biomeProbePos.set(sx, sy, sz);
                if (!level.hasChunkAt(this.biomeProbePos)) {
                    break;
                }

                BlockState state = level.getBlockState(this.biomeProbePos);
                if (!isBarrierState(state)) {
                    continue;
                }

                int enclosureScore = ((ENCLOSURE_SCAN_STEPS - step + 1) * Q8_MAX) / ENCLOSURE_SCAN_STEPS;
                enclosureAccum += enclosureScore;
                barrierHits++;
                insulationAccum += insulationScoreQ8(state);
                materialBiasAccum += materialBiasFixed(state);
                break;
            }
        }

        int roofTransmissionQ8 = Q8_MAX;
        int glassRoofQ8 = 0;
        boolean foundRoof = false;
        for (int dy = 1; dy <= ROOF_SCAN_STEPS; dy++) {
            int sy = y + dy;
            if (sy >= maxY) {
                break;
            }
            this.biomeProbePos.set(x, sy, z);
            if (!level.hasChunkAt(this.biomeProbePos)) {
                break;
            }
            BlockState roof = level.getBlockState(this.biomeProbePos);
            if (roof.isAir()) {
                continue;
            }
            foundRoof = true;
            roofTransmissionQ8 = roofTransmissionQ8(roof);
            if (isGlassLike(roof)) {
                glassRoofQ8 = roofTransmissionQ8;
            }
            break;
        }
        if (!foundRoof) {
            roofTransmissionQ8 = Q8_MAX;
        }

        int enclosureQ8 = Mth.clamp(Math.round(enclosureAccum / 6.0f), 0, Q8_MAX);
        int insulationQ8 = barrierHits <= 0 ? 0 : Mth.clamp(Math.round(insulationAccum / (float) barrierHits), 0, Q8_MAX);
        int materialBias = barrierHits <= 0 ? 0 : Math.round(materialBiasAccum / (float) barrierHits);
        int solarExposureQ8 = roofTransmissionQ8;
        int greenhouseQ8 = 0;
        if (enclosureQ8 >= 96 && glassRoofQ8 > 0) {
            greenhouseQ8 = Math.round((enclosureQ8 / 255.0f) * (glassRoofQ8 / 255.0f) * Q8_MAX);
        }

        return packMicroClimate(enclosureQ8, solarExposureQ8, greenhouseQ8, insulationQ8, materialBias);
    }

    private static boolean isBarrierState(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (!state.getFluidState().isEmpty()) {
            return true;
        }
        return state.canOcclude() || state.is(BlockTags.LEAVES) || isGlassLike(state);
    }

    private static int roofTransmissionQ8(BlockState state) {
        if (isGlassLike(state)) {
            return 200;
        }
        if (state.is(BlockTags.LEAVES)) {
            return 120;
        }
        return 0;
    }

    private static int insulationScoreQ8(BlockState state) {
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS) || state.is(BlockTags.WOOL)) {
            return 230;
        }
        if (isGlassLike(state)) {
            return 70;
        }
        if (state.is(BlockTags.LEAVES)) {
            return 140;
        }
        if (state.is(BlockTags.ICE) || state.is(Blocks.WATER)) {
            return 96;
        }
        // Default: stone-like envelope.
        return 128;
    }

    private static int materialBiasFixed(BlockState state) {
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS) || state.is(BlockTags.WOOL)) {
            return toFixed(1.4f);
        }
        if (isGlassLike(state)) {
            return toFixed(0.2f);
        }
        if (state.is(BlockTags.LEAVES)) {
            return toFixed(0.5f);
        }
        if (state.is(BlockTags.ICE) || state.is(Blocks.WATER)) {
            return toFixed(-0.4f);
        }
        // Default: stone-like walls tend to feel a bit cooler.
        return toFixed(-0.8f);
    }

    private static boolean isGlassLike(BlockState state) {
        if (state.is(Blocks.GLASS_PANE) || state.is(Blocks.TINTED_GLASS) || state.is(Blocks.GLASS)) {
            return true;
        }
        String path = state.getBlock().builtInRegistryHolder().key().location().getPath();
        return path.contains("glass") || path.contains("pane");
    }

    private static long packMicroClimate(
            int enclosureQ8,
            int solarExposureQ8,
            int greenhouseQ8,
            int insulationQ8,
            int materialBiasFixed
    ) {
        long packed = (enclosureQ8 & 0xFFL);
        packed |= (solarExposureQ8 & 0xFFL) << 8;
        packed |= (greenhouseQ8 & 0xFFL) << 16;
        packed |= (insulationQ8 & 0xFFL) << 24;
        packed |= ((long) materialBiasFixed & 0xFFFFL) << 32;
        return packed;
    }

    private static int unpackQ8(long packed, int shift) {
        return (int) ((packed >>> shift) & 0xFFL);
    }

    private static int unpackSigned16(long packed, int shift) {
        return (short) ((packed >>> shift) & 0xFFFFL);
    }

    private HeatProps resolvePropsAt(
            ServerLevel level,
            int x,
            int y,
            int z,
            BlockState state,
            BlockPos.MutableBlockPos scratchBelow
    ) {
        if (!state.is(Blocks.FIRE)) {
            return this.propsResolver.resolve(state);
        }

        boolean soulBase = false;
        if (y > level.getMinBuildHeight()) {
            BlockPos.MutableBlockPos belowPos = scratchBelow != null ? scratchBelow : new BlockPos.MutableBlockPos();
            belowPos.set(x, y - 1, z);
            if (level.hasChunkAt(belowPos)) {
                soulBase = HeatPropsResolver.isSoulFireBase(level.getBlockState(belowPos));
            }
        }
        return this.propsResolver.resolve(state, soulBase);
    }

    private boolean isThermallyRelevantState(BlockState state) {
        if (state.hasProperty(BlockStateProperties.LIT)) {
            return true;
        }
        return this.propsResolver.resolve(state).isPersistentSource();
    }

    private void applyHeatState(ServerLevel level, int x, int y, int z, HeatProps props) {
        if (props.hasTarget()) {
            setTemperatureFixed(level, x, y, z, props.targetFixed);
            return;
        }
        if (props.generationQ != 0.0f) {
            int current = getTemperatureFixed(level, x, y, z);
            setTemperatureFixed(level, x, y, z, clampFixed(current + Math.round(props.generationQ)));
        }
    }

    private void markDirtyCell(ServerLevel level, int x, int y, int z, boolean createIfMissing) {
        if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
            return;
        }
        long key = sectionKeyFromBlock(x, y, z);
        if (createIfMissing) {
            int secX = SectionPos.x(key);
            int secZ = SectionPos.z(key);
            if (!level.hasChunk(secX, secZ)) {
                return;
            }
        }
        HeatSection section = getSection(level, key, x, y, z, createIfMissing);
        if (section == null) {
            return;
        }
        section.markDirty(x & 15, y & 15, z & 15);
        this.activeSections.enqueue(key, this.tickCounter);
    }

    private HeatSection getSection(
            ServerLevel level,
            long sectionKey,
            int sampleX,
            int sampleY,
            int sampleZ,
            boolean createIfAbsent
    ) {
        HeatSection existing = this.sections.get(sectionKey);
        if (existing != null) {
            return existing;
        }

        // Lazy restore persisted section only when corresponding chunk is loaded.
        byte[] persisted = this.persistedSections.get(sectionKey);
        if (persisted != null) {
            int secX = SectionPos.x(sectionKey);
            int secZ = SectionPos.z(sectionKey);
            if (level.hasChunk(secX, secZ) || createIfAbsent) {
                HeatSection restored = createBlankSection(level, sectionKey, sampleX, sampleY, sampleZ);
                decodeTempArray(persisted, restored.temp);
                System.arraycopy(restored.temp, 0, restored.tempNext, 0, HeatSection.VOLUME);
                this.persistedSections.remove(sectionKey);
                this.sections.put(sectionKey, restored);
                return restored;
            }
        }

        if (!createIfAbsent) {
            return null;
        }

        HeatSection created = createBlankSection(level, sectionKey, sampleX, sampleY, sampleZ);
        this.sections.put(sectionKey, created);
        return created;
    }

    private HeatSection createBlankSection(ServerLevel level, long sectionKey, int sampleX, int sampleY, int sampleZ) {
        int ambient = getAmbientFixed(level, sampleX, sampleY, sampleZ);
        int phase = (int) (sectionKey ^ (sectionKey >>> 17) ^ (sectionKey >>> 33));
        return new HeatSection(ambient, phase);
    }
}
