package fr.neolegal.fec.liassefiscale;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.ObjectUtils;

import lombok.Builder;
import lombok.Data;

@Data
public class Formulaire {
    NatureFormulaire nature;
    Map<Repere, Double> champs = new HashMap<>();

    @Builder
    public Formulaire(NatureFormulaire nature, Map<Repere, Double> champs) {
        this.nature = nature;
        this.champs = ObjectUtils.firstNonNull(champs, new HashMap<>());
    }

    public Formulaire(NatureFormulaire formulaire) {
        this(formulaire, null);
    }

    public Double getMontant(String repere, Double defaultMontant) {
        return getMontant(repere).orElse(defaultMontant);
    }

    public Optional<Double> getMontant(String symboleRepere) {
        Repere repere = Repere.get(symboleRepere);
        if (Objects.isNull(repere)) {
            return Optional.empty();
        }
        return Optional.ofNullable(champs.get(repere));
    }

    Set<Repere> reperes() {
        return champs.keySet();
    }
}
