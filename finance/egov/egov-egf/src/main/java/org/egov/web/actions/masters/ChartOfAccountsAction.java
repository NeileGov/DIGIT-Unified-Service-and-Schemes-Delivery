/*******************************************************************************
 * eGov suite of products aim to improve the internal efficiency,transparency,
 *    accountability and the service delivery of the government  organizations.
 *
 *     Copyright (C) <2015>  eGovernments Foundation
 *
 *     The updated version of eGov suite of products as by eGovernments Foundation
 *     is available at http://www.egovernments.org
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program. If not, see http://www.gnu.org/licenses/ or
 *     http://www.gnu.org/licenses/gpl.html .
 *
 *     In addition to the terms of the GPL license to be adhered to in using this
 *     program, the following additional terms are to be complied with:
 *
 * 	1) All versions of this program, verbatim or modified must carry this
 * 	   Legal Notice.
 *
 * 	2) Any misrepresentation of the origin of the material is prohibited. It
 * 	   is required that all modified versions of this material be marked in
 * 	   reasonable ways as different from the original version.
 *
 * 	3) This license does not grant any rights to any user of the program
 * 	   with regards to rights under trademark law for use of the trade names
 * 	   or trademarks of eGovernments Foundation.
 *
 *   In case of any queries, you can reach eGovernments Foundation at contact@egovernments.org.
 ******************************************************************************/
package org.egov.web.actions.masters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.ParentPackage;
import org.apache.struts2.convention.annotation.Result;
import org.apache.struts2.convention.annotation.Results;
import org.apache.struts2.interceptor.validation.SkipValidation;
import org.egov.commons.Accountdetailtype;
import org.egov.commons.CChartOfAccountDetail;
import org.egov.commons.CChartOfAccounts;
import org.egov.commons.EgfAccountcodePurpose;
import org.egov.infra.admin.master.service.AppConfigValueService;
import org.egov.infra.web.struts.actions.BaseFormAction;
import org.egov.infstr.ValidationError;
import org.egov.infstr.ValidationException;
import org.egov.infstr.services.PersistenceService;
import org.egov.infstr.utils.HibernateUtil;
import org.egov.model.masters.AccountCodePurpose;
import org.egov.utils.Constants;
import org.hibernate.SQLQuery;
import org.springframework.beans.factory.annotation.Autowired;

import com.exilant.GLEngine.ChartOfAccounts;
import com.exilant.exility.common.TaskFailedException;

@ParentPackage("egov")
@Results({
    @Result(name = "detailed-code" , location = "chartOfAccounts-detailed-code.jsp"),
    @Result(name = "detailed" , location = "chartOfAccounts-detailed.jsp"),
    @Result(name = Constants.EDIT , location = "chartOfAccounts-edit.jsp"),
    @Result(name = Constants.VIEW , location = "chartOfAccounts-view.jsp"),
    @Result(name = "generated-glcode", location = "chartOfAccounts-generated-glcode.jsp")})
public class ChartOfAccountsAction extends BaseFormAction {
    private static final long serialVersionUID = 3393565721493478018L;
    private static final long LONG_FOUR = 4l;
    private static final long LONG_TWO = 2l;
    PersistenceService<CChartOfAccounts, Long> chartOfAccountService;
    CChartOfAccounts model = new CChartOfAccounts();
    List<String> accountDetailTypeList = new ArrayList<String>();
    List<Accountdetailtype> accountDetailType = new ArrayList<Accountdetailtype>();
    private static final Logger LOGGER = Logger.getLogger(ChartOfAccountsAction.class);

    boolean activeForPosting = false;
    boolean functionRequired = false;
    boolean budgetCheckRequired = false;
    Long coaId;
    Long parentId;
    @Autowired
    AppConfigValueService appConfigValuesService;
    @Autowired
    private ChartOfAccounts chartOfAccounts;
    String glCode = "";
    List<CChartOfAccounts> allChartOfAccounts;
    int majorCodeLength = 0;
    int minorCodeLength = 0;
    int subMinorCodeLength = 0;
    int detailedCodeLength = 0;
    EgfAccountcodePurpose accountcodePurpose;
    private String generatedGlcode;
    String newGlcode;
    String parentForDetailedCode = "";
    private final Map<Long, Integer> glCodeLengths = new HashMap<Long, Integer>();
    private boolean updateOnly = false;

    @Override
    public Object getModel() {
        return model;
    }

