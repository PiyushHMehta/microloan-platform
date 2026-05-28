# Warroom Microloan Platform

## Overview
The Microloan Platform is a Spring Boot-based microservice designed to manage microloans, borrowers, and related operations. It features secure data handling with field-level encryption, flexible notification strategies, and robust filtering for entity queries. The project is part of a larger multi-module system and is production-ready for cloud-native deployments.

## Features
- **Spring Boot** backend with RESTful APIs
- **JPA/Hibernate** for ORM and dynamic queries
- **AES-256 encryption** for sensitive fields
- **Notification strategy pattern** for flexible notification delivery
- **Specification-based filtering** for advanced search
- **Comprehensive unit and integration tests**
- **Helm charts** for Kubernetes deployment
- **Docker support**

## Project Structure
```
warroom/
├── src/
│   ├── main/java/...
│   ├── main/resources/
│   ├── test/java/...
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
   cd <project>
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

## API Usage
- The REST API exposes endpoints for managing borrowers, loan applications, products, and more.
- See the [postman.json](../postman.json) collection for example requests and workflows.
- **Note:** For encrypted fields (e.g., borrower personal info), filtering supports only exact matches (equality). Partial/LIKE filtering is not supported on encrypted fields.

## Encryption & Filtering Logic
- Sensitive fields (e.g., fullName, email, phoneNumber) are encrypted using AES-256/CBC/PKCS5Padding via a JPA AttributeConverter.
- **Filtering:**
  - Encrypted fields: Only equality filtering is supported. Filter values must be encrypted before comparison.
  - Non-encrypted string fields: LIKE (case-insensitive) filtering is supported.
  - Enum/boolean fields: Equality filtering.
- See `EncryptionService.java` and service classes for implementation details.

## Notification Strategy
- Uses a strategy pattern for notification delivery (e.g., Atropos, Direct).
- `NotificationDispatcher` routes notifications to the appropriate strategy.
- Easily extendable for new notification channels.