package fr.neolegal.fec.liassefiscale;

import java.time.LocalDate;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;

/* Modélisation de la liasse fiscale, contenant huit tableaux comptables, qui portent les
numéros 2050 (bilan-actif), 2051 (bilan-passif), 2052 et 2053
(compte de résultat, en liste), 2054 (immobilisations), 2055
(amortissements), 2056 (provisions) et 2057 (état des
échéances des créances et des dettes à la clôture de l’exer-
cice)  */
@Data
public class LiasseFiscale {

    String siren;
    LocalDate clotureExercice;
    RegimeImposition regime;
    final List<Formulaire> formulaires = new LinkedList<>();

    @Builder
    public LiasseFiscale(RegimeImposition regime, String siren, LocalDate clotureExercice) {
        this.siren = siren;
        this.clotureExercice = clotureExercice;
        this.regime = regime;
    }

    /**
     * Renvoie le formulaire correspondant. Le crée s'il n'existe pas dans la
     * liasse.
     */
    public Formulaire getFormulaire(ModeleFormulaire modele) {
        return formulaires.stream().filter(f -> Objects.equals(f.getModele(), modele)).findFirst()
                .orElse(Formulaire.builder().modele(modele).build());
    }

    public Annexe getAnnexe(NatureAnnexe natureAnnexe) {
        return formulaires.stream().flatMap(f -> f.getAnnexe(natureAnnexe).stream()).findFirst()
                .orElse(Annexe.builder().natureAnnexe(natureAnnexe).build());
    }

    /**
     * Renvoie le montant correspondant au repère passé en paramètre, s'il est
     * connu.
     */
    public Optional<Double> getMontant(String repere) {
        return formulaires.stream().flatMap(f -> f.getMontant(repere).stream()).findFirst();
    }

    public Optional<Double> getMontant(Repere repere) {
        if (Objects.isNull(repere)) {
            return Optional.empty();
        }
        return getMontant(repere.getSymbole());
    }

    public Optional<Repere> getRepere(String symbole) {
        return formulaires.stream().flatMap(f -> f.getRepere(symbole).stream()).findFirst();
    }

}
