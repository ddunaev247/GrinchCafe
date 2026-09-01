package com.grinchcafe.admin.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

/**
 * Reads the first worksheet of an .xlsx file into a table of strings.
 */
public final class XlsxTableReader {

    private XlsxTableReader() {
    }

    public static List<List<String>> read(File file) throws Exception {
        ZipFile zip = new ZipFile(file);
        try {
            List<String> shared = readSharedStrings(zip);
            ZipEntry sheet = zip.getEntry("xl/worksheets/sheet1.xml");
            if (sheet == null) {
                sheet = findFirstSheet(zip);
            }
            if (sheet == null) {
                throw new IllegalArgumentException("В файле нет листа Excel");
            }
            InputStream in = zip.getInputStream(sheet);
            try {
                return readSheet(in, shared);
            } finally {
                in.close();
            }
        } finally {
            zip.close();
        }
    }

    private static ZipEntry findFirstSheet(ZipFile zip) {
        java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
        ZipEntry first = null;
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")) {
                if (first == null || name.compareTo(first.getName()) < 0) {
                    first = entry;
                }
            }
        }
        return first;
    }

    private static List<String> readSharedStrings(ZipFile zip) throws Exception {
        List<String> list = new ArrayList<>();
        ZipEntry entry = zip.getEntry("xl/sharedStrings.xml");
        if (entry == null) {
            return list;
        }
        InputStream in = zip.getInputStream(entry);
        try {
            XmlPullParser parser = newPullParser(in);
            int event = parser.getEventType();
            StringBuilder current = null;
            boolean inSi = false;
            boolean inT = false;
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String name = localName(parser);
                    if ("si".equals(name)) {
                        inSi = true;
                        current = new StringBuilder();
                    } else if (inSi && "t".equals(name)) {
                        inT = true;
                    }
                } else if (event == XmlPullParser.TEXT) {
                    if (inT && current != null) {
                        current.append(parser.getText());
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    String name = localName(parser);
                    if ("t".equals(name)) {
                        inT = false;
                    } else if ("si".equals(name)) {
                        list.add(current == null ? "" : current.toString());
                        current = null;
                        inSi = false;
                    }
                }
                event = parser.next();
            }
        } finally {
            in.close();
        }
        return list;
    }

    private static List<List<String>> readSheet(InputStream in, List<String> shared) throws Exception {
        XmlPullParser parser = newPullParser(in);
        List<List<String>> rows = new ArrayList<>();
        Map<Integer, String> currentRow = null;
        int maxCol = 0;
        String cellRef = null;
        String cellType = null;
        StringBuilder value = null;
        boolean inV = false;
        boolean inT = false;

        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String name = localName(parser);
                if ("row".equals(name)) {
                    currentRow = new HashMap<>();
                } else if ("c".equals(name)) {
                    cellRef = attr(parser, "r");
                    cellType = attr(parser, "t");
                    value = new StringBuilder();
                } else if ("v".equals(name) || "t".equals(name)) {
                    inV = "v".equals(name);
                    inT = "t".equals(name);
                }
            } else if (event == XmlPullParser.TEXT) {
                if ((inV || inT) && value != null) {
                    value.append(parser.getText());
                }
            } else if (event == XmlPullParser.END_TAG) {
                String name = localName(parser);
                if ("v".equals(name) || "t".equals(name)) {
                    inV = false;
                    inT = false;
                } else if ("c".equals(name)) {
                    if (currentRow != null) {
                        int col = columnIndex(cellRef);
                        currentRow.put(col, cellText(cellType, value == null ? "" : value.toString(), shared));
                        if (col > maxCol) {
                            maxCol = col;
                        }
                    }
                    cellRef = null;
                    cellType = null;
                    value = null;
                } else if ("row".equals(name) && currentRow != null) {
                    List<String> row = new ArrayList<>();
                    int last = Math.max(maxCol, 4);
                    for (int i = 0; i <= last; i++) {
                        String cell = currentRow.get(i);
                        row.add(cell == null ? "" : cell);
                    }
                    rows.add(row);
                    currentRow = null;
                }
            }
            event = parser.next();
        }
        return rows;
    }

    private static String cellText(String type, String raw, List<String> shared) {
        if (raw == null) {
            return "";
        }
        if ("s".equals(type)) {
            try {
                int index = Integer.parseInt(raw.trim());
                if (index >= 0 && index < shared.size()) {
                    return shared.get(index);
                }
            } catch (Exception ignored) {
            }
            return "";
        }
        if ("b".equals(type)) {
            return "1".equals(raw) ? "да" : "";
        }
        return raw;
    }

    private static int columnIndex(String cellRef) {
        if (cellRef == null || cellRef.length() == 0) {
            return 0;
        }
        int col = 0;
        for (int i = 0; i < cellRef.length(); i++) {
            char ch = cellRef.charAt(i);
            if (ch >= 'A' && ch <= 'Z') {
                col = col * 26 + (ch - 'A' + 1);
            } else if (ch >= 'a' && ch <= 'z') {
                col = col * 26 + (ch - 'a' + 1);
            } else {
                break;
            }
        }
        return Math.max(0, col - 1);
    }

    private static XmlPullParser newPullParser(InputStream in) throws Exception {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new BufferedInputStream(in), "UTF-8");
        return parser;
    }

    private static String attr(XmlPullParser parser, String name) {
        String value = parser.getAttributeValue(null, name);
        if (value != null) {
            return value;
        }
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            String attrName = parser.getAttributeName(i);
            if (attrName != null && (attrName.equals(name) || attrName.endsWith(":" + name))) {
                return parser.getAttributeValue(i);
            }
        }
        return null;
    }

    private static String localName(XmlPullParser parser) {
        String name = parser.getName();
        if (name == null) {
            return "";
        }
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    public static boolean isXlsx(File file) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            byte[] header = new byte[4];
            if (in.read(header) < 2) {
                return false;
            }
            return header[0] == 'P' && header[1] == 'K';
        } catch (Exception e) {
            return false;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static boolean isLegacyXls(File file) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            int a = in.read();
            int b = in.read();
            return a == 0xD0 && b == 0xCF;
        } catch (Exception e) {
            return false;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
