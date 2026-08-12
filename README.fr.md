# Step de workflow Keycloak : `send-verify-email`

[![CI](https://github.com/jul-m/keycloak-workflow-send-verify-email/actions/workflows/ci.yml/badge.svg)](https://github.com/jul-m/keycloak-workflow-send-verify-email/actions/workflows/ci.yml)
[![CodeQL](https://github.com/jul-m/keycloak-workflow-send-verify-email/actions/workflows/codeql.yml/badge.svg)](https://github.com/jul-m/keycloak-workflow-send-verify-email/actions/workflows/codeql.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

*[English version](README.md)*

Extension Keycloak qui ajoute un step de workflow, `send-verify-email`, envoyant à l'utilisateur un
lien signé de vérification d'adresse email — le même lien d'action que celui produit par l'endpoint
`send-verify-email` de l'API Admin native — depuis n'importe quel
[workflow Keycloak](https://www.keycloak.org/docs/latest/server_admin/#_workflows).

- **Sommaire**
  - [Pourquoi ce step](#pourquoi-ce-step)
  - [Prérequis](#prérequis)
  - [Installation](#installation)
  - [Compatibilité](#compatibilité)
  - [Démarrage rapide](#démarrage-rapide)
  - [Référence de configuration](#référence-de-configuration)
  - [Modes d'envoi](#modes-denvoi)
  - [Variables de template](#variables-de-template)
  - [Exemples](#exemples)
  - [Comportement et limites](#comportement-et-limites)
  - [Dépannage](#dépannage)
  - [Contribuer](#contribuer)
  - [Sécurité](#sécurité)
  - [Licence](#licence)

## Pourquoi ce step

Les steps de workflow natifs de Keycloak permettent d'envoyer une simple notification à un
utilisateur (`notify-user`), mais aucun ne permet d'envoyer un lien *actionnable*, qui laisse
l'utilisateur vérifier son adresse email, définir un mot de passe et exécuter les required actions
de son compte. Aujourd'hui, il faut appeler l'endpoint `send-verify-email` de l'API Admin depuis un
script externe pour obtenir ce résultat.

Ce step apporte cette capacité directement dans le moteur de workflows de Keycloak, ce qui permet de
couvrir sans automatisation externe des scénarios comme :

- **Onboarding** — à la création d'un compte (`user-created`, ou à l'ajout dans un groupe), envoyer
  automatiquement un lien de vérification qui permet aussi à l'utilisateur de définir son mot de
  passe.
- **Relances** — renvoyer périodiquement un lien de vérification aux utilisateurs qui n'ont jamais
  confirmé leur adresse.
- **Re-vérification** — réinitialiser `emailVerified` et redemander une confirmation d'adresse après
  un changement de politique ou de fournisseur de messagerie.

Comparé à un message `notify-user` fait main, le lien est un véritable token d'action signé :
validé, rattaché à un client, avec une durée de vie configurable et une redirection optionnelle
après vérification. La validation de l'utilisateur, du client et de l'URI de redirection, ainsi que
toutes les valeurs par défaut, sont déléguées au même code interne que l'API Admin native
(`UserResource.verifySendEmailParams`), afin de rester cohérent avec le reste de Keycloak.

## Prérequis

| Prérequis | Détails |
| --- | --- |
| Keycloak | Voir le [tableau de compatibilité](#compatibilité) ci-dessous. |
| Fonctionnalité `workflows` | Fonctionnalité Keycloak officiellement supportée, activée par défaut : aucun `--features=workflows` à passer (sauf si un administrateur l'a explicitement désactivée). |
| SMTP du realm | Le realm doit disposer d'une configuration **Realm settings → Email** fonctionnelle : le step envoie ses emails via le fournisseur d'email de Keycloak. |
| `frontendUrl` du realm | **Obligatoire.** Un step de workflow s'exécute hors d'une requête HTTP entrante : le lien de vérification est donc toujours construit à partir de la `frontendUrl` du realm, jamais depuis une URI de requête. Sans elle, le step échoue à l'exécution — voir [Comportement et limites](#comportement-et-limites). |
| Java | Java 17 (JDK), uniquement pour compiler depuis les sources. Utiliser le JAR publié ne nécessite rien d'autre que Keycloak. |

## Installation

1. Télécharger le JAR depuis la page
   [Releases](https://github.com/jul-m/keycloak-workflow-send-verify-email/releases). Chaque release
   fournit un **JAR unique** couvrant toutes les versions Keycloak listées dans ses notes de
   version ; le numéro de version de l'extension n'encode jamais une version de Keycloak.
2. Vérifier la somme de contrôle avant déploiement, par rapport au digest SHA256 que GitHub calcule
   et publie lui-même pour l'asset (visible sur la page de release, ou via `gh release view`) :

   ```bash
   version=<version> # ex. 0.1.0
   jar="keycloak-workflow-send-verify-email-$version.jar"
   remote_sha="$(gh release view "v$version" --json assets \
     --jq ".assets[] | select(.name == \"$jar\") | .digest" | cut -d: -f2)"
   [[ "$(sha256sum "$jar" | cut -d' ' -f1)" == "$remote_sha" ]] && echo OK || echo MISMATCH
   ```
3. Copier le JAR dans le répertoire `providers/` de Keycloak.
4. Reconstruire l'augmentation du serveur puis redémarrer Keycloak :

   ```bash
   bin/kc.sh build
   bin/kc.sh start
   ```

Pour confirmer que le step est bien enregistré, vérifier que `send-verify-email` figure dans la
catégorie de providers `workflow-step` :

- **Console d'administration** : passer sur le realm **master**, ouvrir **Server info**, puis
  l'onglet **Provider info**.
- **API REST** :

  ```bash
  curl -s -H "Authorization: Bearer $TOKEN" \
    "https://keycloak.example.com/admin/serverinfo" \
    | jq '.providers."workflow-step".providers | keys'
  ```

## Compatibilité

<!-- kc-compat:start -->
| Keycloak | Extension |
| --- | --- |
| 26.7.x | Dernière release (voir les [Releases](https://github.com/jul-m/keycloak-workflow-send-verify-email/releases) pour les versions antérieures de l'extension et leur plage de compatibilité) |
<!-- kc-compat:end -->

Cette extension prend en charge Keycloak 26.7 et les versions suivantes, comme déclaré dans
[`scripts/kc-versions.sh`](scripts/kc-versions.sh), qui fait foi pour le tableau ci-dessus. Chaque version listée est
recompilée et retestée à chaque release, et chaque release GitHub indique précisément la plage sur
laquelle elle a été validée. Les nouvelles versions de Keycloak sont testées automatiquement avec de
vrais tests d'intégration (voir
[`new-keycloak-version.yml`](.github/workflows/new-keycloak-version.yml)) et ajoutées à la plage
supportée via une pull request revue, jamais poussées directement.

## Démarrage rapide

Le plus simple est de passer par la console d'administration. Ouvrez la page **Workflows** de votre
realm et créez un nouveau workflow : l'éditeur de la console attend la définition sous forme de YAML,
pas un formulaire avec un champ par paramètre — même si le step déclare les propriétés listées dans
la [référence de configuration](#référence-de-configuration) ci-dessous, la console ne les affiche pas
individuellement. Collez ce qui suit ; cela envoie un lien de vérification à chaque nouvel utilisateur
créé, avec le template verify-email standard de Keycloak :

```yaml
name: Verify email on user creation
on: user-created
steps:
  - uses: send-verify-email
```

Créez un utilisateur avec une adresse email dans ce realm : il reçoit l'email de vérification.

Pour du scripting ou de l'automatisation, la même définition peut aussi être postée via l'API Admin
REST, qui accepte aussi bien le YAML que le JSON. Enregistrez-la dans `workflow.yml` puis exécutez :

```bash
curl -X POST "https://keycloak.example.com/admin/realms/myrealm/workflows" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/yaml" \
  --data-binary @workflow.yml
```

> `$TOKEN` est un token d'accès administrateur pour le realm cible. `Content-Type: application/json`
> est également accepté si vous préférez poster l'équivalent JSON.

## Référence de configuration

Le step s'applique aux ressources de type **utilisateur**. Tous les paramètres sont optionnels :

| Paramètre | Type | Défaut | Description |
| --- | --- | --- | --- |
| `message` | chaîne | *(aucun)* | Corps d'email personnalisé. Le renseigner bascule le step en [mode message personnalisé](#modes-denvoi). Accepte les [variables de template](#variables-de-template). |
| `subject` | chaîne | `accountNotificationSubject` | Sujet utilisé en mode message personnalisé. Normalement une clé de message Keycloak ; accepte les [variables de template](#variables-de-template). |
| `client_id` | chaîne | Le client système utilisé par l'API Admin native | Client servant à valider `redirect_uri` et inscrit dans le token d'action. |
| `redirect_uri` | chaîne | *(aucun)* | Destination de l'utilisateur après vérification réussie. Validée contre les URI de redirection déclarées du client `client_id`. Accepte les [variables de template](#variables-de-template). |
| `lifespan` | entier (secondes) | La durée `action-token-generated-by-admin` du realm | Durée de validité du token d'action généré. |
| `reset_email_verification` | booléen | `false` | Si `true`, remet `emailVerified` à `false` et ajoute la required action native `VERIFY_EMAIL` avant l'envoi. |

## Modes d'envoi

La présence de `message` sélectionne l'un des deux modes, mutuellement exclusifs.

**Mode par défaut** (sans `message`) : délègue à `EmailTemplateProvider.sendVerifyEmail`, qui utilise
le template verify-email standard du thème. L'email est strictement identique à celui envoyé par
l'API Admin native `send-verify-email` : toute personnalisation de thème déjà en place continue de
fonctionner telle quelle.

**Mode message personnalisé** (avec `message`) : rend le template de notification de workflow
(`workflow-notification.ftl`, celui utilisé par `notify-user`) avec votre message en corps, après
remplacement des [variables de template](#variables-de-template) dans `message` et dans `subject`.

Dans ce mode, `subject` est résolu comme celui de `notify-user` : normalement une clé de message
recherchée dans le bundle de messages du thème. Les variables sont remplacées d'abord ; si la valeur
obtenue ne correspond à aucune clé du bundle, elle est utilisée telle quelle comme sujet. Les deux
formes fonctionnent donc :

```yaml
subject: emailVerificationSubject             # résolu depuis le bundle de messages du thème
subject: Validation de votre compte ${realm.name}  # utilisé comme texte littéral
```

## Variables de template

Les variables suivantes sont utilisables dans `message`, `subject` et `redirect_uri` :

| Variable | Valeur |
| --- | --- |
| `${link}` | Le lien signé de vérification d'adresse email. **Non disponible dans `redirect_uri`**, qui est résolue et validée avant la génération du lien. |
| `${user.<attribut>}` | Première valeur de l'attribut utilisateur `<attribut>`, comme avec `notify-user`. Couvre les champs de profil (`username`, `email`, `firstName`, `lastName`) et les attributs personnalisés. |
| `${realm.name}` | Nom technique du realm. |
| `${realm.displayName}` | Nom d'affichage du realm. |
| `${realm.frontendUrl}` | La `frontendUrl` configurée sur le realm, si elle existe. |
| `${realm.baseUrl}` | URL de base calculée de Keycloak, par exemple `https://keycloak.example.com/realms`. |
| `${realmFullBaseUrl}` | URL complète du realm, incluant son nom, par exemple `https://keycloak.example.com/realms/myrealm`. |

Une variable inconnue ne résout rien et reste telle quelle dans le résultat — repérer un `${...}`
littéral dans un email reçu si une valeur semble manquante.

Lire un attribut utilisateur **personnalisé** (non déclaré dans le profil utilisateur du realm)
suppose en plus que la politique d'attributs non gérés (unmanaged attributes) du realm l'autorise —
sans quoi `${user.department}` et consorts ne résolvent rien.

## Exemples

Workflow d'onboarding complet : message personnalisé avec un lien valable 48 heures, redirection vers
l'espace utilisateur, et réinitialisation de `emailVerified` pour forcer la confirmation d'adresse.

```yaml
name: Onboarding new users
on: user-created
steps:
  - uses: send-verify-email
    with:
      subject: Validation de votre compte ${realm.name}
      lifespan: "172800"
      reset_email_verification: true
      client_id: account
      redirect_uri: ${realmFullBaseUrl}/account
      message: >-
        <p>Bonjour ${user.firstName} ${user.lastName},</p>

        <p>Un compte a été créé pour vous par votre administrateur.</p>

        <p><a href="${link}">Cliquez ici</a> pour vérifier votre adresse email, définir
        votre mot de passe et activer votre compte. Ce lien est valable 48 heures.</p>

        <p>Passé ce délai, vous pouvez toujours activer votre compte depuis votre
        <a href="${realmFullBaseUrl}/account">espace utilisateur</a>, avec le nom
        d'utilisateur « ${user.username} » ou votre adresse email, et le mot de passe
        temporaire défini par votre administrateur — ou via le lien « Mot de passe
        oublié ».</p>

        <p>Cordialement,<br/>L'équipe technique</p>
```

D'autres définitions prêtes à poster sont disponibles dans [`docs/examples/`](docs/examples/).

## Comportement et limites

- **La `frontendUrl` est obligatoire en pratique.** Un step de workflow s'exécute hors d'une requête
  HTTP entrante — qu'il soit déclenché par un événement comme `user-created` ou par un déclencheur
  planifié — il n'y a donc normalement aucune URI de requête à partir de laquelle construire le lien.
  Le step se rabat sur la `frontendUrl` du realm ; si elle n'est pas définie, il échoue avec une
  erreur explicite plutôt que de produire un lien cassé ou relatif.
- **Les utilisateurs non éligibles sont ignorés silencieusement.** L'éligibilité est déléguée à
  `UserResource.verifySendEmailParams`, qui exige un utilisateur actif et doté d'une adresse email.
  Si l'une des deux conditions manque, le moteur de workflows intercepte et journalise l'erreur :
  aucun email n'est envoyé, et rien d'autre — création de l'utilisateur incluse — n'est perturbé.
- **Un échec d'envoi ne fait pas échouer le workflow.** Une erreur SMTP est journalisée en niveau
  `ERROR` et le workflow continue. Surveiller le log serveur pour
  `Failed to send verify email to user ...`.
- **`reset_email_verification` s'applique avant l'envoi** et n'est pas annulé si l'envoi échoue
  ensuite. L'utilisateur reste alors avec `emailVerified = false` et la required action
  `VERIFY_EMAIL`, ce qui le bloque à la connexion tant qu'aucun lien ne lui parvient — relancer le
  workflow, ou passer par l'API Admin, pour lui en envoyer un nouveau.
- **Le mode message personnalisé ignore le template verify-email du thème.** Il rend le template de
  notification de workflow à la place : adapter le HTML en conséquence.

## Dépannage

| Symptôme | Cause probable |
| --- | --- |
| `send-verify-email` est absent de l'onglet Provider info du realm master ou de `serverinfo` | JAR absent de `providers/`, ou `bin/kc.sh build` non relancé après la copie. |
| Aucun email, aucune erreur dans le log | L'utilisateur est désactivé ou n'a pas d'adresse email — voir [Comportement et limites](#comportement-et-limites). |
| `Failed to send verify email to user ...` dans le log | Configuration SMTP du realm absente ou erronée. La tester depuis **Realm settings → Email**. |
| `Cannot generate a verify-email link from an asynchronous workflow` | Le workflow s'est exécuté hors requête HTTP et le realm n'a pas de `frontendUrl`. La définir dans **Realm settings → General**. |
| Les liens pointent vers le mauvais hôte (adresse interne, par exemple) | Définir la `frontendUrl` du realm, ou l'option `hostname` de Keycloak, sur l'URL joignable depuis l'extérieur. |
| L'email contient un `${user.quelquechose}` littéral | Variable inconnue, ou attribut personnalisé bloqué par la politique d'attributs non gérés du realm. |
| `400 Bad Request` à la création du workflow | URI de redirection invalide pour le `client_id` choisi, ou paramètre de step inconnu. Le corps de la réponse nomme la valeur fautive. |

## Contribuer

Les contributions sont les bienvenues. Voir [CONTRIBUTING.FR.md](docs/fr/CONTRIBUTING.FR.md) pour
l'installation locale, les conventions de code, la structure des tests et le processus de pull
request, et [CODE_OF_CONDUCT.fr.md](CODE_OF_CONDUCT.fr.md) pour les règles de la communauté.

## Sécurité

Merci de ne pas signaler de vulnérabilité via une issue publique. Voir
[SECURITY.FR.md](docs/fr/SECURITY.FR.md) pour le processus de divulgation.

## Licence

Distribué sous [licence Apache 2.0](LICENSE).
