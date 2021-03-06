/*
 * Copyright 2008 The Kuali Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.opensource.org/licenses/ecl2.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kuali.kfs.module.bc.document.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.ojb.broker.PersistenceBrokerException;
import org.kuali.kfs.module.bc.BCConstants;
import org.kuali.kfs.module.bc.BCKeyConstants;
import org.kuali.kfs.module.bc.businessobject.BudgetConstructionAccountBalance;
import org.kuali.kfs.module.bc.businessobject.BudgetConstructionOrgAccountObjectDetailReport;
import org.kuali.kfs.module.bc.businessobject.BudgetConstructionOrgAccountObjectDetailReportTotal;
import org.kuali.kfs.module.bc.document.dataaccess.BudgetConstructionAccountObjectDetailReportDao;
import org.kuali.kfs.module.bc.document.service.BudgetConstructionAccountObjectDetailReportService;
import org.kuali.kfs.module.bc.document.service.BudgetConstructionOrganizationReportsService;
import org.kuali.kfs.module.bc.report.BudgetConstructionReportHelper;
import org.kuali.kfs.module.bc.util.BudgetConstructionUtils;
import org.kuali.kfs.sys.KFSConstants;
import org.kuali.kfs.sys.KFSPropertyConstants;
import org.kuali.rice.core.api.config.property.ConfigurationService;
import org.kuali.rice.core.api.util.type.KualiInteger;
import org.kuali.rice.krad.service.BusinessObjectService;
import org.kuali.rice.krad.service.PersistenceService;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service implementation of BudgetConstructionAccountSummaryReportService.
 */
@Transactional
public class BudgetConstructionAccountObjectDetailReportServiceImpl implements BudgetConstructionAccountObjectDetailReportService {

    protected BudgetConstructionAccountObjectDetailReportDao budgetConstructionAccountObjectDetailReportDao;
    protected ConfigurationService kualiConfigurationService;
    protected BudgetConstructionOrganizationReportsService budgetConstructionOrganizationReportsService;
    protected BusinessObjectService businessObjectService;
    protected PersistenceService persistenceServiceOjb;

    /**
     * @see org.kuali.kfs.module.bc.document.service.BudgetReportsControlListService#updateSubFundSummaryReport(java.lang.String)
     */
    @Override
    public void updateAccountObjectDetailReport(String principalName, boolean consolidated) {
        String expenditureINList = BudgetConstructionUtils.getExpenditureINList();
        String revenueINList =  BudgetConstructionUtils.getRevenueINList();
        if (consolidated) {
            budgetConstructionAccountObjectDetailReportDao.updateReportsAccountObjectConsolidatedTable(principalName,expenditureINList, revenueINList);
        }
        else {
            budgetConstructionAccountObjectDetailReportDao.updateReportsAccountObjectDetailTable(principalName, expenditureINList, revenueINList);
        }
    }

    @Override
    public Collection<BudgetConstructionOrgAccountObjectDetailReport> buildReports(Integer universityFiscalYear, String principalName, boolean consolidated) {
        Collection<BudgetConstructionOrgAccountObjectDetailReport> reportSet = new ArrayList();
        Collection<BudgetConstructionAccountBalance> accountObjectDetailList;

        // build searchCriteria
        Map searchCriteria = new HashMap();
        searchCriteria.put(KFSPropertyConstants.KUALI_USER_PERSON_UNIVERSAL_IDENTIFIER, principalName);

        // force OJB to go to DB since it is populated using JDBC
        // normally done in BudgetConstructionReportsServiceHelperImpl.getDataForBuildingReports
        persistenceServiceOjb.clearCache();

        // build order list
        List<String> orderList = buildOrderByList();
        accountObjectDetailList = budgetConstructionOrganizationReportsService.getBySearchCriteriaOrderByList(BudgetConstructionAccountBalance.class, searchCriteria, orderList);


        // BudgetConstructionReportHelper.deleteDuplicated((List) positionFundingDetailList, fieldsForSubFundTotal())
        List listForCalculateObject = BudgetConstructionReportHelper.deleteDuplicated((List) accountObjectDetailList, fieldsForObject());
        List listForCalculateLevel = BudgetConstructionReportHelper.deleteDuplicated((List) accountObjectDetailList, fieldsForLevel());
        List listForCalculateGexpAndType = BudgetConstructionReportHelper.deleteDuplicated((List) accountObjectDetailList, fieldsForGexpAndType());
        List listForCalculateAccountTotal = BudgetConstructionReportHelper.deleteDuplicated((List) accountObjectDetailList, fieldsForAccountTotal());
        List listForCalculateSubFundTotal = BudgetConstructionReportHelper.deleteDuplicated((List) accountObjectDetailList, fieldsForSubFundTotal());

        // Calculate Total Section
        List<BudgetConstructionOrgAccountObjectDetailReportTotal> accountObjectDetailTotalObjectList;
        List<BudgetConstructionOrgAccountObjectDetailReportTotal> accountObjectDetailTotalLevelList;
        List<BudgetConstructionOrgAccountObjectDetailReportTotal> accountObjectDetailTotalGexpAndTypeList;
        List<BudgetConstructionOrgAccountObjectDetailReportTotal> accountObjectDetailAccountTotalList;
        List<BudgetConstructionOrgAccountObjectDetailReportTotal> accountObjectDetailSubFundTotalList;

        accountObjectDetailTotalObjectList = calculateObjectTotal((List) accountObjectDetailList, listForCalculateObject);
        accountObjectDetailTotalLevelList = calculateLevelTotal((List) accountObjectDetailList, listForCalculateLevel);
        accountObjectDetailTotalGexpAndTypeList = calculateGexpAndTypeTotal((List) accountObjectDetailList, listForCalculateGexpAndType);
        accountObjectDetailAccountTotalList = calculateAccountTotal((List) accountObjectDetailList, listForCalculateAccountTotal);
        accountObjectDetailSubFundTotalList = calculateSubFundTotal((List) accountObjectDetailList, listForCalculateSubFundTotal);

        for (BudgetConstructionAccountBalance accountObjectDetailEntry : accountObjectDetailList) {
            BudgetConstructionOrgAccountObjectDetailReport accountObjectDetailReport = new BudgetConstructionOrgAccountObjectDetailReport();
            buildReportsHeader(universityFiscalYear, accountObjectDetailReport, accountObjectDetailEntry, consolidated);
            buildReportsBody(universityFiscalYear, accountObjectDetailReport, accountObjectDetailEntry);
            buildReportsTotal(accountObjectDetailReport, accountObjectDetailEntry, accountObjectDetailTotalObjectList, accountObjectDetailTotalLevelList, accountObjectDetailTotalGexpAndTypeList, accountObjectDetailAccountTotalList, accountObjectDetailSubFundTotalList);
            reportSet.add(accountObjectDetailReport);
        }
        return reportSet;
    }


