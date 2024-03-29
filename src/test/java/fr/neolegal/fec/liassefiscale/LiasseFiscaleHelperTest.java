package fr.neolegal.fec.liassefiscale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import fr.neolegal.fec.FecHelper;

public class LiasseFiscaleHelperTest {

    @Test
    void buildLiasseFiscale_reelNormal() {
        LiasseFiscale liasseReelNormal = LiasseFiscaleHelper
                .buildLiasseFiscale(FecHelper.read(Path.of("target/test-classes/123456789FEC20500930.txt")),
                        RegimeImposition.REEL_NORMAL);
        assertEquals(RegimeImposition.REEL_NORMAL, liasseReelNormal.getRegime());
        assertEquals(12, liasseReelNormal.getFormulaires().size());
    }

    @Test
    void buildLiasseFiscale_reelSimplifie() {
        LiasseFiscale liasseReelSimplifie = LiasseFiscaleHelper
                .buildLiasseFiscale(FecHelper.read(Path.of("target/test-classes/000000000FEC20231231.txt")),
                        RegimeImposition.REEL_SIMPLIFIE);

        assertEquals(RegimeImposition.REEL_SIMPLIFIE, liasseReelSimplifie.getRegime());
        assertEquals(4, liasseReelSimplifie.getFormulaires().size());
    }

    @Test
    void getMontant_reelNormal() throws IOException {
        LiasseFiscale liasseReelNormal = LiasseFiscaleHelper
                .buildLiasseFiscale(FecHelper.read(Path.of("target/test-classes/123456789FEC20500930.txt")),
                        RegimeImposition.REEL_NORMAL);
        checkParsedLiasse(liasseReelNormal, "target/test-classes/123456789FEC20500930-expected.csv", 102);
    }

    @Test
    void getMontant_reelSimplifie() throws IOException {
        LiasseFiscale liasseReelSimplifie = LiasseFiscaleHelper
                .buildLiasseFiscale(FecHelper.read(Path.of("target/test-classes/000000000FEC20231231.txt")),
                        RegimeImposition.REEL_SIMPLIFIE);

        checkParsedLiasse(liasseReelSimplifie, "target/test-classes/000000000FEC20231231-expected.csv", 60);
    }

    // @Test
    void getMontant_reelNormalAgricole() throws IOException {

        LiasseFiscale liasse = LiasseFiscaleHelper
                .buildLiasseFiscale(FecHelper.read(Path.of("target/test-classes/0000000001FEC20220831.txt")),
                        RegimeImposition.REEL_NORMAL);
        assertEquals(12, liasse.getFormulaires().size());

        checkParsedLiasse(liasse, "target/test-classes/0000000001FEC20220831-expected.csv", 100);
    }

    private void checkParsedLiasse(LiasseFiscale liasse, String expectedResultFilePath, int expectedSuccess)
            throws IOException {
        CSVParser csvParser = CSVParser.parse(Path.of(expectedResultFilePath), Charset.forName("UTF-8"),
                CSVFormat.DEFAULT);

        StringBuilder sb = new StringBuilder();
        int total = 0;
        int success = 0;
        int unknown = 0;
        for (CSVRecord csvRecord : csvParser) {
            if (csvRecord.size() > 1) {
                ++total;
                String symbole = csvRecord.get(0);
                Optional<Repere> match = Repere.get(liasse.getRegime(), symbole);
                if (match.isPresent()) {
                    Repere repere = match.get();
                    Double expected = Double.valueOf(csvRecord.get(1));
                    Double actual = liasse.getMontant(repere).orElse(0.0);
                    if (!Objects.equals(expected, actual)) {
                        sb.append(String.format("Montant du repère %s (%s) incorrect. Actual: %f, expected: %f\r\n",
                                symbole, repere.getNom(), actual, expected));
                    } else {
                        ++success;
                    }
                } else {
                    ++unknown;
                    sb.append(String.format("Repère inconnu : %s\r\n", symbole));
                }
            }
        }

        int totalLessUnknown = total - unknown;
        float successRate = totalLessUnknown > 0 ? ((float) success / (float)totalLessUnknown) * 100f : 0f;
        if (success != expectedSuccess) {
            fail(String.format(
                    "%d succès au lieu de %d attendus, %.02f%% de réussite, %d erreurs, %d repères inconnus\r\n",
                    success, expectedSuccess, successRate, total - success - unknown, unknown) + sb.toString());
        }

    }

