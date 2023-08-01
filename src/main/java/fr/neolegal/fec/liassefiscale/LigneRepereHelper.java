package fr.neolegal.fec.liassefiscale;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import fr.neolegal.fec.Fec;
import fr.neolegal.fec.FecHelper;

public class LigneRepereHelper {

    public final static Character MIN_REPERE = 'A';
    public final static Character MAX_REPERE = 'O';
    public final static int MIN_NUM_COMPTE = 1;
    public final static int MAX_NUM_COMPTE = 7999;
    public final static String REPERE_REGEX = "(?i)([" + MIN_REPERE + "-" + MAX_REPERE + "][A-Z])";

    private LigneRepereHelper() {
    }

    static boolean isNumeroCompte(String candidate) {
        return parseNumeroCompte(candidate).isPresent();
    }

    static boolean isLigneRepere(String candidate) {
        if (StringUtils.isBlank(candidate)) {
            return false;
        }
        candidate = candidate.trim();
        return candidate.length() == 2
                && candidate.matches(REPERE_REGEX);
    }

    static Optional<String> parseNumeroCompte(String candidate) {
        if (StringUtils.isBlank(candidate)) {
            return Optional.empty();
        }

        candidate = candidate.trim();
        if (StringUtils.isNumeric(candidate)) {
            int numCompte = Integer.parseInt(candidate);
            if (numCompte >= MIN_NUM_COMPTE && numCompte <= MAX_NUM_COMPTE) {
                return Optional.of(candidate);
            }
        }

        if (StringUtils.startsWith(candidate, LigneRepere.PREFIXE_VARIABLE_COMPTE)) {
            return Optional.of(StringUtils.substring(candidate, LigneRepere.PREFIXE_VARIABLE_COMPTE.length()));
        }

        return Optional.empty();
    }

    public static Set<String> resolveComptes(String repereLigneOrNumeroCompte) {
        return resolveComptes(repereLigneOrNumeroCompte, 10);
    }

    private static Set<String> resolveComptes(String repereLigneOrNumeroCompte, int maxDepth) {
        Optional<String> numeroCompte = parseNumeroCompte(repereLigneOrNumeroCompte);
        if (numeroCompte.isPresent()) {
            return Set.of(numeroCompte.get());
        }

        LigneRepere repere = LigneRepere.REPERES.get(repereLigneOrNumeroCompte);
        if (Objects.isNull(repere)) {
            return Set.of();
        }

        return resolveComptes(repere, maxDepth);        
    }

    public static Set<String> resolveComptes(LigneRepere repere) {
        return resolveComptes(repere, 10);
    }

    private static Set<String> resolveComptes(LigneRepere repere, int maxDepth) {
        if (Objects.isNull(repere)) {
            return Set.of();
        }

        if (maxDepth < 0) {
            throw new RuntimeException("Boucle infine détectée lors de la résolution des numéros de comptes");
        }
        
        Set<String> comptes = new TreeSet<String>();
        Matcher matcher = Pattern.compile(LigneRepere.PREFIXE_VARIABLE_COMPTE + "([0-9]+)").matcher(repere.getExpression());
        while (matcher.find()) {
            comptes.addAll(resolveComptes(matcher.group(), maxDepth-1));
        }

        matcher = Pattern.compile(REPERE_REGEX).matcher(repere.getExpression());
        while (matcher.find()) {
            comptes.addAll(resolveComptes(matcher.group(), maxDepth-1));
        }

        
        return comptes;
    }

    public static double computeMontantLigneRepere(String repere, Fec fec) {
        LigneRepere ligneRepere = LigneRepere.get(repere);
        return computeMontantLigneRepere(ligneRepere, fec);
    }

    public static double computeMontantLigneRepere(LigneRepere repere, Fec fec) {
        Set<String> comptes = resolveComptes(repere);
        return FecHelper.computeSoldeComptesByNumero(fec.getLignes(),comptes);
    }
}