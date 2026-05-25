# LavaRise — Claude Code Context

## Proje Özeti
Paper 1.21.11 Minecraft plugin — sıfır bağımlılık, NMS-optimized yükselen lav minigame motoru.
- **Java 21**, Gradle (Kotlin DSL), Shadow JAR, paperweight userdev
- **Branch:** `claude/project-optimization-37F85`
- **Group:** `dev.lavarise` | **Version:** `1.0.0`

---

## Build & Run

```bash
# Derle
./gradlew build

# Test çalıştır
./gradlew test

# Paper sunucusu başlat (1.21.11)
./gradlew runServer

# Temizle + yeniden derle
./gradlew clean build
```

Çıktı JAR: `build/libs/LavaRise-1.0.0.jar`

---

## Proje Mimarisi

```
src/main/java/dev/lavarise/
├── core/
│   ├── LavaRisePlugin.java     # Ana giriş noktası (onEnable/onDisable)
│   └── GameManager.java        # Tüm arena örnekleri + oyuncu→arena O(1) map
├── arena/
│   ├── Arena.java              # Arena entity (config + session tutar)
│   ├── ArenaConfig.java        # Immutable record (minX/maxX vb. computed)
│   └── ArenaSession.java       # Aktif oyun oturumu
├── engine/
│   ├── LavaEngine.java         # Tick-based batch lav yerleştirme motoru
│   ├── ChunkPreloader.java     # Oyun öncesi chunk yükleme
│   ├── WorldResetter.java      # O(1) sequential pointer ile arena reset
│   └── nms/
│       └── FastBlockSetter.java # NMS direkt chunk section yazımı + packet gönderimi
├── state/                      # FSM — oyun fazları
│   ├── GameState.java          # Interface (onEnter/onExit/onTick/isJoinable)
│   ├── LobbyState.java
│   ├── CountdownState.java
│   ├── ActiveState.java
│   └── EndingState.java
├── mode/                       # Oyun modları
│   ├── GameMode.java           # Interface
│   ├── MinigameMode.java       # Yapılandırılmış arena maçları
│   ├── SurvivalChallengeMode.java # Dünya geneli lav etkinliği
│   └── AdminEventMode.java     # Manuel tetiklemeli etkinlik
├── feature/
│   ├── BossBarModule.java      # Lav seviyesi BossBar
│   ├── ScoreboardModule.java   # Sidebar scoreboard
│   ├── ParticleModule.java     # Lav yüzeyinde parçacık efektleri
│   ├── SoundModule.java        # Ses efektleri
│   └── gui/
│       ├── ArenaSelectorGUI.java
│       └── LavaRiseGUIHolder.java
├── data/
│   ├── ArenaRepository.java    # YAML'dan arena yükle/kaydet
│   └── ConfigManager.java      # config.yml okuma
├── listener/
│   ├── PlayerListener.java     # Bukkit player eventleri
│   └── ArenaEventRouter.java   # Arena custom eventlerini dağıt
├── command/
│   └── LavaRiseCommand.java    # /lavarise (join|leave|list|admin)
├── api/events/
│   ├── ArenaStartEvent.java
│   ├── ArenaEndEvent.java
│   └── PlayerEliminatedEvent.java
└── hook/
    └── PapiExpansion.java      # PlaceholderAPI hook
```

---

## Kritik Tasarım Desenleri

### NMS Blok Motoru (`FastBlockSetter`)
- `LevelChunkSection.setBlockState()` ile direkt yazar → fizik/ışık hesabı YOK
- Chunk cache: son kullanılan chunk/section'ı saklar, O(1) tekrar erişim
- `ClientboundLevelChunkWithLightPacket` ile tek paket broadcast
- **Önemli:** Sadece main thread'den çağrılmalı

### Lav Motoru (`LavaEngine`)
- `maxBlocksPerTick` (config: 64) ile her tick'te sınırlı blok yerleştirir
- `cx, cz, currentFillY` pointer'larıyla kaldığı yerden devam eder
- `riseLava()` → `targetY` artırır; `processBatch()` her tick çağrılır

### FSM Oyun Durumları
Arena'nın aktif `GameState`'i: `Lobby → Countdown → Active → Ending`
Her durum `onEnter/onExit/onTick` implement eder.

### Arena Reset (`WorldResetter`)
Async snapshot alır → O(1) sequential lookup ile geri yükler (WorldEdit/schematic YOK)

---

## Bağımlılıklar

| Kütüphane | Kapsam | Notlar |
|-----------|--------|--------|
| Paper 1.21.11 | compileOnly (paperweight) | NMS erişimi için userdev |
| PlaceholderAPI 2.11.6 | compileOnly | softdepend |
| JUnit 5.10.2 | test | |
| Mockito 5.11.0 | test | |

Adventure API Paper'a bundle'lı — shade etme.

---

## Konfigürasyon Dosyaları

- `src/main/resources/config.yml` — performans ayarları, mod ayarları, efektler
- `src/main/resources/messages.yml` — oyuncu mesajları (MiniMessage formatı)
- `src/main/resources/plugin.yml` — komutlar, izinler, softdepend listesi
- `plugins/LavaRise/arenas/*.yml` — arena tanımları (runtime)

**Önemli config değerleri:**
```yaml
performance.max-blocks-per-tick: 64      # LavaEngine.maxBlocksPerTick
performance.player-check-interval: 5    # tick başına kontrol sıklığı
arena-defaults.lava-rise-interval: 60   # ticks arası lav yükselme
```

---

## Komutlar & İzinler

```
/lavarise join <arena>   → lavarise.play
/lavarise leave          → lavarise.play
/lavarise list           → lavarise.play
/lavarise admin          → lavarise.admin
```

Alias: `/lr`, `/lava`

---

## Test

```bash
./gradlew test
```

Test: `src/test/java/dev/lavarise/state/LobbyStateTest.java`
Mockito ile Bukkit mock'lama kullanılıyor.

---

## Geliştirme Kuralları

- Yorum sadece neden açık değilse ekle; ne yaptığını açıklama
- Yeni özellikler için ilgili FSM state veya mode sınıfına ekle
- NMS çağrıları sadece `engine/nms/` altında tutulmalı
- Main thread güvenliği: `FastBlockSetter` async çağrılamaz
- MiniMessage formatı kullan (`<red>`, `<gradient:...>` vb.) — legacy `§` kod yok
- `ConcurrentHashMap` → oyuncu lookupları (GameManager.playerArenaMap)
- Yeni arena eventleri → `api/events/` altında Cancellable implement et

---

## Git

```bash
# Mevcut branch
git checkout claude/project-optimization-37F85

# Push
git push -u origin claude/project-optimization-37F85
```