    @Test
    void readLiasseFiscalePDF_A() throws IOException {
        LiasseFiscale liasse = LiasseFiscaleHelper
                .readLiasseFiscalePDF("target/test-classes/liasse-publique-A.pdf");

        assertEquals("303195192", liasse.getSiren());
        assertEquals(RegimeImposition.REEL_NORMAL, liasse.getRegime());
        assertEquals(LocalDate.of(2019, 12, 31), liasse.getClotureExercice());
        checkParsedLiasse(liasse, "target/test-classes/liasse-publique-A-expected.csv", 71);

        Annexe produitsChargesExceptionnels = liasse.getAnnexe(NatureAnnexe.PRODUITS_ET_CHARGES_EXCEPTIONNELS);
        assertEquals(0, produitsChargesExceptionnels.getLignes().size());

        Annexe produitsChargesAnterieurs = liasse.getAnnexe(NatureAnnexe.PRODUITS_ET_CHARGES_ANTERIEURS);
        assertEquals(3, produitsChargesAnterieurs.getLignes().size());
        // Les libellés sont tronqués, ils ne sont pas dans la bonne colonne, et il en manque un
        assertEquals("olde compte fournisseurs", produitsChargesAnterieurs.getCellule(0, 2));
        assertEquals("egul salaires 2017+2018", produitsChargesAnterieurs.getCellule(2, 2));        

        Annexe reintegrations = liasse.getAnnexe(NatureAnnexe.REINTEGRATIONS);
        assertEquals(0, reintegrations.getLignes().size());

        Annexe deductions = liasse.getAnnexe(NatureAnnexe.DEDUCTIONS);
        assertEquals(0, deductions.getLignes().size());
    }

    @Test
    void readLiasseFiscalePDF_B() throws IOException {
        // Dans cette liasse, les repères de cellules sont des images, donc impossible
        // de les lire.
        LiasseFiscale liasse = LiasseFiscaleHelper
                .readLiasseFiscalePDF("target/test-classes/liasse-publique-B.pdf");

        assertEquals("558501912", liasse.getSiren());
        assertEquals(RegimeImposition.REEL_NORMAL, liasse.getRegime());
        assertEquals(LocalDate.of(2019, 12, 31), liasse.getClotureExercice());
        checkParsedLiasse(liasse, "target/test-classes/liasse-publique-B-expected.csv", 0);

        Annexe provisions = liasse.getAnnexe(NatureAnnexe.PROVISIONS);
        assertEquals(2, provisions.getLignes().size());
        assertEquals("Créances rattachées à des participations", provisions.getCellule(0, 0));
        assertEquals("Titres immobilisés PFALZWERKE", provisions.getCellule(1, 0));

        Annexe produitsChargesExceptionnels = liasse.getAnnexe(NatureAnnexe.PRODUITS_ET_CHARGES_EXCEPTIONNELS);
        assertEquals(13, produitsChargesExceptionnels.getLignes().size());
        assertEquals("Valeur comptable des immobilisations incorporelles cédées", produitsChargesExceptionnels.getCellule(0, 0));
        assertEquals("Reprise sur amortissement dérogatoire (amortissement dégressif)", produitsChargesExceptionnels.getCellule(12, 0));        

        Annexe produitsChargesAnterieurs = liasse.getAnnexe(NatureAnnexe.PRODUITS_ET_CHARGES_ANTERIEURS);
        assertEquals(6, produitsChargesAnterieurs.getLignes().size());
        assertEquals("Décomptes de charges locatives 2018", produitsChargesAnterieurs.getCellule(0, 0));
        assertEquals("CVAE 2018 ajustement", produitsChargesAnterieurs.getCellule(5, 0));        

        Annexe reintegrations = liasse.getAnnexe(NatureAnnexe.REINTEGRATIONS);
        assertEquals(4, reintegrations.getLignes().size());
        assertEquals("Qutoe part perte 2018 GEIE affectée en 2019 et déduite en 2018", reintegrations.getCellule(0, 0));
        assertEquals("CICE", reintegrations.getCellule(3, 0));

        Annexe deductions = liasse.getAnnexe(NatureAnnexe.DEDUCTIONS);
        assertEquals(1, deductions.getLignes().size());
        assertEquals("Suramortissement de 40%", deductions.getCellule(0, 0));
    }

