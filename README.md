# AI Development Assistant

An AI-powered development assistant that helps with coding, learning, and requirements engineering. Built with React and Java Spring Boot, packaged as a single Docker container.

## What It Does

- **Quick Chat** - General AI conversations
- **Code Assistant** - Code review, debugging, and suggestions with a built-in editor
- **Learning Topics** - Interactive programming tutorials
- **Requirements Engineering** - Generate product requirements documents
- **SDLC Workflow** - Requirements, architecture, implementation, testing, review, and human approval
- **AI Agents** - Shorter engineer and reviewer run against the same sandbox tools

## Quick Start

### Prerequisites

- Docker and Docker Compose (or Java 17 + Node for local dev)
- **Ollama** (default LLM) — [install](https://ollama.com), then `ollama pull llama3.2` and `ollama serve`
- Google AI API key only when `LLM_PROVIDER=gemini`

### Setup

1. Create a `.env` file in the project root (see `env.example`):

```bash
LLM_PROVIDER=ollama
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=llama3.2
SDLC_USE_API=0
```

2. Run with Docker:

```bash
docker compose up --build
```

3. Open your browser: **http://localhost:8080**

That's it! The app bundles the React frontend and Java backend into a single container.

## Alternative: Run Without Docker

If you prefer running locally:

```bash
# Build everything
cd backend
mvn clean package -DskipTests

# Run the JAR
java -jar target/backend-1.0.0.jar
```

Or run with Maven directly:

```bash
cd backend
mvn spring-boot:run
```

## Development

For active development, you can run frontend and backend separately:

**Frontend** (with hot reload):
```bash
cd frontend
npm install
npm run dev
# Runs on http://localhost:3000
```

**Backend**:
```bash
cd backend
mvn spring-boot:run
# Runs on http://localhost:8080
```

## Configuration

Environment variables (set in `.env` or export in shell):

| Variable | Default | Purpose |
|----------|---------|---------|
| `LLM_PROVIDER` | `ollama` | `ollama` or `gemini` |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama server URL |
| `OLLAMA_MODEL` | `llama3.2` | Ollama model name |
| `GOOGLE_AI_API_KEY` | — | Required when `LLM_PROVIDER=gemini` |
| `GOOGLE_AI_MODEL` | `gemini-2.5-flash` | Gemini model name |
| `SDLC_USE_API` | `0` | `0` mock SDLC, `1` real LangGraph + LLM |

Optional keys in `backend/src/main/resources/application.properties`:

- `llm.provider` — Same as `LLM_PROVIDER`
- `agent.sdlc.use-api` — Same as `SDLC_USE_API`
- `agent.max-steps` — Tool-call budget per ReAct stage (default: 8)
- `agent.max-review-rounds` — Extra implementation attempts after failed tests (default: 1)
- `agent.sdlc.recursion-limit` — LangGraph max node visits (default: 50)
- `agent.approval.hitl` — Pause for human approval (default: true)
- `execution.timeout-seconds` — `run_code` timeout (default: 15)

### Switching LLM provider later

All LLM calls go through one `ChatModel` bean in `LangChainConfig.java`. To change provider:

1. **Ollama (default)** — set `LLM_PROVIDER=ollama`, run Ollama locally, restart backend.
2. **Gemini** — set `LLM_PROVIDER=gemini`, set `GOOGLE_AI_API_KEY`, restart backend.
3. **Another provider (OpenAI, Anthropic, etc.)** — add the LangChain4j module in `pom.xml`, add a `case` in `LangChainConfig.chatModel()`, add env vars in `application.properties` / `.env`. No changes needed in SDLC graph, agents, or frontend.

## SDLC Workflow

With `SDLC_USE_API=0` (default), the SDLC page advances six phases with fake docs, tool steps, and nested sandbox paths—no LLM required. Poll `GET /api/workflow/{id}` while status is `running` for live progress.

With `SDLC_USE_API=1`, the page runs a LangGraph4j state graph. LangChain4j calls the configured LLM (Ollama by default) and exposes sandbox tools. Snapshots update as each node finishes so the UI can poll the same status endpoint.

It walks six stages: requirements, architecture, implementation, testing, review, then human approval. Failed tests and requested changes return to implementation until the round limit. Approval waits for you to approve or send notes. `run_code` still uses `ProcessBuilder` with a timeout, and paths stay under `backend/data/sandbox/`.

Example task:

```
Write a Python program fizzbuzz.py that prints FizzBuzz from 1 to 20 (Fizz for multiples of 3, Buzz for 5, FizzBuzz for both). Then run it to verify the output.
```

## AI Personas

Personas are stored in `backend/data/personas/` organized by role. You can:

- Select personas from the UI dropdown in the AI Agents page
- Create custom personas by adding `.md` files with the persona definition
- Personas define the AI's expertise, tone, and behavior patterns

## Troubleshooting

**Port 8080 already in use:**
```bash
lsof -ti:8080 | xargs kill -9
```

**React assets not loading:**
Rebuild the project - the frontend needs to be bundled into the backend JAR:
```bash
cd backend && mvn clean package -DskipTests
```

**Ollama connection errors:**
- Ensure Ollama is running: `ollama serve`
- Pull the model: `ollama pull llama3.2` (or your `OLLAMA_MODEL`)
- Check `OLLAMA_BASE_URL` matches where Ollama listens

**Google AI (Gemini) API errors** (when `LLM_PROVIDER=gemini`):
- Verify your API key is correct
- Check billing / quota on Google AI Studio
- Ensure the model is available (e.g. `gemini-2.5-flash`)

## Tech Stack

- **Frontend**: React 18, TypeScript, Vite, Monaco Editor
- **Backend**: Java 17, Spring Boot 3.2
- **AI**: Ollama (default) or Google Gemini via LangChain4j, LangGraph4j for SDLC orchestration
- **Deployment**: Single JAR or Docker container