    /**
     * builds report Header
     *
     * @param BudgetConstructionObjectSummary bcas
     */
    protected void buildReportsHeader(Integer universityFiscalYear, BudgetConstructionOrgAccountObjectDetailReport orgAccountObjectDetailReportEntry, BudgetConstructionAccountBalance accountBalance, boolean consolidated) {
        String orgChartDesc = accountBalance.getOrganizationChartOfAccounts().getFinChartOfAccountDescription();
        String chartDesc = accountBalance.getChartOfAccounts().getFinChartOfAccountDescription();
        String orgName = "";
        try {
            orgName = accountBalance.getOrganization().getOrganizationName();
        }
        catch (PersistenceBrokerException e) {
        }
        String reportChartDesc = accountBalance.getChartOfAccounts().getReportsToChartOfAccounts().getFinChartOfAccountDescription();
        String subFundGroupName = accountBalance.getSubFundGroup().getSubFundGroupCode();
        String subFundGroupDes = accountBalance.getSubFundGroup().getSubFundGroupDescription();
        String fundGroupName = accountBalance.getSubFundGroup().getFundGroupCode();
        String fundGroupDes = accountBalance.getSubFundGroup().getFundGroup().getName();

        Integer prevFiscalyear = universityFiscalYear - 1;
        orgAccountObjectDetailReportEntry.setFiscalYear(prevFiscalyear.toString() + "-" + universityFiscalYear.toString().substring(2, 4));
        orgAccountObjectDetailReportEntry.setOrgChartOfAccountsCode(accountBalance.getOrganizationChartOfAccountsCode());

        if (orgChartDesc == null) {
            orgAccountObjectDetailReportEntry.setOrgChartOfAccountDescription(kualiConfigurationService.getPropertyValueAsString(BCKeyConstants.ERROR_REPORT_GETTING_CHART_DESCRIPTION));
        }
        else {
            orgAccountObjectDetailReportEntry.setOrgChartOfAccountDescription(orgChartDesc);
        }

        orgAccountObjectDetailReportEntry.setOrganizationCode(accountBalance.getOrganizationCode());
        if (orgName == null) {
            orgAccountObjectDetailReportEntry.setOrganizationName(kualiConfigurationService.getPropertyValueAsString(BCKeyConstants.ERROR_REPORT_GETTING_ORGANIZATION_NAME));
        }
        else {
            orgAccountObjectDetailReportEntry.setOrganizationName(orgName);
        }

        orgAccountObjectDetailReportEntry.setChartOfAccountsCode(accountBalance.getChartOfAccountsCode());
        if (chartDesc == null) {
            orgAccountObjectDetailReportEntry.setChartOfAccountDescription(kualiConfigurationService.getPropertyValueAsString(BCKeyConstants.ERROR_REPORT_GETTING_CHART_DESCRIPTION));
        }
        else {
            orgAccountObjectDetailReportEntry.setChartOfAccountDescription(chartDesc);
        }

        orgAccountObjectDetailReportEntry.setFundGroupCode(accountBalance.getSubFundGroup().getFundGroupCode());
        if (fundGroupDes == null) {
            orgAccountObjectDetailReportEntry.setFundGroupName(kualiConfigurationService.getPropertyValueAsString(BCKeyConstants.ERROR_REPORT_GETTING_FUNDGROUP_NAME));
        }
        else {
            orgAccountObjectDetailReportEntry.setFundGroupName(fundGroupDes);
        }

        orgAccountObjectDetailReportEntry.setSubFundGroupCode(accountBalance.getSubFundGroupCode());
        if (subFundGroupDes == null) {
            orgAccountObjectDetailReportEntry.setSubFundGroupDescription(kualiConfigurationService.getPropertyValueAsString(BCKeyConstants.ERROR_REPORT_GETTING_SUBFUNDGROUP_DESCRIPTION));
        }
        else {
            orgAccountObjectDetailReportEntry.setSubFundGroupDescription(subFundGroupDes);
        }

        Integer prevPrevFiscalyear = prevFiscalyear - 1;
        orgAccountObjectDetailReportEntry.setBaseFy(prevPrevFiscalyear.toString() + "-" + prevFiscalyear.toString().substring(2, 4) + " Base");
        orgAccountObjectDetailReportEntry.setReqFy(prevFiscalyear.toString() + "-" + universityFiscalYear.toString().substring(2, 4) + " Request");
        if (consolidated) {
            orgAccountObjectDetailReportEntry.setConsHdr(BCConstants.Report.CONSOLIIDATED);
        }
        else {
            orgAccountObjectDetailReportEntry.setConsHdr(BCConstants.Report.BLANK);
        }

        orgAccountObjectDetailReportEntry.setAccountNumber(accountBalance.getAccountNumber());
        String accountName = accountBalance.getAccount().getAccountName();
        if (accountName == null) {
            orgAccountObjectDetailReportEntry.setAccountName(kualiConfigurationService.getPropertyValueAsString(BCKeyConstants.ERROR_REPORT_GETTING_ACCOUNT_DESCRIPTION));
        }
        else {
            orgAccountObjectDetailReportEntry.setAccountName(accountName);
        }

        orgAccountObjectDetailReportEntry.setSubAccountNumber(accountBalance.getSubAccountNumber());

        String subAccountName = StringUtils.EMPTY;
        String subAccountNumberAndName = StringUtils.EMPTY;
        String divider = StringUtils.EMPTY;
        // set accountNumber and name, subAccountNumber and name
        if (accountBalance.getAccount() != null) {
            orgAccountObjectDetailReportEntry.setAccountNumberAndName(accountBalance.getAccountNumber() + " " + accountBalance.getAccount().getAccountName());
            orgAccountObjectDetailReportEntry.setAccountName(accountBalance.getAccount().getAccountName());
        }

        if (!accountBalance.getSubAccountNumber().equals(KFSConstants.getDashSubAccountNumber())) {
            divider = BCConstants.Report.DIVIDER;
            try {
                subAccountName = accountBalance.getSubAccount().getSubAccountName();
                subAccountNumberAndName = accountBalance.getSubAccount().getSubAccountNumber() + " " + accountBalance.getSubAccount().getSubAccountName();
            }
            catch (PersistenceBrokerException e) {
                subAccountName = kualiConfigurationService.getPropertyValueAsString(BCKeyConstants.ERROR_REPORT_GETTING_SUB_ACCOUNT_DESCRIPTION);
                subAccountNumberAndName = subAccountName;
            }
        }

        orgAccountObjectDetailReportEntry.setSubAccountName(subAccountName);
        orgAccountObjectDetailReportEntry.setSubAccountNumberAndName(subAccountNumberAndName);
        orgAccountObjectDetailReportEntry.setDivider(divider);

        // For group
        orgAccountObjectDetailReportEntry.setSubAccountNumber(accountBalance.getSubAccountNumber() + accountBalance.getAccountNumber());
        orgAccountObjectDetailReportEntry.setFinancialObjectCode(accountBalance.getFinancialObjectCode());
        orgAccountObjectDetailReportEntry.setFinancialSubObjectCode(accountBalance.getFinancialSubObjectCode());
        // orgAccountObjectDetailReportEntry.setFinancialObjectLevelCode(accountBalance.getFinancialObjectLevelCode());
        orgAccountObjectDetailReportEntry.setIncomeExpenseCode(accountBalance.getIncomeExpenseCode());
        // orgAccountObjectDetailReportEntry.setFinancialConsolidationSortCode(accountBalance.getFinancialConsolidationSortCode());
        orgAccountObjectDetailReportEntry.setFinancialLevelSortCode(accountBalance.getFinancialLevelSortCode());

        // page break

        // page break org_fin_coa_cd, org_cd, sub_fund_grp_cd)%\
        orgAccountObjectDetailReportEntry.setPageBreak(accountBalance.getOrganizationChartOfAccountsCode() + accountBalance.getOrganizationCode() + accountBalance.getSubFundGroupCode());
    }


    /**
     * builds report body
     *
     * @param BudgetConstructionLevelSummary bcas
     */
    protected void buildReportsBody(Integer universityFiscalYear, BudgetConstructionOrgAccountObjectDetailReport orgAccountObjectDetailReportEntry, BudgetConstructionAccountBalance accountBalance) {
        if (accountBalance.getFinancialSubObjectCode().equals(KFSConstants.getDashFinancialSubObjectCode())) {
            orgAccountObjectDetailReportEntry.setFinancialObjectName(accountBalance.getFinancialObject().getFinancialObjectCodeName());
        }
        else {
            orgAccountObjectDetailReportEntry.setFinancialObjectName(accountBalance.getFinancialSubObject().getFinancialSubObjectCodeName());
        }

        orgAccountObjectDetailReportEntry.setPositionCsfLeaveFteQuantity(BudgetConstructionReportHelper.setDecimalDigit(accountBalance.getPositionCsfLeaveFteQuantity(), 2, true));

        orgAccountObjectDetailReportEntry.setPositionFullTimeEquivalencyQuantity(BudgetConstructionReportHelper.setDecimalDigit(accountBalance.getPositionFullTimeEquivalencyQuantity(), 2, true));

        orgAccountObjectDetailReportEntry.setAppointmentRequestedCsfFteQuantity(BudgetConstructionReportHelper.setDecimalDigit(accountBalance.getAppointmentRequestedCsfFteQuantity(), 2, true));

        orgAccountObjectDetailReportEntry.setAppointmentRequestedFteQuantity(BudgetConstructionReportHelper.setDecimalDigit(accountBalance.getAppointmentRequestedFteQuantity(), 2, true));

        orgAccountObjectDetailReportEntry.setAccountLineAnnualBalanceAmount(accountBalance.getAccountLineAnnualBalanceAmount());

        orgAccountObjectDetailReportEntry.setFinancialBeginningBalanceLineAmount(accountBalance.getFinancialBeginningBalanceLineAmount());

        orgAccountObjectDetailReportEntry.setAmountChange(accountBalance.getAccountLineAnnualBalanceAmount().subtract(accountBalance.getFinancialBeginningBalanceLineAmount()));
        orgAccountObjectDetailReportEntry.setPercentChange(BudgetConstructionReportHelper.calculatePercent(orgAccountObjectDetailReportEntry.getAmountChange(), orgAccountObjectDetailReportEntry.getFinancialBeginningBalanceLineAmount()));
    }

    /**
     * builds report total
     */