    public ChartOfAccountsAction() {
       
          addRelatedEntity("purpose", AccountCodePurpose.class); addRelatedEntity("chartOfAccountDetails.detailTypeId",
          AccountCodePurpose.class);
         
    }

    @Override
    public void prepare() {
        super.prepare();
        populateChartOfAccounts();
        populateCodeLength();
        parentForDetailedCode = getAppConfigValueFor("EGF", "parent_for_detailcode");
        populateGlCodeLengths();
        allChartOfAccounts = chartOfAccountService.findAllBy("from CChartOfAccounts where classification=?",
                Long.valueOf(parentForDetailedCode));
        if (model != null)
            if (accountcodePurpose != null && accountcodePurpose.getId() != null)
                accountcodePurpose = getPurposeCode(Integer.valueOf(accountcodePurpose.getId()));
        dropdownData.put("purposeList", persistenceService.findAllBy("from EgfAccountcodePurpose order by name"));
        dropdownData.put("accountDetailTypeList", persistenceService.findAllBy("from Accountdetailtype order by name"));
    }

    private void populateAccountDetailTypeList() {
        if (model.getChartOfAccountDetails() != null)
            for (final CChartOfAccountDetail entry : model.getChartOfAccountDetails())
                accountDetailTypeList.add(entry.getDetailTypeId().getId().toString());
    }

    void populateGlCodeLengths() {
        getGlCodeLengths().put(Constants.LONG_ONE, majorCodeLength);
        getGlCodeLengths().put(LONG_TWO, minorCodeLength - majorCodeLength);
        getGlCodeLengths().put(3l, subMinorCodeLength - minorCodeLength);
        if (parentForDetailedCode.equals("2"))
            getGlCodeLengths().put(LONG_FOUR, detailedCodeLength - minorCodeLength);
        else if (parentForDetailedCode.equals("3"))
            getGlCodeLengths().put(LONG_FOUR, detailedCodeLength - subMinorCodeLength);
    }

    private EgfAccountcodePurpose getPurposeCode(final Integer id) {
        return (EgfAccountcodePurpose) persistenceService.find("from EgfAccountcodePurpose where id=?", id);
    }

    private void populateChartOfAccounts() {
        if (model.getId() != null)
            model = chartOfAccountService.findById(model.getId(), false);
    }

    @Override
    public String execute() throws Exception {
        return NEW;
    }

    @Action(value = "/masters/chartOfAccounts-view")
    public String view() throws Exception {
        populateAccountCodePurpose();
        populateAccountDetailTypeList();
        populateCoaRequiredFields();
        coaId = model.getId();
        return Constants.VIEW;
    }

    public boolean shouldAllowCreation() {
        return !Long.valueOf("4").equals(model.getClassification());
    }
    @Action(value = "/masters/chartOfAccounts-modify")
    public String modify() throws Exception {
        populateAccountDetailTypeList();
        populateCoaRequiredFields();
        populateAccountCodePurpose();
        return Constants.EDIT;
    }

    private void populateCoaRequiredFields() {
        activeForPosting = getIsActiveForPosting();
        functionRequired = getFunctionReqd();
        budgetCheckRequired = budgetCheckReq();
    }

    private void populateAccountCodePurpose() {
        if (model != null && model.getPurposeId() != null)
            accountcodePurpose = getPurposeCode(model.getPurposeId().intValue());
    }
    @Action(value = "/masters/chartOfAccounts-update")
    public String update() throws Exception {
        setPurposeOnCoa();
        updateOnly = true;
        populateAccountDetailType();
        model.setIsActiveForPosting(activeForPosting);
        model.setFunctionReqd(functionRequired);
        model.setBudgetCheckReq(budgetCheckRequired);
        chartOfAccountService.persist(model);
        saveCoaDetails(model);
        addActionMessage(getText("chartOfAccount.modified.successfully"));
        clearCache();
        coaId = model.getId();
        return Constants.VIEW;
    }

    private void setPurposeOnCoa() {
        if (accountcodePurpose != null && accountcodePurpose.getId() != null)
            model.setPurposeId(accountcodePurpose.getId().longValue());
        if (model.getPurposeId()!=null && model.getPurposeId().compareTo(0l) == 0)
            model.setPurposeId(null);
    }

    private void populateAccountDetailType() {
        persistenceService.setType(Accountdetailtype.class);
        for (final String row : accountDetailTypeList)
            accountDetailType.add((Accountdetailtype) persistenceService.find("from Accountdetailtype where id=?",
                    Integer.valueOf(row)));
    }

