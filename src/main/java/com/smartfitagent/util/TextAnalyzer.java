package com.smartfitagent.util;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文本分析工具类
 *
 * 用于 AI Pipeline 中对用户输入进行本地预处理：
 *  - 提取数字实体（体重、身高、次数、组数）
 *  - 检测情感倾向（正面/负面/中性）
 *  - 识别健身领域专业术语
 *  - 清理和规范化用户输入
 *  - 文本语言检测（中英文混合处理）
 */
public final class TextAnalyzer {

    private TextAnalyzer() {}

    // ── Number entity extraction ──────────────────────────────────────────────

    public record NumericEntity(double value, String unit, String rawText) {}

    private static final List<Pattern> NUMERIC_PATTERNS = List.of(
        Pattern.compile("(\\d+\\.?\\d*)\\s*(kg|千克|公斤)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\\d+\\.?\\d*)\\s*(斤)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\\d+\\.?\\d*)\\s*(cm|厘米|米)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\\d+\\.?\\d*)\\s*(kcal|千卡|卡路里|卡)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\\d+)\\s*(?:次|个|rep)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\\d+)\\s*(?:组|set)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\\d+)\\s*(?:分钟|min|minute)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\\d+)\\s*(?:小时|h|hour)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\\d+)\\s*(?:天|day)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\\d+)\\s*(?:周|week)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\\d+)\\.?(\\d*)\\s*(?:岁|years?old)", Pattern.CASE_INSENSITIVE)
    );

    public static List<NumericEntity> extractNumbers(String text) {
        List<NumericEntity> entities = new ArrayList<>();
        for (Pattern p : NUMERIC_PATTERNS) {
            Matcher m = p.matcher(text);
            while (m.find()) {
                try {
                    double val = Double.parseDouble(m.group(1));
                    String unit = m.groupCount() >= 2 ? m.group(2) : "";
                    entities.add(new NumericEntity(val, unit, m.group()));
                } catch (NumberFormatException ignored) {}
            }
        }
        return entities;
    }

    /**
     * 从文本中提取最可能的体重值（kg）
     */
    public static OptionalDouble extractWeight(String text) {
        List<NumericEntity> entities = extractNumbers(text);
        for (NumericEntity e : entities) {
            String unit = e.unit().toLowerCase();
            if (unit.contains("kg") || unit.contains("千克") || unit.contains("公斤")) {
                if (e.value() > 30 && e.value() < 300) return OptionalDouble.of(e.value());
            }
            if (unit.contains("斤")) {
                double kg = e.value() / 2.0;
                if (kg > 30 && kg < 300) return OptionalDouble.of(kg);
            }
        }
        return OptionalDouble.empty();
    }

    /**
     * 从文本中提取最可能的身高值（cm）
     */
    public static OptionalDouble extractHeight(String text) {
        List<NumericEntity> entities = extractNumbers(text);
        for (NumericEntity e : entities) {
            String unit = e.unit().toLowerCase();
            if (unit.contains("cm") || unit.contains("厘米")) {
                if (e.value() > 100 && e.value() < 250) return OptionalDouble.of(e.value());
            }
            if (unit.contains("m") && e.value() > 1.0 && e.value() < 2.5) {
                return OptionalDouble.of(e.value() * 100);
            }
        }
        return OptionalDouble.empty();
    }

    // ── Sentiment analysis ────────────────────────────────────────────────────

    public enum Sentiment { POSITIVE, NEGATIVE, NEUTRAL }

    private static final Set<String> POSITIVE_WORDS = new HashSet<>(Arrays.asList(
        "好", "棒", "厉害", "加油", "坚持", "进步", "成功", "满意", "高兴", "感谢",
        "有效", "减肥成功", "增肌了", "瘦了", "变壮了", "状态好", "精力充沛"
    ));

    private static final Set<String> NEGATIVE_WORDS = new HashSet<>(Arrays.asList(
        "难受", "痛", "受伤", "放弃", "失败", "没效果", "累死了", "坚持不住",
        "胖了", "反弹", "焦虑", "担心", "无力", "没动力", "不想练"
    ));

