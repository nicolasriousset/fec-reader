package fr.neolegal.fec.liassefiscale;

import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static java.util.Objects.isNull;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;

import fr.neolegal.fec.Fec;
import net.objecthunter.exp4j.VariableProvider;
import technology.tabula.ObjectExtractor;
import technology.tabula.Page;
import technology.tabula.PageIterator;
import technology.tabula.Rectangle;
import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

public class LiasseFiscaleHelper {

    private LiasseFiscaleHelper() {
    }

    public static LiasseFiscale buildLiasseFiscale(RegimeImposition regime) {
        LiasseFiscale liasse = LiasseFiscale.builder().regime(regime).build();
        for (NatureFormulaire formulaire : regime.formulaires()) {
            liasse.getFormulaires().add(buildFormulaire(formulaire));
        }

        return liasse;
    }

    public static LiasseFiscale buildLiasseFiscale(Fec fec, RegimeImposition regime) {

        LiasseFiscale liasse = buildLiasseFiscale(regime);
        liasse.setSiren(fec.getSiren());
        liasse.setClotureExercice(fec.getClotureExercice());

        VariableProvider provider = new FecVariableProvider(fec, regime);
        for (Formulaire formulaire : liasse.getFormulaires()) {
            for (Repere repere : formulaire.getChamps().keySet()) {
                Double montant = RepereHelper.computeMontantRepereCellule(repere, fec, provider).orElse(null);
                formulaire.getChamps().put(repere, montant);
            }
        }

        return liasse;
    }

    public static Formulaire buildFormulaire(NatureFormulaire nature) {
        Formulaire formulaire = new Formulaire(nature);

        Collection<Repere> reperes = Repere.DEFINITIONS.getOrDefault(nature, Map.of()).values();
        for (Repere repere : reperes) {
            formulaire.getChamps().put(repere, null);
        }
        return formulaire;
    }

    public static LiasseFiscale readLiasseFiscalePDF(String filename) throws IOException {
        LiasseFiscale liasse = LiasseFiscale.builder().build();
        // Détermination empirique des distances entre les lignes et les colonnes des
        // tableaux des liasses fiscales
        SpreadsheetExtractionAlgorithm sea = new SpreadsheetExtractionAlgorithm()
                .withMaxGapBetweenAlignedHorizontalRulings(30)
                .withMaxGapBetweenAlignedVerticalRulings(15)
                .withMinSpacingBetweenRulings(10f);

        List<Table> docTables = new LinkedList<>();
        try (InputStream in = new FileInputStream(filename);
                PDDocument document = PDDocument.load(in);
                ObjectExtractor extractor = new ObjectExtractor(document)) {
            PageIterator pi = extractor.extract();
            while (pi.hasNext()) {
                Page page = pi.next();                
                Optional<NatureFormulaire> match = resolveNatureFormulaire(page);
                if (match.isPresent() && liasse.getFormulaires().stream()
                        .noneMatch(formulaire -> match.get().equals(formulaire.getNature()))) {
                    NatureFormulaire natureFormulaire = match.get();
                    if (liasse.getRegime() == null) {
                        liasse.setRegime(natureFormulaire.getRegimeImposition());
                    }
                    List<Table> pageTables = sea.extract(page);                    
                    Optional<Table> tableMatch = pageTables.stream()
                            .max(Comparator.comparing(Table::getRowCount));
                    if (tableMatch.isPresent()) {                        
                        Table table = tableMatch.get();
                        docTables.add(table);
                        Formulaire formulaire = parseFormulaire(table, natureFormulaire);
                        liasse.getFormulaires().add(formulaire);

                        String tableText = extractTableText(table, page);
                        if (StringUtils.isEmpty(liasse.getSiren()) && natureFormulaire.containsSiren()) {
                            liasse.setSiren(parseSiren(tableText).orElse(null));
                        }
                        if (isNull(liasse.getClotureExercice()) && natureFormulaire.containsClotureExercice()) {
                            liasse.setClotureExercice(parseClotureExercice(table).orElse(null));
                        }
                    }
                }
            }
        }
        // writeTablesAsSvg(docTables, "tables.html");
        return liasse;
    }

    @SuppressWarnings("rawtypes")
    static Optional<LocalDate> parseClotureExercice(Table table) {
        for (List<RectangularTextContainer> row : table.getRows()) {
            for (RectangularTextContainer<?> cell : row) {
                String text = cell.getText();
                Optional<LocalDate> match = parseClotureExercice(text);
                if (match.isPresent()) {
                    return match;
                }
            }
        }

        return Optional.empty();
    }

