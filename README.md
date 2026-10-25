# NOVA — Enterprise AI Knowledge Assistant

NOVA is a Spring Boot application that demonstrates Retrieval-Augmented Generation (RAG). Users upload PDF documents, the application extracts and chunks their text, stores the chunks in ChromaDB, retrieves relevant context for questions, and sends that context to a chat model through Groq's OpenAI-compatible API. Chat history is stored using Spring Data JPA and H2.

## Contents

- [Features](#features)
- [Technology Stack](#technology-stack)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
- [Run ChromaDB](#run-chromadb)
- [Build and Run](#build-and-run)
- [API Endpoints](#api-endpoints)
- [Swagger / OpenAPI](#swagger--openapi)
- [Persistence and Limitations](#persistence-and-limitations)
- [Troubleshooting](#troubleshooting)
- [Security Checklist](#security-checklist)

## Features

- Upload PDF documents and extract their text.
- Split extracted text into overlapping chunks.
- Store and retrieve document chunks using ChromaDB similarity search.
- Answer questions using a RAG workflow with Spring AI and Groq.
- Persist chat history using Spring Data JPA and H2.
- Support regular and streaming chat flows where implemented.
- Expose REST endpoints through Spring MVC.
- Provide interactive API documentation through Swagger UI / OpenAPI when Springdoc is configured.

## Technology Stack

| Technology | Purpose |
|---|---|
| Java 17 | Application runtime |
| Spring Boot 3.5.x | Application framework |
| Spring AI 1.1.x | Chat model and vector-store integrations |
| Maven | Build and dependency management |
| Groq API | Hosted language-model inference |
| ChromaDB | Vector storage and similarity search |
| Docker | Run ChromaDB as a separate service |
| Spring Data JPA / Hibernate | Relational persistence |
| H2 | Local relational database for chat history |
| Apache PDFBox | PDF text extraction |
| Spring Web MVC | REST API |
| Spring WebFlux | Reactive/streaming support where used |
| Springdoc OpenAPI UI | Swagger UI and OpenAPI documentation, if configured |

Check the project's `pom.xml` and configuration files for the exact dependency and image versions used by your checkout. The ChromaDB Python package version does not necessarily match the Docker image version. Vector dimensions alone do not identify an embedding model; confirm the configured model before documenting its name.

## Architecture

### PDF ingestion

1. A client uploads a PDF to `POST /api/v1/documents/upload`.
2. `DocumentIngestionService` validates the uploaded file.
3. `PdfService` extracts text from the PDF.
4. `ChunkingService` splits the text into chunks (currently 1,000 characters with 150 characters of overlap).
5. Each Spring AI `Document` stores the chunk text and metadata such as `source` and `chunk_index`.
6. The vector store adds the documents to ChromaDB.

### Chat / RAG

1. A client submits a question to `POST /api/v1/chat`.
2. `ChatService` loads the session history.
3. ChromaDB similarity search requests up to four relevant chunks.
4. Retrieved text and source metadata are assembled into context.
5. Spring AI sends the context and question to the configured Groq model.
6. User and assistant messages are saved in chat history.

Source labels in generated answers are prompt-driven unless the application validates citations programmatically.

## Prerequisites

- JDK 17
- Apache Maven 3.9.x or a compatible version
- Docker Desktop or another Docker-compatible runtime
- A valid Groq API key
- Git

Verify the tools in Windows Command Prompt:

```cmd
java -version
javac -version
mvn -version
docker --version
docker info
```

Ensure `JAVA_HOME` points to a JDK 17 installation.

## Configuration

### Groq API key

Set the key in the same terminal session used to start the application.

**Windows Command Prompt:**

```cmd
set GROQ_API_KEY=replace_with_your_groq_api_key
```

**PowerShell:**

```powershell
$env:GROQ_API_KEY = "replace_with_your_groq_api_key"
```

Do not commit the real key. If a key was previously exposed or committed, revoke it and create a replacement.

The relevant `application.yml` settings should use an environment variable, merged with your existing configuration rather than replacing unrelated settings:

```yaml
spring:
  ai:
    openai:
      api-key: ${GROQ_API_KEY}
      base-url: https://api.groq.com/openai
      chat:
        options:
          model: openai/gpt-oss-120b

    vectorstore:
      chroma:
        client:
          host: http://localhost
          port: 8000
        tenant-name: default_tenant
        database-name: default_database
        initialize-schema: true
        collection-name: nova-documents-cosine
```

The configured H2 JDBC URL is:

```text
jdbc:h2:file:./data/chatdb
```

Keep other existing JPA/H2 settings as required. Do not commit local database files or uploaded documents.

## Run ChromaDB

NOVA expects ChromaDB to be reachable at `http://localhost:8000`.

If you already have a ChromaDB container, check it before creating another container on the same port:

```cmd
docker ps -a
docker inspect <container-name>
curl.exe http://localhost:8000/api/v2/heartbeat
```

Run ChromaDB using the official instructions for the image version you choose. For persistent storage, configure a Docker volume or bind mount and ensure the ChromaDB server writes its data to that mounted location. A bind mount alone does not prove the server is using it. Do not use a machine-specific local path in project documentation; describe the mount generically or provide an example that works across environments.

## Build and Run

Run commands from the project directory containing `pom.xml`.

Build and test:

```bash
mvn clean verify
```

Run from source:

```bash
mvn spring-boot:run
```

Alternatively, package and run the JAR:

```bash
mvn clean package
java -jar target/<generated-jar-name>.jar
```

Replace `<generated-jar-name>.jar` with the actual filename created in `target`. The application is expected to listen on port `8080`, unless configuration overrides it.

## API Endpoints

The controller base path is `/api/v1`.

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/v1/documents/upload` | Upload and index a PDF. Send `multipart/form-data` with the field name `file`. |
| `POST` | `/api/v1/chat` | Submit a chat prompt using the JSON request body defined by the controller DTO. |

### Upload a PDF

Replace the example path with a PDF available on your machine:

```cmd
curl.exe -X POST "http://localhost:8080/api/v1/documents/upload" -F "file=@C:\path\to\document.pdf"
```

A successful upload returns a message containing the filename and number of chunks indexed.

Use Swagger UI or inspect the controller DTO to confirm the exact JSON schema for `/api/v1/chat`; do not guess request fields.

## Swagger / OpenAPI

When Springdoc is included and configured:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

If Springdoc is not yet included, add the following dependency inside `<dependencies>` in `pom.xml`:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.13</version>
</dependency>
```

Then rebuild and restart the application:

```bash
mvn clean verify
mvn spring-boot:run
```

## Persistence and Limitations

- **Chat history:** H2 file database at `./data/chatdb`.
- **Document vectors:** ChromaDB service on port `8000`.
- **Persistence across restarts:** ChromaDB must be configured to write data to a persistent volume or mounted path.
- **Duplicate uploads:** The current ingestion flow may index the same PDF more than once because each upload creates new document records. Duplicate prevention has not yet been implemented, and existing duplicate chunks are not automatically removed.
- **Embedding compatibility:** Indexing and query-time embeddings must use compatible models and preprocessing. Matching vector dimensions alone does not guarantee compatibility.

## Troubleshooting

### Groq requests fail

- Confirm `GROQ_API_KEY` is set in the same terminal session that launches the application.
- Check that the key is valid and the configured model is available to your account.
- Never share API keys in logs, screenshots, or source code.

### ChromaDB is unavailable

```cmd
docker ps
curl.exe http://localhost:8000/api/v2/heartbeat
```

Confirm that the container is running and Spring AI is configured to connect to `localhost:8000`.

### Swagger UI returns 404

- Confirm the Springdoc dependency is in `pom.xml`.
- Run `mvn clean verify`.
- Restart the application and check `/v3/api-docs`.

### PDF upload works but answers do not use the document

- Confirm upload reports a non-zero chunk count.
- Check application logs for retrieved chunks.
- Confirm ingestion and search use the same ChromaDB collection.
- Check the relevance of the retrieved chunks.
- Verify embedding model compatibility between indexing and retrieval.

### Duplicate results appear

The current ingestion implementation may add the same PDF repeatedly. Avoid repeated uploads while investigating. Deduplication and cleanup of existing duplicates are separate tasks.

## Security Checklist

Before publishing the repository:

- [ ] Revoke any API key that was previously exposed and generate a new one.
- [ ] Use `${GROQ_API_KEY}` instead of a literal key in YAML.
- [ ] Add `.gitignore` entries for `target/`, local database files, logs, uploaded PDFs, and local ChromaDB data.
- [ ] Review `git status` and staged changes before committing.
- [ ] Do not commit personal or confidential PDFs.
- [ ] If a secret was committed earlier, revoke it; deleting it from the latest file does not remove it from Git history.
- [ ] Remove local usernames, absolute machine-specific paths, and other personal environment details from documentation.
- [ ] Ensure the README contains no secret values.

## License

Add a `LICENSE` file if you intend to distribute the project as open source. Without a license, others are not explicitly granted permission to reuse the code.
