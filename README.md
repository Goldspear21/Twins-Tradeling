# Tradeling stock workspace

## Run locally

The app uses MySQL by default. Configure `MYSQL_URL`, `MYSQL_USER`, and `MYSQL_PASSWORD` for your local database, then start it from this directory:

```powershell
.\mvnw.cmd spring-boot:run
```

## Local AI with Ollama

The AI chat and optional inventory narration use a local Ollama server only. No cloud provider, API key, or network fallback is configured. Install and start [Ollama for Windows](https://ollama.com/download), then download the default model:

```powershell
ollama pull qwen3:4b
```

Check that the server is running:

```powershell
Invoke-RestMethod http://localhost:11434/api/version
ollama list
```

The app defaults to `http://localhost:11434` and `qwen3:4b`. Optionally copy `.env.example` to `.env` beside `pom.xml` and adjust `OLLAMA_BASE_URL` or `OLLAMA_MODEL`; no key is needed. Restart the Java application after changing these values.

Questions and relevant business context are sent to the local Ollama endpoint at `http://localhost:11434/api/chat`. They are not sent to an AI cloud service by this integration. The app removes customer labels and ledger references from context. Chat is read-only, forecasts are deterministic, and transfer suggestions are proposals; inventory changes require a separate user action.

Keep Ollama bound to localhost. Its local API does not authenticate callers by default, so do not expose port `11434` to an untrusted network.
