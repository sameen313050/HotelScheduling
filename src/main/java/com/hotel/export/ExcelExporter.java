package com.hotel.export;

import com.hotel.dao.*;
import com.hotel.model.Employee;
import com.hotel.model.Facility;
import com.hotel.model.ShiftType;
import com.hotel.scheduling.Assignment;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.List;

/**
 * 排班表 Excel 导出
 * シフト表 Excel 出力
 * Schedule Excel export
 */
public class ExcelExporter {

    // 星期简称（日本常用：日月火水木金土）
    // 曜日の略称（日本の慣例：日月火水木金土）
    // Weekday short names (Japanese convention: Sun-Sat → 日月火水木金土)
    private static final String[] WEEK_NAMES = {"日", "月", "火", "水", "木", "金", "土"};

    // 员工类型排序：正社员 → 兼职 → 契约 → 夜勤专属
    // 従業員種別の並び順：正社員 → アルバイト → 契約社員 → 夜勤専属
    // Employee type sort order: regular → part-time → contract → night-only
    private static final Map<String, Integer> TYPE_ORDER = new HashMap<>();
    static {
        TYPE_ORDER.put("正社員", 1);
        TYPE_ORDER.put("アルバイト", 2);
        TYPE_ORDER.put("契約社員", 3);
        TYPE_ORDER.put("夜勤専属", 4);
    }

    private final EmployeeDao employeeDao = new EmployeeDao();
    private final FacilityDao facilityDao = new FacilityDao();
    private final WishOffDao wishOffDao = new WishOffDao();
    private final PaidLeaveDao paidLeaveDao = new PaidLeaveDao();

