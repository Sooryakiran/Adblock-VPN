# Adblock VPN (Android) — P0

A local-only VPN that observes DNS requests on the device and shows per-domain
counts. No traffic leaves the device except the same DNS queries Android would
have made anyway (forwarded to `1.1.1.1`).

## How it works

1. `LocalVpnService` brings up a tiny TUN owning `10.7.0.0/24` and registers
   `10.7.0.2` as the system DNS server. Only `10.7.0.2/32` is routed through
   the TUN — everything else uses the normal network (split tunnel), so app
   connectivity is unaffected.
2. Apps using the system resolver send DNS queries to `10.7.0.2`. Those
   IPv4/UDP packets land in the TUN.
3. For each packet, the QNAME is parsed and `count++` / `lastSeen` is upserted
   into Room. The original query is forwarded verbatim to `1.1.1.1` over a
   `DatagramSocket` that's been `protect()`-ed (so the forwarded packet does
   NOT loop back through the TUN). The response is wrapped in an IPv4/UDP
   envelope and written back to the originating app.
4. Compose UI subscribes to the Room flow and lists domains by frequency.

## Limitations (known, acceptable for P0)

- IPv4 only.
- Apps that bypass the system resolver are invisible:
  - Browsers using DoH/DoT (Chrome's "Secure DNS", Firefox DoH).
  - Apps using hardcoded resolvers (8.8.8.8, etc.) over UDP/53 directly — they
    still go through the regular network because we don't capture them.
  - "Private DNS" enabled in system settings (DoT) — disable it for testing.
- Not a content filter yet; blocking comes next (just synthesize an
  `NXDOMAIN` / `0.0.0.0` answer and skip upstream forwarding).

## Build

You'll need Android Studio (Koala / 2024.1+) or the Android SDK + a recent JDK
17 and Gradle 8.9.

```sh
# From the project root, generate the Gradle wrapper jar (one-time):
gradle wrapper

# Then:
./gradlew :app:installDebug
```

Or open the folder in Android Studio and Run.

## Use

1. Launch the app.
2. Tap **Start VPN** and accept the system VPN consent dialog.
3. Browse / use any app. Domains will appear and counters tick up.
4. Tap **Stop VPN** (or use the notification action) to tear down the tunnel.

## Next (P1+)

- Per-domain block toggle: synthesize a refused/zero answer instead of
  forwarding upstream.
- Wildcard / regex / hosts-file rules.
- Charts (requests over time, top-N).
- IPv6, TCP/53 fallback, EDNS pad.
