package link.botwmcs.core.net.neb;

import net.minecraft.resources.ResourceLocation;

/**
 * One encoded core payload frame in NEB aggregation buffer.
 */
public record CoreNebFrame(ResourceLocation type, byte[] data) {
}