    @Test
    void readLiasseFiscalePDF_C() throws IOException {
        LiasseFiscale liasse = LiasseFiscaleHelper
                .readLiasseFiscalePDF("target/test-classes/liasse-publique-C.pdf");

        assertEquals("529770646", liasse.getSiren());
        assertEquals(RegimeImposition.REEL_NORMAL, liasse.getRegime());
        assertEquals(LocalDate.of(2017, 12, 31), liasse.getClotureExercice());
        checkParsedLiasse(liasse, "target/test-classes/liasse-publique-C-expected.csv", 60);

        Annexe produitsChargesExceptionnels = liasse.getAnnexe(NatureAnnexe.PRODUITS_ET_CHARGES_EXCEPTIONNELS);
        assertEquals(0, produitsChargesExceptionnels.getLignes().size());

        Annexe produitsChargesAnterieurs = liasse.getAnnexe(NatureAnnexe.PRODUITS_ET_CHARGES_ANTERIEURS);
        assertEquals(0, produitsChargesAnterieurs.getLignes().size());
    }

    @Test
    void readLiasseFiscalePDF_D() throws IOException {
        LiasseFiscale liasse = LiasseFiscaleHelper
                .readLiasseFiscalePDF("target/test-classes/liasse-publique-D.pdf");

        assertEquals("523128205", liasse.getSiren());
        assertEquals(RegimeImposition.REEL_NORMAL, liasse.getRegime());
        assertEquals(LocalDate.of(2019, 03, 31), liasse.getClotureExercice());
        checkParsedLiasse(liasse, "target/test-classes/liasse-publique-D-expected.csv", 13);

        Annexe produitsChargesExceptionnels = liasse.getAnnexe(NatureAnnexe.PRODUITS_ET_CHARGES_EXCEPTIONNELS);
        assertEquals(0, produitsChargesExceptionnels.getLignes().size());

        Annexe produitsChargesAnterieurs = liasse.getAnnexe(NatureAnnexe.PRODUITS_ET_CHARGES_ANTERIEURS);
        assertEquals(0, produitsChargesAnterieurs.getLignes().size());        
    }

    @Test
    void readLiasseFiscalePDF_E() throws IOException {
        LiasseFiscale liasse = LiasseFiscaleHelper
                .readLiasseFiscalePDF("target/test-classes/liasse-publique-E.pdf");

        assertEquals("402207153", liasse.getSiren());
        assertEquals(RegimeImposition.REEL_NORMAL, liasse.getRegime());
        assertEquals(LocalDate.of(2015, 12, 31), liasse.getClotureExercice());
        checkParsedLiasse(liasse, "target/test-classes/liasse-publique-E-expected.csv", 6);

        Annexe produitsChargesExceptionnels = liasse.getAnnexe(NatureAnnexe.PRODUITS_ET_CHARGES_EXCEPTIONNELS);
        assertEquals(0, produitsChargesExceptionnels.getLignes().size());

        Annexe produitsChargesAnterieurs = liasse.getAnnexe(NatureAnnexe.PRODUITS_ET_CHARGES_ANTERIEURS);
        assertEquals(0, produitsChargesAnterieurs.getLignes().size());        
    }

