# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2026-06-11

### Added
- **Premium Swing GUI**: High-fidelity dark mode application interface showing real-time request logs, port configuration, custom file drop zones, and browser integration.
- **Dynamic Font-Metric UI Scaling**: Automated Layout and font scaling based on system configuration, with CLI-argument override (`java -jar cors_proxy.jar <scale_factor>`) to support High-DPI screens.
- **CORS Proxy & Web Server**: Thread-safe HTTP server serving local static web files and proxying remote endpoint connections.
- **Headless Chrome Integration**: Automatic invocation of local Google Chrome / Chromium binaries to render dynamic client-side JS pages before returning DOM content.
- **JSON API Bypassing**: Bypasses headless browser for backend API and JSON/RPC calls (e.g. `/maps/preview/entitylist/getlist`) to speed up execution and prevent proxy timeouts.
- **Auto Decompression**: Strip client `Accept-Encoding` headers and decode `gzip`/`deflate` response payloads automatically.
- **Subdirectory Debug Logs**: Incremental, thread-safe request logs written to a `debug/response.txt` folder mapped relative to the active HTML page's parent directory.
- **GitHub Actions Release Pipeline**: Auto-compilation and executable JAR deployment to GitHub Releases when pushing version tags (`v*`).
