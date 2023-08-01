package fr.neolegal.fec.liassefiscale;

import java.util.Set;

import fr.neolegal.fec.Fec;

public class LiasseFiscaleHelper {

    public final static Character MIN_REPERE = 'A';
    public final static Character MAX_REPERE = 'O';
    public final static int MIN_NUM_COMPTE = 1;
    public final static int MAX_NUM_COMPTE = 7999;
    public final static String REPERE_REGEX = "(?i)([" + MIN_REPERE + "-" + MAX_REPERE + "][A-Z])";

    private LiasseFiscaleHelper() {
    }

    static LiasseFiscale buildLiasseFiscale(Fec fec) {
        return LiasseFiscale.builder()
                .bilanActif(buildBilanActif(fec))
                .bilanPassif(buildBilanPassif(fec))
                .compteDeResultatEnListe(buildCompteDeResultatsEnListe(fec))
                .compteDeResultat(buildCompteDeResultats(fec))
                .build();
    }

    public static TableauComptable buildBilanActif(Fec fec) {
        return buildTableauComptable(fec, "Bilan - actif", "2050", "2050-SD 2023", Set.of("AA", "AB"));
    }

    public static TableauComptable buildBilanPassif(Fec fec) {
        return buildTableauComptable(fec, "Bilan - passif", "2051", "2051-SD 2023",
                Set.of("DD", "DG", "DH", "DL", "EE"));
    }

    public static TableauComptable buildCompteDeResultatsEnListe(Fec fec) {
        return buildTableauComptable(fec, "Compte de résultat de l'exercice (en liste)", "2052", "2052-SD 2023",
                Set.of("FL", "FR", "GF", "FY", "FZ", "GG", "GV", "GW"));
    }

    public static TableauComptable buildCompteDeResultats(Fec fec) {
        return buildTableauComptable(fec, "Compte de résultat de l'exercice", "2053", "2053-SD 2023",
                Set.of("HI", "HK", "HN"));
    }

    private static TableauComptable buildTableauComptable(Fec fec, String nom, String numero, String cerfa,
            Set<String> reperes) {
        TableauComptable tableau = new TableauComptable(nom, numero, cerfa, null);

        for (String repere : reperes) {
            LigneRepere ligneRepere = LigneRepere.get(repere);
            double montant = LigneRepereHelper.computeMontantLigneRepere(ligneRepere, fec);
            tableau.getLignes().put(ligneRepere, montant);
        }
        return tableau;
    }
}
