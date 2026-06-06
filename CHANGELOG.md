# Changelog

All notable changes to this project will be documented in this file.

## [1.0.0] - Initial Release

### Added
- Local DNS sinkhole using VpnService API
- Bloom filter implementation for O(1) domain lookup performance
- LRU cache for frequent query caching
- Whitelist management for user-defined allowed domains
- DNS query logging with block/allow status
- Automatic VPN restart on device boot
- Watchdog service for resilience (every 15 minutes)
- Remote blocklist URL support
- Jetpack Compose UI with dark theme