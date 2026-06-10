# CORS Proxy & Static Web Server

A premium, cross-platform Java Swing desktop application and HTTP server designed to serve local Single Page Applications (SPAs) and act as a CORS proxy for web pages and API endpoints.

---

## Key Features

* **Premium UI & High-DPI Scaling**: High-fidelity dark mode Swing GUI with dynamic font-metric scaling. Fully compatible with Linux High-DPI screens, supporting system scaling properties and command-line scale overrides (e.g. `java -jar cors_proxy.jar 1.5`).
* **Static Web Server**: Serves your custom or default `app.html` locally along with all relative static assets (images, CSS, JS) from its parent directory.
* **CORS Proxy**: Decoupled local CORS proxy allowing direct local frontend API fetches to bypass CORS restrictions.
* **Smart JS Engine Execution**: Automatically runs a headless session of Google Chrome/Chromium to execute client-side JavaScript for complex maps/redirect list links, returning the fully loaded DOM back to the frontend app.
* **Direct Backend API Bypassing**: Skips launching Chrome for JSON, RPC, and telemetry endpoints (such as `getlist` or `generate_204`) to optimize speed and prevent proxy timeouts.
* **Automatic Content Decompression**: Automatically strips client `Accept-Encoding` headers and handles on-the-fly `gzip` or `deflate` response decoding via standard JDK streams, ensuring logs and browser responses remain uncorrupted.
* **Thread-Safe Append Logging**: Logs all incoming client requests and targets into a local `debug/response.txt` log subdirectory (relative to the active HTML directory) using clear `===` block separators.

---

## Getting Started

### Prerequisites

* **Java Development Kit (JDK) 21** or higher.
* **Google Chrome / Chromium** (required only for javascript-rendered list links).

### Compilation & Building

To compile the Java source files and build the executable JAR:

```bash
chmod +x build.sh
./build.sh
```

### Running the App

To run the application:

```bash
# Start with default automatic scaling
java -jar cors_proxy.jar

# Or specify a custom scaling factor (e.g., 1.5x font/layout scaling)
java -jar cors_proxy.jar 1.5
```

---

## Development & Release Pipeline

* **GPG Signed Commits & Tags**: All commits and tags are GPG-signed by default.
* **Automated Releases**: Pushing a version tag matching `v*` triggers a GitHub Actions workflow that compiles the executable JAR and attaches it to the GitHub Release.