    protected void buildReportsTotal(BudgetConstructionOrgAccountObjectDetailReport orgObjectSummaryReportEntry, BudgetConstructionAccountBalance accountBalance, List<BudgetConstructionOrgAccountObjectDetailReportTotal> accountObjectDetailTotalObjectList, List<BudgetConstructionOrgAccountObjectDetailReportTotal> accountObjectDetailTotalLevelList, List<BudgetConstructionOrgAccountObjectDetailReportTotal> accountObjectDetailTotalGexpAndTypeList, List<BudgetConstructionOrgAccountObjectDetailReportTotal> accountObjectDetailAccountTotalList, List<BudgetConstructionOrgAccountObjectDetailReportTotal> accountObjectDetailSubFundTotalList) {

        for (BudgetConstructionOrgAccountObjectDetailReportTotal objectTotal : accountObjectDetailTotalObjectList) {
            if (BudgetConstructionReportHelper.isSameEntry(accountBalance, objectTotal.getBudgetConstructionAccountBalance(), fieldsForObject())) {
                orgObjectSummaryReportEntry.setTotalObjectDescription(accountBalance.getFinancialObject().getFinancialObjectCodeName());

                orgObjectSummaryReportEntry.setTotalObjectPositionCsfLeaveFteQuantity(BudgetConstructionReportHelper.setDecimalDigit(objectTotal.getTotalObjectPositionCsfLeaveFteQuantity(), 2, true));
                orgObjectSummaryReportEntry.setTotalObjectPositionFullTimeEquivalencyQuantity(BudgetConstructionReportHelper.setDecimalDigit(objectTotal.getTotalObjectPositionFullTimeEquivalencyQuantity(), 2, true));
                orgObjectSummaryReportEntry.setTotalObjectFinancialBeginningBalanceLineAmount(objectTotal.getTotalObjectFinancialBeginningBalanceLineAmount());
                orgObjectSummaryReportEntry.setTotalObjectAppointmentRequestedCsfFteQuantity(BudgetConstructionReportHelper.setDecimalDigit(objectTotal.getTotalObjectAppointmentRequestedCsfFteQuantity(), 2, true));
                orgObjectSummaryReportEntry.setTotalObjectAppointmentRequestedFteQuantity(BudgetConstructionReportHelper.setDecimalDigit(objectTotal.getTotalObjectAppointmentRequestedFteQuantity(), 2, true));
                orgObjectSummaryReportEntry.setTotalObjectAccountLineAnnualBalanceAmount(objectTotal.getTotalObjectAccountLineAnnualBalanceAmount());

                KualiInteger totalObjectAmountChange = objectTotal.getTotalObjectAccountLineAnnualBalanceAmount().subtract(objectTotal.getTotalObjectFinancialBeginningBalanceLineAmount());
                orgObjectSummaryReportEntry.setTotalObjectAmountChange(totalObjectAmountChange);
                orgObjectSummaryReportEntry.setTotalObjectPercentChange(BudgetConstructionReportHelper.calculatePercent(totalObjectAmountChange, objectTotal.getTotalObjectFinancialBeginningBalanceLineAmount()));
            }
        }

        for (BudgetConstructionOrgAccountObjectDetailReportTotal levelTotal : accountObjectDetailTotalLevelList) {
            if (BudgetConstructionReportHelper.isSameEntry(accountBalance, levelTotal.getBudgetConstructionAccountBalance(), fieldsForLevel())) {
                orgObjectSummaryReportEntry.setTotalLevelDescription(accountBalance.getFinancialObjectLevel().getFinancialObjectLevelName());

                orgObjectSummaryReportEntry.setTotalLevelPositionCsfLeaveFteQuantity(BudgetConstructionReportHelper.setDecimalDigit(levelTotal.getTotalLevelPositionCsfLeaveFteQuantity(), 2, true));
                orgObjectSummaryReportEntry.setTotalLevelPositionFullTimeEquivalencyQuantity(BudgetConstructionReportHelper.setDecimalDigit(levelTotal.getTotalLevelPositionFullTimeEquivalencyQuantity(), 2, true));
                orgObjectSummaryReportEntry.setTotalLevelFinancialBeginningBalanceLineAmount(levelTotal.getTotalLevelFinancialBeginningBalanceLineAmount());
                orgObjectSummaryReportEntry.setTotalLevelAppointmentRequestedCsfFteQuantity(BudgetConstructionReportHelper.setDecimalDigit(levelTotal.getTotalLevelAppointmentRequestedCsfFteQuantity(), 2, true));
                orgObjectSummaryReportEntry.setTotalLevelAppointmentRequestedFteQuantity(BudgetConstructionReportHelper.setDecimalDigit(levelTotal.getTotalLevelAppointmentRequestedFteQuantity(), 2, true));
                orgObjectSummaryReportEntry.setTotalLevelAccountLineAnnualBalanceAmount(levelTotal.getTotalLevelAccountLineAnnualBalanceAmount());

                KualiInteger totalLevelAmountChange = levelTotal.getTotalLevelAccountLineAnnualBalanceAmount().subtract(levelTotal.getTotalLevelFinancialBeginningBalanceLineAmount());
                orgObjectSummaryReportEntry.setTotalLevelAmountChange(totalLevelAmountChange);
                orgObjectSummaryReportEntry.setTotalLevelPercentChange(BudgetConstructionReportHelper.calculatePercent(totalLevelAmountChange, levelTotal.getTotalLevelFinancialBeginningBalanceLineAmount()));
            }
        }


        for (BudgetConstructionOrgAccountObjectDetailReportTotal gexpAndTypeTotal : accountObjectDetailTotalGexpAndTypeList) {
            if (BudgetConstructionReportHelper.isSameEntry(accountBalance, gexpAndTypeTotal.getBudgetConstructionAccountBalance(), fieldsForGexpAndType())) {

                orgObjectSummaryReportEntry.setGrossFinancialBeginningBalanceLineAmount(gexpAndTypeTotal.getGrossFinancialBeginningBalanceLineAmount());
                orgObjectSummaryReportEntry.setGrossAccountLineAnnualBalanceAmount(gexpAndTypeTotal.getGrossAccountLineAnnualBalanceAmount());
                KualiInteger grossAmountChange = gexpAndTypeTotal.getGrossAccountLineAnnualBalanceAmount().subtract(gexpAndTypeTotal.getGrossFinancialBeginningBalanceLineAmount());
                orgObjectSummaryReportEntry.setGrossAmountChange(grossAmountChange);
                orgObjectSummaryReportEntry.setGrossPercentChange(BudgetConstructionReportHelper.calculatePercent(grossAmountChange, gexpAndTypeTotal.getGrossFinancialBeginningBalanceLineAmount()));

                if (accountBalance.getIncomeExpenseCode().equals(BCConstants.Report.INCOME_EXP_TYPE_A)) {
                    orgObjectSummaryReportEntry.setTypeDesc(kualiConfigurationService.getPropertyValueAsString(BCKeyConstants.MSG_REPORT_INCOME_EXP_DESC_UPPERCASE_REVENUE));
                }
                else {
                    orgObjectSummaryReportEntry.setTypeDesc(kualiConfigurationService.getPropertyValueAsString(BCKeyConstants.MSG_REPORT_INCOME_EXP_DESC_EXPENDITURE_NET_TRNFR));
                }

                orgObjectSummaryReportEntry.setTypePositionCsfLeaveFteQuantity(BudgetConstructionReportHelper.setDecimalDigit(gexpAndTypeTotal.getTypePositionCsfLeaveFteQuantity(), 2, true));
                orgObjectSummaryReportEntry.setTypePositionFullTimeEquivalencyQuantity(BudgetConstructionReportHelper.setDecimalDigit(gexpAndTypeTotal.getTypePositionFullTimeEquivalencyQuantity(), 2, true));
                orgObjectSummaryReportEntry.setTypeFinancialBeginningBalanceLineAmount(gexpAndTypeTotal.getTypeFinancialBeginningBalanceLineAmount());
                orgObjectSummaryReportEntry.setTypeAppointmentRequestedCsfFteQuantity(BudgetConstructionReportHelper.setDecimalDigit(gexpAndTypeTotal.getTypeAppointmentRequestedCsfFteQuantity(), 2, true));
                orgObjectSummaryReportEntry.setTypeAppointmentRequestedFteQuantity(BudgetConstructionReportHelper.setDecimalDigit(gexpAndTypeTotal.getTypeAppointmentRequestedFteQuantity(), 2, true));

                orgObjectSummaryReportEntry.setTypeAccountLineAnnualBalanceAmount(gexpAndTypeTotal.getTypeAccountLineAnnualBalanceAmount());
                KualiInteger typeAmountChange = gexpAndTypeTotal.getTypeAccountLineAnnualBalanceAmount().subtract(gexpAndTypeTotal.getTypeFinancialBeginningBalanceLineAmount());
                orgObjectSummaryReportEntry.setTypeAmountChange(typeAmountChange);
                orgObjectSummaryReportEntry.setTypePercentChange(BudgetConstructionReportHelper.calculatePercent(typeAmountChange, gexpAndTypeTotal.getTypeFinancialBeginningBalanceLineAmount()));
            }
        }

        for (BudgetConstructionOrgAccountObjectDetailReportTotal accountTotal : accountObjectDetailAccountTotalList) {
            if (BudgetConstructionReportHelper.isSameEntry(accountBalance, accountTotal.getBudgetConstructionAccountBalance(), fieldsForAccountTotal())) {

                if (orgObjectSummaryReportEntry.getSubAccountName().equals(BCConstants.Report.BLANK)) {
                    orgObjectSummaryReportEntry.setAccountNameForAccountTotal(orgObjectSummaryReportEntry.getAccountName());
                }
                else {
                    orgObjectSummaryReportEntry.setAccountNameForAccountTotal(orgObjectSummaryReportEntry.getSubAccountName());
                }

                orgObjectSummaryReportEntry.setAccountPositionCsfLeaveFteQuantity(BudgetConstructionReportHelper.setDecimalDigit(accountTotal.getAccountPositionCsfLeaveFteQuantity(), 2, true));
                orgObjectSummaryReportEntry.setAccountPositionFullTimeEquivalencyQuantity(BudgetConstructionReportHelper.setDecimalDigit(accountTotal.getAccountPositionFullTimeEquivalencyQuantity(), 2, true));
                orgObjectSummaryReportEntry.setAccountAppointmentRequestedCsfFteQuantity(BudgetConstructionReportHelper.setDecimalDigit(accountTotal.getAccountAppointmentRequestedCsfFteQuantity(), 2, true));
                orgObjectSummaryReportEntry.setAccountAppointmentRequestedFteQuantity(BudgetConstructionReportHelper.setDecimalDigit(accountTotal.getAccountAppointmentRequestedFteQuantity(), 2, true));

                orgObjectSummaryReportEntry.setAccountRevenueFinancialBeginningBalanceLineAmount(accountTotal.getAccountRevenueFinancialBeginningBalanceLineAmount());
                orgObjectSummaryReportEntry.setAccountRevenueAccountLineAnnualBalanceAmount(accountTotal.getAccountRevenueAccountLineAnnualBalanceAmount());

                KualiInteger accountRevenueAmountChange = accountTotal.getAccountRevenueAccountLineAnnualBalanceAmount().subtract(accountTotal.getAccountRevenueFinancialBeginningBalanceLineAmount());
                orgObjectSummaryReportEntry.setAccountRevenueAmountChange(accountRevenueAmountChange);
                orgObjectSummaryReportEntry.setAccountRevenuePercentChange(BudgetConstructionReportHelper.calculatePercent(accountRevenueAmountChange, accountTotal.getAccountRevenueFinancialBeginningBalanceLineAmount()));

                KualiInteger accountGrossFinancialBeginningBalanceLineAmount = accountTotal.getAccountExpenditureFinancialBeginningBalanceLineAmount().subtract(accountTotal.getAccountTrnfrInFinancialBeginningBalanceLineAmount());
                KualiInteger accountGrossAccountLineAnnualBalanceAmount = accountTotal.getAccountExpenditureAccountLineAnnualBalanceAmount().subtract(accountTotal.getAccountTrnfrInAccountLineAnnualBalanceAmount());
                orgObjectSummaryReportEntry.setAccountGrossFinancialBeginningBalanceLineAmount(accountGrossFinancialBeginningBalanceLineAmount);
                orgObjectSummaryReportEntry.setAccountGrossAccountLineAnnualBalanceAmount(accountGrossAccountLineAnnualBalanceAmount);
                KualiInteger accountGrossAmountChange = accountGrossAccountLineAnnualBalanceAmount.subtract(accountGrossFinancialBeginningBalanceLineAmount);
                orgObjectSummaryReportEntry.setAccountGrossAmountChange(accountGrossAmountChange);
                orgObjectSummaryReportEntry.setAccountGrossPercentChange(BudgetConstructionReportHelper.calculatePercent(accountGrossAmountChange, accountGrossFinancialBeginningBalanceLineAmount));

                orgObjectSummaryReportEntry.setAccountTrnfrInFinancialBeginningBalanceLineAmount(accountTotal.getAccountTrnfrInFinancialBeginningBalanceLineAmount());
                orgObjectSummaryReportEntry.setAccountTrnfrInAccountLineAnnualBalanceAmount(accountTotal.getAccountTrnfrInAccountLineAnnualBalanceAmount());

                KualiInteger accountTrnfrInAmountChange = accountTotal.getAccountTrnfrInAccountLineAnnualBalanceAmount().subtract(accountTotal.getAccountTrnfrInFinancialBeginningBalanceLineAmount());
                orgObjectSummaryReportEntry.setAccountTrnfrInAmountChange(accountTrnfrInAmountChange);
                orgObjectSummaryReportEntry.setAccountTrnfrInPercentChange(BudgetConstructionReportHelper.calculatePercent(accountTrnfrInAmountChange, accountTotal.getAccountTrnfrInFinancialBeginningBalanceLineAmount()));

                orgObjectSummaryReportEntry.setAccountExpenditureFinancialBeginningBalanceLineAmount(accountTotal.getAccountExpenditureFinancialBeginningBalanceLineAmount());
                orgObjectSummaryReportEntry.setAccountExpenditureAccountLineAnnualBalanceAmount(accountTotal.getAccountExpenditureAccountLineAnnualBalanceAmount());

                KualiInteger accountExpenditureAmountChange = accountTotal.getAccountExpenditureAccountLineAnnualBalanceAmount().subtract(accountTotal.getAccountExpenditureFinancialBeginningBalanceLineAmount());
                orgObjectSummaryReportEntry.setAccountExpenditureAmountChange(accountExpenditureAmountChange);
                orgObjectSummaryReportEntry.setAccountExpenditurePercentChange(BudgetConstructionReportHelper.calculatePercent(accountExpenditureAmountChange, accountTotal.getAccountExpenditureFinancialBeginningBalanceLineAmount()));

                KualiInteger accountDifferenceFinancialBeginningBalanceLineAmount = accountTotal.getAccountRevenueFinancialBeginningBalanceLineAmount().subtract(accountTotal.getAccountExpenditureFinancialBeginningBalanceLineAmount());
                KualiInteger accountDifferenceAccountLineAnnualBalanceAmount = accountTotal.getAccountRevenueAccountLineAnnualBalanceAmount().subtract(accountTotal.getAccountExpenditureAccountLineAnnualBalanceAmount());

                orgObjectSummaryReportEntry.setAccountDifferenceFinancialBeginningBalanceLineAmount(accountDifferenceFinancialBeginningBalanceLineAmount);
                orgObjectSummaryReportEntry.setAccountDifferenceAccountLineAnnualBalanceAmount(accountDifferenceAccountLineAnnualBalanceAmount);

                orgObjectSummaryReportEntry.setAccountDifferenceFinancialBeginningBalanceLineAmount(accountDifferenceFinancialBeginningBalanceLineAmount);
                orgObjectSummaryReportEntry.setAccountDifferenceAccountLineAnnualBalanceAmount(accountDifferenceAccountLineAnnualBalanceAmount);

                KualiInteger accountDifferenceAmountChange = accountDifferenceAccountLineAnnualBalanceAmount.subtract(accountDifferenceFinancialBeginningBalanceLineAmount);
                orgObjectSummaryReportEntry.setAccountDifferenceAmountChange(accountDifferenceAmountChange);
                orgObjectSummaryReportEntry.setAccountDifferencePercentChange(BudgetConstructionReportHelper.calculatePercent(accountDifferenceAmountChange, accountDifferenceFinancialBeginningBalanceLineAmount));
            }
        }

        for (BudgetConstructionOrgAccountObjectDetailReportTotal subFundTotal : accountObjectDetailSubFundTotalList) {
            if (BudgetConstructionReportHelper.isSameEntry(accountBalance, subFundTotal.getBudgetConstructionAccountBalance(), fieldsForSubFundTotal())) {

                orgObjectSummaryReportEntry.setSubFundPositionCsfLeaveFteQuantity(BudgetConstructionReportHelper.setDecimalDigit(subFundTotal.getSubFundPositionCsfLeaveFteQuantity(), 2, true));
                orgObjectSummaryReportEntry.setSubFundPositionFullTimeEquivalencyQuantity(BudgetConstructionReportHelper.setDecimalDigit(subFundTotal.getSubFundPositionFullTimeEquivalencyQuantity(), 2, true));
                orgObjectSummaryReportEntry.setSubFundAppointmentRequestedCsfFteQuantity(BudgetConstructionReportHelper.setDecimalDigit(subFundTotal.getSubFundAppointmentRequestedCsfFteQuantity(), 2, true));
                orgObjectSummaryReportEntry.setSubFundAppointmentRequestedFteQuantity(BudgetConstructionReportHelper.setDecimalDigit(subFundTotal.getSubFundAppointmentRequestedFteQuantity(), 2, true));

                orgObjectSummaryReportEntry.setSubFundRevenueFinancialBeginningBalanceLineAmount(subFundTotal.getSubFundRevenueFinancialBeginningBalanceLineAmount());
                orgObjectSummaryReportEntry.setSubFundRevenueAccountLineAnnualBalanceAmount(subFundTotal.getSubFundRevenueAccountLineAnnualBalanceAmount());

                KualiInteger subFundRevenueAmountChange = subFundTotal.getSubFundRevenueAccountLineAnnualBalanceAmount().subtract(subFundTotal.getSubFundRevenueFinancialBeginningBalanceLineAmount());
                orgObjectSummaryReportEntry.setSubFundRevenueAmountChange(subFundRevenueAmountChange);
                orgObjectSummaryReportEntry.setSubFundRevenuePercentChange(BudgetConstructionReportHelper.calculatePercent(subFundRevenueAmountChange, subFundTotal.getSubFundRevenueFinancialBeginningBalanceLineAmount()));

                KualiInteger subFundGrossFinancialBeginningBalanceLineAmount = subFundTotal.getSubFundExpenditureFinancialBeginningBalanceLineAmount().subtract(subFundTotal.getSubFundTrnfrInFinancialBeginningBalanceLineAmount());
                KualiInteger subFundGrossAccountLineAnnualBalanceAmount = subFundTotal.getSubFundExpenditureAccountLineAnnualBalanceAmount().subtract(subFundTotal.getSubFundTrnfrInAccountLineAnnualBalanceAmount());
                orgObjectSummaryReportEntry.setSubFundGrossFinancialBeginningBalanceLineAmount(subFundGrossFinancialBeginningBalanceLineAmount);
                orgObjectSummaryReportEntry.setSubFundGrossAccountLineAnnualBalanceAmount(subFundGrossAccountLineAnnualBalanceAmount);
                KualiInteger subFundGrossAmountChange = subFundGrossAccountLineAnnualBalanceAmount.subtract(subFundGrossFinancialBeginningBalanceLineAmount);
                orgObjectSummaryReportEntry.setSubFundGrossAmountChange(subFundGrossAmountChange);
                orgObjectSummaryReportEntry.setSubFundGrossPercentChange(BudgetConstructionReportHelper.calculatePercent(subFundGrossAmountChange, subFundGrossFinancialBeginningBalanceLineAmount));

                orgObjectSummaryReportEntry.setSubFundTrnfrInFinancialBeginningBalanceLineAmount(subFundTotal.getSubFundTrnfrInFinancialBeginningBalanceLineAmount());
                orgObjectSummaryReportEntry.setSubFundTrnfrInAccountLineAnnualBalanceAmount(subFundTotal.getSubFundTrnfrInAccountLineAnnualBalanceAmount());

                KualiInteger subFundTrnfrInAmountChange = subFundTotal.getSubFundTrnfrInAccountLineAnnualBalanceAmount().subtract(subFundTotal.getSubFundTrnfrInFinancialBeginningBalanceLineAmount());
                orgObjectSummaryReportEntry.setSubFundTrnfrInAmountChange(subFundTrnfrInAmountChange);
                orgObjectSummaryReportEntry.setSubFundTrnfrInPercentChange(BudgetConstructionReportHelper.calculatePercent(subFundTrnfrInAmountChange, subFundTotal.getSubFundTrnfrInFinancialBeginningBalanceLineAmount()));

                orgObjectSummaryReportEntry.setSubFundExpenditureFinancialBeginningBalanceLineAmount(subFundTotal.getSubFundExpenditureFinancialBeginningBalanceLineAmount());
                orgObjectSummaryReportEntry.setSubFundExpenditureAccountLineAnnualBalanceAmount(subFundTotal.getSubFundExpenditureAccountLineAnnualBalanceAmount());

                KualiInteger subFundExpenditureAmountChange = subFundTotal.getSubFundExpenditureAccountLineAnnualBalanceAmount().subtract(subFundTotal.getSubFundExpenditureFinancialBeginningBalanceLineAmount());
                orgObjectSummaryReportEntry.setSubFundExpenditureAmountChange(subFundExpenditureAmountChange);
                orgObjectSummaryReportEntry.setSubFundExpenditurePercentChange(BudgetConstructionReportHelper.calculatePercent(subFundExpenditureAmountChange, subFundTotal.getSubFundExpenditureFinancialBeginningBalanceLineAmount()));

                KualiInteger subFundDifferenceFinancialBeginningBalanceLineAmount = subFundTotal.getSubFundRevenueFinancialBeginningBalanceLineAmount().subtract(subFundTotal.getSubFundExpenditureFinancialBeginningBalanceLineAmount());
                KualiInteger subFundDifferenceAccountLineAnnualBalanceAmount = subFundTotal.getSubFundRevenueAccountLineAnnualBalanceAmount().subtract(subFundTotal.getSubFundExpenditureAccountLineAnnualBalanceAmount());

                orgObjectSummaryReportEntry.setSubFundDifferenceFinancialBeginningBalanceLineAmount(subFundDifferenceFinancialBeginningBalanceLineAmount);
                orgObjectSummaryReportEntry.setSubFundDifferenceAccountLineAnnualBalanceAmount(subFundDifferenceAccountLineAnnualBalanceAmount);

                orgObjectSummaryReportEntry.setSubFundDifferenceFinancialBeginningBalanceLineAmount(subFundDifferenceFinancialBeginningBalanceLineAmount);
                orgObjectSummaryReportEntry.setSubFundDifferenceAccountLineAnnualBalanceAmount(subFundDifferenceAccountLineAnnualBalanceAmount);

                KualiInteger subFundDifferenceAmountChange = subFundDifferenceAccountLineAnnualBalanceAmount.subtract(subFundDifferenceFinancialBeginningBalanceLineAmount);
                orgObjectSummaryReportEntry.setSubFundDifferenceAmountChange(subFundDifferenceAmountChange);
                orgObjectSummaryReportEntry.setSubFundDifferencePercentChange(BudgetConstructionReportHelper.calculatePercent(subFundDifferenceAmountChange, subFundDifferenceFinancialBeginningBalanceLineAmount));
            }
        }


    }