    void deleteAccountDetailType(final List<Accountdetailtype> accountDetailType, final CChartOfAccounts accounts) {
        String accountDetail = "";
        if (accounts.getChartOfAccountDetails() == null)
            return;
        chartOfAccountService.getSession().flush();
        persistenceService.setType(CChartOfAccountDetail.class);
        try {
            for (final Accountdetailtype row : accountDetailType) {
                final Iterator<CChartOfAccountDetail> iterator = accounts.getChartOfAccountDetails().iterator();
                while (iterator.hasNext()) {
                    final CChartOfAccountDetail next = iterator.next();
                    accountDetail = row.getName();
                    if (next == null || next.getDetailTypeId().getId().equals(row.getId())) {
                        iterator.remove();
                        persistenceService.delete(persistenceService.findById(next.getId(), false));
                        HibernateUtil.getCurrentSession().flush();
                    }
                }
            }
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
            populateAccountDetailTypeList();
            final String message = accountDetail.concat(" ").concat(e.toString());
            throw new ValidationException(Arrays.asList(new ValidationError(message, message)));
        }
    }

    boolean hasReference(final Integer id, final String glCode) {
        final SQLQuery query = HibernateUtil.getCurrentSession().createSQLQuery(
                "select * from chartofaccounts c,generalledger gl,generalledgerdetail gd " +
                        "where c.glcode='" + glCode + "' and gl.glcodeid=c.id and gd.generalledgerid=gl.id and gd.DETAILTYPEID="
                        + id);
        final List list = query.list();
        if (list != null && list.size() > 0)
            return true;
        return false;
    }

    boolean validAddtition(final String glCode) {
        final StringBuffer strQuery = new StringBuffer();
        strQuery.append("select bd.billid from  eg_billdetails bd, chartofaccounts coa,  eg_billregistermis brm where coa.glcode = '"
                + glCode + "' and bd.glcodeid = coa.id and brm.billid = bd.billid and brm.voucherheaderid is null ");
        strQuery.append(" intersect SELECT br.id FROM eg_billregister br, eg_billdetails bd, chartofaccounts coa,egw_status  sts WHERE coa.glcode = '"
                + glCode + "' AND bd.glcodeid = coa.id AND br.id= bd.billid AND br.statusid=sts.id ");
        strQuery.append(" and sts.id not in (select id from egw_status where upper(moduletype) like '%BILL%' and upper(description) like '%CANCELLED%') ");
        final SQLQuery query = HibernateUtil.getCurrentSession().createSQLQuery(strQuery.toString());
        final List list = query.list();
        if (list != null && list.size() > 0)
            return false;
        return true;
    }

    void saveCoaDetails(final CChartOfAccounts accounts) {
        final List<Accountdetailtype> rowsToBeDeleted = getAccountDetailTypeToBeDeleted(accountDetailType, accounts);
        final List<Accountdetailtype> rowsToBeAdded = getAccountDetailTypeToBeAdded(accountDetailType, accounts);
        deleteAccountDetailType(rowsToBeDeleted, accounts);
        if (accountDetailType.size() == 1 && rowsToBeAdded.size() == 1 && rowsToBeDeleted.size() == 0 && updateOnly)
            if (!validAddtition(model.getGlcode()))
            {
                final String message = getText("chartOfAccount.accDetail.uncancelled.bills");
                throw new ValidationException(Arrays.asList(new ValidationError(message, message)));
            }
        for (final Accountdetailtype entry : rowsToBeAdded)
            if (!coaHasAccountdetailtype(entry, accounts)) {
                final CChartOfAccountDetail chartOfAccountDetail = new CChartOfAccountDetail();
                chartOfAccountDetail.setDetailTypeId(entry);
                chartOfAccountDetail.setGlCodeId(accounts);
                accounts.getChartOfAccountDetails().add(chartOfAccountDetail);
            }
        chartOfAccountService.persist(accounts);
        chartOfAccountService.getSession().flush();
    }

    List<Accountdetailtype> getAccountDetailTypeToBeDeleted(final List<Accountdetailtype> accountDetailType,
            final CChartOfAccounts accounts) {
        final List<Accountdetailtype> rowsToBeDeleted = new ArrayList<Accountdetailtype>();
        for (final CChartOfAccountDetail entry : accounts.getChartOfAccountDetails())
            if (accountDetailType != null && accountDetailType.isEmpty())
                rowsToBeDeleted.add(entry.getDetailTypeId());
            else if (!accountDetailTypeContains(accountDetailType, entry.getDetailTypeId()))
                rowsToBeDeleted.add(entry.getDetailTypeId());
        return rowsToBeDeleted;
    }

