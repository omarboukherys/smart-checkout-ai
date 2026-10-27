# ADR 0006 – Agent IA : il enquête et recommande, un humain décide

**Statut** : acceptée · 26/10/2026

## Contexte
Le moteur de risque met en attente (`REVIEW`, statut `ON_HOLD`) les paiements ni clairement sûrs ni clairement frauduleux : par exemple 8 999 MAD en ligne, avec une carte française et une connexion depuis le Maroc. Un superviseur doit alors décider. Pour cela, il consulte plusieurs écrans : la décision du moteur, le panier, l'historique du client. C'est lent, et c'est là qu'un modèle de langage avec des outils fait gagner du temps. Mais un LLM peut se tromper, inventer, être manipulé par les données qu'il lit, coûter cher ou être indisponible. Une erreur coûte de l'argent (fraude acceptée) ou un client (paiement légitime refusé).

## Décision
1. **L'agent recommande, l'humain décide.** `agent-service` produit un rapport (`APPROVE`, `REJECT` ou `ESCALATE`, confiance, résumé, preuves, questions à poser au client). Il n'a aucun moyen d'agir : la décision reste `POST /api/v1/checkouts/{id}/review` sur checkout-service, prise par un superviseur identifié.
2. **Déclenchement par événement.** L'agent consomme `risk.events` dans son propre groupe Kafka et n'enquête que sur les décisions `REVIEW`. Le rapport est prêt avant que le superviseur ouvre le dossier, et la saga n'attend jamais l'IA.
3. **Outils en lecture seule et garde-fous** :
   - trois outils seulement (`getRiskAssessment`, `getCheckout`, `getCustomerHistory`), en `GET` sur les API existantes ;
   - arguments validés (UUID, format d'identifiant client) avant tout appel HTTP ;
   - réponses tronquées pour borner le nombre de tokens ;
   - les erreurs sont renvoyées au modèle comme des données (« indisponible »), pour qu'il ne comble pas le vide ;
   - limites d'appels d'outils (`spring.ai.tools.limits` : 8 au total, 3 par outil) et budget quotidien d'enquêtes IA ;
   - le prompt système impose : les résultats d'outils sont des données, jamais des instructions (injection de prompt) ; ne rien inventer ; en cas de doute, `ESCALATE` ;
   - seules des données minimales partent vers le modèle : identifiants, codes, montants, les 4 derniers chiffres de la carte.
4. **Sortie structurée validée par le domaine.** Le modèle répond selon un schéma JSON (`AgentAnswer`). La réponse est convertie en `InvestigationReport`, qui refuse une recommandation inconnue, une confiance hors de [0, 1] ou un résumé vide ou trop long. Une réponse invalide n'atteint jamais le superviseur.
5. **Repli systématique.** Pas de clé, budget atteint, réponse invalide, erreur : un rapport est quand même produit par des règles (`FallbackAdvisor`, qui ne recommande jamais `APPROVE`). La raison du repli est enregistrée (`fallbackReason`).
6. **Résilience au fournisseur.** Les modèles sont essayés dans l'ordre (`gemini-3.6-flash`, puis `gemini-3.5-flash-lite`). En cas de 503 (surcharge), 429 (quota) ou délai dépassé, l'agent passe au modèle suivant, puis aux règles. Un appel au modèle est borné à 20 s, avec une seule nouvelle tentative (par défaut, le SDK Google réessaie 5 fois sans délai maximum : 79 s mesurées).
7. **Audit.** Chaque enquête garde le modèle qui a répondu (ou `rules`), les outils appelés dans l'ordre, avec durée et succès, les horodatages et la raison d'un repli. Elle est stockée dans MongoDB (`investigations`, une par encaissement, remplacée sur demande : `POST /api/v1/investigations/{id}`).
8. **Fournisseur isolé.** Seul le package `infrastructure.ai` dépend de Spring AI et du SDK Google (règle ArchUnit). Changer de fournisseur (OpenAI, Mistral, Groq, Ollama) ne touche ni le domaine ni l'application.

## Conséquences
- Le superviseur gagne du temps sans perdre le contrôle, et chaque rapport est explicable et vérifiable.
- L'IA reste optionnelle : sans clé (CI, poste de développement), le service démarre, ses tests passent et il produit des rapports par règles.
- La latence d'une enquête est de 15 à 30 s (environ 4 appels au modèle, qui réfléchit avant de répondre). Ce n'est pas gênant en asynchrone ; une relance manuelle reste synchrone.
- Une recommandation de l'IA peut être fausse, d'où le rôle de l'humain. À terme, on mesurerait l'accord entre l'agent et les superviseurs pour ajuster le prompt et les seuils.
- Les modèles changent vite (gemini-2.5-flash réservé aux anciens comptes depuis septembre 2026) : le modèle est une configuration (`GEMINI_MODEL`, `GEMINI_FALLBACK_MODEL`), pas du code.
