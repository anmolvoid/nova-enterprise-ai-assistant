# NOVA --- Enterprise AI Knowledge Assistant

NOVA is a Spring Boot application that demonstrates Retrieval-Augmented
Generation (RAG): users upload PDF documents, the application extracts
and chunks their text, stores the chunks in ChromaDB, retrieves relevant
context for questions, and sends that context to a chat model through
Groq's OpenAI-compatible API. Chat history is stored using Spring Data
JPA and H2.

> This README reflects the configuration and versions confirmed during
> development. Verify the actual repository and environment before
> publishing.

## Contents

-   [Features](#features)
-   [Technology stack](#technology-stack)
-   [Architecture](#architecture)
-   [Prerequisites](#prerequisites)
-   [Configuration](#configuration)
-   [Run ChromaDB](#run-chromadb)
-   [Build and run](#build-and-run)
-   [API endpoints](#api-endpoints)
-   [Swagger / OpenAPI](#swagger--openapi)
-   [Persistence and limitations](#persistence-and-limitations)
-   [Troubleshooting](#troubleshooting)
-   [GitHub security checklist](#github-security-checklist)

## Features

-   PDF upload and text extraction.
-   Text chunking with overlap.
-   Vector indexing and similarity search with ChromaDB.
-   RAG-based chat using Spring AI and Groq.
-   Chat history persistence with Spring Data JPA and H2.
-   Regular and streaming chat flows in the application.
-   REST API built with Spring MVC.
-   Interactive API documentation with Swagger UI / OpenAPI when
    Springdoc is installed.

## Technology stack

  -------------------------------------------------------------------------------
  Technology              Version / setting               Role
  ----------------------- ------------------------------- -----------------------
  Java                    17; development machine         Runtime
                          reported 17.0.12                

  Spring Boot             3.5.6                           Application framework

  Spring AI               1.1.0 BOM                       Chat model and
                                                          vector-store
                                                          integrations

  Maven                   3.9.16 on development machine   Build

  Groq API                OpenAI-compatible base URL      Hosted LLM inference
                          `https://api.groq.com/openai`   

  Chat model              `openai/gpt-oss-120b`           Configured model

  ChromaDB                Python package 1.5.9 confirmed; Vector database
                          Docker image version must be    
                          checked separately              

  Spring AI Chroma vector 1.1.0                           Vector storage and
  store                                                   retrieval

  Spring AI Transformers  1.1.0 starter                   Local text embeddings;
                                                          exact model ID should
                                                          be confirmed

  H2                      Version managed by Spring Boot  Local relational
                          dependency management           database

  Spring Data JPA /       Versions managed by Spring Boot ORM and persistence
  Hibernate                                               

  Spring Web MVC          Version managed by Spring Boot  REST endpoints

  Spring WebFlux          Dependency included for         Streaming support
                          reactive/streaming              
                          functionality where used        

  Apache PDFBox           3.0.5                           PDF text extraction

  Springdoc OpenAPI UI    2.8.13 if added to `pom.xml`    Swagger UI and OpenAPI

  Docker Desktop          Installed on development        Runs ChromaDB
                          machine                         

  Python                  3.12.10 in the development      Diagnostics/scripts
                          Chroma diagnostic environment   
  -------------------------------------------------------------------------------

**Version accuracy:** Java/Maven versions describe the development
machine. Spring Boot manages versions of several transitive
dependencies. The Python ChromaDB package version does not establish the
Docker image version. A 384-dimensional vector does not identify the
embedding model; confirm the configured model ID before naming it in
documentation.

## Architecture

### PDF ingestion

1.  Client sends a PDF to `POST /api/v1/documents/upload`.
2.  `DocumentIngestionService` validates the file.
3.  `PdfService` extracts text.
4.  `ChunkingService` splits text into chunks (currently size 1,000,
    overlap 150).
5.  Each Spring AI `Document` stores text and metadata such as `source`
    and `chunk_index`.
6.  `VectorStore` adds the documents to ChromaDB.

### Chat / RAG

1.  Client submits a question to `POST /api/v1/chat`.
2.  `ChatService` loads session history.
3.  ChromaDB similarity search requests up to four relevant chunks.
4.  Retrieved text and source metadata are assembled into context.
5.  Spring AI sends the context and question to the configured Groq
    model.
6.  User and assistant messages are saved in chat history.

Source labels in the generated answer are prompt-driven unless the
application validates citations programmatically.

## Prerequisites

-   JDK 17
-   Apache Maven 3.9.x (or a compatible Maven version)
-   Docker Desktop running
-   A valid Groq API key
-   Git

Verify in Windows CMD:

``` cmd
java -version
javac -version
mvn -version
docker --version
docker info
```

Ensure `JAVA_HOME` points to a JDK 17 installation.

## Configuration

### Groq API key

In Windows CMD, set the key for the current terminal session:

``` cmd
set GROQ_API_KEY=replace_with_your_new_groq_api_key
```

The variable applies only to that CMD session. Do not commit the key.
Revoke any key that was previously exposed and generate a new one.

The relevant `application.yml` configuration should look like this;
merge it with the existing file rather than replacing unrelated
settings:

``` yaml
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

``` text
jdbc:h2:file:./data/chatdb
```

Keep other existing JPA/H2 settings as needed. Do not commit local
database files.

## Run ChromaDB

NOVA expects ChromaDB at `http://localhost:8000`.

If your existing `chroma-docker` container is already running, do not
create another container on the same port. Check it first:

``` cmd
docker ps -a
docker inspect chroma-docker
curl.exe http://localhost:8000/api/v2/heartbeat
```

The development container uses a host bind mount from:

``` text
C:\Users\Govind Jha\Downloads\chroma-service\chroma-data
```

to `/data` inside the container. For a new machine, use a persistent
volume or bind mount and ensure the ChromaDB server configuration
actually stores its data in that mounted path. A bind mount alone does
not prove the server writes to it. Consult the official ChromaDB
instructions for the current image tag and startup command before
creating a new container.

## Build and run

Run commands from the project directory containing `pom.xml`.

Build and test:

``` cmd
mvn clean verify
```

Run from source:

``` cmd
mvn spring-boot:run
```

Alternatively, package and run the JAR:

``` cmd
mvn clean package
java -jar target\enterprise-ai-assistant-*.jar
```

If the wildcard matches multiple JARs, specify the exact generated
filename.

The application is expected to listen on port `8080`, unless
configuration overrides it.

## API endpoints

The controller base path is `/api/v1`.

  -----------------------------------------------------------------------------------------
  Method            Endpoint                     Purpose           Request
  ----------------- ---------------------------- ----------------- ------------------------
  `POST`            `/api/v1/documents/upload`   Upload and index  `multipart/form-data`;
                                                 a PDF             field name `file`

  `POST`            `/api/v1/chat`               Submit a chat     JSON request body as
                                                 prompt            defined by the current
                                                                   controller DTO
  -----------------------------------------------------------------------------------------

### Upload a PDF (Windows CMD)

Replace the path with a PDF available on your machine:

``` cmd
curl.exe -X POST "http://localhost:8080/api/v1/documents/upload" -F "file=@C:\path\to\document.pdf"
```

A successful upload returns a message containing the filename and number
of chunks indexed.

Use Swagger UI to inspect the exact JSON schema for `/api/v1/chat`. The
request DTO fields are intentionally not guessed in this README.

## Swagger / OpenAPI

-   Swagger UI: `http://localhost:8080/swagger-ui/index.html`
-   OpenAPI JSON: `http://localhost:8080/v3/api-docs`

If Springdoc has not yet been added, include this dependency inside
`<dependencies>` in `pom.xml`:

``` xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.13</version>
</dependency>
```

Then rebuild and restart:

``` cmd
mvn clean verify
mvn spring-boot:run
```

## Persistence and limitations

-   **Chat history:** H2 file database at `./data/chatdb`.
-   **Document vectors:** ChromaDB on port 8000.
-   **Persistence across restarts:** ChromaDB must be configured to
    write to the persistent mounted path.
-   **Duplicate uploads:** The current ingestion flow can index the same
    PDF more than once because each upload creates new document records.
    Duplicate prevention has not yet been implemented, and existing
    duplicate chunks are not automatically removed.
-   **Embedding compatibility:** Query-time and indexing-time embeddings
    must use compatible models and preprocessing. Equal vector
    dimensions alone do not guarantee compatibility.

## Troubleshooting

### Groq requests fail

-   Confirm `GROQ_API_KEY` is set in the same CMD session that launches
    the application.
-   Check that the key is valid and the model is available to your
    account.
-   Never share API keys in logs, screenshots, or source code.

### ChromaDB is unavailable

``` cmd
docker ps
curl.exe http://localhost:8000/api/v2/heartbeat
```

Confirm the container is running and Spring AI is configured for
`localhost:8000`.

### Swagger UI returns 404

-   Confirm the Springdoc dependency is in `pom.xml`.
-   Run `mvn clean verify`.
-   Restart the application and check `/v3/api-docs`.

### PDF upload works but answers do not use the document

-   Confirm upload reports a non-zero chunk count.
-   Check application logs for retrieved chunks.
-   Confirm ingestion and search use the same Chroma collection.
-   Check relevance of the retrieved chunks.
-   Verify embedding model compatibility between indexing and retrieval.

### Duplicate results appear

The current ingestion implementation may add the same PDF repeatedly.
Avoid repeated uploads while investigating. Deduplication and cleanup of
existing duplicates are separate tasks.

## GitHub security checklist

Before publishing:

-   [ ] Revoke any API key previously exposed and generate a new one.
-   [ ] Use `${GROQ_API_KEY}` instead of a literal key in YAML.
-   [ ] Add `.gitignore` entries for `target/`, local database files,
    logs, uploaded PDFs, and local ChromaDB data.
-   [ ] Review `git status` and staged changes before committing.
-   [ ] Do not commit personal or confidential PDFs.
-   [ ] If a secret was committed earlier, revoke it; deleting it from
    the latest file does not remove it from Git history.
-   [ ] Ensure README setup instructions do not contain secret values.

## License

Add a `LICENSE` file if you intend to distribute the project as open
source. Without a license, others are not explicitly granted permission
to reuse the code.
