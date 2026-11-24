# Rate limiting

Native auth POST routes consume an HTTP IP quota before bearer authentication. Controller login,
register and reset methods consume a separate canonical email quota before calling the use case. Recovery
declares SKIP_VOID on its controller annotation. The aspect checks email quota before the use-case transaction. Exhausted recovery
email quota returns 202 and sends no mail. HTTP exhaustion returns 429 and Retry-After. Limiter
store failures return 503 on covered routes and operations.

Configuration uses Spring field @Value. Profiles document all initial bandwidths as
capacity/ISO-duration, with comma-separated bandwidths in one atomic bucket. HTTP action and session
defaults are provisional 60/minute and 120/minute. Measure client retries and NAT traffic before
production rollout. These are greedy token buckets. A capacity is available immediately, then
tokens refill continuously.

Production requires TRELLIS_RATE_LIMIT_KEY_SECRET, TRELLIS_RATE_LIMIT_NAMESPACE and a Redis URI
in TRELLIS_REDIS_URI. Use a dedicated high-entropy HMAC secret shared by serving instances.
The prod store defaults to Redis and rejects memory. Dev/test use bounded memory storage, where
eviction and restart reset quota.

Set TRELLIS_RATE_LIMIT_TRUSTED_PROXIES to comma-separated proxy CIDRs only for proxies controlled by
the deployment. Keep server.forward-headers-strategy=none and disable servlet-container remote-IP
rewriting. The limiter must inspect the real TCP peer before trusting X-Forwarded-For.
The ingress must overwrite untrusted incoming headers and restrict backend origin access.
With an empty trust list the limiter uses the peer address.

## Release and rollback

1. Deploy compatible binary versions with exactly the same active policy values, revision,
   namespace and secret version. Include non-HTTP adapters when inventorying callers.
2. For a policy, namespace or secret change, stop admission to affected operations at every
   adapter. Drain in-flight requests and record the last consume time.
3. Wait the longest full-refill interval of the active bandwidths, measured from completion of
   draining. The initial recovery bandwidth needs one hour. Preserve the old Redis keys and secret
   version through the rollback window.
4. Apply the new configuration to every instance, confirm readiness and version equality, then
   resume admission. Record maintenance duration and configuration versions.
5. Before admission resumes, rollback can restore all old configuration. After admission resumes,
   prefer a compatible binary rollback using the current active configuration. Changing namespace,
   revision or secret again requires repeating the drain and full-refill wait for the active policy.

The initial migration from in-memory or legacy raw keys also requires this cutover for existing
operation quotas. Do not serve two namespaces simultaneously during a configuration transition.

## Operations

Monitor trellis.rate.limit counters by policy, outcome and store. Exceeded recovery email quota
appears under identity.forgot/exceeded. Subject values never become metric labels. Monitor Redis
latency, key count, memory/eviction and 429/503 response rates independently. A Redis outage opens
a five-second circuit after a failed consume, with a two-second command timeout. Shared circuit
state protects the store across covered policies. Management remains available through its
existing security chain.

Run the JDK 21 Gradle build before deployment. Testcontainers verifies Redis concurrent quota and
restart persistence. Production load, BFF forwarding, mixed-version deployment and cutover drills
must be run against the deployment topology before rollout.
