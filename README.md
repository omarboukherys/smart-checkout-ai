# Smart Checkout AI

![CI](https://github.com/omarboukherys/smart-checkout-ai/actions/workflows/ci.yml/badge.svg)

Plateforme d'**encaissement omnicanal** (caisse en magasin, site web, application mobile) construite en **microservices event-driven**, avec un **agent IA** qui assiste l'analyse des paiements suspects.

> Projet personnel de démonstration, construit étape par étape : Java 21, Spring Boot 4, Kafka, PostgreSQL, MongoDB, Spring AI, Vue.js 3.

## Architecture cible

```mermaid
flowchart LR
    POS[Caisse POS] --> CS
    WEB[Site web] --> CS
    MOB[App mobile] --> CS
    CS[checkout-service<br/>PostgreSQL + inbox/outbox<br/>orchestre la saga] -- checkout.events --> K[(Kafka)]
    K -- checkout.events --> RS[risk-service<br/>règles + MongoDB]
    RS -- risk.events --> K
    K -- risk.events --> CS
    CS -- payment.commands --> K
    K -- payment.commands --> PS[payment-service<br/>PostgreSQL + PSP simulé]
    PS -- payment.events --> K
    K -- payment.events --> CS
    K -- paiements en attente --> AG[agent-service<br/>Spring AI]
    BO[Back-office Vue.js] --> CS
    BO --> AG
```

| Étape | Contenu | Statut |
|---|---|---|
| 1 | `checkout-service` : API REST idempotente, PostgreSQL, outbox → Kafka, tests, CI | ✅ |
| 2 | `risk-service` : score de risque explicable, consommateur idempotent, MongoDB, dead-letter topic | ✅ |
| 3 | `payment-service` + saga orchestrée par le checkout : PSP simulé idempotent, inbox/outbox, validation humaine | ✅ |
| 4 | `agent-service` : agent Spring AI (outils, sortie structurée, validation humaine) | 🚧 |
| 5 | Front Vue.js 3 : caisse et back-office | 🚧 |
| 6 | Qualité : SonarCloud, couverture, observabilité | 🚧 |

## Choix techniques

- **Architecture hexagonale** : domaine en Java pur, ports et adaptateurs, règles vérifiées par ArchUnit → [ADR 0001](docs/adr/0001-architecture-hexagonale.md)
- **Outbox transactionnelle** : l'encaissement et son événement sont enregistrés dans la même transaction, aucun événement perdu → [ADR 0002](docs/adr/0002-outbox-transactionnelle.md)
- **Idempotence** (`Idempotency-Key`), **montants en centimes**, **aucun numéro de carte** (jeton PSP, PCI-DSS) → [ADR 0003](docs/adr/0003-idempotence-et-montants.md)
- **Erreurs normalisées** RFC 9457 (`ProblemDetail`) : 400 format, 404, 409 doublon simultané, 422 règle métier
- **Contrats d'événements** typés (interface `sealed` + discriminant `type`) dans un module partagé sans framework
- **Score de risque explicable** : règles indépendantes (montant, vélocité de la carte, pays carte/IP, nouvel appareil), score entier sur 100, chaque décision accompagnée de ses raisons ; ajouter une règle = ajouter une classe (principe ouvert/fermé)
- **Consommateur idempotent**, temps de l'événement, dead-letter topic ; jeton de carte jamais stocké (empreinte SHA-256) → [ADR 0004](docs/adr/0004-consommateur-idempotent.md)
- **Saga** : chorégraphie pour le risque, orchestration du paiement par le checkout, **inbox + outbox** (chaque événement appliqué une seule fois), **validation humaine** des paiements en attente, paiement idempotent jusqu'au prestataire (clé d'idempotence, état `AUTHORIZING` avant l'appel, pas de transaction ouverte pendant l'appel distant) → [ADR 0005](docs/adr/0005-saga-encaissement.md)

### Parcours d'un paiement

```mermaid
sequenceDiagram
    participant C as Caisse / Web / Mobile
    participant CS as checkout-service
    participant RS as risk-service
    participant PS as payment-service
    participant PSP as Prestataire (simulé)
    C->>CS: POST /checkouts (Idempotency-Key)
    CS-->>C: 201 PENDING_PAYMENT
    CS->>RS: CheckoutRequested (outbox)
    RS->>CS: RiskAssessed (APPROVE / REVIEW / BLOCK)
    alt APPROVE
        CS->>PS: AuthorizePayment (outbox)
        PS->>PSP: authorize (clé d'idempotence)
        PSP-->>PS: approuvé / refusé
        PS->>CS: PaymentAuthorized / PaymentDeclined
        Note over CS: PAID / DECLINED
    else REVIEW
        Note over CS: ON_HOLD jusqu'à la décision d'un superviseur
        C->>CS: POST /checkouts/{id}/review (APPROVE)
        CS->>PS: AuthorizePayment
    else BLOCK
        Note over CS: REJECTED
    end
```

