# Contributing to LavaRise

First off, thank you for considering contributing to LavaRise! It's people like you that make open source such a great community.

## Development Setup

1. Fork the repository
2. Clone your fork: `git clone https://github.com/YOUR-USERNAME/LavaRise.git`
3. Open the project in IntelliJ IDEA (or your preferred IDE)
4. Run `./gradlew build` to verify the setup

## Coding Standards

- **NMS Code**: Be careful when modifying NMS (net.minecraft.server) code. Always test your changes on the target Minecraft version.
- **Garbage Collection**: Avoid object allocations in hot paths (e.g., ticking arenas, block placement). Use primitive arrays and primitive maps where applicable.
- **Java 21**: We utilize modern Java 21 features.

## Submitting Pull Requests

1. Create a new branch: `git checkout -b feature/my-new-feature`
2. Commit your changes: `git commit -m 'feat: Add some feature'`
3. Push to the branch: `git push origin feature/my-new-feature`
4. Submit a Pull Request targeting the `main` branch.

Please ensure all tests pass and that your code compiles properly with `./gradlew build`.