    protected List calculateObjectTotal(List<BudgetConstructionAccountBalance> bcosList, List<BudgetConstructionAccountBalance> simpleList) {

        BigDecimal totalObjectPositionCsfLeaveFteQuantity = BigDecimal.ZERO;
        BigDecimal totalObjectPositionFullTimeEquivalencyQuantity = BigDecimal.ZERO;
        KualiInteger totalObjectFinancialBeginningBalanceLineAmount = KualiInteger.ZERO;
        BigDecimal totalObjectAppointmentRequestedCsfFteQuantity = BigDecimal.ZERO;
        BigDecimal totalObjectAppointmentRequestedFteQuantity = BigDecimal.ZERO;
        KualiInteger totalObjectAccountLineAnnualBalanceAmount = KualiInteger.ZERO;

        List returnList = new ArrayList();

        for (BudgetConstructionAccountBalance simpleBcosEntry : simpleList) {

            BudgetConstructionOrgAccountObjectDetailReportTotal bcObjectTotal = new BudgetConstructionOrgAccountObjectDetailReportTotal();
            for (BudgetConstructionAccountBalance bcosListEntry : bcosList) {
                if (BudgetConstructionReportHelper.isSameEntry(simpleBcosEntry, bcosListEntry, fieldsForObject())) {
                    totalObjectFinancialBeginningBalanceLineAmount = totalObjectFinancialBeginningBalanceLineAmount.add(bcosListEntry.getFinancialBeginningBalanceLineAmount());
                    totalObjectAccountLineAnnualBalanceAmount = totalObjectAccountLineAnnualBalanceAmount.add(bcosListEntry.getAccountLineAnnualBalanceAmount());
                    totalObjectPositionCsfLeaveFteQuantity = totalObjectPositionCsfLeaveFteQuantity.add(bcosListEntry.getPositionCsfLeaveFteQuantity());
                    totalObjectPositionFullTimeEquivalencyQuantity = totalObjectPositionFullTimeEquivalencyQuantity.add(bcosListEntry.getPositionFullTimeEquivalencyQuantity());
                    totalObjectAppointmentRequestedCsfFteQuantity = totalObjectAppointmentRequestedCsfFteQuantity.add(bcosListEntry.getAppointmentRequestedCsfFteQuantity());
                    totalObjectAppointmentRequestedFteQuantity = totalObjectAppointmentRequestedFteQuantity.add(bcosListEntry.getAppointmentRequestedFteQuantity());
                }
            }
            bcObjectTotal.setBudgetConstructionAccountBalance(simpleBcosEntry);
            bcObjectTotal.setTotalObjectPositionCsfLeaveFteQuantity(totalObjectPositionCsfLeaveFteQuantity);
            bcObjectTotal.setTotalObjectPositionFullTimeEquivalencyQuantity(totalObjectPositionFullTimeEquivalencyQuantity);
            bcObjectTotal.setTotalObjectFinancialBeginningBalanceLineAmount(totalObjectFinancialBeginningBalanceLineAmount);
            bcObjectTotal.setTotalObjectAppointmentRequestedCsfFteQuantity(totalObjectAppointmentRequestedCsfFteQuantity);
            bcObjectTotal.setTotalObjectAppointmentRequestedFteQuantity(totalObjectAppointmentRequestedFteQuantity);
            bcObjectTotal.setTotalObjectAccountLineAnnualBalanceAmount(totalObjectAccountLineAnnualBalanceAmount);

            returnList.add(bcObjectTotal);

            totalObjectPositionCsfLeaveFteQuantity = BigDecimal.ZERO;
            totalObjectPositionFullTimeEquivalencyQuantity = BigDecimal.ZERO;
            totalObjectFinancialBeginningBalanceLineAmount = KualiInteger.ZERO;
            totalObjectAppointmentRequestedCsfFteQuantity = BigDecimal.ZERO;
            totalObjectAppointmentRequestedFteQuantity = BigDecimal.ZERO;
            totalObjectAccountLineAnnualBalanceAmount = KualiInteger.ZERO;
        }
        return returnList;

    }

