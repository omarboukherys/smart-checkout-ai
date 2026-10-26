# ADR 0003 – Idempotence des encaissements, montants en centimes, pas de numéro de carte

**Statut** : acceptée · 26/10/2026

## Contexte
En caisse comme en ligne, une requête de paiement peut être renvoyée (coupure réseau, double clic, nouvelle tentative automatique). Un encaissement manipule de l'argent et des données de carte.

## Décision
1. **Idempotence** : chaque `POST /api/v1/checkouts` porte un en-tête `Idempotency-Key`. Si la clé est connue, on renvoie l'encaissement existant (200) au lieu d'en créer un autre (201). Une contrainte `UNIQUE` en base bloque deux requêtes simultanées (409).
2. **Montants en unités mineures** (`long`, centimes) : jamais de `double`, aucune erreur d'arrondi ; bornes sur les quantités et les prix pour rendre tout dépassement impossible.
3. **Pas de numéro de carte (PAN)** : seul le jeton du prestataire de paiement, les 4 derniers chiffres et le pays transitent. Une valeur qui ressemble à un PAN est refusée (422), ce qui garde le service hors du périmètre PCI-DSS. Le jeton n'est jamais renvoyé par l'API.

## Conséquences
- Le client n'est jamais débité deux fois pour la même intention de paiement.
- Limite connue : une même clé réutilisée avec un contenu différent renvoie l'encaissement d'origine ; en production on comparerait une empreinte de la requête (422 si elle diffère).
