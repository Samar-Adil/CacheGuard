# Architecture

CacheGuard follows MVC with one rule: **the model never imports Swing**, and the view never changes
the model directly.

```
        user input                                   notifies
  View ────────────▶ Controller ──────▶ Model ───────────────▶ View
 (Swing panels)     (listeners,        (caches, Simulation,     (PropertyChange on AppState,
                     SwingWorkers)      AppState)                controller listeners)
```

## Model

- `Cache<K>` is the contract. `AbstractCache` owns the hit / miss / evict / insert sequence and the
  counters, so a policy only supplies `onHit`, `evict`, `insert`, `peekVictim` and `clear`.
- LRU and MRU share `RecencyListCache` (a `HashMap` plus a sentinel-based doubly linked list). LFU keeps
  a `HashMap` of frequencies and a bucket per frequency, with `minFrequency` tracked incrementally so the
  victim is found without scanning.
- `Simulation` replays a request array through one cache. It records a full per-step trace for up to
  10,000 requests and only counters plus a 400-point timeline beyond that, so memory stays flat for
  multi-million request runs. The request array is shared and never copied, so five simulations can read
  the same stream.
- `SlotLayout` maps blocks to physical slots for the visualisation; policies know nothing about it.
- `AppState` is the observable root (`Settings` + session history). It is confined to the event thread.

## Controller

- `SimulationController.runAll()` uses a `SwingWorker`. Cancellation uses an `AtomicBoolean` rather than
  `SwingWorker.cancel()`, because `cancel()` fires `done()` immediately while the background thread may
  still be stepping the simulation the event thread is about to read.
- `ComparisonController` starts one pool thread per policy under a coordinating `SwingWorker`, aggregates
  progress with an `AtomicLong`, and builds or parses the request stream on the worker thread too.
- `AppController` saves settings and history on a single background thread, so file I/O never blocks the
  window, and drains pending saves on shutdown.

## Persistence

`StorageService` reads and writes JSON through `FileStore`, which writes to a temporary file and moves it
into place so a crash cannot leave a half-written file. Unreadable files are quarantined, never deleted.

## View

`MainFrame` hosts a sidebar and a `CardLayout` of `ScreenPanel`s. Custom components (`CacheBlocksPanel`,
the charts, badges) read colours from `ThemeManager.palette()` while painting, so a theme switch needs only
a repaint. Components that read a `Simulation` directly are told when a background run starts so they never
read it mid-run.