    public void setBusinessObjectService(BusinessObjectService businessObjectService) {
        this.businessObjectService = businessObjectService;
    }

    protected List calculateLevelTotal(List<BudgetConstructionAccountBalance> bcosList, List<BudgetConstructionAccountBalance> simpleList) {

        BigDecimal totalLevelPositionCsfLeaveFteQuantity = BigDecimal.ZERO;
        BigDecimal totalLevelPositionFullTimeEquivalencyQuantity = BigDecimal.ZERO;
        KualiInteger totalLevelFinancialBeginningBalanceLineAmount = KualiInteger.ZERO;
        BigDecimal totalLevelAppointmentRequestedCsfFteQuantity = BigDecimal.ZERO;
        BigDecimal totalLevelAppointmentRequestedFteQuantity = BigDecimal.ZERO;
        KualiInteger totalLevelAccountLineAnnualBalanceAmount = KualiInteger.ZERO;

        List returnList = new ArrayList();

        for (BudgetConstructionAccountBalance simpleBcosEntry : simpleList) {

            BudgetConstructionOrgAccountObjectDetailReportTotal bcObjectTotal = new BudgetConstructionOrgAccountObjectDetailReportTotal();
            for (BudgetConstructionAccountBalance bcosListEntry : bcosList) {
                if (BudgetConstructionReportHelper.isSameEntry(simpleBcosEntry, bcosListEntry, fieldsForLevel())) {
                    totalLevelFinancialBeginningBalanceLineAmount = totalLevelFinancialBeginningBalanceLineAmount.add(bcosListEntry.getFinancialBeginningBalanceLineAmount());
                    totalLevelAccountLineAnnualBalanceAmount = totalLevelAccountLineAnnualBalanceAmount.add(bcosListEntry.getAccountLineAnnualBalanceAmount());
                    totalLevelPositionCsfLeaveFteQuantity = totalLevelPositionCsfLeaveFteQuantity.add(bcosListEntry.getPositionCsfLeaveFteQuantity());
                    totalLevelPositionFullTimeEquivalencyQuantity = totalLevelPositionFullTimeEquivalencyQuantity.add(bcosListEntry.getPositionFullTimeEquivalencyQuantity());
                    totalLevelAppointmentRequestedCsfFteQuantity = totalLevelAppointmentRequestedCsfFteQuantity.add(bcosListEntry.getAppointmentRequestedCsfFteQuantity());
                    totalLevelAppointmentRequestedFteQuantity = totalLevelAppointmentRequestedFteQuantity.add(bcosListEntry.getAppointmentRequestedFteQuantity());
                }
            }
            bcObjectTotal.setBudgetConstructionAccountBalance(simpleBcosEntry);
            bcObjectTotal.setTotalLevelPositionCsfLeaveFteQuantity(totalLevelPositionCsfLeaveFteQuantity);
            bcObjectTotal.setTotalLevelPositionFullTimeEquivalencyQuantity(totalLevelPositionFullTimeEquivalencyQuantity);
            bcObjectTotal.setTotalLevelFinancialBeginningBalanceLineAmount(totalLevelFinancialBeginningBalanceLineAmount);
            bcObjectTotal.setTotalLevelAppointmentRequestedCsfFteQuantity(totalLevelAppointmentRequestedCsfFteQuantity);
            bcObjectTotal.setTotalLevelAppointmentRequestedFteQuantity(totalLevelAppointmentRequestedFteQuantity);
            bcObjectTotal.setTotalLevelAccountLineAnnualBalanceAmount(totalLevelAccountLineAnnualBalanceAmount);

            returnList.add(bcObjectTotal);

            totalLevelPositionCsfLeaveFteQuantity = BigDecimal.ZERO;
            totalLevelPositionFullTimeEquivalencyQuantity = BigDecimal.ZERO;
            totalLevelFinancialBeginningBalanceLineAmount = KualiInteger.ZERO;
            totalLevelAppointmentRequestedCsfFteQuantity = BigDecimal.ZERO;
            totalLevelAppointmentRequestedFteQuantity = BigDecimal.ZERO;
            totalLevelAccountLineAnnualBalanceAmount = KualiInteger.ZERO;
        }
        return returnList;
    }


