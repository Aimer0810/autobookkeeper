package com.autobookkeeper.accounting;

import com.autobookkeeper.domain.Bill;
import com.autobookkeeper.domain.TransactionType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BillImportService {

    private static final Logger logger = LoggerFactory.getLogger(BillImportService.class);
    private static final DateTimeFormatter FULL_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // Column type constants for smart detection
    private static final int COL_UNKNOWN = 0;
    private static final int COL_DATE = 1;
    private static final int COL_AMOUNT = 2;
    private static final int COL_MERCHANT = 3;
    private static final int COL_IO = 4;        // 收/支
    private static final int COL_STATUS = 5;
    private static final int COL_TYPE = 6;       // 交易类型
    private static final int COL_PRODUCT = 7;    // 商品名称

    public List<Bill> parse(byte[] data, String filename) {
        String name = filename == null ? "" : filename.toLowerCase();
        if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
            return parseExcel(data);
        }
        String content = detectEncoding(data);
        return parseCsv(content);
    }

    // ==================== Excel Parsing ====================

    private List<Bill> parseExcel(byte[] data) {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) throw new IllegalArgumentException("Excel 文件为空");

            List<String[]> rows = new ArrayList<>();
            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) { rows.add(new String[0]); continue; }
                int lastCol = Math.max(row.getLastCellNum(), 0);
                String[] cells = new String[lastCol];
                for (int j = 0; j < lastCol; j++) {
                    cells[j] = cellToString(row.getCell(j));
                }
                rows.add(cells);
            }
            return parseRows(rows);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Excel 文件解析失败: " + e.getMessage());
        }
    }

    private String cellToString(Cell cell) {
        if (cell == null) return "";
        CellType type = cell.getCellType();
        if (type == CellType.STRING) return cell.getStringCellValue().trim();
        if (type == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                Date d = cell.getDateCellValue();
                return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().format(FULL_DATETIME);
            }
            double v = cell.getNumericCellValue();
            if (v == Math.floor(v) && !Double.isInfinite(v)) return String.valueOf((long) v);
            return String.valueOf(v);
        }
        if (type == CellType.BOOLEAN) return String.valueOf(cell.getBooleanCellValue());
        if (type == CellType.FORMULA) {
            try { return cell.getStringCellValue().trim(); } catch (Exception e1) {
                try { return String.valueOf(cell.getNumericCellValue()); } catch (Exception e2) { return ""; }
            }
        }
        return "";
    }

    // ==================== CSV Parsing ====================

    private List<Bill> parseCsv(String content) {
        List<String[]> rows = new ArrayList<>();
        for (String line : content.split("\n")) {
            if (line.trim().isEmpty()) continue;
            rows.add(parseCsvLine(line));
        }
        return parseRows(rows);
    }

    private String detectEncoding(byte[] data) {
        String utf8 = new String(data, StandardCharsets.UTF_8);
        if (utf8.contains("交易") || utf8.contains("金额") || utf8.contains("收")) return utf8;
        try {
            String gbk = new String(data, Charset.forName("GBK"));
            if (gbk.contains("交易") || gbk.contains("金额")) return gbk;
        } catch (Exception ignored) {}
        return utf8;
    }

    // ==================== Smart Row Parsing ====================

    private List<Bill> parseRows(List<String[]> rows) {
        // Step 1: Find header row by looking for a row with multiple non-empty cells
        //         that contain keywords suggesting it's a data table
        int headerIndex = findHeaderRow(rows);
        if (headerIndex < 0) {
            throw new IllegalArgumentException("无法识别文件格式：找不到数据表头");
        }

        String[] headers = rows.get(headerIndex);

        // Step 2: Detect column types using fuzzy keyword matching
        int[] colTypes = detectColumnTypes(headers);

        // Step 3: Parse data rows
        List<Bill> bills = new ArrayList<>();
        for (int i = headerIndex + 1; i < rows.size(); i++) {
            String[] cols = rows.get(i);
            if (cols.length == 0) continue;
            // Skip summary/footer rows
            String first = cols[0].trim();
            if (first.startsWith("共") || first.startsWith("-") || first.startsWith("=")) continue;
            try {
                Bill bill = parseRow(colTypes, cols, headers);
                if (bill != null) bills.add(bill);
            } catch (Exception e) {
                logger.debug("第{}行解析失败: {}", i, e.getMessage());
            }
        }
        if (bills.isEmpty()) {
            throw new IllegalArgumentException("未识别到有效交易记录，请确认文件包含交易数据");
        }
        return bills;
    }

    private int findHeaderRow(List<String[]> rows) {
        int bestIndex = -1;
        int bestScore = 0;
        for (int i = 0; i < Math.min(rows.size(), 30); i++) {
            String[] row = rows.get(i);
            if (row.length < 3) continue;
            int nonEmpty = 0;
            int keywordScore = 0;
            for (String cell : row) {
                String c = cell.replaceAll("[\\s　]", "");
                if (!c.isEmpty()) nonEmpty++;
                if (containsAny(c, "时间", "日期", "金额", "收", "支", "对方", "商户", "商品",
                        "交易", "金额", "状态", "类型", "备注", "说明", "名称", "付款", "收款")) {
                    keywordScore++;
                }
            }
            int score = keywordScore * 10 + nonEmpty;
            if (score > bestScore && nonEmpty >= 3) {
                bestScore = score;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private int[] detectColumnTypes(String[] headers) {
        int[] types = new int[headers.length];
        for (int i = 0; i < headers.length; i++) {
            types[i] = classifyColumn(headers[i]);
        }
        // Ensure we have at least amount and one of (merchant/product)
        boolean hasAmount = false, hasMerchant = false;
        for (int t : types) {
            if (t == COL_AMOUNT) hasAmount = true;
            if (t == COL_MERCHANT || t == COL_PRODUCT) hasMerchant = true;
        }
        if (!hasAmount) {
            // Try to find amount column by looking for "元" or numeric-looking headers
            for (int i = 0; i < headers.length; i++) {
                if (types[i] == COL_UNKNOWN && (headers[i].contains("元") || headers[i].contains("¥"))) {
                    types[i] = COL_AMOUNT;
                    break;
                }
            }
        }
        return types;
    }

    private int classifyColumn(String header) {
        String h = header.replaceAll("[\\s　()（）]", "");
        if (h.isEmpty()) return COL_UNKNOWN;

        // Date
        if (containsAny(h, "时间", "日期", "创建时间", "付款时间", "交易时间", "支付时间")) return COL_DATE;

        // Amount - be very specific to avoid matching "金额状态" etc
        if (h.equals("金额") || h.equals("金额元") || h.contains("金额") || h.contains("交易金额")
                || h.contains("付款金额") || h.contains("实付") || h.contains("退款金额")) return COL_AMOUNT;

        // Income/Expense indicator
        if (h.contains("收/支") || h.contains("收支") || h.contains("资金状态") || h.equals("收或支")) return COL_IO;

        // Status
        if (containsAny(h, "状态", "交易状态", "当前状态", "资金状态")) return COL_STATUS;

        // Transaction type (e.g. 即时到账, 转账)
        if (h.equals("类型") || h.equals("交易类型") || h.contains("交易来源")) return COL_TYPE;

        // Product name
        if (containsAny(h, "商品", "商品名称", "商品说明", "商品描述")) return COL_PRODUCT;

        // Merchant / counterparty
        if (containsAny(h, "对方", "商家", "商户", "交易对方", "收付款", "收款方", "付款方",
                "商家名称", "对方名称", "交易商户")) return COL_MERCHANT;

        return COL_UNKNOWN;
    }

    private Bill parseRow(int[] colTypes, String[] cols, String[] headers) {
        LocalDate date = LocalDate.now();
        BigDecimal amount = BigDecimal.ZERO;
        String merchant = "";
        boolean isIncome = false;
        boolean hasIO = false;
        String status = "";
        String txType = "";

        for (int i = 0; i < Math.min(cols.length, colTypes.length); i++) {
            String val = cols[i].trim();
            switch (colTypes[i]) {
                case COL_DATE:
                    date = parseDate(val);
                    break;
                case COL_AMOUNT:
                    amount = parseAmount(val);
                    break;
                case COL_MERCHANT:
                    if (merchant.isEmpty()) merchant = val;
                    break;
                case COL_PRODUCT:
                    if (merchant.isEmpty() || merchant.equals("未知商家")) merchant = val;
                    break;
                case COL_IO:
                    hasIO = true;
                    if (val.contains("收入") || val.contains("已收入") || val.contains("入账")) isIncome = true;
                    break;
                case COL_STATUS:
                    status = val;
                    break;
                case COL_TYPE:
                    txType = val;
                    break;
            }
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) return null;
        if (merchant.isEmpty()) merchant = "未知商家";

        // Filter out non-completed transactions
        if (!status.isEmpty()) {
            if (status.contains("退款") || status.contains("关闭") || status.contains("失败")
                    || status.contains("已退款") || status.contains("已关闭")) return null;
            // If status indicates success but we couldn't determine income/expense from IO column,
            // use status hints
            if (!hasIO) {
                if (status.contains("已收入") || status.contains("已收钱") || status.contains("入账")) isIncome = true;
            }
        }

        // Filter out certain transaction types
        if (!txType.isEmpty()) {
            if (txType.contains("不计收支") || txType.contains("退款")) return null;
        }

        return new Bill(date, amount, merchant,
                isIncome ? TransactionType.INCOME : TransactionType.EXPENSE,
                "未分类", "账单导入", "{}", 1.0, false);
    }

    // ==================== Utility Methods ====================

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.trim().isEmpty()) return LocalDate.now();
        String v = value.trim();
        // Try common formats
        for (DateTimeFormatter fmt : new DateTimeFormatter[]{
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                DateTimeFormatter.ofPattern("yyyyMMdd"),
        }) {
            try { return LocalDate.parse(v, fmt); } catch (DateTimeParseException ignored) {}
            try { return LocalDateTime.parse(v, fmt).toLocalDate(); } catch (DateTimeParseException ignored) {}
        }
        // Try substring
        if (v.length() >= 10) {
            try { return LocalDate.parse(v.substring(0, 10)); } catch (Exception ignored) {}
        }
        return LocalDate.now();
    }

    private BigDecimal parseAmount(String value) {
        String cleaned = value.replaceAll("[¥￥,，元\\s\\-+]", "").trim();
        if (cleaned.isEmpty() || cleaned.equals("--") || cleaned.equals("/")) return BigDecimal.ZERO;
        try {
            return new BigDecimal(cleaned).abs();
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuote = false;
        StringBuilder field = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuote && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    inQuote = !inQuote;
                }
            } else if (c == ',' && !inQuote) {
                fields.add(field.toString().trim());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString().trim());
        return fields.toArray(new String[0]);
    }
}