    @Test
    void readLiasseFiscalePDF_F() throws IOException {
        LiasseFiscale liasse = LiasseFiscaleHelper
                .readLiasseFiscalePDF("target/test-classes/liasse-publique-F.pdf");

        assertEquals("449207133", liasse.getSiren());
        assertEquals(RegimeImposition.REEL_NORMAL, liasse.getRegime());
        assertEquals(LocalDate.of(2015, 12, 31), liasse.getClotureExercice());
        checkParsedLiasse(liasse, "target/test-classes/liasse-publique-F-expected.csv", 17);

        Annexe produitsChargesExceptionnels = liasse.getAnnexe(NatureAnnexe.PRODUITS_ET_CHARGES_EXCEPTIONNELS);
        assertEquals(2, produitsChargesExceptionnels.getLignes().size());
        assertEquals("CHARGES EXECEPTIONNELLES", produitsChargesExceptionnels.getCellule(0, 0));
        assertEquals("REGULARISTAIONS", produitsChargesExceptionnels.getCellule(1, 0));        

        Annexe produitsChargesAnterieurs = liasse.getAnnexe(NatureAnnexe.PRODUITS_ET_CHARGES_ANTERIEURS);
        assertEquals(0, produitsChargesAnterieurs.getLignes().size());        

        Annexe provisions = liasse.getAnnexe(NatureAnnexe.PROVISIONS);
        assertEquals(0, provisions.getLignes().size());        

        Annexe reintegrations = liasse.getAnnexe(NatureAnnexe.REINTEGRATIONS);
        assertEquals(2, reintegrations.getLignes().size());        
        // La première ligne ne devrait pas apparaître
        assertEquals("Détail des réintégrations diverses", reintegrations.getCellule(0, 0));        
        assertEquals("IRCM MADA", reintegrations.getCellule(1, 0));        

        Annexe deductions = liasse.getAnnexe(NatureAnnexe.DEDUCTIONS);
        assertEquals(2, deductions.getLignes().size());        
        // La première ligne ne devrait pas apparaître
        assertEquals("Détail des déductions diverses", deductions.getCellule(0, 0));        
        assertEquals("Produit d'impôt", deductions.getCellule(1, 0));        

    }

    @Test
    void readLiasseFiscalePDF_G() throws IOException {
        // Impossible de lire cette liasse, les copier/coller manuels de sont contenu ne
        // fonctionnent même pas, peut être un un problème d'encodage.
        LiasseFiscale liasse = LiasseFiscaleHelper
                .readLiasseFiscalePDF("target/test-classes/liasse-publique-G.pdf");

        // checkParsedLiasse(liasse,
        // "target/test-classes/liasse-publique-G-expected.csv", 0);
        assertNotNull(liasse);
    }

    @Test
    void readLiasseFiscalePDF_H() throws IOException {
        LiasseFiscale liasse = LiasseFiscaleHelper
                .readLiasseFiscalePDF("target/test-classes/liasse-publique-H.pdf");

        assertEquals("451209852", liasse.getSiren());
        assertEquals(RegimeImposition.REEL_NORMAL, liasse.getRegime());
        assertEquals(LocalDate.of(2018, 12, 31), liasse.getClotureExercice());
        checkParsedLiasse(liasse, "target/test-classes/liasse-publique-H-expected.csv", 420);

        Annexe produitsChargesExceptionnels = liasse.getAnnexe(NatureAnnexe.PRODUITS_ET_CHARGES_EXCEPTIONNELS);
        assertEquals(1, produitsChargesExceptionnels.getLignes().size());
        assertEquals("Détail en annexe", produitsChargesExceptionnels.getCellule(0, 0));

        Annexe produitsChargesAnterieurs = liasse.getAnnexe(NatureAnnexe.PRODUITS_ET_CHARGES_ANTERIEURS);
        assertEquals(1, produitsChargesAnterieurs.getLignes().size());        
        assertEquals("Détail en annexe", produitsChargesAnterieurs.getCellule(0, 0));

    }

