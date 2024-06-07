package fr.neolegal.fec.liassefiscale;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.contains;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public class FormulaireHelper {

    private FormulaireHelper() {
    }

    private static Set<ModeleFormulaire> modelesFormulaires = loadModelesFormulaires();

    public static Set<ModeleFormulaire> loadModelesFormulaires() {
        Set<ModeleFormulaire> modeles = new LinkedHashSet<>();

        ObjectMapper mapper = new ObjectMapper();
        try {
            List<String> files = IOUtils.readLines(
                    ModeleFormulaire.class.getClassLoader().getResourceAsStream("formulaires/"),
                    StandardCharsets.UTF_8);
            files.sort(String::compareTo);

            for (String filename : files) {
                try (InputStream is = ModeleFormulaire.class.getClassLoader()
                        .getResourceAsStream("formulaires/" + filename)) {
                    if (is != null) {
                        ModeleFormulaire modele = mapper.readValue(is, ModeleFormulaire.class);
                        modeles.add(modele);
                    }
                } catch (IOException e) {
                    Logger.getLogger(FormulaireHelper.class.getName()).log(Level.SEVERE,
                            String.format(
                                    "Erreur lors du chargement du modèle de formulaire %s: ", filename)
                                    + e.getMessage());

                }
            }

        } catch (IOException e) {
            Logger.getLogger(FormulaireHelper.class.getName()).log(Level.SEVERE,
                    "Impossible de lister les modèles de formulaires: " + e.getMessage());
        }

        // saveAsJson(modeles);
        return modeles;
    }

    private static void saveAsJson(Set<ModeleFormulaire> modeles) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
        int count = 0;
        for (ModeleFormulaire modele : modeles) {
            try {
                count++;
                writer.writeValue(new File(modele.getIdentifiant() + ".json"), modele);
            } catch (Exception e) {
                Logger.getLogger(Repere.class.getName()).log(Level.INFO, String.format("Impossible d'enregistrer le modèle %s", modele.getIdentifiant()));
            }
        }
        Logger.getLogger(Repere.class.getName()).log(Level.INFO, String.format("Sauvegarde de %d formulaires", count));
    }

    public static Set<ModeleFormulaire> getModelesFormulaires() {
        return modelesFormulaires;
    }

    public static Optional<ModeleFormulaire> getModeleFormulaireFromIdentifiant(String identifiant) {
        for (ModeleFormulaire modele : modelesFormulaires) {
            if (modele.identifiant.equals(identifiant)) {
                return Optional.of(modele);
            }
        }
        return Optional.empty();
    }

    public static Optional<ModeleFormulaire> resolveModeleFormulaire(String header) {
        return resolveModeleFormulaire(header, true);
    }

    public static Optional<ModeleFormulaire> resolveModeleFormulaire(String header, boolean checkTitre) {
        if (isBlank(header)) {
            return Optional.empty();
        }

        // Si l'en-tête contient l'identifiant exact, bingo
        for (ModeleFormulaire modele : modelesFormulaires) {
            if (StrUtils.containsIgnoreCase(header, modele.getIdentifiant())) {
                return Optional.of(modele);
            }
        }

        List<ModeleFormulaire> modelesAvecPage = modelesFormulaires.stream()
                .filter(f -> isNotBlank(f.getPage()))
                .collect(Collectors.toList());
        // C'est un formulaire, sans identifiant exact connu.
        // Recherche de variations autour du numéro de formulaire avec la page
        for (ModeleFormulaire modele : modelesAvecPage) {
            String regex = String.format(".*(%d[\\s\\-]?%s).*", modele.getNumero(),
                    modele.getPage());
            Pattern pattern = Pattern.compile(regex,
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
            Matcher matcher = pattern.matcher(header);

            if (matcher.matches()) {
                return Optional.of(modele);
            }
        }

        // puis test des formulaires sans numéro de page
        List<ModeleFormulaire> modelesSansPage = modelesFormulaires.stream()
                .filter(f -> isBlank(f.getPage()))
                .collect(Collectors.toList());
        for (ModeleFormulaire modele : modelesSansPage) {
            if (contains(header, modele.getNumero().toString())) {
                return Optional.of(modele);
            }
        }

        // Test des formulaires avec un numéro de page, mais sans
        // préciser le numéro de page
        for (ModeleFormulaire modele : modelesAvecPage) {
            if (contains(header, modele.getNumero().toString())) {
                return Optional.of(modele);
            }
        }

        // Recherche du titre
        if (checkTitre) {
            for (ModeleFormulaire modele : modelesFormulaires) {
                if (StrUtils.containsIgnoreCase(header, modele.getNom())) {
                    return Optional.of(modele);
                }
            }
        }

        // Aucune correspondance trouvée
        return Optional.empty();
    }
}
