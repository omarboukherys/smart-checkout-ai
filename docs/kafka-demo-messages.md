# Messages de démonstration à envoyer avec Kafka UI

Kafka UI : http://localhost:18190 → **Topics** → choisir le topic → bouton **Produce Message** → remplir **Key** et **Value** → **Produce Message**.

## Paiement autorisé (topic `payment.commands`)

Key :
```
11111111-1111-1111-1111-111111111111
```
Value :
```json
{"type":"AuthorizePayment","commandId":"aaaaaaaa-0000-0000-0000-000000000001","checkoutId":"11111111-1111-1111-1111-111111111111","issuedAt":"2026-10-26T16:00:00Z","amountMinor":11430,"currency":"MAD","paymentMethod":"CARD","paymentToken":"tok_visa_4242"}
```
Résultat : `GET http://localhost:18103/api/v1/payments/11111111-1111-1111-1111-111111111111` → `AUTHORIZED`, et un `PaymentAuthorized` dans `payment.events`.

## Prestataire instable : 2 échecs puis succès (topic `payment.commands`)

Key :
```
22222222-2222-2222-2222-222222222222
```
Value :
```json
{"type":"AuthorizePayment","commandId":"aaaaaaaa-0000-0000-0000-000000000002","checkoutId":"22222222-2222-2222-2222-222222222222","issuedAt":"2026-10-26T16:00:00Z","amountMinor":11430,"currency":"MAD","paymentMethod":"CARD","paymentToken":"tok_flaky_01"}
```
Résultat : dans les logs, deux erreurs « Provider timeout » espacées de 1 s puis 2 s, puis `-> AUTHORIZED`.

## Fonds insuffisants (topic `payment.commands`)

Key :
```
33333333-3333-3333-3333-333333333333
```
Value :
```json
{"type":"AuthorizePayment","commandId":"aaaaaaaa-0000-0000-0000-000000000003","checkoutId":"33333333-3333-3333-3333-333333333333","issuedAt":"2026-10-26T16:00:00Z","amountMinor":11430,"currency":"MAD","paymentMethod":"CARD","paymentToken":"tok_insufficient_01"}
```
Résultat : `DECLINED (INSUFFICIENT_FUNDS)` et un `PaymentDeclined` dans `payment.events`.
