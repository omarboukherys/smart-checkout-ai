# ADR 0004 – Consommateur Kafka idempotent et dead-letter topic (risk-service)

**Statut** : acceptée · 26/10/2026

## Contexte
Kafka livre les messages **au moins une fois** : après un crash ou un rééquilibrage, un `CheckoutRequested` peut être reçu deux fois. Le risk-service écrit dans MongoDB puis publie `RiskAssessed`. Certains messages peuvent aussi être illisibles (JSON invalide, type inconnu).

## Décision
1. **Idempotence par construction** : l'id de l'encaissement est l'`_id` du document MongoDB. À la deuxième réception, rien n'est recalculé : la décision enregistrée est republiée avec le **même `eventId`**, que les consommateurs aval utilisent pour dédupliquer.
2. **Pas d'outbox ici** : on enregistre puis on publie, de façon synchrone, avant d'acquitter le message. Si le service tombe entre les deux, le message est relivré et la décision enregistrée est republiée. (Côté checkout, une requête HTTP ne se rejoue pas, d'où l'outbox de l'ADR 0002.)
3. **Gestion des erreurs** : 3 nouvelles tentatives à 1 s d'intervalle pour les pannes passagères ; un message illisible part directement dans `checkout.events.DLT`, sans bloquer la partition.
4. **Temps de l'événement** : l'historique (vélocité de la carte, appareils connus) est mesuré avec la date du paiement, pas l'heure de traitement : rejouer les messages redonne les mêmes décisions.

## Conséquences
- Aucun encaissement noté deux fois différemment, même en cas de rejeu.
- Les messages en dead-letter doivent être surveillés (alerte sur le nombre de messages) et rejoués après correction.
- Limite : un type d'événement inconnu part en dead-letter ; si de nouveaux types sont ajoutés côté checkout, il faudra les ignorer explicitement ici (lecteur tolérant).