    List<Accountdetailtype> getAccountDetailTypeToBeAdded(final List<Accountdetailtype> accountDetailType,
            final CChartOfAccounts accounts) {
        final List<Accountdetailtype> rowsToBeAdded = new ArrayList<Accountdetailtype>();
        for (final Accountdetailtype row : accountDetailType)
            if (!coaHasAccountdetailtype(row, accounts))
                rowsToBeAdded.add(row);
        return rowsToBeAdded;
    }

    private boolean coaHasAccountdetailtype(final Accountdetailtype entry, final CChartOfAccounts accounts) {
        for (final CChartOfAccountDetail row : accounts.getChartOfAccountDetails())
            if (row.getDetailTypeId().getId().equals(entry.getId()))
                return true;
        return false;
    }

    private boolean accountDetailTypeContains(final List<Accountdetailtype> list, final Accountdetailtype entry) {
        for (final Accountdetailtype row : list)
            if (row.getId().equals(entry.getId()))
                return true;
        return false;
    }

    @Action(value = "/masters/chartOfAccounts-addNewCoa")
    public String addNewCoa() throws Exception {
        model = new CChartOfAccounts();
        if (parentId != null)
            model.setParentId(parentId);
        final CChartOfAccounts parent = chartOfAccountService.findById(parentId, false);
        model.setType(parent.getType());
        setClassification(parent);
        final Long glCode = findNextGlCode(parent);
        if (parent.getClassification() == null || parent.getClassification() == 0)
            generatedGlcode = "";
        else
            generatedGlcode = parent.getGlcode();
        if (glCode == null) {
            populateGlcode(null);
            newGlcode = model.getGlcode();
        } else {
            newGlcode = String.valueOf(glCode + 1);
            if (model.getClassification().equals(LONG_TWO))
                newGlcode = newGlcode.substring(majorCodeLength, newGlcode.length());
            else if (model.getClassification().equals(3l))
                newGlcode = newGlcode.substring(minorCodeLength, newGlcode.length());
            else if (model.getClassification().equals(LONG_FOUR))
                extractDetailCode();
        }
        return NEW;
    }

    private Long findNextGlCode(final CChartOfAccounts parentCoa) {
        Long glcode = (Long) persistenceService.find("select max(glcode) from CChartOfAccounts where parentId=?",
                parentCoa.getId());
        return glcode;
    }

    void setClassification(final CChartOfAccounts parentCoa) {
        if (parentCoa.getClassification() == null)
            model.setClassification(Constants.LONG_ONE);
        else if (Constants.LONG_ZERO.equals(parentCoa.getClassification()))
            model.setClassification(Constants.LONG_ONE);
        else if (Constants.LONG_ONE.equals(parentCoa.getClassification()))
            model.setClassification(LONG_TWO);
        else if (parentCoa.getClassification().equals(LONG_TWO)) {
            if (parentForDetailedCode.equals("2"))
                model.setClassification(LONG_FOUR);
            else
                model.setClassification(3l);
        }
        else if (parentCoa.getClassification().equals(3l))
            model.setClassification(LONG_FOUR);
    }

    String getAppConfigValueFor(final String module, final String key) {
        return appConfigValuesService.getConfigValuesByModuleAndKey(module, key).get(0).getValue();
    }

    void populateCodeLength() {
        majorCodeLength = Integer.valueOf(getAppConfigValueFor(Constants.EGF, "coa_majorcode_length"));
        minorCodeLength = Integer.valueOf(getAppConfigValueFor(Constants.EGF, "coa_minorcode_length"));
        subMinorCodeLength = Integer.valueOf(getAppConfigValueFor(Constants.EGF, "coa_subminorcode_length"));
        detailedCodeLength = Integer.valueOf(getAppConfigValueFor(Constants.EGF, "coa_detailcode_length"));
    }

    void populateGlcode(Long classification) {
        model.setGlcode(StringUtils.leftPad("", glCodeLengths.get(classification), '0'));
    }

