# ADR 0002 – Outbox transactionnelle pour publier les événements

**Statut** : acceptée · 26/10/2026

## Contexte
Créer un encaissement demande deux écritures : la ligne en base et l'événement Kafka. Les faire séparément (« double écriture ») crée deux pannes possibles : encaissement enregistré mais événement perdu, ou événement publié pour un encaissement annulé par un rollback.

## Décision
L'événement est écrit dans la table `outbox_event`, **dans la même transaction** que l'encaissement. Un relais (`OutboxRelay`) lit les lignes en attente toutes les 500 ms avec `FOR UPDATE SKIP LOCKED`, les publie sur Kafka (clé = id de l'encaissement) puis les marque publiées.

## Conséquences
- Aucun événement perdu : si Kafka est indisponible, les lignes attendent et repartent ensuite.
- Livraison **au moins une fois** : un crash entre l'envoi et le commit renvoie l'événement. Chaque événement porte un `eventId` pour que les consommateurs dédupliquent.
- Ordre garanti par encaissement (même clé → même partition ; le relais s'arrête au premier échec).
- Plusieurs instances peuvent relayer en parallèle grâce à `SKIP LOCKED`.
- Alternative envisagée : CDC avec Debezium, plus robuste à grande échelle mais une brique d'infrastructure de plus. À reconsidérer si le volume l'exige.
- À prévoir en production : purge des lignes publiées, métrique sur l'âge de la plus ancienne ligne en attente.
