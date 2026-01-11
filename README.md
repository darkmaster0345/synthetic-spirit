# Synthetic Spirit - DNS Shield

A simple Android app in Kotlin that blocks adult content using a local DNS sinkhole via the `VpnService` API.

## Features
- **Local DNS Interception**: Intercepts DNS requests (port 53) without routing all traffic through the VPN.
- **NXDOMAIN Sinkhole**: Returns an `NXDOMAIN` response for any domain in the blocklist.
- **Customizable Blocklist**: Add or remove domains in `app/src/main/assets/blocked_domains.txt`.
- **Minimalist UI**: Simple toggle to start/stop the protection.

## How it works
The app uses the `VpnService` to create a virtual network interface. It sets the DNS server of the system to a local dummy address (`10.0.0.1`) and adds a route to ensure only DNS traffic to that address is captured by the VPN.

The `DnsVpnService` then:
1. Reads raw IP packets from the TUN interface.
2. Extracts UDP/DNS queries.
3. Checks the requested domain against the blocklist.
4. If blocked, constructs a DNS response with `RCODE 3` (NXDOMAIN) and sends it back.
5. If not blocked, forwards the query to an upstream DNS server (default: `8.8.8.8`) and relays the response.

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
- `app/src/main/assets/blocked_domains.txt`: List of domains to block.