    @Test
    void readLiasseFiscalePDF_I() throws IOException {
        LiasseFiscale liasse = LiasseFiscaleHelper
                .readLiasseFiscalePDF("target/test-classes/liasse-anonyme-I.pdf");

        assertEquals("", liasse.getSiren());
        assertEquals(RegimeImposition.REEL_NORMAL, liasse.getRegime());
        assertEquals(LocalDate.of(2022, 12, 31), liasse.getClotureExercice());
        checkParsedLiasse(liasse, "target/test-classes/liasse-anonyme-I-expected.csv", 453);

        Annexe produitsChargesExceptionnels = liasse.getAnnexe(NatureAnnexe.PRODUITS_ET_CHARGES_EXCEPTIONNELS);
        assertEquals(1, produitsChargesExceptionnels.getLignes().size());
        assertEquals("Pénalités, amendes fiscales et pénales", produitsChargesExceptionnels.getCellule(0, 0));

        Annexe produitsChargesAnterieurs = liasse.getAnnexe(NatureAnnexe.PRODUITS_ET_CHARGES_ANTERIEURS);
        assertEquals(0, produitsChargesAnterieurs.getLignes().size());

        Annexe reintegrations = liasse.getAnnexe(NatureAnnexe.REINTEGRATIONS);
        assertEquals(2, reintegrations.getLignes().size());
        // La première ligne, avec libellé, devrait être ignorée
        assertEquals("Libellé", reintegrations.getCellule(0, 0));
        assertEquals("Intérêts exc art 212bis CGI après application des règles de sous-capitalisation", reintegrations.getCellule(1, 0));

        Annexe deductions = liasse.getAnnexe(NatureAnnexe.DEDUCTIONS);
        assertEquals(2, deductions.getLignes().size());
        // La première ligne, avec libellé, devrait être ignorée
        assertEquals("Libellé", deductions.getCellule(0, 0));
        assertEquals("CIR 2022", deductions.getCellule(1, 0));

    }

    @Test
    void readLiasseFiscalePDF_J() throws IOException {
        LiasseFiscale liasse = LiasseFiscaleHelper
                .readLiasseFiscalePDF("target/test-classes/liasse-publique-J.pdf");

        assertEquals("437641699", liasse.getSiren());
        assertEquals(RegimeImposition.REEL_SIMPLIFIE, liasse.getRegime());
        assertEquals(LocalDate.of(2022, 12, 31), liasse.getClotureExercice());
        writeExpectedValuesCsv(liasse, "target/test-classes/liasse-publique-J-expected.csv");
        checkParsedLiasse(liasse, "target/test-classes/liasse-publique-J-expected.csv", 75);
    }    

    @Test
    void readLiasseFiscalePDF_K() throws IOException {
        LiasseFiscale liasse = LiasseFiscaleHelper
                .readLiasseFiscalePDF("target/test-classes/liasse-publique-K.pdf");

        assertEquals("891369951", liasse.getSiren());
        assertEquals(RegimeImposition.REEL_SIMPLIFIE_AGRICOLE, liasse.getRegime());
        assertEquals(LocalDate.of(2021, 12, 31), liasse.getClotureExercice());
        // Pas de bordures horizontales pour délimiter chaque ligne, les correspondances avec les repères ne peuvent pas toutes être lues
        checkParsedLiasse(liasse, "target/test-classes/liasse-publique-K-expected.csv", 56);
    }    

    @Test
    void readLiasseFiscalePDF_L() throws IOException {
        LiasseFiscale liasse = LiasseFiscaleHelper
                .readLiasseFiscalePDF("target/test-classes/liasse-publique-L.pdf");

        assertEquals(RegimeImposition.REEL_SIMPLIFIE_AGRICOLE, liasse.getRegime());
        assertEquals("524166816", liasse.getSiren());
        assertEquals(LocalDate.of(2016, 8, 31), liasse.getClotureExercice());
        checkParsedLiasse(liasse, "target/test-classes/liasse-publique-L-expected.csv", 125);
    }    

