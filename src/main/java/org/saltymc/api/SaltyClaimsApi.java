package org.saltymc.api;

import org.saltymc.claim.Bounds;
import org.saltymc.claim.Claim;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Obtain from Bukkit's ServicesManager using SaltyClaimsApi.class. */
public interface SaltyClaimsApi {
    /**
     * Create a full-height parent claim for an online or offline player. Call on the server thread.
     * Success completes on the server thread after durable storage and protection publication.
     * Failure completes exceptionally (invalid bounds/world, overlap, limits, busy/unavailable storage, or disable).
     * Bypasses player allowances, root counts and player claiming switches in either mode.
     * Geometry/world-border limits and normal claim defaults still apply. Completion callbacks must not block.
     * An accepted save cannot be cancelled; a disable failure may have committed to disk before shutdown.
     */
    CompletionStage<Claim> createClaim(UUID owner,UUID world,Bounds bounds);
}