    /**
     * 导出一整月的排班报告到 Excel 文件（3 个 Sheet）
     * 月次シフト表を Excel ファイルに出力（3 シート構成）
     * Exports a monthly schedule report as an Excel file (3 sheets)
     */
    public void export(String yearMonth, List<Assignment> assignments, File outputFile) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            String[] parts = yearMonth.split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);

            buildByEmployeeSheet(workbook, year, month, assignments);
            buildByFacilitySheet(workbook, year, month, assignments);
            buildStatisticsSheet(workbook, year, month, yearMonth, assignments);

            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                workbook.write(fos);
            }
        }
    }

    // ===== Sheet 1：按员工看 / 従業員別シフト表 / Sheet 1: by employee =====
    private void buildByEmployeeSheet(Workbook wb, int year, int month, List<Assignment> assignments) {
        Sheet sheet = wb.createSheet("従業員別シフト表");
        YearMonth ym = YearMonth.of(year, month);
        int daysInMonth = ym.lengthOfMonth();

        CellStyle headerStyle = createHeaderStyle(wb);
        CellStyle nameStyle = createNameStyle(wb);
        CellStyle normalStyle = createNormalStyle(wb);
        CellStyle wishUnmetStyle = createWishUnmetStyle(wb);
        CellStyle paidLeaveStyle = createPaidLeaveStyle(wb);

        // 标题 / タイトル / Title
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(year + " 年 " + month + " 月 シフト表（従業員別)");
        CellStyle titleStyle = wb.createCellStyle();
        Font titleFont = wb.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, daysInMonth + 1));

        // 表头：従業員 | 1(日) | 2(月) | ... | 勤務時間
        // Headers: Employee | 1(Sun) | 2(Mon) | ... | Working hours
        Row headerRow = sheet.createRow(2);
        Cell c0 = headerRow.createCell(0);
        c0.setCellValue("従業員");
        c0.setCellStyle(headerStyle);

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate d = ym.atDay(day);
            int wdIdx = d.getDayOfWeek().getValue() % 7;
            Cell c = headerRow.createCell(day);
            c.setCellValue(day + "(" + WEEK_NAMES[wdIdx] + ")");
            c.setCellStyle(headerStyle);
        }
        Cell lastCol = headerRow.createCell(daysInMonth + 1);
        lastCol.setCellValue("勤務時間");
        lastCol.setCellStyle(headerStyle);

        // 员工数据 / 従業員データ / Employee rows
        List<Employee> employees = getSortedActiveEmployees();
        String yearMonthStr = String.format("%04d-%02d", year, month);

        int rowIdx = 3;
        for (Employee emp : employees) {
            Row row = sheet.createRow(rowIdx++);
            Cell nameCell = row.createCell(0);
            nameCell.setCellValue(emp.getName());
            nameCell.setCellStyle(nameStyle);

            Set<String> wishDates = wishOffDao.findByEmployeeAndMonth(emp.getId(), yearMonthStr);
            Map<String, Integer> paidLeaves = paidLeaveDao.findByEmployeeAndMonth(emp.getId(), yearMonthStr);

            int totalMinutes = 0;
            for (int day = 1; day <= daysInMonth; day++) {
                LocalDate date = ym.atDay(day);
                String dateStr = date.toString();

                Assignment found = null;
                for (Assignment a : assignments) {
                    if (a.getDate().equals(date) && a.getEmployee().getId() == emp.getId()) {
                        found = a;
                        break;
                    }
                }

                Cell c = row.createCell(day);
                if (found != null) {
                    String facShort = found.getFacility().getName().length() > 3
                            ? found.getFacility().getName().substring(0, 3)
                            : found.getFacility().getName();
                    c.setCellValue(found.getShiftType().getPeriod() + " " + facShort);

                    // 希望休未满足 → 红框
                    // 希望休が未達 → 赤枠
                    // Unmet preferred day-off → red border
                    if (wishDates.contains(dateStr)) {
                        c.setCellStyle(wishUnmetStyle);
                    } else {
                        c.setCellStyle(normalStyle);
                    }
                    totalMinutes += shiftDurationMinutes(found.getShiftType());
                } else if (paidLeaves.containsKey(dateStr)) {
                    c.setCellValue("有休 " + paidLeaves.get(dateStr) + "h");
                    c.setCellStyle(paidLeaveStyle);
                } else {
                    c.setCellValue("");
                    c.setCellStyle(normalStyle);
                }
            }

            Cell hoursCell = row.createCell(daysInMonth + 1);
            hoursCell.setCellValue(String.format("%.1f h", totalMinutes / 60.0));
            hoursCell.setCellStyle(nameStyle);
        }

        sheet.setColumnWidth(0, 3000);
        for (int day = 1; day <= daysInMonth; day++) sheet.setColumnWidth(day, 2600);
        sheet.setColumnWidth(daysInMonth + 1, 2800);
        // 冻结表头和员工列 / ヘッダと従業員列を固定表示 / Freeze header row and employee column
        sheet.createFreezePane(1, 3);
    }

    // ===== Sheet 2：按设施看 / 施設別シフト表 / Sheet 2: by facility =====
    private void buildByFacilitySheet(Workbook wb, int year, int month, List<Assignment> assignments) {
        Sheet sheet = wb.createSheet("施設別シフト表");
        YearMonth ym = YearMonth.of(year, month);
        int daysInMonth = ym.lengthOfMonth();

        CellStyle headerStyle = createHeaderStyle(wb);
        CellStyle nameStyle = createNameStyle(wb);
        CellStyle normalStyle = createNormalStyle(wb);
        // 单元格自动换行（一个设施一天可能有多个员工）
        // セル内で自動改行（1 施設・1 日に複数従業員が入ることがある）
        // Wrap text in cell (a facility may have multiple employees on the same day)
        normalStyle.setWrapText(true);

        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(year + " 年 " + month + " 月 シフト表（施設別)");
        CellStyle titleStyle = wb.createCellStyle();
        Font titleFont = wb.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, daysInMonth));

        Row headerRow = sheet.createRow(2);
        Cell c0 = headerRow.createCell(0);
        c0.setCellValue("施設");
        c0.setCellStyle(headerStyle);

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate d = ym.atDay(day);
            int wdIdx = d.getDayOfWeek().getValue() % 7;
            Cell c = headerRow.createCell(day);
            c.setCellValue(day + "(" + WEEK_NAMES[wdIdx] + ")");
            c.setCellStyle(headerStyle);
        }

        List<Facility> facilities = facilityDao.findAll();
        int rowIdx = 3;
        for (Facility f : facilities) {
            Row row = sheet.createRow(rowIdx++);
            row.setHeightInPoints(80);
            Cell nameCell = row.createCell(0);
            nameCell.setCellValue(f.getName());
            nameCell.setCellStyle(nameStyle);

            for (int day = 1; day <= daysInMonth; day++) {
                LocalDate date = ym.atDay(day);
                List<Assignment> cellList = new ArrayList<>();
                for (Assignment a : assignments) {
                    if (a.getDate().equals(date) && a.getFacility().getId() == f.getId()) {
                        cellList.add(a);
                    }
                }

                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < cellList.size(); i++) {
                    if (i > 0) sb.append("\n");
                    Assignment a = cellList.get(i);
                    sb.append(a.getEmployee().getName())
                            .append("(").append(a.getShiftType().getPeriod()).append(")");
                }

                Cell c = row.createCell(day);
                c.setCellValue(sb.toString());
                c.setCellStyle(normalStyle);
            }
        }

        sheet.setColumnWidth(0, 4000);
        for (int day = 1; day <= daysInMonth; day++) sheet.setColumnWidth(day, 3500);
        sheet.createFreezePane(1, 3);
    }

    // ===== Sheet 3：统计 / 勤務時間統計 / Sheet 3: statistics =====
    private void buildStatisticsSheet(Workbook wb, int year, int month, String yearMonth, List<Assignment> assignments) {
        Sheet sheet = wb.createSheet("勤務時間統計");

        CellStyle headerStyle = createHeaderStyle(wb);
        CellStyle normalStyle = createNormalStyle(wb);
        CellStyle shortStyle = createShortStyle(wb);

        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(year + " 年 " + month + " 月 勤務時間統計");
        CellStyle titleStyle = wb.createCellStyle();
        Font titleFont = wb.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        titleStyle.setFont(titleFont);
        titleCell.setCellStyle(titleStyle);

        // 表头 / ヘッダ / Column headers
        String[] cols = {"氏名", "種別", "出勤日数", "シフト時間", "有休", "合計時間", "目標時間", "差分", "朝", "昼", "夕", "夜", "希望休充足"};
        Row headerRow = sheet.createRow(2);
        for (int i = 0; i < cols.length; i++) {
            Cell c = headerRow.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(headerStyle);
        }

        List<Employee> employees = getSortedActiveEmployees();
        Map<Integer, Set<LocalDate>> workDaysByEmp = new HashMap<>();
        for (Assignment a : assignments) {
            workDaysByEmp.computeIfAbsent(a.getEmployee().getId(), k -> new HashSet<>())
                    .add(a.getDate());
        }

        int rowIdx = 3;
        for (Employee emp : employees) {
            int days = workDaysByEmp.getOrDefault(emp.getId(), Collections.emptySet()).size();

            // 各时段班次计数 / 時間帯別のシフト回数 / Count shifts per time slot
            int cntA = 0, cntH = 0, cntY = 0, cntN = 0;
            int totalMinutes = 0;
            for (Assignment a : assignments) {
                if (a.getEmployee().getId() != emp.getId()) continue;
                String p = a.getShiftType().getPeriod();
                switch (p) {
                    case "朝": cntA++; break;
                    case "昼": cntH++; break;
                    case "夕": cntY++; break;
                    case "夜": cntN++; break;
                }
                totalMinutes += shiftDurationMinutes(a.getShiftType());
            }

            // 累加有休小时数（有休也算工时）
            // 有給休暇の時間数を加算（有給も勤務時間に計上）
            // Add paid leave hours (paid leave counts as working hours)
            int paidLeaveHours = 0;
            for (Integer h : paidLeaveDao.findByEmployeeAndMonth(emp.getId(), yearMonth).values()) {
                paidLeaveHours += h;
            }
            double scheduledHours = totalMinutes / 60.0;
            double totalHours = scheduledHours + paidLeaveHours;

            Set<String> wishDates = wishOffDao.findByEmployeeAndMonth(emp.getId(), yearMonth);
            int wishTotal = wishDates.size();
            int wishMet = 0;
            Set<LocalDate> workDates = workDaysByEmp.getOrDefault(emp.getId(), Collections.emptySet());
            for (String ds : wishDates) {
                if (!workDates.contains(LocalDate.parse(ds))) wishMet++;
            }

            // 仅正社员有目标工时（40h/周 × 当月天数 / 7）
            // 正社員のみ目標時間を設定（40h/週 × 当月日数 / 7）
            // Only regular employees have target hours (40h/week × days in month / 7)
            String targetText = "—";
            String gapText = "—";
            boolean isShort = false;
            if ("正社員".equals(emp.getEmployeeType())) {
                int target = (int) Math.round(40.0 * YearMonth.of(year, month).lengthOfMonth() / 7.0);
                double gap = totalHours - target;
                targetText = target + " h";
                gapText = String.format("%+.1f h", gap);
                if (gap < -1) isShort = true;
            }

            Row row = sheet.createRow(rowIdx++);
            CellStyle rowStyle = isShort ? shortStyle : normalStyle;
            Object[] vals = {
                    emp.getName(),
                    typeShort(emp.getEmployeeType()),
                    days + " 日",
                    String.format("%.1f h", scheduledHours),
                    paidLeaveHours == 0 ? "—" : paidLeaveHours + " h",
                    String.format("%.1f h", totalHours),
                    targetText,
                    gapText,
                    cntA, cntH, cntY, cntN,
                    wishTotal == 0 ? "—" : wishMet + " / " + wishTotal
            };
            for (int i = 0; i < vals.length; i++) {
                Cell c = row.createCell(i);
                if (vals[i] instanceof Integer) {
                    c.setCellValue((Integer) vals[i]);
                } else {
                    c.setCellValue(vals[i].toString());
                }
                c.setCellStyle(rowStyle);
            }
        }

        int[] widths = {2500, 2200, 1800, 2200, 1800, 2200, 2200, 2000, 1200, 1200, 1200, 1200, 2500};
        for (int i = 0; i < widths.length; i++) sheet.setColumnWidth(i, widths[i]);
    }

    // ===== 工具方法 / ユーティリティ / Utility methods =====

    /**
     * 获取按类型排序的在职员工列表
     * 種別順で並べた在職従業員リストを取得
     * Returns active employees sorted by type
     */
    private List<Employee> getSortedActiveEmployees() {
        List<Employee> list = new ArrayList<>();
        for (Employee e : employeeDao.findAll()) {
            if (e.isActive()) list.add(e);
        }
        list.sort((a, b) -> {
            int oa = TYPE_ORDER.getOrDefault(a.getEmployeeType(), 99);
            int ob = TYPE_ORDER.getOrDefault(b.getEmployeeType(), 99);
            if (oa != ob) return Integer.compare(oa, ob);
            return Integer.compare(a.getId(), b.getId());
        });
        return list;
    }

    /**
     * 员工类型显示短名
     * 従業員種別の短縮表示名
     * Short display name for employee type
     */
    private String typeShort(String t) {
        if (t == null) return "";
        switch (t) {
            case "正社員": return "正社員";
            case "契約社員": return "契約社員";
            case "アルバイト": return "アルバイト";
            case "夜勤専属": return "夜勤専属";
            default: return t;
        }
    }

    /**
     * 计算班次时长（分钟），跨日班次自动 +24h
     * シフト時間（分）を計算、日跨ぎシフトは自動的に +24h
     * Calculates shift duration in minutes; cross-day shifts automatically add 24h
     */
    private int shiftDurationMinutes(ShiftType s) {
        String[] st = s.getStartTime().split(":");
        String[] et = s.getEndTime().split(":");
        int sm = Integer.parseInt(st[0]) * 60 + Integer.parseInt(st[1]);
        int em = Integer.parseInt(et[0]) * 60 + Integer.parseInt(et[1]);
        int d = em - sm;
        if (d <= 0) d += 24 * 60;
        return d;
    }

    // ===== 单元格样式 / セルスタイル / Cell styles =====

    /** 表头样式（灰底 + 粗体） / ヘッダスタイル（グレー背景＋太字） / Header style (grey background + bold) */
    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        addBorders(s);
        return s;
    }

    /** 员工/设施名样式 / 従業員・施設名のスタイル / Employee/facility name style */
    private CellStyle createNameStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        addBorders(s);
        return s;
    }

    /** 普通单元格样式 / 通常セルのスタイル / Normal cell style */
    private CellStyle createNormalStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        addBorders(s);
        return s;
    }

    /** 希望休未满足样式（粉底 + 红字 + 红粗框） / 希望休未達スタイル（ピンク背景＋赤字＋赤枠） / Unmet preferred day-off style (pink bg + red text + red border) */
    private CellStyle createWishUnmetStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setColor(IndexedColors.DARK_RED.getIndex());
        f.setBold(true);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        s.setBorderTop(BorderStyle.MEDIUM);
        s.setBorderBottom(BorderStyle.MEDIUM);
        s.setBorderLeft(BorderStyle.MEDIUM);
        s.setBorderRight(BorderStyle.MEDIUM);
        s.setTopBorderColor(IndexedColors.RED.getIndex());
        s.setBottomBorderColor(IndexedColors.RED.getIndex());
        s.setLeftBorderColor(IndexedColors.RED.getIndex());
        s.setRightBorderColor(IndexedColors.RED.getIndex());
        return s;
    }

    /** 有休样式（浅蓝底 + 深蓝字） / 有給休暇スタイル（薄青背景＋濃青字） / Paid leave style (light blue bg + dark blue text) */
    private CellStyle createPaidLeaveStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setColor(IndexedColors.DARK_BLUE.getIndex());
        f.setBold(true);
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        addBorders(s);
        return s;
    }

    /** 工时不足样式（红字） / 勤務時間不足スタイル（赤字） / Under-target style (red text) */
    private CellStyle createShortStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setColor(IndexedColors.RED.getIndex());
        s.setFont(f);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setVerticalAlignment(VerticalAlignment.CENTER);
        addBorders(s);
        return s;
    }

    /** 添加四边细边框 / 四辺に細いボーダー追加 / Add thin borders on all sides */
    private void addBorders(CellStyle s) {
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
    }
}