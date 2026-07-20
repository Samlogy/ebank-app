# Procédure — Effectuer un virement international (SWIFT / SEPA)

## Vue d'ensemble
Un virement international permet d'envoyer des fonds vers un compte détenu dans une
banque à l'étranger. eBank prend en charge les virements **SEPA** (zone euro) et
**SWIFT** (hors zone euro).

## Informations nécessaires
- **IBAN** du bénéficiaire (format international, ex: `DE89 3704 0044 0532 0130 00`).
- **BIC / SWIFT** de la banque du bénéficiaire (8 ou 11 caractères).
- Nom complet et adresse du bénéficiaire.
- Montant et devise.
- Motif du paiement (obligatoire pour les virements hors UE > 10 000 €).

## Étapes
1. Connectez-vous à votre espace eBank et ouvrez **Virements > Nouveau virement international**.
2. Ajoutez le bénéficiaire (IBAN + BIC). Un nouveau bénéficiaire est soumis à une
   validation par code OTP envoyé par SMS.
3. Saisissez le montant, la devise et le motif.
4. Choisissez l'option de frais : **SHA** (partagés, par défaut), **OUR** (à votre charge)
   ou **BEN** (à la charge du bénéficiaire).
5. Vérifiez le récapitulatif (taux de change indicatif affiché pour les devises étrangères).
6. Confirmez avec votre code OTP. Le virement passe alors au statut `PENDING`.

## Délais et frais
- **SEPA** : 0 à 1 jour ouvré, gratuit ou frais fixes selon l'offre.
- **SWIFT** : 1 à 4 jours ouvrés, frais variables (banque émettrice + intermédiaires).
- Un plafond quotidien s'applique (voir la procédure « Plafonds et limites »).

## Sécurité et conformité
- Tout virement international déclenche un contrôle **AML/KYC** automatique.
- Les virements vers des pays sous sanctions sont bloqués.
- En cas de doute sur un bénéficiaire, contactez un conseiller avant de confirmer.

## Annulation
Un virement au statut `PENDING` peut être annulé depuis **Virements > En cours**.
Une fois au statut `COMPLETED`, il faut demander un **rappel de fonds** (recall), sans
garantie de récupération.
