# Politique de sécurité

*[English version](../SECURITY.md)*

## Versions supportées

Les correctifs de sécurité ne sont fournis que pour la [dernière release](https://github.com/jul-m/keycloak-workflow-send-verify-email/releases), et uniquement pour les versions Keycloak listées dans le [tableau de compatibilité du README](../../README.fr.md#compatibilité). Les releases plus anciennes ne reçoivent aucun correctif de sécurité : merci de mettre à jour vers la dernière release et, si votre version Keycloak est entre-temps sortie de cette plage, vers une version supportée.

## Signaler une vulnérabilité

Merci de **ne pas** signaler de vulnérabilité de sécurité via une issue publique, une discussion ou une pull request GitHub.

Signalez-la plutôt en privé via [GitHub Security Advisories](https://github.com/jul-m/keycloak-workflow-send-verify-email/security/advisories/new) : cela permet de coordonner directement avec vous un correctif et sa divulgation.

Merci d'inclure autant que possible les éléments suivants :

- Une description de la vulnérabilité et de son impact potentiel
- Les étapes pour la reproduire, ou une preuve de concept
- La ou les versions affectées de cette extension et de Keycloak
- Une mesure de contournement, si vous en connaissez une

## À quoi vous attendre

Nous nous efforcerons de :

- Accuser réception de votre signalement
- Mener l'investigation et vous tenir informé de l'avancement vers un correctif
- Publier une nouvelle release et une alerte de sécurité (security advisory) une fois le correctif disponible, en vous créditant sauf si vous préférez rester anonyme

## Périmètre

Ce projet est un provider Keycloak (un JAR déployé dans `providers/`) et ne fonctionne pas comme un service autonome. Les vulnérabilités de Keycloak lui-même doivent être signalées au [projet Keycloak](https://www.keycloak.org/security).
