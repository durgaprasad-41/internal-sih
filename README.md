# EXAMIQ – AI-Powered Academic Intelligence Platform

EXAMIQ is an AI-powered previous-question-paper repository and academic intelligence platform. The application follows the requirements in the provided SRS and is implemented as a modular monorepo with a React frontend, Spring Boot backend, Python AI service, and MySQL persistence layer.

## Stack

- Frontend: React, Tailwind CSS, React Router
- Backend: Java 21, Spring Boot 3, Spring Security, JWT, JPA, MySQL
- AI Service: Python, FastAPI, OCR, embeddings, similarity search
- Storage: Local filesystem abstraction for future S3/Cloudinary integration
- Deployment: Docker, Docker Compose

## Repository layout

- backend/ – Spring Boot API
- frontend/ – React + Tailwind client
- ai-service/ – FastAPI AI microservice
- db/ – SQL schema and seed scripts
- docs/ – Architecture, API, ER, and roadmap documentation
- docker-compose.yml – local orchestration
- .env.example – environment configuration template

## Local startup

1. Copy `.env.example` to `.env` and adjust values
2. Start MySQL and the services with Docker Compose
3. Start the backend via Maven
4. Start the AI service via uvicorn or FastAPI
5. Start the frontend via Vite

## Development phases

1. Project setup
2. MySQL + JPA modeling
3. Auth + JWT + roles
4. Student portal
5. Faculty portal
6. Admin portal
7. Paper upload + storage
8. Python AI services
9. OCR + segmentation
10. Semantic search + FAISS
11. Duplicate + subject + quality checks
12. Verification pipeline
13. Analytics + generator
14. Notifications + reputation
15. Tests + deployment

## Main principles

- Use real database operations and REST APIs
- Keep AI service separate from the Spring application
- Do not expose JPA entities directly through controllers
- Use DTOs and role-based security
- Keep file storage separate from relational data

## Docs

- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- [docs/DATABASE-ER.md](docs/DATABASE-ER.md)
- [docs/API-DESIGN.md](docs/API-DESIGN.md)
- [docs/ROADMAP.md](docs/ROADMAP.md)
