# Plugin claim creation API

Depend on SaltyClaims and compile against its JAR as a compile-only dependency. Do not bundle a second copy of SaltyClaims classes in your plugin.

For a Bukkit plugin.yml integration:

```yaml
depend: [SaltyClaims]
```

Obtain the service after SaltyClaims enables. Service absence means the dependency did not initialize successfully; do not provision an unprotected island as though creation succeeded.

```java
import org.bukkit.Bukkit;
import org.saltymc.api.SaltyClaimsApi;
import org.saltymc.claim.Bounds;

SaltyClaimsApi claims = Bukkit.getServicesManager().load(SaltyClaimsApi.class);
if (claims == null) {
    throw new IllegalStateException("SaltyClaims API is unavailable");
}

// Execute this on the main server thread. UUIDs may identify an offline owner.
// Bounds are inclusive: this example covers 100 x 100 blocks, full world height.
claims.createClaim(ownerUuid, worldUuid, new Bounds(0, 99, 0, 99, null, null))
    .whenComplete((claim, failure) -> {
        if (failure != null) {
            // Abort or defer island provisioning; log/handle the cause.
            getLogger().warning("Could not create island protection: " + failure);
            return;
        }
        // Save claim.id() with your island data. Protection is now active.
        getLogger().info("Island claim saved: " + claim.id());
    });
```

Use an async integration's own scheduling adapter or Bukkit scheduler to enter the server thread before calling. Never block the server thread with join/get; the save finishes asynchronously and publishes on that same server thread. Successful callbacks run only after SQLite persistence and state/index publication. Rejected requests complete exceptionally. On disable, pending calls fail instead of waiting indefinitely, and the service is unregistered. If shutdown interrupts publication, a failed call may already have committed to disk; callers must reconcile island provisioning after restart. Cancelling a derived future does not cancel an accepted save.

The API creates parent claims only. It works with server-manages-claims either true or false. It bypasses player claim-block budgets, maximum-root counts, and the world's player claiming switch. It still requires a loaded world, full-height valid bounds, the configured minimum width and maximum area, the chunk cap, a location within the world border, and no conflicting parent claim. Normal configured claim flags and owner-derived access apply. It cannot overwrite an existing claim or silently queue conflicting changes. Busy, unavailable, validation and storage errors are returned as exceptional completion; wait until the previous mutation finishes before retrying a busy response.

API calls use the same global mutation reservation and storage worker as commands. Claim creation is audited in the server console. Claims persist through restarts like tool-created claims. Staff retain administrative claim tools; this API currently exposes creation only.
