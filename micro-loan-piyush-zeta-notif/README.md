# Microloan Notification Service

## Overview
The Microloan Notification Service is a Spring Boot-based microservice responsible for handling notifications within the microloan platform. It consumes notification events, processes them using a strategy pattern, and delivers notifications through various channels. The service is designed for extensibility, reliability, and seamless integration with the broader microloan ecosystem.

## Features
- **Spring Boot** backend for event-driven notification processing
- **Notification strategy pattern** for flexible delivery (e.g., Atropos, Direct)
- **Kafka/Queue consumer** for event ingestion (if applicable)
- **REST endpoints** for health and admin operations
- **Comprehensive unit and integration tests**
- **Helm charts** for Kubernetes deployment
- **Docker support**

## Project Structure
```
micro-loan-piyush-zeta-notif/
├── src/
│   ├── main/java/in/zeta/...
│   ├── main/resources/
│   ├── test/java/in/zeta/...
│   └── test/resources/
├── helm-chart/
│   ├── Chart.yaml
│   ├── values.yaml
│   └── templates/
├── Dockerfile
├── pom.xml
├── mvnw, mvnw.cmd
├── ci.Jenkinsfile
├── ...
```

## Setup & Build
1. **Clone the repository**
   ```sh
   git clone <repo-url>
   cd micro-loan-piyush-zeta-notif
   ```
2. **Build the project**
   ```sh
   ./mvnw clean install
   ```
3. **Run tests**
   ```sh
   ./mvnw test
   ```
4. **Run locally**
   ```sh
   ./mvnw spring-boot:run
   ```

## Notification Processing
- Consumes notification events (e.g., from Kafka or REST, as configured).
- Uses a strategy pattern to route notifications to the appropriate delivery channel.
- Easily extendable for new notification types or channels.
- See `notification/` package for strategy implementations.

## API Usage
- Exposes REST endpoints for health checks and admin operations.
- Notification consumption is typically event-driven (e.g., Kafka consumer).
- For details, see the controller and consumer classes in `src/main/java/in/zeta/`.

## Testing
- Unit and integration tests are located in `src/test/java/in/zeta/`.
- Coverage reports are generated in `target/site/jacoco/`.