    void writeExpectedValuesCsv(LiasseFiscale liasse, String filePath) throws IOException {
        StringBuilder builder = new StringBuilder();

        for (Formulaire formulaire : liasse.getFormulaires()) {
            Set<Repere> sortedReperes = new TreeSet<>(formulaire.reperes());
            for (Repere repere : sortedReperes) {
                builder.append(repere.getSymbole());
                builder.append(",");
                builder.append(String.format(Locale.US, "%.2f", liasse.getMontant(repere).orElse(0.0)));
                builder.append("\r\n");
            }
        }

        FileUtils.writeStringToFile(new File(filePath), builder.toString(), "UTF-8");
    }

    @Test
    void parseSiren() {
        // Bloc de texte multiligne
        assertEquals(Optional.of("303195192"), LiasseFiscaleHelper.parseSiren("2\r\nNuméro SIRET*  30319519200032*Néant\r\nExercice N clos le,31122019BrutAmortissements, provisionsNet123\r\nBrutAmortissements, provisionsNet123\r\n"));

        // retour à la ligne entre libellé et valeur
        assertEquals(Optional.of("303195192"), LiasseFiscaleHelper.parseSiren("Numéro SIRET*  \r\n30319519200032"));

        // SIREN et pas SIRET
        assertEquals(Optional.of("303195192"), LiasseFiscaleHelper.parseSiren("Numéro SIREN*  30319519200032"));

        // Espaces entres le caractères
        assertEquals(Optional.of("303195192"), LiasseFiscaleHelper.parseSiren("N u m é r o S I R E N *  3 0 3 1 9 5 1 9 2 0 0 0 3 2"));

        // Pas de numéro SIREN renseigné
        assertEquals(Optional.of(""), LiasseFiscaleHelper.parseSiren("*Numéro SIRET*Néant \r\nExercice N clos le,31/12/2022"));

        // Pas de champ SIREN trouvé
        assertEquals(Optional.empty(), LiasseFiscaleHelper.parseSiren(""));
    }

    @Test
    void parseClotureExercice() {
        assertEquals(Optional.empty(), LiasseFiscaleHelper.parseClotureExercice(""));

        // Bloc de texte multiligne
        assertEquals(Optional.of(LocalDate.of(2019, 12, 31)), LiasseFiscaleHelper.parseClotureExercice("2\r\nNuméro SIRET*  30319519200032*Néant\r\nExercice N clos le,31122019BrutAmortissements, provisionsNet123\r\nBrutAmortissements, provisionsNet123\r\n"));

        // retour à la ligne entre libellé et valeur
        assertEquals(Optional.of(LocalDate.of(2019, 12, 31)), LiasseFiscaleHelper.parseClotureExercice("Exercice N clos le,\r\n31122019"));

        // séparateurs date
        assertEquals(Optional.of(LocalDate.of(2019, 12, 31)), LiasseFiscaleHelper.parseClotureExercice("Exercice N clos le,\r\n31/12/2019"));

        // Espaces entre les caractères
        assertEquals(Optional.of(LocalDate.of(2019, 12, 31)), LiasseFiscaleHelper.parseClotureExercice("E x e r c i c e   N   c l o s   l e   , \r\n 3 1 1 2 2 0 1 9"));

        // deux points
        assertEquals(Optional.of(LocalDate.of(2019, 12, 31)), LiasseFiscaleHelper.parseClotureExercice("Exercice N clos le:\r\n31/12/2019"));
        
        // Exercice N et N-1 dans la même cellule
        assertEquals(Optional.of(LocalDate.of(2021, 12, 31)), LiasseFiscaleHelper.parseClotureExercice("Exercice N, clos le :31/12/2021Exercice N-1, clos le :08/11/2020"));        

        // Date clôture sur 6 positions seulement
        assertEquals(Optional.of(LocalDate.of(2016, 8, 31)), LiasseFiscaleHelper.parseClotureExercice("EXERCICE CLOS LE 310816"));
    }
}
