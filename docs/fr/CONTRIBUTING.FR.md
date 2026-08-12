# Contribuer

*[English version](../CONTRIBUTING.md)*

Merci de l'intérêt porté à ce projet. Ce document décrit l'installation locale, les conventions de code et le déroulement des pull requests.

## Prérequis

- Java 17 (JDK)
- Maven (ou le wrapper `mvn` déjà résolu par votre environnement)

Docker n'est pas nécessaire pour développer en local. Les tests d'intégration utilisent par défaut le mode `distribution` de Keycloak, qui télécharge et décompresse une vraie version de Keycloak pour l'exécuter comme un simple processus externe — aucun conteneur n'entre en jeu, et aucun n'est utilisé en CI non plus (voir « Ajouter le support d'une nouvelle version Keycloak » plus bas).

## Organisation du projet

- `src/main/java` — le provider du step de workflow et sa factory (`SendVerifyEmailStepProvider`, `SendVerifyEmailStepProviderFactory`)
- `src/main/resources/META-INF/services` — fichier d'enregistrement SPI découvert par Keycloak au démarrage
- `src/test/java` — le test unitaire (métadonnées du step) et les tests d'intégration (`*IT.java`), basés sur le test-framework officiel de Keycloak
- `scripts/build-tests.sh` — construit et valide (`mvn clean verify`) le provider pour une version Keycloak donnée, ou pour toutes les versions supportées avec `all` ; sans argument, utilise la version déjà déclarée dans `pom.xml`. Accepte aussi une version qui n'est pas encore listée dans `kc-versions.sh` (avec un avertissement) : c'est ainsi que `new-keycloak-version.yml` teste les versions candidates.
- `scripts/kc-versions.sh` — source de vérité unique pour la plage de versions Keycloak supportées
- `scripts/compute-next-version.sh` — calcule le prochain numéro de version sémantique de l'extension à partir de la dernière release publiée et d'un bump `minor`/`patch` ; utilisé par `ci.yml` sur les branches `feat/*`/`fix/*`/`kc-fix/*` (voir « Versionning et releases » plus bas)
- `scripts/render-readme-compat-table.sh` — régénère le tableau de compatibilité dans `README.md`/`README.fr.md` à partir de `scripts/kc-versions.sh`
- `scripts/check-pom-kc-versions-sync.sh` — échoue si la version de build de `pom.xml` diverge de la version maximale supportée dans `scripts/kc-versions.sh`
- `docs/` — documentation de contribution et de sécurité (en anglais dans `docs/`, en français dans `docs/fr/`)
- `docs/examples/` — définitions de workflow prêtes à poster, référencées depuis le README
- `.github/workflows/` — CI, release, CodeQL et `new-keycloak-version.yml` (détection planifiée des nouvelles versions Keycloak)

## Build et tests

L'extension est livrée sous la forme d'un JAR unique avec son propre numéro de version sémantique (voir `scripts/kc-versions.sh` pour les versions Keycloak supportées, qui ne sont jamais encodées dans la version de l'artefact lui-même). Sur `main`, `pom.xml` compile toujours contre la version Keycloak supportée la plus récente.

Pour construire et valider intégralement l'extension sur une version Keycloak précise (tests unitaires et d'intégration) :

```bash
bash scripts/build-tests.sh 26.7
```

