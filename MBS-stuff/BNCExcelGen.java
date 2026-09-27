package com.ctl.sbm.helper;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class BNCExcelGen {

    private static Logger logger = LoggerFactory.getLogger(BNCExcelGen.class);

    public static String GetCurrentTimeStamp() {
        SimpleDateFormat sdfDate = new SimpleDateFormat("MM-dd-yyyy");
        Date now = new Date();
        String strDate = sdfDate.format(now);
        return strDate;
    }

    protected static Connection MBSgetConnection(String Url, String Uname, String Password, String Driver) throws java.sql.SQLException {
        Connection mCon = null;
        try {
            Class.forName(Driver);
        } catch (ClassNotFoundException e1) {
            e1.printStackTrace();
        }
        try {
            mCon = DriverManager.getConnection(Url, Uname, Password);
            System.out.println("Connection created");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return mCon;
    }

    public boolean generateBillCompareReport(String aCustomerId, String numOfBillingMonths, String systemName, HttpServletResponse response) {
        logger.info("Generating Bill Compare Report");
        boolean isSuccess = true;
        XSSFWorkbook workbook = new XSSFWorkbook();
        Connection localConnection = null;
        CallableStatement cs = null;
        ResultSet result = null;
        XSSFSheet sheet = null;

        try {
            localConnection = establishDatabaseConnection();  //  1
            result = executeStoredProcedure(localConnection, aCustomerId, numOfBillingMonths, systemName);  //  2

            // Initialize maps and process result set
            Map<String, Map<String, BigDecimal>> revenueMap = new HashMap<>();
            Map<String, Map<String, BigDecimal>> expenseMap = new HashMap<>();
            Map<String, Map<String, BigDecimal>> taxMap = new HashMap<>();
            Map<String, Map<String, BigDecimal>> paymentMap = new HashMap<>();
            Map<String, Map<String, BigDecimal>> adjustmentMap = new HashMap<>();
            Map<String, String> serviceCodeDescMap = new HashMap<>();
            Set<String> months = new LinkedHashSet<>();
            Map<String, BigDecimal> monthlyTotals = new HashMap<>();

            processResultSet(result, revenueMap, expenseMap, taxMap, paymentMap, adjustmentMap, serviceCodeDescMap,
                    months, monthlyTotals);  //  3

            // Generate Excel File
            sheet = workbook.createSheet("Bill Compare");
            generateExcelSheet(sheet, months, revenueMap, expenseMap, taxMap, paymentMap, adjustmentMap,
                    serviceCodeDescMap, monthlyTotals);  // 4

            writeWorkbookToResponse(workbook, response, aCustomerId);  // 5

        } catch (SQLException | IOException e) {
            isSuccess = false;
            e.printStackTrace();
        } finally {
            cleanupResources(result, cs, localConnection);
        }
        return isSuccess;
    }

    //Establish Database Connection
    private Connection establishDatabaseConnection() throws SQLException {
        URL resource = BNCExcelGen.class.getResource("/application.properties");
        Properties prop = new Properties();
        try (InputStream input = Files.newInputStream(Paths.get(resource.toURI()).toFile().toPath())) {
            prop.load(input);
            String jdbcURL = prop.getProperty("spring.datasource.url");
            String username = prop.getProperty("spring.datasource.username");
            String password = prop.getProperty("spring.datasource.password");
            String Driver = prop.getProperty("spring.datasource.driver-class-name");

            return MBSgetConnection(jdbcURL, username, password, Driver);
        } catch (URISyntaxException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Execute Stored Procedure
    private ResultSet executeStoredProcedure(Connection connection, String aCustomerId, String numOfBillingMonths,
                                             String systemName) throws SQLException {
        CallableStatement cs = connection.prepareCall("{call MBSOWNER.BILL_COMPARE_NON_QBC_NEW(?,?,?,?)}");
        cs.setString(1, aCustomerId);
        cs.setString(2, systemName);
        cs.setInt(3, Integer.parseInt(numOfBillingMonths));
        cs.registerOutParameter(4, oracle.jdbc.OracleTypes.CURSOR);
        cs.execute();
        return (ResultSet) cs.getObject(4);
    }

    //Process Result Set
    private void processResultSet(ResultSet result,
                                  Map<String, Map<String, BigDecimal>> revenueMap,
                                  Map<String, Map<String, BigDecimal>> expenseMap,
                                  Map<String, Map<String, BigDecimal>> taxMap,
                                  Map<String, Map<String, BigDecimal>> paymentMap,
                                  Map<String, Map<String, BigDecimal>> adjustmentMap,
                                  Map<String, String> serviceCodeDescMap,
                                  Set<String> months,
                                  Map<String, BigDecimal> monthlyTotals) throws SQLException {
        while (result.next()) {
            String serviceCode = result.getString(1).trim();
            String description = result.getString(4).trim();
            String billPullMnth = (result.getString(5) + " " + result.getString(6)).trim();

            // Handle special case for year 2000, replace with 2100
            if (billPullMnth.endsWith("2000")) {
                billPullMnth = billPullMnth.replace("2000", "2100");
            }

            String chargeSrc = result.getString(7).trim();
            BigDecimal sumCrm = result.getBigDecimal(8) != null ? result.getBigDecimal(8) : BigDecimal.ZERO;
            BigDecimal sumPrm = result.getBigDecimal(9) != null ? result.getBigDecimal(9) : BigDecimal.ZERO;
            String expType = result.getString(11).trim();

            // Key for service code and charge source combination
            String key = serviceCode + "|" + chargeSrc;
            serviceCodeDescMap.put(key, description);
            months.add(billPullMnth);

            // Categorize the values and populate the maps
            BigDecimal revenue = BigDecimal.ZERO;
            BigDecimal expense = BigDecimal.ZERO;
            BigDecimal tax = BigDecimal.ZERO;
            BigDecimal payment = BigDecimal.ZERO;
            BigDecimal adjustment = BigDecimal.ZERO;

            switch (serviceCode) {
                case "PYMNT_AMNT":
                    payment = sumPrm.compareTo(BigDecimal.ZERO) != 0 ? sumPrm : sumCrm;
                    paymentMap.computeIfAbsent(key, k -> new HashMap<>()).put(billPullMnth, payment);
                    revenue = payment; // Display in Revenue column
                    break;
                case "TAX_AMNT":
                    tax = sumPrm.compareTo(BigDecimal.ZERO) != 0 ? sumPrm : sumCrm;
                    taxMap.computeIfAbsent(key, k -> new HashMap<>()).put(billPullMnth, tax);
                    revenue = tax; // Display in Revenue column
                    break;
                case "ADJ_AMNT":
                    adjustment = sumPrm.compareTo(BigDecimal.ZERO) != 0 ? sumPrm : sumCrm;
                    adjustmentMap.computeIfAbsent(key, k -> new HashMap<>()).put(billPullMnth, adjustment);
                    revenue = adjustment; // Display in Revenue column
                    break;
                default:
                    revenue = sumPrm.compareTo(BigDecimal.ZERO) != 0 ? sumPrm : sumCrm;
                    if (isExpenseType(expType)) {
                        expense = revenue;
                        revenue = BigDecimal.ZERO;
                    }
                    revenueMap.computeIfAbsent(key, k -> new HashMap<>()).put(billPullMnth, revenue);
                    expenseMap.computeIfAbsent(key, k -> new HashMap<>()).put(billPullMnth, expense);
                    break;
            }

            // Calculate the total for the month
            BigDecimal totalForMonth = revenue.add(tax).subtract(expense).subtract(payment).add(adjustment);
            monthlyTotals.merge(billPullMnth, totalForMonth, BigDecimal::add);
        }
    }

    //Generate Excel Sheet
    private void generateExcelSheet(XSSFSheet sheet,
                                    Set<String> months,
                                    Map<String, Map<String, BigDecimal>> revenueMap,
                                    Map<String, Map<String, BigDecimal>> expenseMap,
                                    Map<String, Map<String, BigDecimal>> taxMap,
                                    Map<String, Map<String, BigDecimal>> paymentMap,
                                    Map<String, Map<String, BigDecimal>> adjustmentMap,
                                    Map<String, String> serviceCodeDescMap,
                                    Map<String, BigDecimal> monthlyTotals) {

        List<String> sortedMonths = sortMonthsDescending(months);

        writeNewHeaderLine(sheet, sortedMonths);

        // Write data rows for revenue and expense
        for (String key : revenueMap.keySet()) {
            writeRowToSheet(sheet, key, revenueMap, expenseMap, taxMap, paymentMap, adjustmentMap, serviceCodeDescMap, sortedMonths);
        }

        // Write data rows for tax values
        for (String key : taxMap.keySet()) {
            writeRowToSheet(sheet, key, revenueMap, expenseMap, taxMap, paymentMap, adjustmentMap, serviceCodeDescMap, sortedMonths);
        }

        // Write data rows for payment values
        for (String key : paymentMap.keySet()) {
            writeRowToSheet(sheet, key, revenueMap, expenseMap, taxMap, paymentMap, adjustmentMap, serviceCodeDescMap, sortedMonths);
        }

        // Write data rows for adjustment values
        for (String key : adjustmentMap.keySet()) {
            writeRowToSheet(sheet, key, revenueMap, expenseMap, taxMap, paymentMap, adjustmentMap, serviceCodeDescMap, sortedMonths);
        }

        // Write totals row
        writeTotalRow(sheet, sortedMonths, revenueMap, expenseMap, taxMap, paymentMap, adjustmentMap, monthlyTotals);
    }

    //Write the workbook to response
    private void writeWorkbookToResponse(XSSFWorkbook workbook, HttpServletResponse response, String aCustomerId) throws IOException {
        String excelFilePath = "BillCompare(" + aCustomerId + ")_" + GetCurrentTimeStamp().replace(":", "_")
                .replace(".", "_") + ".xlsx";
        response.reset();
        response.setContentType("application/excel");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + excelFilePath + "\"");
        try (OutputStream outputStream = response.getOutputStream()) {
            workbook.write(outputStream);
        }
    }

    // Helper methods for writing rows
    private void writeRowToSheet(XSSFSheet sheet, String key,
                                 Map<String, Map<String, BigDecimal>> revenueMap,
                                 Map<String, Map<String, BigDecimal>> expenseMap,
                                 Map<String, Map<String, BigDecimal>> taxMap,
                                 Map<String, Map<String, BigDecimal>> paymentMap,
                                 Map<String, Map<String, BigDecimal>> adjustmentMap,
                                 Map<String, String> serviceCodeDescMap,
                                 List<String> months) {

        String[] keys = key.split("\\|");
        String serviceCode = keys[0];
        String chargeSrc = keys[1];
        String description = serviceCodeDescMap.get(key);

        // Create a new row in the Excel sheet
        Row row = sheet.createRow(sheet.getLastRowNum() + 1);
        int columnCount = 0;

        // Write the service code, description, and charge source to the row
        Cell cell = row.createCell(columnCount++);
        cell.setCellValue(serviceCode);

        cell = row.createCell(columnCount++);
        cell.setCellValue(description);

        cell = row.createCell(columnCount++);
        cell.setCellValue(chargeSrc);

        // Write the financial data for each month (Revenue and Expense or Payment, Tax, Adjustment)
        for (String month : months) {
            BigDecimal revenue = revenueMap.getOrDefault(key, new HashMap<>()).getOrDefault(month, BigDecimal.ZERO);
            BigDecimal expense = expenseMap.getOrDefault(key, new HashMap<>()).getOrDefault(month, BigDecimal.ZERO);
            BigDecimal tax = taxMap.getOrDefault(key, new HashMap<>()).getOrDefault(month, BigDecimal.ZERO);
            BigDecimal payment = paymentMap.getOrDefault(key, new HashMap<>()).getOrDefault(month, BigDecimal.ZERO);
            BigDecimal adjustment = adjustmentMap.getOrDefault(key, new HashMap<>()).getOrDefault(month, BigDecimal.ZERO);

            if ("PYMNT_AMNT".equals(serviceCode)) {
                // If it's a payment, write the payment value under Revenue column
                cell = row.createCell(columnCount++);
                cell.setCellValue(payment.setScale(2, RoundingMode.HALF_UP).toPlainString());

                // Skip the expense column for this row
                columnCount++;
            } else if ("TAX_AMNT".equals(serviceCode)) {
                // If it's a tax, write the tax value under Revenue column
                cell = row.createCell(columnCount++);
                cell.setCellValue(tax.setScale(2, RoundingMode.HALF_UP).toPlainString());

                // Skip the expense column for this row
                columnCount++;
            } else if ("ADJ_AMNT".equals(serviceCode)) {
                // If it's an adjustment, write the adjustment value under Revenue column
                cell = row.createCell(columnCount++);
                cell.setCellValue(adjustment.setScale(2, RoundingMode.HALF_UP).toPlainString());

                // Skip the expense column for this row
                columnCount++;
            } else {
                // For regular service codes, write both revenue and expense values
                cell = row.createCell(columnCount++);
                cell.setCellValue(revenue.setScale(2, RoundingMode.HALF_UP).toPlainString());

                cell = row.createCell(columnCount++);
                cell.setCellValue(expense.setScale(2, RoundingMode.HALF_UP).toPlainString());
            }
        }
    }

    // Method for writing the Total row
    private void writeTotalRow(XSSFSheet sheet, List<String> months,
                               Map<String, Map<String, BigDecimal>> revenueMap,
                               Map<String, Map<String, BigDecimal>> expenseMap,
                               Map<String, Map<String, BigDecimal>> taxMap,
                               Map<String, Map<String, BigDecimal>> paymentMap,
                               Map<String, Map<String, BigDecimal>> adjustmentMap,
                               Map<String, BigDecimal> monthlyTotals) {
        Row totalRow = sheet.createRow(sheet.getLastRowNum() + 1);
        Cell totalCell = totalRow.createCell(0);
        totalCell.setCellValue("TOTAL");

        int columnCount = 3;  // Adjust for service code, description, and charge source
        for (String month : months) {
            // Initialize the total components for the current month
            BigDecimal totalRevenue = BigDecimal.ZERO;
            BigDecimal totalExpense = BigDecimal.ZERO;
            BigDecimal totalTax = BigDecimal.ZERO;
            BigDecimal totalPayment = BigDecimal.ZERO;
            BigDecimal totalAdjustment = BigDecimal.ZERO;

            // Loop through the service codes and aggregate the values for this month
            for (String key : revenueMap.keySet()) {
                totalRevenue = totalRevenue.add(revenueMap.getOrDefault(key, new HashMap<>()).getOrDefault(month, BigDecimal.ZERO));
                totalExpense = totalExpense.add(expenseMap.getOrDefault(key, new HashMap<>()).getOrDefault(month, BigDecimal.ZERO));
            }
            for (String key : taxMap.keySet()) {
                totalTax = totalTax.add(taxMap.getOrDefault(key, new HashMap<>()).getOrDefault(month, BigDecimal.ZERO));
            }
            for (String key : paymentMap.keySet()) {
                totalPayment = totalPayment.add(paymentMap.getOrDefault(key, new HashMap<>()).getOrDefault(month, BigDecimal.ZERO));
            }
            for (String key : adjustmentMap.keySet()) {
                totalAdjustment = totalAdjustment.add(adjustmentMap.getOrDefault(key, new HashMap<>()).getOrDefault(month, BigDecimal.ZERO));
            }

            // Calculate the total for this month using the formula
            BigDecimal netTotalForMonth = totalRevenue.add(totalTax).subtract(totalExpense).subtract(totalPayment).add(totalAdjustment);

            // Set the total value in the row for this month
            Cell cell = totalRow.createCell(columnCount);
            cell.setCellValue(netTotalForMonth.setScale(2, RoundingMode.HALF_UP).toPlainString());

            // Move to the next pair of columns (skip the expense column)
            columnCount += 2;
        }
    }

    // Resource cleanup function
    private void cleanupResources(ResultSet result, CallableStatement cs, Connection localConnection) {
        if (result != null) {
            try {
                result.close();
            } catch (SQLException e) {
                e.printStackTrace();/* Ignored */
            }
        }
        if (cs != null) {
            try {
                cs.close();
            } catch (SQLException e) {
                e.printStackTrace();/* Ignored */
            }
        }
        if (localConnection != null) {
            try {
                localConnection.close();
            } catch (SQLException e) {
                e.printStackTrace();/* Ignored */
            }
        }
    }


    private void writeNewHeaderLine(XSSFSheet sheet, List<String> months) {
        Row headerRow = sheet.createRow(0);

        Cell headerCell = headerRow.createCell(0);
        headerCell.setCellValue("BILL_SERVICE_CODE");

        headerCell = headerRow.createCell(1);
        headerCell.setCellValue("Service Code Desc");

        headerCell = headerRow.createCell(2);
        headerCell.setCellValue("Charge Source");

        int columnCount = 3;
        for (String month : months) {
            headerCell = headerRow.createCell(columnCount++);
            headerCell.setCellValue(month + " Revenue");

            headerCell = headerRow.createCell(columnCount++);
            headerCell.setCellValue(month + " Expense");
        }
    }

    private boolean isExpenseType(String expType) {
        return expType.equals("EXP_BILSERV_NON_INVOICED") || expType.equals("EXP_COMP_INVOICED");
    }

    // Method to sort months in descending order with year 2100 placed at the end
    private List<String> sortMonthsDescending(Set<String> months) {
        List<String> sortedMonths = new ArrayList<>(months);

        sortedMonths.sort((a, b) -> {
            String[] partsA = a.split(" ");
            String[] partsB = b.split(" ");
            int yearA = Integer.parseInt(partsA[1]);
            int yearB = Integer.parseInt(partsB[1]);

            int monthA = getMonthNumber(partsA[0]);
            int monthB = getMonthNumber(partsB[0]);

            if (yearA != yearB) {
                return yearB - yearA;
            } else {
                return monthB - monthA;
            }
        });

        return sortedMonths;
    }

    private int getMonthNumber(String month) {
        Map<String, Integer> monthMap = new HashMap<>();
        monthMap.put("January", 1);
        monthMap.put("February", 2);
        monthMap.put("March", 3);
        monthMap.put("April", 4);
        monthMap.put("May", 5);
        monthMap.put("June", 6);
        monthMap.put("July", 7);
        monthMap.put("August", 8);
        monthMap.put("September", 9);
        monthMap.put("October", 10);
        monthMap.put("November", 11);
        monthMap.put("December", 12);

        return monthMap.get(month);
    }

}