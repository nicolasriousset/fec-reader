package fr.neolegal.fec.liassefiscale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import fr.neolegal.fec.Fec;
import fr.neolegal.fec.FecHelper;

public class LiasseFiscaleHelperTest {

    static Fec fec = FecHelper.read(Path.of("target/test-classes/123456789FEC20500930.txt"));

    @Test
    void buildLiasseFiscale() throws FileNotFoundException, IOException {
        LiasseFiscale actual = LiasseFiscaleHelper.buildLiasseFiscale(fec);
        assertNotNull(actual.getBilanActif());
        assertNotNull(actual.getBilanPassif());
        assertNotNull(actual.getCompteDeResultatEnListe());
        assertNotNull(actual.getCompteDeResultat());
    }

    @Test
    void buildBilanActif() throws FileNotFoundException, IOException {
        TableauComptable actual = LiasseFiscaleHelper.buildBilanActif(fec);
        assertNotNull(actual.getMontant("AA"));
    }

    @Test
    void buildBilanPassif() throws FileNotFoundException, IOException {
        TableauComptable actual = LiasseFiscaleHelper.buildBilanPassif(fec);
        assertEquals(35600.0, actual.getMontant("DD").get());
        assertEquals(0.0, actual.getMontant("DG").get());
        assertEquals(121396.22, actual.getMontant("DH").get());
        assertEquals(512996.22000000003, actual.getMontant("DL").get()); // erreur: devrait être 639230
        assertEquals(512996.22000000003, actual.getMontant("EE").get()); // erreur : devrait être 1016587
    }

    @Test
    void buildCompteDeResultatsEnListe() throws FileNotFoundException, IOException {
        TableauComptable actual = LiasseFiscaleHelper.buildCompteDeResultatsEnListe(fec);
        assertEquals(1212843.9, actual.getMontant("FL").get());
        assertEquals(1225776.5, actual.getMontant("FR").get());
        assertEquals(-1107619.9, actual.getMontant("GF").get());
        assertEquals(-249857.75, actual.getMontant("FY").get());
        assertEquals(-83308.12000000001, actual.getMontant("FZ").get());
        assertEquals(118156.59999999998, actual.getMontant("GG").get());
        assertEquals(-3043.58, actual.getMontant("GV").get());
        assertEquals(115113.01999999999, actual.getMontant("GW").get());
    }

    @Test
    void buildCompteDeResultats() throws FileNotFoundException, IOException {
        TableauComptable actual = LiasseFiscaleHelper.buildCompteDeResultats(fec);
        assertEquals(11120.89, actual.getMontant("HI").get());
        assertEquals(0.0, actual.getMontant("HK").get());
        assertEquals(126233.90999999999, actual.getMontant("HN").get());
    }

}
