# Microloan Management Platform

## Project Demo / Reference

[Project Drive Link](https://drive.google.com/file/d/1sByyE22TgsLCiGQPLQWj6n8bt0nrvYp9/view?usp=sharing)

---

# Project Overview

End-to-end microloan management platform for digital lending, built with Java, Spring Boot, and Kubernetes.

The platform follows a modular microservices architecture consisting of:

* **Core Loan Service (`micro-loan-piyush-zeta`)**
* **Notification Microservice (`micro-loan-piyush-zeta-notif`)**
* **Cloud-native deployment infrastructure (`cluster-spec`)**

Designed for:

* High reliability
* Horizontal scalability
* Financial compliance
* Secure and resilient distributed processing

---

# Key Features & Responsibilities

## Loan Origination & Management

Implemented complete digital lending workflows including:

* Borrower onboarding
* KYC verification
* Loan application
* Approval & rejection flows
* Loan disbursement
* Repayment processing
* Loan closure lifecycle

### Business Rules Implemented

* KYC level verification checks
* Income eligibility validation
* Principal amount validation
* Loan tenure validation
* RBI-compliant EMI-to-income ratio enforcement

### Concurrency & Repayment Handling

* FIFO allocation of repayments across installments
* Pessimistic locking for concurrency safety
* Transaction-safe repayment reconciliation

---

## Event-Driven Architecture

Implemented asynchronous communication using **Atropos Event Bus**.

### Published Domain Events

* Loan application created
* Loan approved
* Loan rejected
* Loan disbursed
* Repayment received
* Loan closed

### Benefits

* Loose coupling between services
* Improved scalability
* Better auditability
* Reliable event processing

---

## Notification Microservice (`micro-loan-piyush-zeta-notif`)

Dedicated microservice for transactional communication.

### Responsibilities

* Consumed loan lifecycle events
* Sent transactional emails
* Generated and attached PDF documents (KFS)
* Centralized notification workflows

### Notifications Supported

* KYC OTP emails
* Loan approval/rejection notifications
* Repayment receipts
* Loan closure confirmations

---

## Exception Handling & Validation

Implemented robust API validation and centralized exception handling.

### Features

* Structured error codes
* Consistent API error responses
* UUID validation for all resource identifiers
* Request payload validation
* Business exception mapping

---

## Cloud-Native Deployment (`cluster-spec`)

Designed Kubernetes-native deployment infrastructure.

### Kubernetes Components

* Helm charts
* Deployments
* Services
* Horizontal Pod Autoscaler (HPA)
* ConfigMaps
* Monitoring integrations

### CI/CD

* Jenkins pipelines for:

  * Build
  * Test
  * Deployment
* Environment promotion:

  * Development
  * Staging
  * Production

---

# Testing & Quality

Implemented strong testing and quality assurance practices.

### Testing

* Unit tests using JUnit
* Integration testing
* Postman collections for API validation

### Quality Tooling

* Jacoco code coverage
* Static code analysis
* CI-integrated quality gates
* Allure reporting

---

# Documentation & Collaboration

Prepared comprehensive engineering documentation including:

* README documentation
* API documentation
* Release notes
* Deployment instructions

Collaborated closely with:

* QA teams
* DevOps engineers
* Product stakeholders

Delivered features within sprint timelines while maintaining production stability.

---

# Tech Stack

## Backend

* Java 17
* Spring Boot 3.x
* Spring Data JPA
* Hibernate
* PostgreSQL

## DevOps & Infrastructure

* Docker
* Kubernetes
* Helm
* Jenkins
* Maven

## Eventing & Tooling

* Atropos Event Bus
* Allure Reporting
* Jacoco
* Postman
* JUnit
* Lombok
* MapStruct

---

# Architecture Highlights

* Distributed microservices architecture
* Event-driven communication
* Cloud-native deployment model
* Scalable loan processing workflows
* Transaction-safe repayment handling
* Modular notification infrastructure
* CI/CD enabled delivery pipeline

---

# Author

Piyush
