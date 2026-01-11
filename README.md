# Synthetic Spirit - DNS Shield

A simple Android app in Kotlin that blocks adult content using a local DNS sinkhole via the `VpnService` API.

## Features
- **O(1) Blocking**: Uses a Bloom Filter and an LRU cache for near-instantaneous domain lookups, ensuring no performance impact.
- **Local DNS Interception**: Intercepts DNS requests (port 53) without routing all traffic through the VPN.
- **NXDOMAIN Sinkhole**: Returns an `NXDOMAIN` response for any domain in the blocklist.
- **Customizable Blocklist**: Add or remove domains in `app/src/main/assets/blocked_domains.txt`.
- **User Whitelist**: Easily whitelist domains from the blocklist through the app's UI.
- **Minimalist UI**: Simple toggle to start/stop the protection.

## How it works
The app uses the `VpnService` to create a virtual network interface. It sets the DNS server of the system to a local dummy address and adds a route to ensure only DNS traffic is captured by the VPN.

The `DnsVpnService` then:
1. Checks if the requested domain is in the user's whitelist.
2. If not whitelisted, it reads raw IP packets from the TUN interface.
3. Extracts UDP/DNS queries.
4. Checks the requested domain against an in-memory Bloom Filter (an O(1) operation).
5. If the domain is not in the filter, it's forwarded to an upstream DNS server (`8.8.8.8`).
6. If the filter reports a potential match, a final check is done against the database.
7. If the domain is confirmed to be blocked, the app constructs a DNS response with `RCODE 3` (NXDOMAIN) and sends it back to the user's device.

## ⚡ High-Performance Architecture
Synthetic Spirit is optimized for speed and battery life:
- **LruCache:** Instant resolution for frequent queries.
- **Bloom Filter:** O(1) probabilistic filtering to minimize database hits.
- **Room Persistence:** Indexed SQLite lookups for the 200k+ domain database.
- **SharedPreferences Whitelist:** Fast O(1) access to user-whitelisted domains.

## Development Setup
1. Open this project in Android Studio.
2. Build and run on an Android device (API 26+).
3. Grant the VPN permission when prompted.

## FOSS (Free and Open Source Software)
This project is 100% Free and Open Source. It contains no trackers, no analytics, and no proprietary dependencies.

## License
Distributed under the MIT License. See `LICENSE` for more information.

## Project Structure
- `app/src/main/kotlin/.../DnsVpnService.kt`: Core VPN and DNS logic.
- `app/src/main/kotlin/.../MainActivity.kt`: User interface.
- `app/src/main/kotlin/.../BlocklistManagerScreen.kt`: UI for managing the blocklist.
- `app/src/main/kotlin/.../BlocklistManagerViewModel.kt`: ViewModel for the `BlocklistManagerScreen`.
- `app/src/main/kotlin/.../WhitelistManager.kt`: Manages the user's whitelist using SharedPreferences.
- `app/src/main/assets/blocked_domains.txt`: List of domains to block.
