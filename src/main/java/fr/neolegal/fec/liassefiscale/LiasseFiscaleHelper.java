package fr.neolegal.fec.liassefiscale;

import java.awt.Point;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static java.util.Objects.isNull;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.left;

import static org.apache.commons.lang3.ObjectUtils.firstNonNull;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Pair;
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
        for (ModeleFormulaire modele : FormulaireHelper.getModelesFormulaires()) {
            if (Objects.equals(regime, modele.getRegimeImposition())) {
                liasse.getFormulaires().add(Formulaire.builder().modele(modele).build());
            }
        }

        return liasse;
    }

    public static LiasseFiscale buildLiasseFiscale(Fec fec, RegimeImposition regime) {

        LiasseFiscale liasse = buildLiasseFiscale(regime);
        liasse.setSiren(fec.getSiren());
        liasse.setClotureExercice(fec.getClotureExercice());

        VariableProvider provider = new FecVariableProvider(fec, liasse);
        for (Formulaire formulaire : liasse.getFormulaires()) {
            for (Repere repere : formulaire.getAllReperes()) {
                RepereHelper.computeMontantRepereCellule(repere, fec, provider)
                        .ifPresent(montant -> formulaire.setMontant(repere, montant));
            }
        }

        return liasse;
    }

    public static LiasseFiscale readLiasseFiscalePDF(String filename) throws IOException {
        return readLiasseFiscalePDF(filename, false);
    }

    public static LiasseFiscale readLiasseFiscalePDF(String filename, boolean outputDebugFiles) throws IOException {
        LiasseFiscale liasse = LiasseFiscale.builder().build();
        // Détermination empirique des distances entre les lignes et les colonnes des
        // tableaux des liasses fiscales
        SpreadsheetExtractionAlgorithm sea = new SpreadsheetExtractionAlgorithm()
                .withMaxGapBetweenAlignedHorizontalRulings(30)
                .withMaxGapBetweenAlignedVerticalRulings(15)
                .withMinColumnWidth(9f)
                .withMinRowHeight(9f);

        List<Table> docTables = new LinkedList<>();
        try (InputStream in = new FileInputStream(filename);
                PDDocument document = PDDocument.load(in);
                ObjectExtractor extractor = new ObjectExtractor(document)) {
            PageIterator pi = extractor.extract();
            while (pi.hasNext()) {
                Page page = pi.next();
                String header = extractPageHeader(page);
                Optional<ModeleFormulaire> modeleMatch = FormulaireHelper.resolveModeleFormulaire(header);
                Optional<NatureAnnexe> annexeMatch = resolveNatureAnnexe(page);
                if (modeleMatch.isPresent() && annexeMatch.isEmpty()) {
                    ModeleFormulaire modele = modeleMatch.get();
                    if (liasse.getRegime() == null) {
                        liasse.setRegime(modele.getRegimeImposition());
                    }
                    List<Table> pageTables = sea.extract(page);
                    Optional<Table> tableMatch = pageTables.stream()
                            .max(Comparator.comparing(Table::getRowCount));
                    if (tableMatch.isPresent()) {
                        Table table = tableMatch.get();
                        docTables.add(table);
                        Formulaire formulaire = parseFormulaire(table, modele);

                        // On peut à tort croire qu'une page correspond à un formulaire (ex : annexe),
                        // et le trouver deux fois dans la liasse. Dans cette situation,
                        // le formulaire avec le plus de valeurs renseignées est sélectionné
                        Formulaire existingFormulaire = liasse.getFormulaires().stream()
                                .filter(f -> Objects.equals(f.getModele(), modele)).findFirst().orElse(null);
                        if (existingFormulaire != null) {
                            if (existingFormulaire.nbMontantsNonNull() < formulaire.nbMontantsNonNull()) {
                                liasse.getFormulaires().remove(existingFormulaire);
                                liasse.getFormulaires().add(formulaire);
                            }
                        } else {
                            liasse.getFormulaires().add(formulaire);
                        }

                        if (isBlank(liasse.getSiren()) && modele.isContainsSiren()) {
                            liasse.setSiren(parseSiren(page).orElse(null));
                        }
                        if (isNull(liasse.getClotureExercice()) && modele.isContainsClotureExercice()) {
                            liasse.setClotureExercice(parseClotureExercice(table, page).orElse(null));
                        }
                    }
                } else if (modeleMatch.isPresent() && annexeMatch.isPresent()) {
                    List<Table> pageTables = sea.extract(page);
                    Optional<Table> tableMatch = pageTables.stream()
                            .max(Comparator.comparing(Table::getRowCount));
                    if (tableMatch.isPresent()) {
                        Table table = tableMatch.get();
                        docTables.add(table);
                        NatureAnnexe natureAnnexe = annexeMatch.get();
                        List<? extends List<String>> lignes = parseAnnexe(table, natureAnnexe, false);
                        liasse.getFormulaire(modeleMatch.get()).getOrAddAnnexe(annexeMatch.get())
                                .getLignes().addAll(lignes);
                    }
                }
            }
        }

        if (outputDebugFiles) {
            String htmlDebugFilename = FilenameUtils.removeExtension(filename) + ".html";
            writeTablesAsSvg(docTables, htmlDebugFilename);

            String csvFileName = FilenameUtils.removeExtension(filename) + ".csv";
            writeLiasseAsCsv(liasse, csvFileName);
        }

        return liasse;
    }

    private static Optional<NatureAnnexe> resolveNatureAnnexe(Page page) throws IOException {
        // On cherche d'abord dans l'en-tête de la page
        Optional<NatureAnnexe> result = NatureAnnexe.resolve(extractPageHeader(page));
        if (result.isPresent() && result.get() == NatureAnnexe.INCONNUE) {
            // C'est une annexe, mais on ne sait pas de quelle nature
            // On va plus loin et analyse le corps du document
            String pageText = extractPageText(page);
            Optional<NatureAnnexe> deeperResolution = NatureAnnexe.resolve(pageText);
            return deeperResolution.or(() -> result);
        }
        return result;
    }

    static Optional<LocalDate> parseClotureExercice(Table table, Page page) throws IOException {
        return parseClotureExercice(table).or(() -> {
            try {
                return parseClotureExercice(page);
            } catch (IOException e) {
                return Optional.empty();
            }
        });
    }

    static Optional<LocalDate> parseClotureExercice(Page page) throws IOException {
        String pageText = extractPageText(page);

        Pair<Boolean, LocalDate> result = parseClotureExercice(pageText);
        if (result.getKey()) {
            return Optional.ofNullable(result.getValue());
        }
        return Optional.empty();
    }

    @SuppressWarnings("rawtypes")
    static Optional<LocalDate> parseClotureExercice(Table table) {
        for (List<RectangularTextContainer> row : table.getRows()) {
            for (int i = 0; i < row.size(); i++) {
                RectangularTextContainer<?> cell = row.get(i);
                String text = cell.getText();
                Pair<Boolean, LocalDate> match = parseClotureExercice(text);
                if (match.getKey()) {
                    if (match.getValue() != null) {
                        return Optional.of(match.getValue());
                    }
                    // la date est peut être dans les cellules suivantes
                    String date = "";
                    boolean moreNumbers = true;
                    for (int j = i + 1; j < row.size() && date.length() < 8 && moreNumbers; j++) {
                        RectangularTextContainer<?> nextCell = row.get(j);
                        String digit = nextCell.getText();
                        if (isNotBlank(digit)) {
                            digit = digit.replaceAll("[^\\d]", "");
                            date = date + digit;
                        }
                        moreNumbers = isNotBlank(digit);
                    }
                    Optional<LocalDate> result = parseDate(date, false);
                    if (result.isPresent()) {
                        return result;
                    }
                }
            }
        }

        return Optional.empty();
    }

    static Pair<Boolean, LocalDate> parseClotureExercice(String text) {
        final String regex = "(clos le|c l o s   l e)([\\s,:]*?)(.+)";
        // Il peut y avoir les dates de cloture des exercices N, et N-1, on s'assure de
        // matcher le N
        Pattern pattern = Pattern.compile(".*N[,\\s]+" + regex,
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        String candidate = "";
        if (matcher.matches()) {
            candidate = matcher.group(3);
        } else {

            // PAs de correspondance, il n'y a peut être pas de N
            pattern = Pattern.compile(".*?" + regex,
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
            matcher = pattern.matcher(text);
            if (!matcher.matches()) {
                return Pair.of(false, null);
            }
            candidate = matcher.group(3);
        }

        Optional<LocalDate> match = parseDate(candidate, false);
        if (match.isPresent()) {
            return Pair.of(true, match.get());
        }

        // Parfois, la date se retrouve avant le libellé, ou beaucoup plus loin à cause
        // de l'algo d'extraction du texte
        // On sait que la date de clôture doit être présente, on élargit la recherche à
        // tout le texte de la page,
        // en se restreignant à un format de date avec séparateur pour limiter les faux
        // positifs
        return Pair.of(true, parseDate(text, true).orElse(null));
    }

    static Optional<LocalDate> parseDate(String str) {
        return parseDate(str, false);
    }

    static Optional<LocalDate> parseDate(String str, boolean dateSeparatorsRequired) {
        str = str.replaceAll("[\\s\\r\\n]", "");
        String pattern = null;

        Map<String, String> formats = new LinkedHashMap<>();
        formats.put("\\d{4}/\\d{2}/\\d{2}", "yyyy/MM/dd");
        formats.put("\\d{4}-\\d{2}-\\d{2}", "yyyy-MM-dd");
        formats.put("\\d{2}/\\d{2}/\\d{4}", "dd/MM/yyyy");
        formats.put("\\d{2}-\\d{2}-\\d{4}", "dd-MM-yyyy");
        formats.put("\\d{2}/\\d{2}/\\d{2}", "dd/MM/yy");
        formats.put("\\d{2}-\\d{2}-\\d{2}", "dd-MM-yy");
        if (!dateSeparatorsRequired) {
            formats.put("\\d{8}", "ddMMyyyy");
            formats.put("\\d{6}", "ddMMyy");
        }

        String candidate = null;
        int distanceToCandidate = Integer.MAX_VALUE;
        for (Map.Entry<String, String> entry : formats.entrySet()) {
            Pattern p = Pattern.compile("(.*?)(" + entry.getKey() + ").*",
                    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
            Matcher m = p.matcher(str);
            if (m.matches() && m.group(1).length() < distanceToCandidate) {
                distanceToCandidate = m.group(1).length();
                candidate = m.group(2);
                pattern = entry.getValue();
            }
        }
        if (isNull(pattern) || isNull(candidate)) {
            return Optional.empty();
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);

        try {
            return Optional.of(LocalDate.parse(candidate, formatter));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    static Optional<String> parseSiren(Page page) throws IOException {
        String pageText = extractPageText(page);
        return parseSiren(pageText);
    }

    static Optional<String> parseSiren(String text) {
        Pattern sirenPattern = Pattern.compile(".*(SIREN|SIRET|S I R E N|S I R E T)[^\\d]{0,5}(.+)",
                Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
        Matcher matcher = sirenPattern.matcher(text);
        if (matcher.matches()) {
            String siren = left(matcher.group(2), 18);
            siren = left(siren.replaceAll("[^\\d]", ""), 9);
            return Optional.of(siren);
        }

        return Optional.empty();
    }

    @SuppressWarnings("rawtypes")
    private static Formulaire parseFormulaire(Table table, ModeleFormulaire modele) {
        Formulaire formulaire = Formulaire.builder().modele(modele).build();

        List<List<RectangularTextContainer>> rows = table.getRows();
        for (int rowIndex = 0; rowIndex < rows.size(); ++rowIndex) {
            List<RectangularTextContainer> row = rows.get(rowIndex);
            for (int colIndex = 0; colIndex < row.size(); ++colIndex) {
                RectangularTextContainer<?> cell = row.get(colIndex);
                String text = getTrimmedText(cell);
                Point origine = new Point(colIndex, rowIndex);
                Optional<Repere> match = formulaire.getRepere(text);
                final boolean fromSymbole = match.isPresent();
                if (match.isEmpty()) {
                    match = formulaire.getRepereByNomIfNavigationDefined(text);
                }
                match.ifPresent(repere -> {
                    Point posValeur = computePositionValeur(origine,
                            fromSymbole ? repere.getFromSymbole() : repere.getFromNom());
                    RectangularTextContainer<?> cellValeur = getCell(table, posValeur.y, posValeur.x);
                    String valeur = getTrimmedText(cellValeur);
                    double montant = parseNumber(valeur);
                    formulaire.setMontant(repere, montant);
                });
            }
        }

        for (NatureAnnexe natureAnnexe : emptyIfNull(modele.getAnnexes())) {
            formulaire.getOrAddAnnexe(natureAnnexe).getLignes()
                    .addAll(parseAnnexe(table, natureAnnexe, true));
        }

        return formulaire;
    }

    private static Point computePositionValeur(Point origine, Point navigation) {
        navigation = firstNonNull(navigation, new Point(1, 0));
        Point posValeur = new Point(origine);
        posValeur.translate(navigation.x, navigation.y);
        return posValeur;
    }

    private static RectangularTextContainer<?> getCell(Table table, int rowIndex, int colIndex) {
        return table.getRows().get(rowIndex).get(colIndex);
    }

    private static String getTrimmedText(RectangularTextContainer<?> cell) {
        return cell.getText().replaceAll("\\s", "");
    }

    public static double parseNumber(String text) {
        text = text.trim();
        boolean isNegative = text.startsWith("(") || text.endsWith(")");
        text = text.replaceAll("[\\s\\(\\)]", "");

        double number = NumberUtils.toDouble(text, 0.0);
        if (isNegative && number != 0.0) {
            number = -number;
        }

        return number;
    }

    @SuppressWarnings("rawtypes")
    private static List<? extends List<String>> parseAnnexe(Table table, NatureAnnexe natureAnnexe,
            boolean searchTitle) {
        List<List<String>> resultat = new LinkedList<>();

        List<List<RectangularTextContainer>> rows = table.getRows();
        if (rows.isEmpty()) {
            return resultat;
        }

        int headerRowIndex = 0;
        if (searchTitle) {
            for (int i = 0; i < rows.size() && headerRowIndex == 0; ++i) {
                // On cherche la première ligne contenant le nom de l'annexe
                // Les données seront présentes sur les lignes suivantes
                for (RectangularTextContainer<?> cell : rows.get(i)) {
                    String text = cell.getText().trim();
                    if (StrUtils.containsIgnoreCase(text, natureAnnexe.getIntitule())) {
                        headerRowIndex = i;
                    }
                }
            }

            // La ligne d'en-tête peut être composée de cellules fusionnées
            // On cherche la prochaine ligne commençant au
            List<RectangularTextContainer> headerRow = rows.get(headerRowIndex);
            float rowStartPos = headerRow.get(0).getLeft();
            while ((headerRowIndex + 1) < rows.size() && rows.get(headerRowIndex + 1).get(0).getLeft() != rowStartPos) {
                ++headerRowIndex;
            }
        }

        List<RectangularTextContainer> prevRow = null;
        boolean moreData = true;
        for (int i = headerRowIndex + 1; i < rows.size() && moreData; ++i) {
            List<RectangularTextContainer> row = rows.get(i);
            List<String> ligne = new LinkedList<>();
            boolean emptyRow = true;
            for (RectangularTextContainer<?> cell : row) {
                String text = cell.getText().trim();
                emptyRow = emptyRow && text.isEmpty();
                ligne.add(text);
            }

            moreData = !emptyRow && (isNull(prevRow) || sameRowStructure(row, prevRow));
            if (moreData) {
                prevRow = row;
                resultat.add(ligne);
            }
        }

        return resultat;
    }

    @SuppressWarnings("rawtypes")
    private static boolean sameRowStructure(List<RectangularTextContainer> row,
            List<RectangularTextContainer> other) {
        if (isNull(row) || isNull(other) || row.size() != other.size()) {
            return false;
        }

        for (int i = 0; i < row.size(); ++i) {
            RectangularTextContainer<?> cell = row.get(i);
            RectangularTextContainer<?> otherCell = other.get(i);
            if (cell.getWidth() != otherCell.getWidth()) {
                return false;
            }
        }
        return true;
    }

    private static String extractPageText(Page page) throws IOException {
        PDFTextStripper reader = new PDFTextStripper();
        reader.setStartPage(page.getPageNumber());
        reader.setEndPage(page.getPageNumber());
        reader.setSortByPosition(true);
        return reader.getText(page.getPDDoc());
    }

    private static String extractPageHeader(Page page) throws IOException {
        PDFTextStripperByArea stripper = new PDFTextStripperByArea();
        Rectangle rect = new Rectangle(0, 0, page.width, (int) ((double) page.height * 0.08));
        stripper.addRegion("top", rect);

        stripper.extractRegions(page.getPDPage());

        return stripper.getTextForRegion("top");
    }

    @SuppressWarnings({ "rawtypes" })
    private static void writeTablesAsSvg(List<Table> tables, String htmlFileName) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        int i = 1;
        for (Table table : tables) {
            sb.append("<h1>Table " + i + "</h1>");
            sb.append(String.format(Locale.US,
                    "<svg width=\"100%%\" viewbox=\"0 0 %s %s\" xmlns=\"http://www.w3.org/2000/svg\">",
                    table.getWidth() + 50.0, table.getHeight() + 100.0));
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

    private static void writeLiasseAsCsv(LiasseFiscale liasse, String filePath) throws IOException {
        StringBuilder builder = new StringBuilder();

        for (Formulaire formulaire : liasse.getFormulaires()) {
            Set<Repere> sortedReperes = new TreeSet<>(formulaire.getAllReperes());
            for (Repere repere : sortedReperes) {
                builder.append(repere.getSymbole());
                builder.append(",");
                builder.append(String.format(Locale.US, "%.2f", liasse.getMontant(repere).orElse(0.0)));
                builder.append("\r\n");
            }
        }

        FileUtils.writeStringToFile(new File(filePath), builder.toString(), "UTF-8");
    }
}
