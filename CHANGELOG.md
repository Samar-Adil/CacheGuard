# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-10-10

### Added
- Five replacement policies, each O(1) per access: LRU, LFU, FIFO, MRU and seeded Random.
- Dashboard, interactive Simulation, Comparison, Statistics and Settings screens.
- Step Forward / Run All / Reset with a cache-block visualiser and hit / miss badges.
- Parallel comparison of all five policies on one request stream (up to 5,000,000 requests).
- Workload generator: uniform, Zipf, loop, hot set with scan, shifting working set.
- JSON persistence of settings and history, with import / export and corrupt-file recovery.
- FlatLaf dark and light themes.