Sans argument, la commande valide contre la version déjà déclarée dans `pom.xml` (c'est-à-dire la version de build courante) :

```bash
bash scripts/build-tests.sh
```

Pour valider en une seule fois toutes les versions Keycloak listées dans `scripts/kc-versions.sh` — un run `mvn clean verify` par version, avec arrêt à la première qui échoue —, passez `all` :

```bash
bash scripts/build-tests.sh all
```

Cette commande exécute systématiquement `mvn clean verify`, qui couvre en un seul passage les tests unitaires et d'intégration et démarre un vrai serveur Keycloak grâce au [test-framework officiel de Keycloak](https://github.com/keycloak/keycloak/tree/main/test-framework) (API Admin, realms/utilisateurs/workflows, faux serveur SMTP pour capturer les emails). Le mode du serveur est piloté par la variable d'environnement `KC_TEST_SERVER` ; par défaut, il vaut `distribution` (une vraie version de Keycloak décompressée et lancée comme processus externe — voir « Prérequis » ci-dessus). Il est aussi possible d'invoquer Maven directement et de choisir le mode `embedded`, qui démarre Keycloak dans la même JVM que les tests — pratique pour déboguer localement (voir « VS Code » ci-dessous) :

```bash
KC_TEST_SERVER=embedded mvn verify
```

Avant d'ouvrir une pull request, vérifiez que `mvn verify` passe en local. La propriété `revision` de `pom.xml` doit rester une valeur littérale (pas de placeholder `${...}`) : le résolveur Maven propre au test-framework l'exige. Le workflow de release contourne cette contrainte en passant explicitement `-Dproject-version=...`/`-Drevision=...`.

### Structure des tests

Tous les scénarios du step `send-verify-email` se trouvent sous `src/test/java/.../sendverifyemail/` :

- `SendVerifyEmailStep*Scenario.java` — une classe `abstract` par scénario (mode par défaut, message personnalisé, client/redirect URI, reset-email-verification, éligibilité de l'utilisateur, planification), qui porte les méthodes `@Test` et leurs assertions.
- `SendVerifyEmailStepIT.java` — la seule classe réellement exécutée par Maven ou un IDE. Elle enveloppe chaque scénario dans une sous-classe `@Nested @KeycloakIntegrationTest`, où chaque méthode `@Test` est redéclarée en une ligne, via un `@Override` qui appelle `super.xxx()`.

Pour ajouter un nouveau scénario, suivez ce modèle : créez un fichier `*Scenario.java`, puis reliez-le à `SendVerifyEmailStepIT.java`. Deux règles comptent et sont faciles à oublier :
- La redéclaration `@Override` — et non un corps `@Nested` vide — est indispensable pour que le panneau Testing de VS Code détecte le test : il ne résout pas les méthodes `@Test` héritées d'un autre fichier, alors même que Maven les exécute très bien dans les deux cas.
- C'est aussi cette structure qui permet à l'exécution du fichier `SendVerifyEmailStepIT.java` dans son ensemble de démarrer un **unique** serveur Keycloak partagé par tous les scénarios (`LifeCycle.GLOBAL` nécessite une seule JVM, et le Test Runner de VS Code lance une JVM par classe de premier niveau), plutôt qu'un serveur par scénario.

### VS Code

`.vscode/settings.json` propose deux profils `java.test.config` : `tests-fast` (mode `distribution`, activé par défaut) et `tests-with-debug` (mode `embedded`, pour déboguer pas à pas le code du provider). Trois pièges à connaître :

- **Processus Keycloak orphelin** : si un run `tests-fast` est interrompu en cours de route (bouton Stop, déconnexion du débogueur), le processus Keycloak externe peut rester actif et continuer à occuper le port 8080. Pour y remédier : `lsof -nP -iTCP:8080 -sTCP:LISTEN`, puis `kill <pid>`.
- **`KC_TEST_SERVER_REUSE` est cassé sur macOS** : ne l'utilisez pas. Le `fuser` intégré à macOS ne prend pas en charge la syntaxe `-n tcp` dont le test-framework a besoin pour déterminer si un serveur peut être réutilisé.
- **`tests-with-debug` (mode embedded) échoue avec `ERROR: The ForkJoinPool has been initialized with the wrong thread factory`** : cette erreur peut devenir reproductible (et non plus seulement occasionnelle) après plusieurs renommages ou rechargements de fichiers au sein d'une même session VS Code, même sans aucune modification de `pom.xml` ni de `.vscode/settings.json`. La cause n'est ni un dossier de sortie `bin/` obsolète, ni un mauvais JDK — deux pistes à écarter en premier car rapides à vérifier (`rm -rf bin/`, et le panneau Java Projects → projet → Classpath → JDK Runtime doit afficher `JavaSE-17`). Si ces deux vérifications sont concluantes et que l'erreur persiste, lancez **« Java: Clean Workspace Cache »** depuis la palette de commandes, puis rechargez la fenêtre : cette opération purge l'état obsolète conservé par le Java Language Server, ce qui constitue la véritable correction dans ce cas.

## Conventions de code

- Le code reste en anglais, y compris les commentaires, les messages de log et les messages de commit.
- Privilégiez des changements petits et ciblés. Évitez les abstractions spéculatives ou les options de configuration qui ne sont pas nécessaires au changement en cours.
- N'ajoutez un commentaire que lorsque le *pourquoi* n'est pas évident à la lecture du code (contournement, contrainte non évidente, invariant subtil). Ne reformulez pas ce que le code dit déjà.
- Tout nouveau comportement doit être couvert par un nouveau scénario (voir « Structure des tests » ci-dessus), en respectant la structure de test existante (voir `support/AbstractSendVerifyEmailWorkflowTest.java`).
- Alignez autant que possible les paramètres de configuration du step et leurs valeurs par défaut sur l'API Admin native (`UserResource.verifySendEmailParams`), comme documenté dans le README.

## Documentation

La documentation est bilingue : chaque page possède un équivalent qui doit être mis à jour dans la même pull request.

| Anglais | Français |
| --- | --- |
| `README.md` | `README.fr.md` |
| `docs/CONTRIBUTING.md` | `docs/fr/CONTRIBUTING.FR.md` |
| `docs/SECURITY.md` | `docs/fr/SECURITY.FR.md` |
| `CODE_OF_CONDUCT.md` | `CODE_OF_CONDUCT.fr.md` |
| `docs/examples/*.yml` | `docs/examples/*.fr.yml` |

`CHANGELOG.md` reste uniquement en anglais. Deux règles supplémentaires s'appliquent :

- Le tableau de compatibilité des deux READMEs est encadré par les marqueurs `<!-- kc-compat:start -->` / `<!-- kc-compat:end -->` et généré automatiquement : modifiez `scripts/kc-versions.sh` puis lancez `bash scripts/render-readme-compat-table.sh`, plutôt que d'éditer le tableau à la main.
- Tout changement des paramètres de configuration du step, des variables de template, des valeurs par défaut ou du comportement doit être répercuté dans les deux READMEs ainsi que dans `CHANGELOG.md`, sous `[Unreleased]`.

## Versionning et releases

Les releases sont déclenchées par la fusion d'une pull request depuis une branche portant l'un de ces préfixes — il n'y a pas d'étape de release manuelle séparée pour un changement normal :

| Préfixe de branche | Incrément | À utiliser pour |
| --- | --- | --- |
| `feat/*` | mineur (`X.Y.0`) | Une nouvelle fonctionnalité |
| `fix/*` | patch (`X.Y.Z`) | Un correctif de bug |
| `kc-fix/*` | patch (`X.Y.Z`) | La correction d'une incompatibilité Keycloak (voir « Ajouter le support d'une nouvelle version Keycloak » plus bas) |

`ci.yml` calcule la prochaine version à partir de la dernière release publiée (`scripts/compute-next-version.sh`) et la committe dans `pom.xml` (`project-version`/`revision`) — inutile d'incrémenter la version vous-même. La fusion de la PR déclenche ensuite `release.yml`, qui construit, tague et publie la release automatiquement, avec la version déjà committée par `ci.yml`.

Une branche `kc-support/*` (ouverte automatiquement par `new-keycloak-version.yml`, voir plus bas) fonctionne différemment : elle n'incrémente pas la version de l'extension et ne déclenche pas de release, puisqu'aucun code n'a changé — la fusionner ne fait qu'étendre la plage de compatibilité documentée sur les notes de la dernière release déjà publiée.

### Si vous contribuez depuis un fork

`ci.yml` committe l'incrément de version directement sur votre branche — ce qui demande un accès en écriture que GitHub refuse aux checks lancés sur une pull request venant d'un fork. Seul `ci.yml` a besoin de s'exécuter sur votre fork pour lever ce blocage (les autres workflows du dépôt ne concernent pas les forks) : si Actions n'y est pas déjà activé, faites-le dans les paramètres de votre fork (**Settings → Actions**, ou via la bannière proposée à la première ouverture de l'onglet Actions). Une fois activé, chaque push sur votre branche y déclenche `ci.yml` avec un accès en écriture complet, qui committe l'incrément de version pour vous. Si le check échoue ici en signalant que la branche n'est pas à jour, c'est justement le signal qu'il faut laisser `ci.yml` tourner sur votre fork, puis repousser.

## Ajouter le support d'une nouvelle version Keycloak

Le tableau `SUPPORTED_KC_VERSIONS` de `scripts/kc-versions.sh` est la source de vérité unique pour la plage de compatibilité Keycloak de l'extension. Elle doit rester une liste contiguë de versions mineures (sans trou), et sa dernière entrée doit toujours correspondre à la version de build de `pom.xml` — invariant vérifié en CI par `scripts/check-pom-kc-versions-sync.sh`.

Pour étendre la plage à une version plus récente :

1. Faire passer `keycloak.version` dans `pom.xml` au patch `.0` de la nouvelle version (par exemple `26.8.0`).
2. Ajouter la version à `SUPPORTED_KC_VERSIONS` dans `scripts/kc-versions.sh` (en fin de liste).
3. Lancer `bash scripts/build-tests.sh all` pour valider toutes les versions désormais listées dans `scripts/kc-versions.sh`, y compris la nouvelle, et corriger les éventuelles incompatibilités d'API.
4. Lancer `bash scripts/render-readme-compat-table.sh` pour rafraîchir le tableau de compatibilité dans `README.md`/`README.fr.md`.

Si une version listée devient incompatible et qu'aucun correctif ne permet de la faire fonctionner sur toute la plage, retirez-la de `scripts/kc-versions.sh` plutôt que de livrer une version cassée. Retirer une version autre que la plus basse impose de retirer également tout ce qui se trouve en dessous, afin que la liste reste sans trou. Les utilisateurs d'une version retirée doivent alors rester sur une version antérieure de l'extension, ou mettre à jour Keycloak.

Les nouvelles versions Keycloak publiées au-delà du maximum actuel sont normalement détectées automatiquement par `.github/workflows/new-keycloak-version.yml` (planifié chaque semaine, ou déclenchable manuellement via `workflow_dispatch`, en forçant éventuellement `keycloak_version`). Il exécute la même suite de tests d'intégration réels que ci-dessus (`scripts/build-tests.sh <version>`) contre la version candidate — pas un simple test de fumée — et ouvre une pull request plutôt que de pousser directement sur `main` :

- **Tests réussis** — ouverture d'une PR depuis une branche `kc-support/<version>` qui fait passer `pom.xml` et `scripts/kc-versions.sh` à la nouvelle version et rafraîchit les deux READMEs ; aucune modification de code. La fusionner ne fait qu'étendre la plage de compatibilité documentée sur les notes de la dernière release déjà publiée — pas de reconstruction, pas de nouvelle release, puisque le code actuel fonctionne déjà sur cette version.
- **Tests en échec** — ouverture d'une PR en brouillon (*draft*) depuis une branche `kc-fix/<version>`, avec la même mise à jour de la liste de versions, plus une issue de suivi que la PR referme à la fusion. La CI de cette branche reste rouge tant que l'incompatibilité n'a pas réellement été corrigée (contournement par réflexion runtime si seule une signature a changé, ou retrait de la version de `scripts/kc-versions.sh` si ce n'est pas le cas — voir « Si une version listée devient incompatible » ci-dessus). Comme fusionner cette PR correspond à un vrai changement de code, elle est versionnée et publiée exactement comme une branche `fix/*` (voir « Versionning et releases » ci-dessus) — aucun calcul de version spécifique.

Ce workflow n'a pas encore été exercé de bout en bout face à une véritable nouvelle version mineure de Keycloak : en attendant, déclenchez-le manuellement via `workflow_dispatch` et vérifiez son résultat avant de lui faire confiance.

## Pull requests

1. Forker le dépôt et créer une branche depuis `main`, nommée `feat/<courte-description>` ou `fix/<courte-description>` selon le type de changement (voir « Versionning et releases » ci-dessus).
2. Effectuer la modification, avec des tests couvrant le nouveau comportement.
3. Vérifier que `mvn verify` passe (tests unitaires et d'intégration) et que la CI est verte.
4. Ouvrir une pull request décrivant le changement et sa motivation, en liant toute issue associée.
5. Rester réactif aux retours de revue : des commits de suivi petits et incrémentaux sont préférés aux réécritures avec force-push en cours de revue.

## Signaler des bugs et demander des fonctionnalités

Utilisez les templates d'issue GitHub. Recherchez d'abord dans les issues existantes pour éviter les doublons.

## Failles de sécurité

N'ouvrez pas d'issue publique pour signaler une faille de sécurité. Voir [SECURITY.FR.md](SECURITY.FR.md).

## Code de conduite

Ce projet suit le [Code de conduite](../../CODE_OF_CONDUCT.fr.md). En y participant, vous acceptez de vous y conformer.
