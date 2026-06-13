package com.autobookkeeper.accounting;

import com.autobookkeeper.domain.Bill;
import com.autobookkeeper.domain.TransactionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class BillImportService {

    private static final Logger logger = LoggerFactory.getLogger(BillImportService.class);
    private static final DateTimeFormatter ALIPAY_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter WECHAT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public List<Bill> parse(byte[] data, String filename) {
        String name = filename == null ? "" : filename.toLowerCase();
        String content = detectAndDecode(data);
        if (name.contains("支付宝") || name.contains("alipay") || content.contains("支付宝")) {
            return parseAlipay(content);
        }
        if (name.contains("微信") || name.contains("wechat") || content.contains("微信支付")) {
            return parseWechat(content);
        }
        // Try auto-detect by content structure
        if (content.contains("交易创建时间") && content.contains("商品说明")) {
            return parseAlipay(content);
        }
        if (content.contains("交易时间") && content.contains("交易对方") && content.contains("支付方式")) {
            return parseWechat(content);
        }
        throw new IllegalArgumentException("无法识别文件格式，请使用支付宝或微信导出的 CSV 文件");
    }

    private String detectAndDecode(byte[] data) {
        // Try UTF-8 first
        String utf8 = new String(data, StandardCharsets.UTF_8);
        if (utf8.contains("交易") || utf8.contains("金额")) {
            return utf8;
        }
        // Try GBK (Alipay sometimes uses GBK)
        try {
            String gbk = new String(data, Charset.forName("GBK"));
            if (gbk.contains("交易") || gbk.contains("金额")) {
                return gbk;
            }
        } catch (Exception ignored) {
        }
        return utf8;
    }

    private List<Bill> parseAlipay(String content) {
        List<Bill> bills = new ArrayList<>();
        String[] lines = content.split("\n");
        // Find header line - skip metadata rows
        int headerIndex = -1;
        for (int i = 0; i < Math.min(lines.length, 30); i++) {
            if (lines[i].contains("交易创建时间") || lines[i].contains("交易号")) {
                headerIndex = i;
                break;
            }
        }
        if (headerIndex < 0) {
            throw new IllegalArgumentException("支付宝账单格式异常：找不到表头行");
        }
        String[] headers = parseCsvLine(lines[headerIndex]);
        int timeIdx = findColumn(headers, "交易创建时间", "付款时间");
        int amountIdx = findColumn(headers, "金额");
        int merchantIdx = findColumn(headers, "商品说明", "商品名称", "对方");
        int typeIdx = findColumn(headers, "收/支", "资金状态");
        int statusIdx = findColumn(headers, "交易状态");

        for (int i = headerIndex + 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("-")) continue;
            try {
                String[] cols = parseCsvLine(line);
                if (cols.length <= Math.max(amountIdx, merchantIdx)) continue;

                // Skip non-completed transactions
                if (statusIdx >= 0 && statusIdx < cols.length) {
                    String status = cols[statusIdx].trim();
                    if (!status.isEmpty() && !status.contains("成功") && !status.contains("已收入") && !status.contains("已支出")) {
                        continue;
                    }
                }

                String typeStr = typeIdx >= 0 && typeIdx < cols.length ? cols[typeIdx].trim() : "";
                // Skip refunds and transfers that aren't income/expense
                if (typeStr.contains("不计收支") || typeStr.contains("退款")) continue;

                boolean isIncome = typeStr.contains("收入");
                BigDecimal amount = parseAmount(cols[amountIdx].trim());
                if (amount.compareTo(BigDecimal.ZERO) <= 0) continue;

                String merchant = merchantIdx >= 0 && merchantIdx < cols.length ? cols[merchantIdx].trim() : "未知商家";
                if (merchant.isEmpty()) merchant = "未知商家";

                LocalDate date = LocalDate.now();
                if (timeIdx >= 0 && timeIdx < cols.length) {
                    try {
                        date = LocalDateTime.parse(cols[timeIdx].trim(), ALIPAY_DATE).toLocalDate();
                    } catch (Exception e) {
                        try { date = LocalDate.parse(cols[timeIdx].trim().substring(0, 10)); } catch (Exception ignored) {}
                    }
                }

                Bill bill = new Bill(date, amount, merchant, isIncome ? TransactionType.INCOME : TransactionType.EXPENSE,
                        "未分类", "支付宝导入", "{}", 1.0, false);
                bills.add(bill);
            } catch (Exception e) {
                logger.debug("支付宝账单第{}行解析失败: {}", i, e.getMessage());
            }
        }
        return bills;
    }

    private List<Bill> parseWechat(String content) {
        List<Bill> bills = new ArrayList<>();
        String[] lines = content.split("\n");
        int headerIndex = -1;
        for (int i = 0; i < Math.min(lines.length, 20); i++) {
            if (lines[i].contains("交易时间") && lines[i].contains("交易对方")) {
                headerIndex = i;
                break;
            }
        }
        if (headerIndex < 0) {
            throw new IllegalArgumentException("微信账单格式异常：找不到表头行");
        }
        String[] headers = parseCsvLine(lines[headerIndex]);
        int timeIdx = findColumn(headers, "交易时间");
        int amountIdx = findColumn(headers, "金额");
        int merchantIdx = findColumn(headers, "交易对方");
        int productIdx = findColumn(headers, "商品");
        int typeIdx = findColumn(headers, "收/支");
        int statusIdx = findColumn(headers, "当前状态");
        int txTypeIdx = findColumn(headers, "交易类型");

        for (int i = headerIndex + 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("-") || line.startsWith("共")) continue;
            try {
                String[] cols = parseCsvLine(line);
                if (cols.length <= Math.max(amountIdx, merchantIdx)) continue;

                // Skip non-completed
                if (statusIdx >= 0 && statusIdx < cols.length) {
                    String status = cols[statusIdx].trim();
                    if (!status.contains("已支付") && !status.contains("已收钱") && !status.contains("已存入零钱")) continue;
                }

                // Skip certain transaction types
                if (txTypeIdx >= 0 && txTypeIdx < cols.length) {
                    String txType = cols[txTypeIdx].trim();
                    if (txType.contains("转账") || txType.contains("红包")) continue;
                }

                String typeStr = typeIdx >= 0 && typeIdx < cols.length ? cols[typeIdx].trim() : "";
                if (typeStr.contains("/") || typeStr.isEmpty()) continue; // "/" means not income/expense
                boolean isIncome = typeStr.contains("收入");

                BigDecimal amount = parseAmount(cols[amountIdx].trim());
                if (amount.compareTo(BigDecimal.ZERO) <= 0) continue;

                // Prefer product name, fallback to merchant
                String merchant = "";
                if (productIdx >= 0 && productIdx < cols.length) {
                    merchant = cols[productIdx].trim();
                }
                if (merchant.isEmpty() && merchantIdx >= 0 && merchantIdx < cols.length) {
                    merchant = cols[merchantIdx].trim();
                }
                if (merchant.isEmpty()) merchant = "未知商家";

                LocalDate date = LocalDate.now();
                if (timeIdx >= 0 && timeIdx < cols.length) {
                    try {
                        date = LocalDateTime.parse(cols[timeIdx].trim(), WECHAT_DATE).toLocalDate();
                    } catch (Exception e) {
                        try { date = LocalDate.parse(cols[timeIdx].trim().substring(0, 10)); } catch (Exception ignored) {}
                    }
                }

                Bill bill = new Bill(date, amount, merchant, isIncome ? TransactionType.INCOME : TransactionType.EXPENSE,
                        "未分类", "微信导入", "{}", 1.0, false);
                bills.add(bill);
            } catch (Exception e) {
                logger.debug("微信账单第{}行解析失败: {}", i, e.getMessage());
            }
        }
        return bills;
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

    private int findColumn(String[] headers, String... names) {
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i].replaceAll("[\\s ]", "");
            for (String name : names) {
                if (h.contains(name)) return i;
            }
        }
        return -1;
    }

    private BigDecimal parseAmount(String value) {
        String cleaned = value.replaceAll("[¥￥,，元\\s]", "").trim();
        if (cleaned.isEmpty() || cleaned.equals("-") || cleaned.equals("--")) return BigDecimal.ZERO;
        try {
            return new BigDecimal(cleaned).abs();
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
