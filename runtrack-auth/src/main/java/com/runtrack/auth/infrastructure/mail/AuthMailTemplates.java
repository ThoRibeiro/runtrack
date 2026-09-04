package com.runtrack.auth.infrastructure.mail;

/**
 * Les deux courriels d'authentification, en HTML et en texte.
 *
 * <p>Un seul squelette, paramétré : les deux messages disent la même chose dans la même forme,
 * et deux gabarits séparés divergeraient au premier changement de couleur.
 *
 * <p><b>Écrit comme on écrivait des pages en 2003</b>, et c'est volontaire : tableaux imbriqués,
 * styles en attribut, largeur fixe. Les clients de messagerie ignorent les feuilles de style
 * externes, Outlook ne connaît ni flexbox ni grid, et Gmail retire tout ce qui est dans un
 * {@code <style>}. La seule mise en forme qui arrive intacte partout est celle qui est portée
 * par l'élément lui-même.
 *
 * <p>Les couleurs sont celles du design system (<code>packages/ui/src/tokens/palette.ts</code>),
 * recopiées et non devinées : l'accent est le même bleu que le bouton de l'application.
 */
final class AuthMailTemplates {

    /** L'accent : 5,17:1 sur blanc, donc lisible en texte comme en fond de bouton. */
    private static final String BLUE = "#2563EB";
    private static final String INK = "#15264D";
    private static final String MUTED = "#53627D";
    private static final String RULE = "#DCE3EE";
    private static final String GROUND = "#F3F6FB";

    private AuthMailTemplates() {
    }

    static String verificationHtml(String link) {
        return page("Bienvenue sur RunTrack",
                "Il ne reste qu'une étape : confirmez votre adresse pour activer votre compte.",
                "Confirmer mon adresse", link,
                "Vous n'avez pas créé de compte ? Ignorez ce message, rien ne sera activé.");
    }

    static String verificationText(String link) {
        return """
                Bienvenue sur RunTrack.

                Confirmez votre adresse en ouvrant ce lien :
                %s

                Vous n'avez pas créé de compte ? Ignorez ce message, rien ne sera activé.
                """.formatted(link);
    }

    static String passwordResetHtml(String link) {
        return page("Nouveau mot de passe",
                "Vous avez demandé à changer votre mot de passe. Ce lien ne sert qu'une fois.",
                "Choisir un mot de passe", link,
                "Cette demande ne vient pas de vous ? Ignorez ce message : "
                        + "votre mot de passe actuel reste valable.");
    }

    static String passwordResetText(String link) {
        return """
                Vous avez demandé un nouveau mot de passe.

                Choisissez-en un ici :
                %s

                Ce lien ne sert qu'une fois. Cette demande ne vient pas de vous ? Ignorez ce
                message : votre mot de passe actuel reste valable.
                """.formatted(link);
    }

    /**
     * Le lien est répété en toutes lettres sous le bouton. Un client qui n'affiche pas les
     * images ou qui neutralise les liens laisserait sinon la personne sans aucun moyen d'aller
     * plus loin — et c'est le seul geste que ce message demande.
     */
    private static String page(String title, String lead, String action, String link, String note) {
        return """
                <!doctype html>
                <html lang="fr">
                <head><meta charset="utf-8"><title>%1$s</title></head>
                <body style="margin:0;padding:0;background-color:%6$s;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0"
                         style="background-color:%6$s;padding:32px 12px;">
                    <tr><td align="center">
                      <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0"
                             style="width:600px;max-width:100%%;">
                        <tr>
                          <td align="center" style="background-color:%3$s;border-radius:16px 16px 0 0;padding:28px;
                                     font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;
                                     font-size:22px;font-weight:700;color:#FFFFFF;letter-spacing:0.3px;">
                            RunTrack
                          </td>
                        </tr>
                        <tr>
                          <td style="background-color:#FFFFFF;border-radius:0 0 16px 16px;padding:36px 32px;
                                     font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
                            <p style="margin:0 0 12px;font-size:21px;font-weight:700;color:%4$s;">%1$s</p>
                            <p style="margin:0 0 28px;font-size:15px;line-height:23px;color:%5$s;">%2$s</p>
                            <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                              <tr><td align="center" bgcolor="%3$s" style="border-radius:12px;">
                                <a href="%7$s" style="display:inline-block;padding:14px 28px;font-size:16px;
                                   font-weight:600;color:#FFFFFF;text-decoration:none;border-radius:12px;">%8$s</a>
                              </td></tr>
                            </table>
                            <p style="margin:28px 0 6px;font-size:13px;color:%5$s;">
                              Le bouton ne fonctionne pas ? Copiez cette adresse :
                            </p>
                            <p style="margin:0;font-size:13px;line-height:20px;word-break:break-all;">
                              <a href="%7$s" style="color:%3$s;">%7$s</a>
                            </p>
                            <hr style="border:none;border-top:1px solid %9$s;margin:28px 0 16px;">
                            <p style="margin:0;font-size:13px;line-height:20px;color:%5$s;">%10$s</p>
                          </td>
                        </tr>
                        <tr>
                          <td align="center" style="padding:20px 8px;
                                     font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;
                                     font-size:12px;color:%5$s;">
                            RunTrack — message automatique, inutile d'y répondre.
                          </td>
                        </tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(title, lead, BLUE, INK, MUTED, GROUND, link, action, RULE, note);
    }
}
