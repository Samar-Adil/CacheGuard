# CacheGuard — Intelligent Cache Replacement Simulator

[![CI](https://github.com/Samar-Adil/CacheGuard/actions/workflows/ci.yml/badge.svg)](https://github.com/Samar-Adil/CacheGuard/actions/workflows/ci.yml)
![Java 17](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-Build-C71A36?logo=apachemaven&logoColor=white)
![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)

**CacheGuard** is an interactive desktop application designed to demonstrate, simulate, and compare cache replacement algorithms. It provides a visual environment for exploring cache behavior, analyzing hit and miss ratios, and understanding how different replacement policies perform under identical workloads.

Built with **Java 17, Java Swing, and FlatLaf**, CacheGuard follows the **Model–View–Controller (MVC)** architectural pattern, with a focus on modularity, performance, and maintainability.

> Explore cache replacement strategies, step through individual memory requests, and compare algorithm performance in one application.

## Features

### Interactive Dashboard
- Overview of key cache performance metrics.
- System information, including Java version, operating system, processor count, and JVM heap information.
- Recent simulation sessions and a quick-reference guide to replacement policies.

### Cache Simulation
- Configure cache capacity from 2 to 16 blocks.
- Select a replacement algorithm and enter a custom request sequence.
- Execute simulations one step at a time or run them to completion.
- Visualize cache contents and distinguish cache hits from misses.
- Inspect request history and step-by-step execution traces.
- Explore example workloads and generate synthetic request sequences.

### Algorithm Comparison
- Run all five replacement policies against the same request stream.
- Compare cache hits, misses, hit ratio, miss ratio, evictions, and execution time.
- Use custom request sequences or generated workloads of up to 5,000,000 requests.
- Track execution progress and cancel long-running comparisons.

### Statistics and History
- Review aggregate simulation statistics.
- Compare average hit ratios across replacement policies.
- Visualize hit-ratio trends across recorded sessions.
- Inspect detailed result tables and simulation history.

### Settings and Data Management
- Switch between dark and light themes.
- Configure default cache capacity and replacement policy.
- Export and import application data in JSON format, with merge and replace options.
- Clear simulation history and open the application data directory.

## Cache Replacement Algorithms

CacheGuard implements five replacement policies:

| Algorithm | Replacement Strategy | Core Data Structures | Expected Access Complexity |
|---|---|---|---|
| LRU | Evicts the least recently used block | HashMap + doubly linked list | O(1) |
| LFU | Evicts the least frequently used block; ties are resolved by recency | HashMap + frequency buckets using LinkedHashSet | O(1) |
| FIFO | Evicts the oldest resident block | ArrayDeque + HashSet | O(1) |
| MRU | Evicts the most recently used block | HashMap + doubly linked list | O(1) |
| Random | Evicts a randomly selected block using a seeded, repeatable random source | ArrayList + index map | O(1) |

The implementation provides a consistent simulation environment for comparing the behavior of these policies across different request patterns.

**Built-in learning scenarios include:**
- Belady's anomaly with FIFO.
- Sequential workloads larger than the available cache capacity.
- Workloads with frequently accessed blocks to explore the behavior of LFU and LRU.

*Complexity figures describe the intended data-structure operations; actual runtime depends on workload size, implementation details, and execution environment.*

## Technology Stack

| Category | Technologies |
|---|---|
| Language | Java 17 |
| Desktop UI | Java Swing |
| UI Theming | FlatLaf |
| Architecture | Model–View–Controller (MVC) |
| Data Persistence | JSON |
| Build Tool | Apache Maven |
| Testing | JUnit / Maven test suite |
| CI/CD | GitHub Actions |

## Requirements

Before building CacheGuard, ensure that the following tools are installed:

- **JDK 17 or newer**
- **Apache Maven 3.8 or newer**
- Git, if cloning the repository

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/Samar-Adil/CacheGuard.git
cd CacheGuard
```

### 2. Build the application

```bash
mvn clean package
```

This compiles the source code, runs the test suite, and packages the application into a JAR file.

### 3. Launch CacheGuard

```bash
java -jar target/cacheguard-1.0.0.jar
```

The packaged application includes its required runtime dependencies, including FlatLaf.

### Alternative: Run from source

```bash
mvn compile exec:java -Dexec.mainClass=com.cacheguard.CacheGuardApp
```

Helper scripts are also provided:

- macOS/Linux: `scripts/run.sh`
- Windows: `scripts/run.bat`

## Keyboard Shortcuts

| Shortcut | Action |
|---|---|
| Ctrl+1 to Ctrl+5 | Navigate between screens |
| Ctrl+T | Toggle dark/light theme |
| Ctrl+E | Export application data |
| Ctrl+I | Import application data |
| Ctrl+Right Arrow | Advance one simulation step |
| Ctrl+Enter | Run or cancel a simulation |
| Ctrl+R | Reset the current simulation |

On macOS, use **Command** instead of **Ctrl** where supported by the application.

## Project Structure

The codebase is organized into separate model, view, controller, and utility layers.

```text
CacheGuard/
├── .github/
│   └── workflows/
│       └── ci.yml
├── docs/
│   └── ARCHITECTURE.md
├── scripts/
│   ├── run.sh
│   └── run.bat
├── site/
│   └── index.html
├── src/
│   ├── main/
│   │   └── java/com/cacheguard/
│   │       ├── CacheGuardApp.java
│   │       ├── model/
│   │       ├── view/
│   │       ├── controller/
│   │       └── util/
│   └── test/
├── .gitignore
├── .vercelignore
├── CHANGELOG.md
├── LICENSE
├── pom.xml
├── vercel.json
└── README.md
```

### Architectural Overview

- **Model:** Contains cache implementations, simulation logic, workload generation, application state, settings, and statistics.
- **View:** Implements the Swing interface, including the dashboard, simulator, comparison screen, statistics, settings, and reusable UI components.
- **Controller:** Coordinates user interactions, simulation execution, navigation, background tasks, persistence, and theme changes.
- **Utilities:** Provides JSON parsing and serialization, theme management, atomic file operations, request parsing, and system information.

For a more detailed explanation, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Testing and Quality Assurance

Run the automated test suite with:

```bash
mvn test
```

The test suite covers cache replacement policies against reference implementations, simulation behavior, request parsing, JSON processing, and persistence scenarios.

Tests are designed to run in headless environments, making them suitable for continuous integration.

## Data and Privacy

CacheGuard stores application settings and simulation history locally on your machine. It does not require a remote database for its core functionality.

By default, application data is stored in:

```text
~/.cacheguard/settings.json
~/.cacheguard/sessions.json
```

The storage location can be customized with the JVM property:

```bash
-Dcacheguard.home=/some/folder
```

The application retains up to 1,000 sessions. When a stored data file is detected as corrupt, it is renamed with a corruption timestamp rather than silently overwritten.

## Continuous Integration

The repository includes a GitHub Actions workflow at `.github/workflows/ci.yml` for building and testing the project across supported operating systems and Java versions.

Check the [Actions tab](https://github.com/Samar-Adil/CacheGuard/actions) for build and test results.

## Deployment

### GitHub Releases

CacheGuard is a desktop application. The runnable JAR can be distributed through GitHub Releases so users can download it and run it locally.

The included CI workflow is configured to attach the JAR to a release when a version tag is pushed, subject to the workflow's configuration and successful execution.

### Vercel Website

The Java Swing application itself cannot run directly in a browser or be hosted as a conventional web application on Vercel.

However, the repository includes a `site/` directory containing a static project page. Vercel can host this page to provide a browser-accessible landing page with project information and download links.

To deploy it:

1. Import the `Samar-Adil/CacheGuard` repository into Vercel.
2. Select **Other** as the framework preset.
3. Configure the project to serve the `site/` directory, as specified in `vercel.json`.
4. Deploy the project and open the generated URL.

See the repository's `vercel.json` and `site/index.html` for the deployment configuration and website content.

## Roadmap

Potential future improvements include:

- Additional replacement policies and configurable workloads.
- More detailed performance visualizations.
- Expanded automated tests and benchmarking.
- Improved accessibility and simulation explanations.

## License

CacheGuard is distributed under the [MIT License](LICENSE).

## Author

**Samar-Adil**

- GitHub: [@Samar-Adil](https://github.com/Samar-Adil)
- Project repository: [CacheGuard](https://github.com/Samar-Adil/CacheGuard)

---

*CacheGuard — making cache replacement algorithms easier to visualize, understand, and compare.*