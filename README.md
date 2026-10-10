# CacheGuard – Intelligent LRU/LFU Cache Simulator

[![CI](https://github.com/YOUR_USERNAME/cacheguard/actions/workflows/ci.yml/badge.svg)](https://github.com/YOUR_USERNAME/cacheguard/actions/workflows/ci.yml)
![Java 17](https://img.shields.io/badge/Java-17-orange)
![License MIT](https://img.shields.io/badge/license-MIT-blue)

An educational desktop app that shows how cache replacement policies behave. Enter a request
sequence, pick a policy and a cache size, and step through it block by block, or replay the same
stream through all five policies at once.

Built with **Java 17**, **Swing** and **FlatLaf** (dark and light themes), using a strict
**MVC** structure. Settings and history are stored as **JSON**; long simulations run on background
threads so the window never freezes.

> Replace `YOUR_USERNAME` in this file and in `site/index.html` with your GitHub username after you push.

## Features

| Screen | What it does |
|---|---|
| **Dashboard** | Headline numbers, quick navigation, system specs (Java, OS, cores, heap), recent sessions, policy cheat sheet |
| **Simulation** | Capacity slider (2–16, default 4), algorithm selector, request input, **Step Forward / Run All / Reset**, animated cache blocks with HIT / MISS badges, request strip, step-by-step trace, example lessons, workload generator |
| **Comparison** | All five policies on identical requests, run in parallel. Hits, misses, hit ratio %, miss ratio %, evictions, time. Custom sequence or generated workload up to 5,000,000 requests, with progress and cancel |
| **Statistics** | Summary cards, average hit ratio per policy, hit-ratio trend per session, breakdown table, full history |
| **Settings** | Dark / light theme, default capacity and algorithm, JSON export / import (merge or replace), clear history, open data folder |

### Policies

| Policy | Evicts | Structure | Cost per access |
|---|---|---|---|
| LRU | Least recently used | `HashMap` + doubly linked list | O(1) |
| LFU | Least frequently used, ties by recency | `HashMap` + frequency buckets (`LinkedHashSet`) | O(1) |
| FIFO | Oldest resident block | `ArrayDeque` + `HashSet` | O(1) |
| MRU | Most recently used | `HashMap` + doubly linked list | O(1) |
| Random | A random block (seeded, repeatable) | `ArrayList` + index map | O(1) |

The first three are the core algorithms; MRU and Random complete the five-way comparison.
The Simulation screen's example menu includes Belady's anomaly for FIFO, a loop larger than the
cache (where MRU wins) and a hot block (where LFU beats LRU).

## Requirements

- JDK 17 or newer
- Maven 3.8+ (to build)

## Build and run

```bash
mvn package                              # compiles, runs tests, builds target/cacheguard-1.0.0.jar
java -jar target/cacheguard-1.0.0.jar    # runnable jar with FlatLaf bundled
```

Or use the helper scripts: `scripts/run.sh` (macOS / Linux) and `scripts/run.bat` (Windows).
To run from source: `mvn compile exec:java -Dexec.mainClass=com.cacheguard.CacheGuardApp`.

### Keyboard shortcuts

| Keys | Action |
|---|---|
| Ctrl+1 … Ctrl+5 | Switch screen (Cmd on macOS) |
| Ctrl+T | Toggle dark / light theme |
| Ctrl+E / Ctrl+I | Export / import data |
| Ctrl+→ | Step forward (Simulation) |
| Ctrl+Enter | Run all, or cancel a running one (Simulation) |
| Ctrl+R | Reset (Simulation) |

## Project structure

```
src/main/java/com/cacheguard/
├── CacheGuardApp.java            entry point
├── model/                        no Swing; fully unit-tested
│   ├── Cache, AbstractCache      interface + template (counters, hit/miss/evict flow)
│   ├── LRUCache, LFUCache, FIFOCache, MRUCache, RandomCache, RecencyListCache
│   ├── Simulation, StepRecord, SimulationResult, SlotLayout
│   ├── WorkloadGenerator, WorkloadPattern
│   ├── AppState, Settings, SessionRecord, StatisticsSummary, CacheStats
├── view/                         Swing screens
│   ├── MainFrame, ScreenPanel
│   ├── DashboardPanel, SimulationPanel, ComparisonPanel, StatisticsPanel, SettingsPanel
│   └── components/               CacheBlocksPanel, BarChartPanel, LineChartPanel, StatCard, ...
├── controller/                   listeners, engines, worker threads
│   ├── AppController             wiring, navigation, background saving, theme changes
│   ├── SimulationController      step / run all (SwingWorker) / reset
│   ├── ComparisonController      SwingWorker + thread pool, one thread per policy
│   ├── StatisticsController, SettingsController, StorageService
└── util/
    ├── Json                      dependency-free JSON parser and writer
    ├── ThemeManager              FlatLaf dark / light + palette for custom painting
    ├── FileStore                 atomic file writes
    └── RequestParser, AppInfo
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for how the pieces fit together.

## Data and privacy

Everything stays on your machine. Preferences and history are written to
`~/.cacheguard/settings.json` and `~/.cacheguard/sessions.json` (override with
`-Dcacheguard.home=/some/folder`). The latest 1,000 sessions are kept. A corrupt file is renamed to
`*.corrupt-<timestamp>` rather than deleted, and the app starts with defaults.

## Tests

```bash
mvn test
```

The suite covers each policy against a naive reference implementation on thousands of random
streams, the simulation engine (stepping, cancelling, resuming, large runs), the JSON parser, the
request parser, and persistence including import / export and recovery from corrupt files.
Tests are headless and need no display.

## Deploying

### GitHub

```bash
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/cacheguard.git
git push -u origin main
```

The included workflow (`.github/workflows/ci.yml`) builds and tests on Linux, Windows and macOS with
Java 17 and 21. Push a tag such as `v1.0.0` and it attaches the runnable jar to a GitHub release.

### Vercel

CacheGuard is a desktop application. **Vercel cannot run Swing apps**, because it hosts websites
and serverless functions and has no display. What Vercel can host is the static project page in
`site/`, which describes the app and links to the jar on your GitHub releases:

1. Import the GitHub repository in Vercel.
2. Leave the framework preset as **Other**. `vercel.json` already sets the output directory to `site`
   and turns off the build step.
3. Deploy. Then replace `YOUR_USERNAME` in `site/index.html` with your GitHub username.

## License

[MIT](LICENSE)