    protected List calculateGexpAndTypeTotal(List<BudgetConstructionAccountBalance> bcabList, List<BudgetConstructionAccountBalance> simpleList) {

        KualiInteger grossFinancialBeginningBalanceLineAmount = KualiInteger.ZERO;
        KualiInteger grossAccountLineAnnualBalanceAmount = KualiInteger.ZERO;

        BigDecimal typePositionCsfLeaveFteQuantity = BigDecimal.ZERO;
        BigDecimal typePositionFullTimeEquivalencyQuantity = BigDecimal.ZERO;
        KualiInteger typeFinancialBeginningBalanceLineAmount = KualiInteger.ZERO;
        BigDecimal typeAppointmentRequestedCsfFteQuantity = BigDecimal.ZERO;
        BigDecimal typeAppointmentRequestedFteQuantity = BigDecimal.ZERO;
        KualiInteger typeAccountLineAnnualBalanceAmount = KualiInteger.ZERO;

        List returnList = new ArrayList();
        for (BudgetConstructionAccountBalance simpleBcosEntry : simpleList) {
            BudgetConstructionOrgAccountObjectDetailReportTotal bcObjectTotal = new BudgetConstructionOrgAccountObjectDetailReportTotal();
            for (BudgetConstructionAccountBalance bcabListEntry : bcabList) {
                if (BudgetConstructionReportHelper.isSameEntry(simpleBcosEntry, bcabListEntry, fieldsForGexpAndType())) {

                    typeFinancialBeginningBalanceLineAmount = typeFinancialBeginningBalanceLineAmount.add(bcabListEntry.getFinancialBeginningBalanceLineAmount());
                    typeAccountLineAnnualBalanceAmount = typeAccountLineAnnualBalanceAmount.add(bcabListEntry.getAccountLineAnnualBalanceAmount());
                    typePositionCsfLeaveFteQuantity = typePositionCsfLeaveFteQuantity.add(bcabListEntry.getPositionCsfLeaveFteQuantity());
                    typePositionFullTimeEquivalencyQuantity = typePositionFullTimeEquivalencyQuantity.add(bcabListEntry.getPositionFullTimeEquivalencyQuantity());
                    typeAppointmentRequestedCsfFteQuantity = typeAppointmentRequestedCsfFteQuantity.add(bcabListEntry.getAppointmentRequestedCsfFteQuantity());
                    typeAppointmentRequestedFteQuantity = typeAppointmentRequestedFteQuantity.add(bcabListEntry.getAppointmentRequestedFteQuantity());

                    if (bcabListEntry.getIncomeExpenseCode().equals("B") && !bcabListEntry.getFinancialObjectLevelCode().equals("CORI") && !bcabListEntry.getFinancialObjectLevelCode().equals("TRIN")) {
                        grossFinancialBeginningBalanceLineAmount = grossFinancialBeginningBalanceLineAmount.add(bcabListEntry.getFinancialBeginningBalanceLineAmount());
                        grossAccountLineAnnualBalanceAmount = grossAccountLineAnnualBalanceAmount.add(bcabListEntry.getAccountLineAnnualBalanceAmount());
                    }
                }
            }
            bcObjectTotal.setBudgetConstructionAccountBalance(simpleBcosEntry);

            bcObjectTotal.setGrossFinancialBeginningBalanceLineAmount(grossFinancialBeginningBalanceLineAmount);
            bcObjectTotal.setGrossAccountLineAnnualBalanceAmount(grossAccountLineAnnualBalanceAmount);

            bcObjectTotal.setTypePositionCsfLeaveFteQuantity(typePositionCsfLeaveFteQuantity);
            bcObjectTotal.setTypePositionFullTimeEquivalencyQuantity(typePositionFullTimeEquivalencyQuantity);
            bcObjectTotal.setTypeFinancialBeginningBalanceLineAmount(typeFinancialBeginningBalanceLineAmount);
            bcObjectTotal.setTypeAppointmentRequestedCsfFteQuantity(typeAppointmentRequestedCsfFteQuantity);
            bcObjectTotal.setTypeAppointmentRequestedFteQuantity(typeAppointmentRequestedFteQuantity);
            bcObjectTotal.setTypeAccountLineAnnualBalanceAmount(typeAccountLineAnnualBalanceAmount);

            returnList.add(bcObjectTotal);

            grossFinancialBeginningBalanceLineAmount = KualiInteger.ZERO;
            grossAccountLineAnnualBalanceAmount = KualiInteger.ZERO;

            typePositionCsfLeaveFteQuantity = BigDecimal.ZERO;
            typePositionFullTimeEquivalencyQuantity = BigDecimal.ZERO;
            typeFinancialBeginningBalanceLineAmount = KualiInteger.ZERO;
            typeAppointmentRequestedCsfFteQuantity = BigDecimal.ZERO;
            typeAppointmentRequestedFteQuantity = BigDecimal.ZERO;
            typeAccountLineAnnualBalanceAmount = KualiInteger.ZERO;
        }
        return returnList;
    }