    static Optional<LocalDate> parseClotureExercice(String text) {
        Pattern pattern = Pattern.compile(".*N[,\\s]+(clos le|c l o s   l e)([\\s,:]*)(.+)",
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        if (matcher.matches()) {
            String date = matcher.group(3);
            date = StringUtils.left(date.replaceAll("[^\\d]", ""), 8);
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("ddMMyyyy");
                return Optional.of(LocalDate.parse(date, formatter));
            } catch (Exception e) {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    static Optional<String> parseSiren(String text) {
        Pattern sirenPattern = Pattern.compile(".*(SIREN|SIRET|S I R E N|S I R E T)[^\\d]{0,5}(.+)",
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
        Matcher matcher = sirenPattern.matcher(text);
        if (matcher.matches()) {
            String siren = StringUtils.left(matcher.group(2), 18);
            siren = StringUtils.left(siren.replaceAll("[^\\d]", ""), 9);
            return Optional.of(siren);
        }

        return Optional.empty();
    }

    @SuppressWarnings("rawtypes")
    private static String getRowText(Page page, List<RectangularTextContainer> row, String delimiter) {

        List<RectangularTextContainer> cells = row.stream().filter(cell -> cell.getArea() > 0.0)
                .collect(Collectors.toList());
        Rectangle rowArea = cells.size() > 0 ? cells.get(0) : new Rectangle();
        for (RectangularTextContainer<?> cell : cells) {
            rowArea.setTop(Math.min(rowArea.getTop(), cell.getTop()));
            rowArea.setLeft(Math.min(rowArea.getLeft(), cell.getLeft()));
            rowArea.setBottom(Math.max(rowArea.getBottom(), cell.getBottom()));
            rowArea.setRight(Math.max(rowArea.getRight(), cell.getRight()));
        }
        return page.getText(rowArea).stream().map(te -> te.getText()).collect(Collectors.joining(delimiter));
    }

    @SuppressWarnings("rawtypes")
    private static Formulaire parseFormulaire(Table table, NatureFormulaire natureFormulaire) {
        Formulaire formulaire = buildFormulaire(natureFormulaire);
        for (Repere repere : formulaire.reperes()) {
            boolean found = false;
            for (List<RectangularTextContainer> row : table.getRows()) {
                if (found) {
                    break;
                }
                for (RectangularTextContainer<?> cell : row) {
                    String text = cell.getText().trim();
                    if (found) {
                        boolean isNegative = text.startsWith("(");
                        text = text.replaceAll(" ()", "");

                        double montant = NumberUtils.toDouble(text, 0.0);
                        if (isNegative) {
                            montant = -montant;
                        }
                        formulaire.setMontant(repere, montant);
                        break;
                    }

                    if (StringUtils.equalsIgnoreCase(repere.getSymbole(), text)) {
                        found = true;
                    }
                }
            }
        }
        return formulaire;
    }

    @SuppressWarnings("rawtypes")
    private static String extractTableText(Table table, Page page) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (List<RectangularTextContainer> row : table.getRows()) {
            sb.append(getRowText(page, row, ""));
            sb.append("\r\n");
        }
        return sb.toString();
    }

    @SuppressWarnings("unused")
    private static String extractPageText(Page page) throws IOException {
        PDFTextStripper reader = new PDFTextStripper();
        reader.setStartPage(page.getPageNumber());
        reader.setEndPage(page.getPageNumber());
        return reader.getText(page.getPDDoc());
    }

    private static Optional<NatureFormulaire> resolveNatureFormulaire(Page page) throws IOException {
        PDFTextStripperByArea stripper = new PDFTextStripperByArea();
        // On ne regarde que le texte de l'en-tete. Empiriquement, on estime que l'en-tête fait 8% de la hauteur max
        Rectangle rect = new Rectangle(0, 0, page.width, (int)((double)page.height * 0.08));
        stripper.addRegion("header", rect);

        stripper.extractRegions(page.getPDPage());

        String headerText = stripper.getTextForRegion("header");


        if (!StringUtils.containsAnyIgnoreCase(headerText, "DGFiP", "N°")) {
            return Optional.empty();
        }

        for (NatureFormulaire formulaire : NatureFormulaire.values()) {
            if (StringUtils.containsIgnoreCase(headerText, formulaire.getNumero().toString())) {
                return Optional.of(formulaire);
            }
        }

        return Optional.empty();
    }

    @SuppressWarnings({ "rawtypes", "unused" })
    private static void writeTablesAsSvg(List<Table> tables, String htmlFileName) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        int i = 1;
        for (Table table : tables) {
            sb.append("<h1>Table " + i + "</h1>");
            sb.append(String.format(Locale.US,
                    "<svg width=\"100%%\" viewbox=\"0 0 %s %s\" xmlns=\"http://www.w3.org/2000/svg\">",
                    table.getWidth() + 50.0, table.getHeight() + 50.0));
            for (List<RectangularTextContainer> row : table.getRows()) {
                for (RectangularTextContainer<?> cell : row) {
                    if (true /* cell.height > 10 && cell.width > 10 */) {
                        String text = cell.getText();
                        sb.append("<g>");
                        sb.append(String.format(Locale.US,
                                "<rect width=\"%f\" height=\"%f\" x=\"%f\" y=\"%f\" rx=\"2\" ry=\"2\" fill=\"white\" stroke=\"blue\"/>",
                                cell.width, cell.height, cell.x, cell.y));
                        sb.append(String.format(Locale.US,
                                "<text x=\"%f\" y=\"%f\" font-family=\"Verdana\" font-size=\"8\">%s</text>",
                                cell.x + 2, cell.y + cell.height - 3,
                                text));
                        sb.append("</g>");
                    }
                }
            }

            sb.append("</svg><br>");
            ++i;
        }
        sb.append("</body></html>");

        String html = sb.toString();

        BufferedWriter writer = new BufferedWriter(new FileWriter(htmlFileName));
        writer.write(html);
        writer.close();
    }

}
