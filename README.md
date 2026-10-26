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
    CS[checkout-service<br/>PostgreSQL + outbox] -- checkout.events --> K[(Kafka)]
    K -- checkout.events --> RS[risk-service<br/>règles + MongoDB]
    RS -- risk.events --> K
    K -- risk.events --> PS[payment-service<br/>PSP simulé]
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
| 3 | `payment-service` : saga d'encaissement, PSP simulé, retour du statut | 🚧 |
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

# 2a. Lancer les services depuis l'IDE (CheckoutServiceApplication, RiskServiceApplication), ou :
# 2b. tout lancer dans Docker
docker compose --profile app up -d --build
```

Essayer l'API : ouvrir [`docs/requests.http`](docs/requests.http) dans IntelliJ, ou lancer `scripts/new-checkout.ps1` sous Windows.

| Service | URL |
|---|---|
| checkout-service | http://localhost:18101/api/v1/checkouts |
| risk-service | http://localhost:18102/api/v1/risk-assessments/{checkoutId} |
| Santé | http://localhost:18101/actuator/health · http://localhost:18102/actuator/health |
| Kafka UI | http://localhost:18190 |

### API `checkout-service`

| Méthode | Chemin | Réponses |
|---|---|---|
| `POST` | `/api/v1/checkouts` (en-tête `Idempotency-Key`) | 201 créé · 200 rejeu · 400 · 409 · 422 |
| `GET` | `/api/v1/checkouts/{id}` | 200 · 404 |
| `GET` | `/api/v1/checkouts?limit=50` | 200 (liste résumée, sans lignes de panier) |

### API `risk-service`

| Méthode | Chemin | Réponses |
|---|---|---|
| `GET` | `/api/v1/risk-assessments/{checkoutId}` | 200 (score, décision, raisons) · 404 |

## Tests

```bash
mvn verify
```

- **Domaine** (`CheckoutTest`, `RiskEngineTest`) : règles métier en Java pur.
- **Cas d'usage** (`CheckoutServiceTest`, `RiskAssessmentServiceTest`) : ports remplacés par des implémentations en mémoire (idempotence, rejeu, fenêtre de vélocité).
- **Architecture** (`ArchitectureTest` dans chaque service) : le domaine ne dépend d'aucun framework, l'application ne dépend pas de l'infrastructure.
- **Intégration** (Testcontainers) : `CheckoutApiIntegrationTest` avec PostgreSQL et Kafka réels, de la requête HTTP au message Kafka ; `RiskServiceIntegrationTest` avec MongoDB et Kafka réels, de `CheckoutRequested` à `RiskAssessed`, rejeu idempotent et dead-letter topic.

La CI GitHub Actions exécute l'ensemble à chaque push.

## Auteur

Omar Boukherys · [LinkedIn](https://www.linkedin.com/in/omar-boukherys-a891191b2)