    public static Sentiment analyzeSentiment(String text) {
        if (text == null || text.isBlank()) return Sentiment.NEUTRAL;
        String lower = text.toLowerCase();
        long positive = POSITIVE_WORDS.stream().filter(lower::contains).count();
        long negative = NEGATIVE_WORDS.stream().filter(lower::contains).count();
        if (positive > negative) return Sentiment.POSITIVE;
        if (negative > positive) return Sentiment.NEGATIVE;
        return Sentiment.NEUTRAL;
    }

    // ── Fitness term recognition ──────────────────────────────────────────────

    private static final Map<String, String> FITNESS_TERMS = new LinkedHashMap<>();
    static {
        FITNESS_TERMS.put("bmi", "身体质量指数");
        FITNESS_TERMS.put("doms", "延迟性肌肉酸痛");
        FITNESS_TERMS.put("1rm", "单次最大重量");
        FITNESS_TERMS.put("hiit", "高强度间歇训练");
        FITNESS_TERMS.put("zone2", "二区有氧训练");
        FITNESS_TERMS.put("tdee", "每日总热量消耗");
        FITNESS_TERMS.put("bmr", "基础代谢率");
        FITNESS_TERMS.put("progressive overload", "渐进超负荷");
        FITNESS_TERMS.put("deload", "减负周");
        FITNESS_TERMS.put("supercompensation", "超量恢复");
        FITNESS_TERMS.put("macros", "宏营养素");
        FITNESS_TERMS.put("cutting", "减脂期");
        FITNESS_TERMS.put("bulking", "增肌期");
    }

    public static Map<String, String> recognizeFitnessTerms(String text) {
        Map<String, String> found = new LinkedHashMap<>();
        String lower = text.toLowerCase();
        for (Map.Entry<String, String> e : FITNESS_TERMS.entrySet()) {
            if (lower.contains(e.getKey())) {
                found.put(e.getKey(), e.getValue());
            }
        }
        return found;
    }

    // ── Text normalization ────────────────────────────────────────────────────

    public static String normalize(String text) {
        if (text == null) return "";
        text = text.trim();
        // 全角转半角数字
        text = text.replace("０", "0").replace("１", "1").replace("２", "2")
                   .replace("３", "3").replace("４", "4").replace("５", "5")
                   .replace("６", "6").replace("７", "7").replace("８", "8")
                   .replace("９", "9");
        // 常见缩写展开
        text = text.replace("kg", " kg").replace("Kg", " kg").replace("KG", " kg");
        text = text.replace("cm", " cm").replace("Cm", " cm").replace("CM", " cm");
        // 多个空格合并
        text = text.replaceAll("\\s{2,}", " ").trim();
        return text;
    }

    // ── Language detection ────────────────────────────────────────────────────

    public static boolean isChinese(String text) {
        if (text == null || text.isBlank()) return false;
        long chineseCount = text.chars().filter(c -> c >= 0x4E00 && c <= 0x9FFF).count();
        return chineseCount > text.length() * 0.3;
    }

    public static boolean isEnglish(String text) {
        if (text == null || text.isBlank()) return false;
        long englishCount = text.chars().filter(c -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')).count();
        return englishCount > text.length() * 0.5;
    }

    // ── Text statistics ───────────────────────────────────────────────────────

    public static int wordCount(String text) {
        if (text == null || text.isBlank()) return 0;
        boolean isCh = isChinese(text);
        if (isCh) return text.length(); // 中文以字符计
        return text.trim().split("\\s+").length;
    }

    public static int estimateReadingTimeSeconds(String text) {
        int words = wordCount(text);
        boolean isCh = isChinese(text);
        int wordsPerMinute = isCh ? 400 : 200;
        return (int) Math.ceil((double) words / wordsPerMinute * 60);
    }

    /**
     * 提取文本中的行动词（用于 ResponsePostProcessStep 的行动项提取）
     */
    public static List<String> extractActionVerbs(String text) {
        List<String> verbs = new ArrayList<>();
        String[] lines = text.split("\n");
        Pattern actionPattern = Pattern.compile("(?:需要|建议|应该|记得|务必|确保|坚持|每天|每周)([^，。！？,!?]{2,15})");
        for (String line : lines) {
            Matcher m = actionPattern.matcher(line);
            while (m.find()) {
                verbs.add(m.group(1).trim());
            }
        }
        return verbs.stream().distinct().limit(5).toList();
    }
}
