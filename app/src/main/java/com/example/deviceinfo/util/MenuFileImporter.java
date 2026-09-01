package com.example.deviceinfo.util;

import com.example.deviceinfo.model.MenuCategory;
import com.example.deviceinfo.model.MenuItem;
import com.example.deviceinfo.model.MenuSection;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MenuFileImporter {

    public static final class Result {
        public final List<MenuItem> items;
        public final int skippedRows;

        public Result(List<MenuItem> items, int skippedRows) {
            this.items = items;
            this.skippedRows = skippedRows;
        }
    }

    private MenuFileImporter() {
    }

    public static Result importFile(File file, MenuSection section) throws Exception {
        if (XlsxTableReader.isLegacyXls(file)) {
            throw new IllegalArgumentException("Нужен файл .xlsx. Сохраните книгу Excel как «Книга Excel (.xlsx)».");
        }
        List<List<String>> rows;
        if (XlsxTableReader.isXlsx(file)) {
            rows = XlsxTableReader.read(file);
        } else {
            rows = readCsv(file);
        }
        return fromRows(rows, section);
    }

    static Result fromRows(List<List<String>> rows, MenuSection section) {
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("Файл пуст");
        }
        int headerIndex = findHeaderRow(rows);
        int[] cols = mapColumns(rows.get(headerIndex));
        List<MenuItem> items = new ArrayList<>();
        int skipped = 0;
        for (int i = headerIndex + 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            MenuItem item = parseRow(row, cols, section);
            if (item == null) {
                skipped++;
                continue;
            }
            items.add(item);
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("В файле нет позиций с названием");
        }
        return new Result(items, skipped);
    }

    private static int findHeaderRow(List<List<String>> rows) {
        int limit = Math.min(rows.size(), 8);
        for (int i = 0; i < limit; i++) {
            int[] cols = mapColumns(rows.get(i));
            if (cols[1] >= 0) {
                return i;
            }
        }
        return 0;
    }

    /**
     * 0 категория, 1 название, 2 состав, 3 кол-во/вес, 4 цена
     */
    private static int[] mapColumns(List<String> header) {
        int[] cols = new int[]{-1, -1, -1, -1, -1};
        for (int i = 0; i < header.size(); i++) {
            String name = normalize(header.get(i));
            if (name.length() == 0) {
                continue;
            }
            if (cols[0] < 0 && (name.contains("категор"))) {
                cols[0] = i;
            } else if (cols[1] < 0 && (name.contains("назван") || name.contains("наимен")
                    || name.equals("блюдо") || name.equals("позиция"))) {
                cols[1] = i;
            } else if (cols[2] < 0 && (name.contains("состав") || name.contains("описан"))) {
                cols[2] = i;
            } else if (cols[3] < 0 && (name.contains("кол-во") || name.contains("количест")
                    || name.contains("вес") || name.contains("объём") || name.contains("объем"))) {
                cols[3] = i;
            } else if (cols[4] < 0 && name.contains("цен")) {
                cols[4] = i;
            }
        }
        // Файл по ТЗ должен иметь порядок колонок:
        // Категория, Название, Состав, Кол-во/Вес, Цена.
        // Иногда заголовки распознаются не идеально (особенно в xlsx), поэтому
        // подстрахуемся: если не нашлась хотя бы колонка "Название" или "Категория" —
        // используем дефолтный порядок 0..4.
        if (header.size() >= 5 && (cols[0] < 0 || cols[1] < 0)) {
            cols[0] = 0;
            cols[1] = 1;
            cols[2] = 2;
            cols[3] = 3;
            cols[4] = 4;
        }
        return cols;
    }

    private static MenuItem parseRow(List<String> row, int[] cols, MenuSection section) {
        String name = cell(row, cols[1]).trim();
        if (name.length() == 0) {
            return null;
        }
        String rawCategory = cell(row, cols[0]).trim();
        MenuItem item = new MenuItem();
        item.setName(name);
        item.setDescription(cell(row, cols[2]).trim());
        item.setSection(section);
        item.setComplex(false);
        item.setCategory(MenuCategory.fromImport(rawCategory, section));
        if (rawCategory.length() > 0) {
            item.setCategoryText(rawCategory);
        } else {
            item.setCategoryText(item.getCategory().getDisplayName());
        }
        String rawAmount = cell(row, cols[3]).trim().replace('\u00a0', ' ');
        if (section == MenuSection.MAIN || section == MenuSection.BAR) {
            item.setAmountText(rawAmount);
            item.setQuantity(1);
            item.setUnit("шт");
        } else {
            parseAmount(rawAmount, item);
        }
        item.setPrice(parsePrice(cell(row, cols[4])));
        return item;
    }

    private static void parseAmount(String raw, MenuItem item) {
        item.setQuantity(1);
        item.setUnit("шт");
        if (raw == null) {
            return;
        }
        String source = raw.trim().replace('\u00a0', ' ');
        if (source.length() == 0) {
            return;
        }

        // Составной формат: "2 шт/1215 гр" — оставляем правую часть в unit, чтобы
        // в приложении отображалось "2 шт/1215 гр" как в файле.
        int slash = source.indexOf('/');
        if (slash > 0) {
            String left = source.substring(0, slash).trim();
            String right = source.substring(slash + 1).trim();

            String leftNormalized = left.toLowerCase(Locale.getDefault()).replace(',', '.');
            String leftNum = leftNormalized.replaceAll("[^0-9.]", "");
            double qty = 1;
            if (leftNum.length() > 0) {
                try {
                    qty = Double.parseDouble(leftNum);
                } catch (Exception ignored) {
                }
            }

            String leftUnit = left.replaceAll("[0-9.,\\s]+", "").trim();
            if (leftUnit.length() == 0) {
                leftUnit = "шт";
            }
            if (right.length() == 0) {
                right = "г";
            }

            item.setQuantity(qty);
            item.setUnit(leftUnit + "/" + right);
            return;
        }

        String value = source.toLowerCase(Locale.getDefault()).replace(',', '.');
        value = value.replace(" ", "");
        String unit = "шт";
        if (value.contains("мл")) {
            unit = "мл";
            value = value.replace("мл", "");
        } else if (value.contains("л")) {
            unit = "л";
            value = value.replace("л", "");
        } else if (value.contains("гр") || value.endsWith("г")) {
            unit = "г";
            value = value.replace("гр", "").replace("г", "");
        } else if (value.contains("шт")) {
            unit = "шт";
            value = value.replace("шт", "");
        }
        value = value.replaceAll("[^0-9.]", "");
        if (value.length() == 0) {
            item.setUnit(unit);
            return;
        }
        try {
            item.setQuantity(Double.parseDouble(value));
            item.setUnit(unit);
        } catch (Exception ignored) {
        }
    }

    private static double parsePrice(String raw) {
        if (raw == null) {
            return 0;
        }
        String value = raw.trim().toLowerCase(Locale.getDefault())
                .replace("руб.", "")
                .replace("руб", "")
                .replace("₽", "")
                .replace(" ", "")
                .replace("\u00a0", "")
                .replace(',', '.');
        value = value.replaceAll("[^0-9.]", "");
        if (value.length() == 0) {
            return 0;
        }
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String cell(List<String> row, int index) {
        if (index < 0 || index >= row.size() || row.get(index) == null) {
            return "";
        }
        return row.get(index);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.getDefault()).replace('ё', 'е');
    }

    private static List<List<String>> readCsv(File file) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        try {
            List<List<String>> rows = new ArrayList<>();
            String first = reader.readLine();
            if (first == null) {
                return rows;
            }
            if (first.startsWith("\ufeff")) {
                first = first.substring(1);
            }
            char delimiter = detectDelimiter(first);
            rows.add(splitCsv(first, delimiter));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().length() == 0) {
                    continue;
                }
                rows.add(splitCsv(line, delimiter));
            }
            return rows;
        } finally {
            reader.close();
        }
    }

    private static char detectDelimiter(String line) {
        int semicolons = countChar(line, ';');
        int commas = countChar(line, ',');
        int tabs = countChar(line, '\t');
        if (tabs >= semicolons && tabs >= commas && tabs > 0) {
            return '\t';
        }
        return semicolons >= commas ? ';' : ',';
    }

    private static int countChar(String line, char ch) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ch) {
                count++;
            }
        }
        return count;
    }

    private static List<String> splitCsv(String line, char delimiter) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                quoted = !quoted;
            } else if (ch == delimiter && !quoted) {
                cells.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(ch);
            }
        }
        cells.add(current.toString().trim());
        return cells;
    }
}
