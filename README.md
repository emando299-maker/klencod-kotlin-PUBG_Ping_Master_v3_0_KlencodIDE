# PUBG Ping Master 3.0

Android Kotlin project for KlencodIDE. Java 17 and Kotlin JVM target 17; compileSdk/targetSdk 34; Build Tools 34.0.0.

## New in 3.0
- TCP connection-time test to each public endpoint on port 443.
- UDP DNS request/response RTT test to each resolver on port 53.
- Independent average, jitter estimate, and failed-attempt percentage for TCP and UDP DNS.
- Results for Cloudflare (1.1.1.1) and Google (8.8.8.8) remain separate.

## Important interpretation
UDP measurement is a DNS query/response RTT, not generic UDP latency and not PUBG game-server latency. TCP measurement is TCP connect time, not ICMP ping. Neither is the in-game PUBG ping. PUBG does not provide a public third-party API for reading live game-server ping. The app does not lower ping or alter game traffic.

## Build
Extract ZIP, open in KlencodIDE, Gradle sync, then build :app:assembleDebug. Source has been generated but not compiled in this environment; verify with a fresh build.
