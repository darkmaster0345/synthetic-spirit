# Synthetic Spirit - DNS Shield

A simple Android app in Kotlin that blocks adult content using a local DNS sinkhole via the `VpnService` API.

## Features
- **O(1) Blocking**: Uses a Bloom Filter and an LRU cache for near-instantaneous domain lookups, ensuring no performance impact.
- **Local DNS Interception**: Intercepts DNS requests (port 53) without routing all traffic through the VPN.
- **NXDOMAIN Sinkhole**: Returns an `NXDOMAIN` response for any domain in the blocklist.
- **Customizable Blocklist**: Add or remove domains in `app/src/main/assets/blocked_domains.txt`.
- **Minimalist UI**: Simple toggle to start/stop the protection.

## How it works
The app uses the `VpnService` to create a virtual network interface. It sets the DNS server of the system to a local dummy address and adds a route to ensure only DNS traffic is captured by the VPN.

The `DnsVpnService` then:
1. Reads raw IP packets from the TUN interface.
2. Extracts UDP/DNS queries.
3. Checks the requested domain against an in-memory Bloom Filter (an O(1) operation).
4. If the domain is not in the filter, it's forwarded to an upstream DNS server (`8.8.8.8`).
5. If the filter reports a potential match, a final check is done against the database.
6. If the domain is confirmed to be blocked, the app constructs a DNS response with `RCODE 3` (NXDOMAIN) and sends it back to the user's device.

## ⚡ High-Performance Architecture
Synthetic Spirit is optimized for speed and battery life:
- **LruCache:** Instant resolution for frequent queries.
- **Bloom Filter:** O(1) probabilistic filtering to minimize database hits.
- **Room Persistence:** Indexed SQLite lookups for the 200k+ domain database.

## Development Setup
1. Open this project in Android Studio.
2. Build and run on an Android device (API 26+).
3. Grant the VPN permission when prompted.

## FOSS (Free and Open Source Software)
This project is 100% Free and Open Source. It contains no trackers, no analytics, and no proprietary dependencies.

## Why Synthetic Spirit?

While services like Cloudflare or AdGuard Family are excellent, **Synthetic Spirit** solves specific problems that Cloud-based DNS cannot:

* **Zero Latency:** Filtering happens locally on your device. There is no "round-trip" delay to a remote server, making your web browsing feel snappier.
* **Total Privacy:** Your browsing history stays on your phone. You don't have to trust a third-party company with a "digital paper trail" of every site you visit.
* **Bloom Filter Efficiency:** We use advanced probabilistic data structures (Bloom Filters) to check millions of blocked domains instantly with near-zero battery impact.
* **Network Independence:** Unlike system DNS settings, Synthetic Spirit's local VPN tunnel protects you automatically across 5G, LTE, and Public Wi-Fi without manual reconfiguration.
* **ISP Stealth:** Prevents "Transparent DNS Proxying" where ISPs hijack and monitor your DNS requests, even when using "Private DNS" settings.

## License
Distributed under the MIT License. See `LICENSE` for more information.

## Project Structure
- `app/src/main/kotlin/.../DnsVpnService.kt`: Core VPN and DNS logic.
- `app/src/main/kotlin/.../MainActivity.kt`: User interface.
- `app/src/main/assets/blocked_domains.txt`: List of domains to block.
