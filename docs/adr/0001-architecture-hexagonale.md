# ADR 0001 – Architecture hexagonale dans chaque service

**Statut** : acceptée · 26/10/2026

## Contexte
Un système d'encaissement vit longtemps et change souvent d'infrastructure (base, broker, prestataire de paiement, modèle d'IA). Les règles métier (montants, statuts, sécurité des cartes) doivent rester lisibles et testables sans lancer de base de données.

## Décision
Chaque service suit trois couches :
- `domain` : le métier en Java pur (agrégat `Checkout`, records, machine à états), sans Spring, JPA ni Jackson ;
- `application` : les cas d'usage (`CheckoutService`) et les **ports** (interfaces) vers l'extérieur ;
- `infrastructure` : les adaptateurs (JPA, outbox/Kafka, REST).

Des interfaces seulement là où l'implémentation varie ou doit être simulée en test (base, messagerie), jamais « au cas où » (YAGNI). Les règles sont vérifiées à chaque build par **ArchUnit** (`ArchitectureTest`).

## Conséquences
- Tests du domaine en millisecondes, tests des cas d'usage avec des fausses implémentations en mémoire.
- Un mapping explicite domaine ↔ entités JPA (un peu plus de code, mais aucune fuite de la persistance dans le métier).
- Changer de base ou de broker ne touche que l'infrastructure.
