# ADR 0005 – Saga d'encaissement : chorégraphie pour le risque, orchestration pour le paiement

**Statut** : acceptée · 26/10/2026

## Contexte
Un encaissement traverse trois services : checkout (création), risk (décision), payment (prestataire de paiement). Il n'y a pas de transaction distribuée possible entre eux. Pour débiter la carte, payment-service a besoin du montant et du jeton de paiement, que seul checkout-service possède (risk-service ne garde jamais le jeton). Certains paiements doivent attendre une décision humaine.

## Décision
1. **Chorégraphie pour le risque** : checkout publie `CheckoutRequested`, risk-service réagit et publie `RiskAssessed`. Aucun service ne pilote l'autre ; d'autres consommateurs (statistiques, agent IA) peuvent s'abonner sans rien changer.
2. **Orchestration du paiement par checkout-service**, propriétaire du cycle de vie de l'encaissement :
   - `APPROVE` → commande `AuthorizePayment` (topic `payment.commands`) ;
   - `REVIEW` → `ON_HOLD`, jusqu'à la décision d'un superviseur (`POST /api/v1/checkouts/{id}/review`) ;
   - `BLOCK` → `REJECTED` ;
   - `PaymentAuthorized` → `PAID`, `PaymentDeclined` → `DECLINED`.
3. **Inbox + outbox** dans checkout-service : l'id de chaque événement reçu est inséré dans `processed_event` (`ON CONFLICT DO NOTHING`), dans la même transaction que le changement de statut et que l'écriture de la commande dans l'outbox. Chaque événement est appliqué une seule fois, et une commande n'est envoyée que si le changement d'état est validé.
4. **payment-service idempotent** : un seul paiement par encaissement (clé primaire), état `AUTHORIZING` enregistré avant l'appel au prestataire, clé d'idempotence transmise au prestataire, identifiant d'événement déterministe pour les republications. Pas de transaction ouverte pendant l'appel distant.
5. **Lecteur tolérant** : une décision inconnue ou un événement tardif sur un encaissement terminé est journalisé et ignoré.

## Conséquences
- Le chemin de chaque encaissement est lisible à un seul endroit (`CheckoutOrchestrator`), et la validation humaine s'y branche naturellement (agent IA à l'étape 4).
- Cohérence à terme : l'API renvoie `PENDING_PAYMENT` à la création, le statut final arrive en général en moins d'une seconde ; le front devra l'interroger ou recevoir une notification.
- Pas de compensation à écrire ici, car aucun débit n'a lieu avant l'autorisation. Avec une capture différée ou un remboursement, il faudrait ajouter des commandes compensatoires (`VoidAuthorization`, `Refund`).