### Décisions du risk-service

| Score | Décision | Exemple |
|---|---|---|
| 0 – 39 | `APPROVE` | achat en caisse de 114,30 MAD |
| 40 – 69 | `REVIEW` (mise en attente, analysée par l'agent IA) | 8 999 MAD en ligne, carte FR, IP MA → 60 ; 5e paiement en 10 min avec la même carte → 45 |
| 70 – 100 | `BLOCK` | 25 000 MAD en ligne, carte FR, IP MA → 80 |

## Stack

Java 21 · Spring Boot 4.1 · Spring Data JPA (Hibernate 7) · Flyway · Spring Kafka · Apache Kafka 4.1 (KRaft) · PostgreSQL 17 · MongoDB 8 · JUnit 6 · AssertJ · ArchUnit · Testcontainers 2 · Docker · GitHub Actions

## Démarrage rapide

Prérequis : Docker Desktop, JDK 21 (ou IntelliJ IDEA).

```bash
# 1. Infrastructure : PostgreSQL, MongoDB, Kafka, Kafka UI
docker compose up -d

# 2a. Lancer les services depuis l'IDE (CheckoutServiceApplication, RiskServiceApplication, PaymentServiceApplication), ou :
# 2b. tout lancer dans Docker
docker compose --profile app up -d --build
```

Essayer l'API : ouvrir [`docs/requests.http`](docs/requests.http) dans IntelliJ, ou lancer `scripts/new-checkout.ps1` sous Windows.

| Service | URL |
|---|---|
| checkout-service | http://localhost:18101/api/v1/checkouts |
| risk-service | http://localhost:18102/api/v1/risk-assessments/{checkoutId} |
| payment-service | http://localhost:18103/api/v1/payments/{checkoutId} |
| Santé | `/actuator/health` sur chaque port |
| Kafka UI | http://localhost:18190 |

### API `checkout-service`

| Méthode | Chemin | Réponses |
|---|---|---|
| `POST` | `/api/v1/checkouts` (en-tête `Idempotency-Key`) | 201 créé · 200 rejeu · 400 · 409 · 422 |
| `GET` | `/api/v1/checkouts/{id}` | 200 · 404 |
| `GET` | `/api/v1/checkouts?limit=50` | 200 (liste résumée, sans lignes de panier) |
| `POST` | `/api/v1/checkouts/{id}/review` (`APPROVE` / `REJECT`, superviseur, commentaire) | 200 · 404 · 422 si l'encaissement n'est pas en attente |

### API `risk-service`

| Méthode | Chemin | Réponses |
|---|---|---|
| `GET` | `/api/v1/risk-assessments/{checkoutId}` | 200 (score, décision, raisons) · 404 |

### API `payment-service`

| Méthode | Chemin | Réponses |
|---|---|---|
| `GET` | `/api/v1/payments/{checkoutId}` | 200 (statut, référence du prestataire, motif de refus) · 404 |

Les paiements démarrent uniquement par la commande Kafka `AuthorizePayment`. Jetons de test du prestataire simulé : `insufficient`, `expired`, `stolen` (refus), `flaky` (2 pannes puis succès), montant > 50 000 MAD (plafond).

## Tests

```bash
mvn verify
```

- **Domaine** (`CheckoutTest`, `RiskEngineTest`, `PaymentTest`) : règles métier en Java pur.
- **Cas d'usage** (`CheckoutServiceTest`, `CheckoutOrchestratorTest`, `RiskAssessmentServiceTest`, `PaymentServiceTest`) : ports remplacés par des implémentations en mémoire (idempotence, rejeu, saga, reprise après panne).
- **Architecture** (`ArchitectureTest` dans chaque service) : le domaine ne dépend d'aucun framework, l'application ne dépend pas de l'infrastructure.
- **Intégration** (Testcontainers, conteneurs partagés par JVM) :
  - `CheckoutApiIntegrationTest` (PostgreSQL + Kafka) : API, outbox, saga APPROVE → commande → `PAID`, mise en attente puis validation humaine ;
  - `RiskServiceIntegrationTest` (MongoDB + Kafka) : décision explicable, rejeu idempotent, dead-letter topic ;
  - `PaymentServiceIntegrationTest` (PostgreSQL + Kafka) : autorisation, rejeu sans second débit, refus, prestataire instable relancé.

La CI GitHub Actions exécute l'ensemble à chaque push.

## Auteur

Omar Boukherys · [LinkedIn](https://www.linkedin.com/in/omar-boukherys-a891191b2)