    public String save() throws Exception {
        if (generatedGlcode == null || newGlcode == null) {
            addActionMessage(getText("chartOfAccount.invalid.glcode"));
            return NEW;
        }
        final CChartOfAccounts coa = chartOfAccountService.find("from CChartOfAccounts where glcode=?",
                generatedGlcode.concat(newGlcode));
        if (coa != null) {
            addActionMessage(getText("chartOfAccount.glcode.already.exists"));
            return NEW;
        }
        model.setGlcode(generatedGlcode.concat(newGlcode));
        if ("0".equals(model.getPurposeId()))
            model.setPurposeId(null);
        if (parentId != null) {
            final CChartOfAccounts parent = chartOfAccountService.findById(parentId, false);
            model.setParentId(parentId);
            model.setType(parent.getType());
        }
        setPurposeOnCoa();
        model.setIsActiveForPosting(activeForPosting);
        model.setBudgetCheckReq(budgetCheckRequired);
        model.setFunctionReqd(functionRequired);
        populateAccountDetailType();
        model.setMajorCode(model.getGlcode().substring(0, majorCodeLength));
        chartOfAccountService.persist(model);
        saveCoaDetails(model);
        addActionMessage(getText("chartOfAccount.saved.successfully"));
        clearCache();
        reset();
        return NEW;
    }

    private void reset() {
        activeForPosting = false;
        budgetCheckRequired = false;
        functionRequired = false;
        generatedGlcode = "";
        newGlcode = "";
        model = new CChartOfAccounts();
    }

    public boolean isActiveForPosting() {
        return activeForPosting;
    }

    public boolean budgetCheckReq() {
        if (model != null && model.getBudgetCheckReq()!=null && model.getBudgetCheckReq())
            return true;
        return false;
    }

    public boolean getFunctionReqd() {
        if (model != null && model.getFunctionReqd()!=null && model.getFunctionReqd())
            return true;
        return false;
    }

    public boolean getIsActiveForPosting() {
        if (model != null && model.getIsActiveForPosting()!=null && model.getIsActiveForPosting())
            return true;
        return false;
    }

    @Action(value = "/masters/chartOfAccounts-detailed")
    public String detailed() throws Exception {
        allChartOfAccounts = chartOfAccountService.findAllBy("from CChartOfAccounts where classification=4");
        return "detailed-code";
    }
    @SkipValidation
    @Action(value = "/masters/chartOfAccounts-modifySearch")
    public String modifySearch() throws Exception {
        if (glCode != null) {
            model = chartOfAccountService.find("from CChartOfAccounts where classification=4 and glcode=?",
                    glCode.split("-")[0]);
            if (model == null) {
                addActionMessage(getText("charOfAccount.no.record"));
                return detailed();
            }
            populateAccountDetailTypeList();
            populateCoaRequiredFields();
            populateAccountCodePurpose();
            return Constants.EDIT;
        } else {
            addActionMessage(getText("charOfAccount.no.record"));
            return detailed();
        }
    }
    @SkipValidation
    @Action(value = "/masters/chartOfAccounts-viewSearch")
    public String viewSearch() throws Exception {
        if (glCode != null) {
            model = chartOfAccountService.find("from CChartOfAccounts where classification=4 and glcode=?",
                    glCode.split("-")[0]);
            if (model == null) {
                addActionMessage(getText("charOfAccount.no.record"));
                return detailed();
            }
            coaId = model.getId();
            populateAccountDetailTypeList();
            populateCoaRequiredFields();
            populateAccountCodePurpose();
            return Constants.VIEW;
        } else {
            addActionMessage(getText("charOfAccount.no.record"));
            return detailed();
        }
    }

    @Action(value = "/masters/chartOfAccounts-addNew")
    public String addNew() throws Exception {
        allChartOfAccounts = chartOfAccountService.findAllBy("from CChartOfAccounts where classification=4");
        populateCodeLength();
        model = new CChartOfAccounts();
        model.setClassification(4l);
        return "detailed";
    }
    @Action(value = "/masters/chartOfAccounts-create")
    public String create() throws Exception {
        if (glCode != null) {
            final CChartOfAccounts parent = chartOfAccountService.find("from CChartOfAccounts where glcode=?",
                    glCode.split("-")[0]);
            if (parent == null) {
                addActionMessage(getText("chartOfAccount.no.data"));
                return detailed();
            }
            if (generatedGlcode == null || newGlcode == null) {
                addActionMessage(getText("chartOfAccount.invalid.glcode"));
                return "detailed";
            }
            final CChartOfAccounts coa = chartOfAccountService.find("from CChartOfAccounts where glcode=?",
                    generatedGlcode.concat(newGlcode));
            if (coa != null) {
                addActionMessage(getText("chartOfAccount.glcode.already.exists"));
                return "detailed";
            }
            parentId = parent.getId();
            model.setParentId(parentId);
            model.setBudgetCheckReq(budgetCheckRequired);
            model.setFunctionReqd(functionRequired);
            model.setType(parent.getType());
            setClassification(parent);
            model.setGlcode(generatedGlcode.concat(newGlcode));
            model.setMajorCode(model.getGlcode().substring(0, majorCodeLength));
            setPurposeOnCoa();
            model.setIsActiveForPosting(activeForPosting);
            populateAccountDetailType();
            chartOfAccountService.persist(model);
            saveCoaDetails(model);
            addActionMessage(getText("chartOfAccount.detailed.saved"));
        } else
            addActionMessage(getText("chartOfAccount.no.data"));
        clearCache();
        return detailed();
    }

