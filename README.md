# AI Development Assistant

An AI-powered development assistant that helps with coding, learning, and requirements engineering. Built with React and Java Spring Boot, packaged as a single Docker container.

## What It Does

- **Quick Chat** - General AI conversations
- **Code Assistant** - Code review, debugging, and suggestions with a built-in editor
- **Learning Topics** - Interactive programming tutorials
- **Requirements Engineering** - Generate product requirements documents
- **AI Agents** - Engineer + Reviewer loop with sandbox file tools and local code execution

## Quick Start

### Prerequisites

- Docker and Docker Compose
- Google AI (Gemini) API key

### Setup

1. Create a `.env` file in the project root:

```bash
GOOGLE_AI_API_KEY=your-google-ai-api-key-here
GOOGLE_AI_MODEL=gemini-2.5-flash
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

- `GOOGLE_AI_API_KEY` - Your Google AI (Gemini) API key (required)
- `GOOGLE_AI_MODEL` - Model to use (default: `gemini-2.5-flash`)

## AI Agents

The AI Agents page runs a bounded tool loop against Gemini function calling:

1. The Engineer persona writes files under `backend/data/sandbox/`
2. `run_code` executes `.py` or `.java` with `ProcessBuilder`, a timeout, and path validation. The Docker image includes Python 3 and a JDK.
3. The QA reviewer reads and runs the result, then `APPROVE`s or requests changes

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

**Google AI (Gemini) API errors:**
- Verify your API key is correct
- Check your Google AI billing / quota
- Ensure the model is available to your project (e.g., `gemini-2.5-flash`)

## Tech Stack

- **Frontend**: React 18, TypeScript, Vite, Monaco Editor
- **Backend**: Java 17, Spring Boot 3.2
- **AI**: Google AI (Gemini)
- **Deployment**: Single JAR or Docker container
