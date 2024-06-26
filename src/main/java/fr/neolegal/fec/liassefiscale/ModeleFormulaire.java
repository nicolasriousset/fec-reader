package fr.neolegal.fec.liassefiscale;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "identifiant", "numero", "page", "cerfa", "nom", "regimeImposition", "containsSiren",
        "containsClotureExercice", "annexes" })
public class ModeleFormulaire extends Section {
    String identifiant;
    Integer numero;
    String page;
    String cerfa;
    RegimeImposition regimeImposition;
    boolean containsSiren;
    boolean containsClotureExercice;
    Set<NatureAnnexe> annexes;

    @Builder
    public ModeleFormulaire(String nom, String identifiant, Integer numero, String page, String cerfa,
            RegimeImposition regimeImposition, boolean containsSiren, boolean containsClotureExercice,
            Set<Repere> reperes, Set<Section> sections, Set<NatureAnnexe> annexes) {
        super(nom, reperes, sections);
        this.numero = numero;
        this.page = page;
        this.identifiant = identifiant;
        this.cerfa = cerfa;
        this.regimeImposition = regimeImposition;
        this.containsSiren = containsSiren;
        this.containsClotureExercice = containsClotureExercice;
        this.annexes = annexes;
    }

    public ModeleFormulaire() {
    }
}