    protected List calculateAccountTotal(List<BudgetConstructionAccountBalance> bcabList, List<BudgetConstructionAccountBalance> simpleList) {
        BigDecimal accountPositionCsfLeaveFteQuantity = BigDecimal.ZERO;
        BigDecimal accountPositionFullTimeEquivalencyQuantity = BigDecimal.ZERO;
        BigDecimal accountAppointmentRequestedCsfFteQuantity = BigDecimal.ZERO;
        BigDecimal accountAppointmentRequestedFteQuantity = BigDecimal.ZERO;

        KualiInteger accountRevenueFinancialBeginningBalanceLineAmount = KualiInteger.ZERO;
        KualiInteger accountRevenueAccountLineAnnualBalanceAmount = KualiInteger.ZERO;
        KualiInteger accountTrnfrInFinancialBeginningBalanceLineAmount = KualiInteger.ZERO;
        KualiInteger accountTrnfrInAccountLineAnnualBalanceAmount = KualiInteger.ZERO;
        KualiInteger accountExpenditureFinancialBeginningBalanceLineAmount = KualiInteger.ZERO;
        KualiInteger accountExpenditureAccountLineAnnualBalanceAmount = KualiInteger.ZERO;

        List returnList = new ArrayList();

        for (BudgetConstructionAccountBalance simpleBcosEntry : simpleList) {
            BudgetConstructionOrgAccountObjectDetailReportTotal bcObjectTotal = new BudgetConstructionOrgAccountObjectDetailReportTotal();
            for (BudgetConstructionAccountBalance bcabListEntry : bcabList) {
                if (BudgetConstructionReportHelper.isSameEntry(simpleBcosEntry, bcabListEntry, fieldsForAccountTotal())) {

                    accountPositionCsfLeaveFteQuantity = accountPositionCsfLeaveFteQuantity.add(bcabListEntry.getPositionCsfLeaveFteQuantity());
                    accountPositionFullTimeEquivalencyQuantity = accountPositionFullTimeEquivalencyQuantity.add(bcabListEntry.getPositionFullTimeEquivalencyQuantity());
                    accountAppointmentRequestedCsfFteQuantity = accountAppointmentRequestedCsfFteQuantity.add(bcabListEntry.getAppointmentRequestedCsfFteQuantity());
                    accountAppointmentRequestedFteQuantity = accountAppointmentRequestedFteQuantity.add(bcabListEntry.getAppointmentRequestedFteQuantity());

                    if (bcabListEntry.getIncomeExpenseCode().equals("A")) {
                        accountRevenueFinancialBeginningBalanceLineAmount = accountRevenueFinancialBeginningBalanceLineAmount.add(bcabListEntry.getFinancialBeginningBalanceLineAmount());
                        accountRevenueAccountLineAnnualBalanceAmount = accountRevenueAccountLineAnnualBalanceAmount.add(bcabListEntry.getAccountLineAnnualBalanceAmount());
                    }
                    else {
                        accountExpenditureFinancialBeginningBalanceLineAmount = accountExpenditureFinancialBeginningBalanceLineAmount.add(bcabListEntry.getFinancialBeginningBalanceLineAmount());
                        accountExpenditureAccountLineAnnualBalanceAmount = accountExpenditureAccountLineAnnualBalanceAmount.add(bcabListEntry.getAccountLineAnnualBalanceAmount());
                    }

                    if (bcabListEntry.getIncomeExpenseCode().equals("B")) {
                        if (bcabListEntry.getFinancialObjectLevelCode().equals("CORI") || bcabListEntry.getFinancialObjectLevelCode().equals("TRIN")) {
                            accountTrnfrInFinancialBeginningBalanceLineAmount = accountTrnfrInFinancialBeginningBalanceLineAmount.add(bcabListEntry.getFinancialBeginningBalanceLineAmount());
                            accountTrnfrInAccountLineAnnualBalanceAmount = accountTrnfrInAccountLineAnnualBalanceAmount.add(bcabListEntry.getAccountLineAnnualBalanceAmount());
                        }
                    }
                }
            }

            bcObjectTotal.setBudgetConstructionAccountBalance(simpleBcosEntry);

            bcObjectTotal.setAccountPositionCsfLeaveFteQuantity(accountPositionCsfLeaveFteQuantity);
            bcObjectTotal.setAccountPositionFullTimeEquivalencyQuantity(accountPositionFullTimeEquivalencyQuantity);
            bcObjectTotal.setAccountAppointmentRequestedCsfFteQuantity(accountAppointmentRequestedCsfFteQuantity);
            bcObjectTotal.setAccountAppointmentRequestedFteQuantity(accountAppointmentRequestedFteQuantity);

            bcObjectTotal.setAccountRevenueFinancialBeginningBalanceLineAmount(accountRevenueFinancialBeginningBalanceLineAmount);
            bcObjectTotal.setAccountRevenueAccountLineAnnualBalanceAmount(accountRevenueAccountLineAnnualBalanceAmount);
            bcObjectTotal.setAccountTrnfrInFinancialBeginningBalanceLineAmount(accountTrnfrInFinancialBeginningBalanceLineAmount);
            bcObjectTotal.setAccountTrnfrInAccountLineAnnualBalanceAmount(accountTrnfrInAccountLineAnnualBalanceAmount);
            bcObjectTotal.setAccountExpenditureFinancialBeginningBalanceLineAmount(accountExpenditureFinancialBeginningBalanceLineAmount);
            bcObjectTotal.setAccountExpenditureAccountLineAnnualBalanceAmount(accountExpenditureAccountLineAnnualBalanceAmount);

            returnList.add(bcObjectTotal);

            accountPositionCsfLeaveFteQuantity = BigDecimal.ZERO;
            accountPositionFullTimeEquivalencyQuantity = BigDecimal.ZERO;
            accountAppointmentRequestedCsfFteQuantity = BigDecimal.ZERO;
            accountAppointmentRequestedFteQuantity = BigDecimal.ZERO;

            accountRevenueFinancialBeginningBalanceLineAmount = KualiInteger.ZERO;
            accountRevenueAccountLineAnnualBalanceAmount = KualiInteger.ZERO;
            accountTrnfrInFinancialBeginningBalanceLineAmount = KualiInteger.ZERO;
            accountTrnfrInAccountLineAnnualBalanceAmount = KualiInteger.ZERO;
            accountExpenditureFinancialBeginningBalanceLineAmount = KualiInteger.ZERO;
            accountExpenditureAccountLineAnnualBalanceAmount = KualiInteger.ZERO;
        }
        return returnList;
    }


