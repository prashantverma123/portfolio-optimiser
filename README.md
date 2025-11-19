# CSV User Importer

A small event-driven demo that accepts bulk user data via CSV, stores the uploaded files on the backend, and processes them asynchronously through a simple publish/subscribe mechanism. A lightweight React UI is provided to upload the file and inspect the processed records.

## Tech Stack

- **Backend:** Kotlin 2.1 + Ktor 2.3 (Netty engine)
- **Frontend:** React + Vite + TypeScript
- **Messaging:** In-memory `Channel` used as a pub/sub queue so file uploads are decoupled from CSV parsing

## Repository Layout

```
backend/      # Kotlin backend
frontend/     # React UI
sample-data/  # Example CSV file to try locally
```

## Prerequisites

- Java 17+
- Node.js 20+ and npm
- Gradle 8.14+ (only needed to regenerate the Gradle wrapper JAR locally)

## Bootstrap the Gradle Wrapper

The repository keeps binary artifacts out of version control, so `gradle/wrapper/gradle-wrapper.jar` is ignored. Run the followi
ng command once after cloning to recreate the wrapper JAR locally:

```bash
cd backend
gradle wrapper
```

After that you can use the standard wrapper commands.

## Running the Backend

```bash
cd backend
./gradlew run
```

The server listens on port `8080` by default. Use the `PORT` environment variable to override the HTTP port and `UPLOAD_DIR` to change where temporary CSV files are stored.

## Running the Frontend

```bash
cd frontend
npm install
npm run dev -- --host
```

The UI expects the backend at `http://localhost:8080`. To point it elsewhere set `VITE_API_BASE_URL` before running Vite, e.g. `VITE_API_BASE_URL=http://localhost:9000 npm run dev -- --host`.

## API Summary

| Method | Endpoint        | Purpose                                  |
| ------ | --------------- | ---------------------------------------- |
| POST   | `/api/uploads`  | Accepts a multipart upload with a CSV file. Stores it on disk and publishes a `FileUploadedEvent`.
| GET    | `/api/users`    | Returns every user record processed by the subscriber.

## Event-Driven Design

1. **Upload** – the API saves the CSV to `uploads/` and publishes `FileUploadedEvent(path)` via an unbounded Kotlin `Channel`.
2. **Subscriber** – a coroutine consumer reads the events, parses the CSV with Apache Commons CSV, and pushes valid rows to an in-memory `UserStore`.
3. **Error handling** – invalid rows are logged but do not stop processing. Every upload file is deleted after processing to keep the workspace clean.

The in-memory queue keeps the upload path separate from the worker. Replacing it with a real broker (Pub/Sub, RabbitMQ, etc.) would only require swapping the `FileUploadEventBus` implementation.

## CSV Structure

The parser expects four headers: `id`, `firstName`, `lastName`, `email`. An example file lives in [`sample-data/users.csv`](sample-data/users.csv).

```
id,firstName,lastName,email
1,Ada,Lovelace,ada@example.com
2,Nikola,Tesla,nikola@example.com
3,Grace,Hopper,grace@example.com
```

## Design Notes

- **Storage** – user data is stored in a thread-safe `CopyOnWriteArrayList` to keep the demo simple.
- **Validation** – both frontend and backend enforce the `.csv` extension; the backend also reports bad rows during parsing.
- **Extensibility** – new subscribers can be added by consuming the same channel, and persisting to a real database would only require a new storage implementation.
