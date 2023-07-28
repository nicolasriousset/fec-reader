package fr.neolegal.fec;

import java.text.NumberFormat;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

public abstract class FecHelper {

    static String FEC_FILENAME_SEPARATOR = "FEC";
    static DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
    static NumberFormat numberFormat = NumberFormat.getInstance(Locale.FRANCE);

    private FecHelper() {
    }

    static LocalDate parseDate(String value) {
        return StringUtils.isBlank(value) ? null : LocalDate.parse(value, dateFormatter);
    }

    static Double parseDouble(String value) throws ParseException {
        return StringUtils.isBlank(value) ? null : numberFormat.parse(value).doubleValue();
    }

    /**
     * IX. – Le fichier des écritures comptables est nommé selon la nomenclature
     * suivante :
     * SirenFECAAAAMMJJ, où " Siren " est le Siren du contribuable mentionné à
     * l'article L. 47 A et AAAAMMJJ la date de clôture de l'exercice comptable.
     */
    static Optional<LocalDate> parseClotureExercice(String filename) {
        filename = FilenameUtils.removeExtension(filename);

        if (StringUtils.isBlank(filename)) {
            return Optional.empty();
        }

        String[] parts = filename.split("(?i)" + FEC_FILENAME_SEPARATOR);
        if (parts.length != 2) {
            return Optional.empty();
        }

        try {
            return Optional.of(LocalDate.parse(parts[1], dateFormatter));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * IX. – Le fichier des écritures comptables est nommé selon la nomenclature
     * suivante :
     * SirenFECAAAAMMJJ, où " Siren " est le Siren du contribuable mentionné à
     * l'article L. 47 A et AAAAMMJJ la date de clôture de l'exercice comptable.
     */
    static Optional<String> parseSiren(String filename) {
        if (StringUtils.isBlank(filename)) {
            return Optional.empty();
        }

        String[] parts = filename.split("(?i)" + FEC_FILENAME_SEPARATOR);
        if (parts.length != 2) {
            return Optional.empty();
        }

        return Optional.of(parts[0]);
    }

    static double computeTotalJournal(List<LEC> lignes, String journalCode, boolean credit) {
        return CollectionUtils.emptyIfNull(lignes).stream().filter(
                ecriture -> StringUtils.equalsIgnoreCase(ecriture.getJournalCode(), journalCode))
                .mapToDouble(ecriture -> credit ? ecriture.getCredit() : ecriture.getDebit()).sum();
    }

    static long countEcritures(List<LEC> lignes) {
        return CollectionUtils.emptyIfNull(lignes).stream().map(ecriture -> ecriture.getEcritureNum()).distinct()
                .count();
    }

    public static Set<String> resolveJournaux(List<LEC> lignes) {
        return CollectionUtils.emptyIfNull(lignes).stream().map(ligne -> ligne.getJournalCode()).distinct()
                .collect(Collectors.toSet());
    }

    public static double computeSoldeComptes(List<LEC> lignes, Collection<PCG> comptes) {
        return computeSoldeComptesByNumero(lignes,
                comptes.stream().map(compte -> compte.getPrefix()).collect(Collectors.toSet()));
    }

    public static double computeSoldeComptesByNumero(List<LEC> lignes, Collection<String> prefixComptes) {
        return CollectionUtils.emptyIfNull(lignes).stream()
                .filter(ligne -> prefixComptes.stream()
                        .anyMatch(prefixCompte -> StringUtils.startsWith(ligne.getCompteNum(), prefixCompte)))
                .mapToDouble(ligne -> ligne.getCredit() - ligne.getDebit()).sum();
    }

    public static double computeSoldeCompte(List<LEC> lignes, PCG compte) {
        return computeSoldeComptes(lignes, List.of(compte));
    }

    public static double computeSoldeReportANouveau(List<LEC> lignes) {
        return computeSoldeCompte(lignes, PCG.REPORT_A_NOUVEAU_CREDITEUR)
                - computeSoldeCompte(lignes, PCG.REPORT_A_NOUVEAU_DEBITEUR);
    }

    public static double computeTotalBilan(List<LEC> lignes) {
        return 0;
    }

    public static double computeChargesExploitation(List<LEC> lignes) {
        return computeSoldeComptes(lignes, Set.of(PCG.ACHATS,
                PCG.CHARGES_EXTERNES,
                PCG.AUTRES_CHARGES_EXTERNES,
                PCG.IMPOTS,
                PCG.CHARGES_PERSONNEL,
                PCG.AUTRES_CHARGES_GESTION_COURANTE));
    }
}