    protected List calculateSubFundTotal(List<BudgetConstructionAccountBalance> bcabList, List<BudgetConstructionAccountBalance> simpleList) {

        BigDecimal subFundPositionCsfLeaveFteQuantity = BigDecimal.ZERO;
        BigDecimal subFundPositionFullTimeEquivalencyQuantity = BigDecimal.ZERO;
        BigDecimal subFundAppointmentRequestedCsfFteQuantity = BigDecimal.ZERO;
        BigDecimal subFundAppointmentRequestedFteQuantity = BigDecimal.ZERO;

        KualiInteger subFundRevenueFinancialBeginningBalanceLineAmount = KualiInteger.ZERO;
        KualiInteger subFundRevenueAccountLineAnnualBalanceAmount = KualiInteger.ZERO;
        KualiInteger subFundTrnfrInFinancialBeginningBalanceLineAmount = KualiInteger.ZERO;
        KualiInteger subFundTrnfrInAccountLineAnnualBalanceAmount = KualiInteger.ZERO;
        KualiInteger subFundExpenditureFinancialBeginningBalanceLineAmount = KualiInteger.ZERO;
        KualiInteger subFundExpenditureAccountLineAnnualBalanceAmount = KualiInteger.ZERO;

        List returnList = new ArrayList();

        for (BudgetConstructionAccountBalance simpleBcosEntry : simpleList) {
            BudgetConstructionOrgAccountObjectDetailReportTotal bcObjectTotal = new BudgetConstructionOrgAccountObjectDetailReportTotal();
            for (BudgetConstructionAccountBalance bcabListEntry : bcabList) {
                if (BudgetConstructionReportHelper.isSameEntry(simpleBcosEntry, bcabListEntry, fieldsForSubFundTotal())) {

                    subFundPositionCsfLeaveFteQuantity = subFundPositionCsfLeaveFteQuantity.add(bcabListEntry.getPositionCsfLeaveFteQuantity());
                    subFundPositionFullTimeEquivalencyQuantity = subFundPositionFullTimeEquivalencyQuantity.add(bcabListEntry.getPositionFullTimeEquivalencyQuantity());
                    subFundAppointmentRequestedCsfFteQuantity = subFundAppointmentRequestedCsfFteQuantity.add(bcabListEntry.getAppointmentRequestedCsfFteQuantity());
                    subFundAppointmentRequestedFteQuantity = subFundAppointmentRequestedFteQuantity.add(bcabListEntry.getAppointmentRequestedFteQuantity());

                    if (bcabListEntry.getIncomeExpenseCode().equals("A")) {
                        subFundRevenueFinancialBeginningBalanceLineAmount = subFundRevenueFinancialBeginningBalanceLineAmount.add(bcabListEntry.getFinancialBeginningBalanceLineAmount());
                        subFundRevenueAccountLineAnnualBalanceAmount = subFundRevenueAccountLineAnnualBalanceAmount.add(bcabListEntry.getAccountLineAnnualBalanceAmount());
                    }
                    else {
                        subFundExpenditureFinancialBeginningBalanceLineAmount = subFundExpenditureFinancialBeginningBalanceLineAmount.add(bcabListEntry.getFinancialBeginningBalanceLineAmount());
                        subFundExpenditureAccountLineAnnualBalanceAmount = subFundExpenditureAccountLineAnnualBalanceAmount.add(bcabListEntry.getAccountLineAnnualBalanceAmount());
                    }

                    if (bcabListEntry.getIncomeExpenseCode().equals("B")) {
                        if (bcabListEntry.getFinancialObjectLevelCode().equals("CORI") || bcabListEntry.getFinancialObjectLevelCode().equals("TRIN")) {
                            subFundTrnfrInFinancialBeginningBalanceLineAmount = subFundTrnfrInFinancialBeginningBalanceLineAmount.add(bcabListEntry.getFinancialBeginningBalanceLineAmount());
                            subFundTrnfrInAccountLineAnnualBalanceAmount = subFundTrnfrInAccountLineAnnualBalanceAmount.add(bcabListEntry.getAccountLineAnnualBalanceAmount());
                        }
                    }
                }
            }

            bcObjectTotal.setBudgetConstructionAccountBalance(simpleBcosEntry);

            bcObjectTotal.setSubFundPositionCsfLeaveFteQuantity(subFundPositionCsfLeaveFteQuantity);
            bcObjectTotal.setSubFundPositionFullTimeEquivalencyQuantity(subFundPositionFullTimeEquivalencyQuantity);
            bcObjectTotal.setSubFundAppointmentRequestedCsfFteQuantity(subFundAppointmentRequestedCsfFteQuantity);
            bcObjectTotal.setSubFundAppointmentRequestedFteQuantity(subFundAppointmentRequestedFteQuantity);

            bcObjectTotal.setSubFundRevenueFinancialBeginningBalanceLineAmount(subFundRevenueFinancialBeginningBalanceLineAmount);
            bcObjectTotal.setSubFundRevenueAccountLineAnnualBalanceAmount(subFundRevenueAccountLineAnnualBalanceAmount);

            bcObjectTotal.setSubFundTrnfrInFinancialBeginningBalanceLineAmount(subFundTrnfrInFinancialBeginningBalanceLineAmount);
            bcObjectTotal.setSubFundTrnfrInAccountLineAnnualBalanceAmount(subFundTrnfrInAccountLineAnnualBalanceAmount);

            bcObjectTotal.setSubFundExpenditureFinancialBeginningBalanceLineAmount(subFundExpenditureFinancialBeginningBalanceLineAmount);
            bcObjectTotal.setSubFundExpenditureAccountLineAnnualBalanceAmount(subFundExpenditureAccountLineAnnualBalanceAmount);

            returnList.add(bcObjectTotal);

            subFundPositionCsfLeaveFteQuantity = BigDecimal.ZERO;
            subFundPositionFullTimeEquivalencyQuantity = BigDecimal.ZERO;
            subFundAppointmentRequestedCsfFteQuantity = BigDecimal.ZERO;
            subFundAppointmentRequestedFteQuantity = BigDecimal.ZERO;

            subFundRevenueFinancialBeginningBalanceLineAmount = KualiInteger.ZERO;
            subFundRevenueAccountLineAnnualBalanceAmount = KualiInteger.ZERO;
            subFundTrnfrInFinancialBeginningBalanceLineAmount = KualiInteger.ZERO;
            subFundTrnfrInAccountLineAnnualBalanceAmount = KualiInteger.ZERO;
            subFundExpenditureFinancialBeginningBalanceLineAmount = KualiInteger.ZERO;
            subFundExpenditureAccountLineAnnualBalanceAmount = KualiInteger.ZERO;
        }
        return returnList;
    }


    /**
     * builds list of fields for comparing entry of Object
     *
     * @return List<String>
     */
    protected List<String> fieldsForObject() {
        List<String> fieldList = new ArrayList();
        fieldList.addAll(fieldsForLevel());
        fieldList.add(KFSPropertyConstants.FINANCIAL_OBJECT_CODE);
        // fieldList.add(KFSPropertyConstants.FINANCIAL_SUB_OBJECT_CODE);
        return fieldList;
    }

    /**
     * builds list of fields for comparing entry of Level
     *
     * @return List<String>
     */
    protected List<String> fieldsForLevel() {
        List<String> fieldList = new ArrayList();
        fieldList.addAll(fieldsForGexpAndType());
        fieldList.add(KFSPropertyConstants.FINANCIAL_LEVEL_SORT_CODE);
        return fieldList;
    }

    /**
     * builds list of fields for comparing entry of GexpAndType
     *
     * @return List<String>
     */
    protected List<String> fieldsForGexpAndType() {
        List<String> fieldList = new ArrayList();
        fieldList.addAll(fieldsForAccountTotal());
        fieldList.add(KFSPropertyConstants.INCOME_EXPENSE_CODE);
        return fieldList;
    }

    /**
     * builds list of fields for comparing entry of AccountTotal
     *
     * @return List<String>
     */
    protected List<String> fieldsForAccountTotal() {
        List<String> fieldList = new ArrayList();
        // fieldList.addAll(fieldsForSubFundTotal());
        fieldList.add(KFSPropertyConstants.CHART_OF_ACCOUNTS_CODE);
        fieldList.add(KFSPropertyConstants.ACCOUNT_NUMBER);
        fieldList.add(KFSPropertyConstants.SUB_ACCOUNT_NUMBER);
        return fieldList;
    }

    /**
     * builds list of fields for comparing entry of SubFundTotal total
     *
     * @return List<String>
     */
    protected List<String> fieldsForSubFundTotal() {
        List<String> fieldList = new ArrayList();

        fieldList.add(KFSPropertyConstants.ORGANIZATION_CHART_OF_ACCOUNTS_CODE);
        fieldList.add(KFSPropertyConstants.ORGANIZATION_CODE);
        fieldList.add(KFSPropertyConstants.SUB_FUND_GROUP_CODE);
        return fieldList;
    }


    public List<String> buildOrderByList() {
        List<String> returnList = new ArrayList();
        returnList.add(KFSPropertyConstants.ORGANIZATION_CHART_OF_ACCOUNTS_CODE);
        returnList.add(KFSPropertyConstants.ORGANIZATION_CODE);
        returnList.add(KFSPropertyConstants.SUB_FUND_GROUP_CODE);
        returnList.add(KFSPropertyConstants.UNIVERSITY_FISCAL_YEAR);
        returnList.add(KFSPropertyConstants.CHART_OF_ACCOUNTS_CODE);
        returnList.add(KFSPropertyConstants.ACCOUNT_NUMBER);
        returnList.add(KFSPropertyConstants.SUB_ACCOUNT_NUMBER);
        returnList.add(KFSPropertyConstants.INCOME_EXPENSE_CODE);
        returnList.add(KFSPropertyConstants.FINANCIAL_CONSOLIDATION_SORT_CODE);
        returnList.add(KFSPropertyConstants.FINANCIAL_LEVEL_SORT_CODE);
        returnList.add(KFSPropertyConstants.FINANCIAL_OBJECT_CODE);
        returnList.add(KFSPropertyConstants.FINANCIAL_SUB_OBJECT_CODE);
        return returnList;
    }

    public void setConfigurationService(ConfigurationService kualiConfigurationService) {
        this.kualiConfigurationService = kualiConfigurationService;
    }

    public ConfigurationService getConfigurationService() {
        return kualiConfigurationService;
    }

    public void setBudgetConstructionAccountObjectDetailReportDao(BudgetConstructionAccountObjectDetailReportDao budgetConstructionAccountObjectDetailReportDao) {
        this.budgetConstructionAccountObjectDetailReportDao = budgetConstructionAccountObjectDetailReportDao;
    }

    public void setBudgetConstructionOrganizationReportsService(BudgetConstructionOrganizationReportsService budgetConstructionOrganizationReportsService) {
        this.budgetConstructionOrganizationReportsService = budgetConstructionOrganizationReportsService;
    }

    /**
     * Gets the persistenceServiceOjb attribute.
     *
     * @return Returns the persistenceServiceOjb
     */

    public PersistenceService getPersistenceServiceOjb() {
        return persistenceServiceOjb;
    }

    /**
     * Sets the persistenceServiceOjb attribute.
     *
     * @param persistenceServiceOjb The persistenceServiceOjb to set.
     */
    public void setPersistenceServiceOjb(PersistenceService persistenceServiceOjb) {
        this.persistenceServiceOjb = persistenceServiceOjb;
    }

}