    public void setGeneratedGlcode(final String generatedGlcode) {
        this.generatedGlcode = generatedGlcode;
    }

    public String getGeneratedGlcode() {
        return generatedGlcode;
    }

    public void setNewGlcode(final String newGlcode) {
        this.newGlcode = newGlcode;
    }

    public String getNewGlcode() {
        return newGlcode;
    }

    @Action(value = "/masters/chartOfAccounts-ajaxNextGlCode")
    public String ajaxNextGlCode() {
        final String parentGlcode = parameters.get("parentGlcode")[0];
        if (parentGlcode != null || !StringUtils.isBlank(parentGlcode)) {
            final CChartOfAccounts coa = chartOfAccountService.find("from CChartOfAccounts where glcode=?", parentGlcode);
            final Long glCode = findNextGlCode(coa);
            if (glCode == null) {
                populateGlcode(coa.getClassification());
                newGlcode = model.getGlcode();
            } else {
                newGlcode = String.valueOf(glCode + 1);
                extractDetailCode();
            }
        }
        return "generated-glcode";
    }

    void extractDetailCode() {
        if (parentForDetailedCode.equals("2"))
            newGlcode = newGlcode.substring(minorCodeLength, newGlcode.length());
        else
            newGlcode = newGlcode.substring(subMinorCodeLength, newGlcode.length());
    }

    public Map<Long, Integer> getGlCodeLengths() {
        return glCodeLengths;
    }

    void clearCache() {
        try {
            chartOfAccounts.getInstance().reLoadAccountData();
        } catch (final TaskFailedException e) {

            LOGGER.error("Error" + e.getMessage(), e);
        }
    }

    public EgfAccountcodePurpose getAccountcodePurpose() {
        return accountcodePurpose;
    }

    public void setAccountcodePurpose(final EgfAccountcodePurpose purposeName) {
        accountcodePurpose = purposeName;
    }

    public List<CChartOfAccounts> getAllChartOfAccounts() {
        return allChartOfAccounts;
    }

    public String getGlCode() {
        return glCode;
    }

    public void setGlCode(final String glCode) {
        this.glCode = glCode;
    }

    public boolean isBudgetCheckRequired() {
        return budgetCheckRequired;
    }

    public boolean isFunctionRequired() {
        return functionRequired;
    }

    public void setBudgetCheckRequired(final boolean budgetCheckReq) {
        budgetCheckRequired = budgetCheckReq;
    }

    public void setFunctionRequired(final boolean functionReqd) {
        functionRequired = functionReqd;
    }

    public Long getCoaId() {
        return coaId;
    }

    public void setCoaId(final Long id) {
        coaId = id;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(final Long id) {
        parentId = id;
    }

    public void setActiveForPosting(final boolean activeForPosting) {
        this.activeForPosting = activeForPosting;
    }

    public List<String> getAccountDetailTypeList() {
        return accountDetailTypeList;
    }

    public void setAccountDetailTypeList(final List<String> accountDetailTypeList) {
        this.accountDetailTypeList = accountDetailTypeList;
    }

    public List<Accountdetailtype> getAccountDetailType() {
        return accountDetailType;
    }

    public void setAccountDetailType(final List<Accountdetailtype> accountDetailType) {
        this.accountDetailType = accountDetailType;
    }

    public void setChartOfAccountService(final PersistenceService<CChartOfAccounts, Long> chartOfAccountService) {
        this.chartOfAccountService = chartOfAccountService;
    }

    public void setModel(CChartOfAccounts model) {
        this.model = model;
    }


}
