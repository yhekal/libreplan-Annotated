/*
 * This file is part of LibrePlan
 *
 * Copyright (C) 2009-2010 Fundación para o Fomento da Calidade Industrial e
 *                         Desenvolvemento Tecnolóxico de Galicia
 * Copyright (C) 2010-2012 Igalia, S.L.
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.libreplan.web.orders;

import static org.libreplan.web.I18nHelper._;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.lang3.Validate;
import org.libreplan.business.advance.entities.AdvanceMeasurement;
import org.libreplan.business.advance.entities.DirectAdvanceAssignment;
import org.libreplan.business.advance.entities.IndirectAdvanceAssignment;
import org.libreplan.business.calendars.daos.IBaseCalendarDAO;
import org.libreplan.business.calendars.entities.BaseCalendar;
import org.libreplan.business.common.IntegrationEntity;
import org.libreplan.business.common.daos.IConfigurationDAO;
import org.libreplan.business.common.entities.Configuration;
import org.libreplan.business.common.entities.EntityNameEnum;
import org.libreplan.business.common.exceptions.InstanceNotFoundException;
import org.libreplan.business.email.entities.EmailNotification;
import org.libreplan.business.externalcompanies.daos.IExternalCompanyDAO;
import org.libreplan.business.externalcompanies.entities.EndDateCommunication;
import org.libreplan.business.externalcompanies.entities.ExternalCompany;
import org.libreplan.business.labels.daos.ILabelDAO;
import org.libreplan.business.labels.entities.Label;
import org.libreplan.business.orders.daos.IOrderDAO;
import org.libreplan.business.orders.daos.IOrderElementDAO;
import org.libreplan.business.orders.entities.HoursGroup;
import org.libreplan.business.orders.entities.Order;
import org.libreplan.business.orders.entities.OrderElement;
import org.libreplan.business.orders.entities.OrderLineGroup;
import org.libreplan.business.orders.entities.OrderStatusEnum;
import org.libreplan.business.planner.entities.PositionConstraintType;
import org.libreplan.business.planner.entities.TaskElement;
import org.libreplan.business.qualityforms.daos.IQualityFormDAO;
import org.libreplan.business.qualityforms.entities.QualityForm;
import org.libreplan.business.requirements.entities.DirectCriterionRequirement;
import org.libreplan.business.resources.daos.ICriterionDAO;
import org.libreplan.business.resources.daos.ICriterionTypeDAO;
import org.libreplan.business.resources.entities.Criterion;
import org.libreplan.business.resources.entities.CriterionType;
import org.libreplan.business.scenarios.IScenarioManager;
import org.libreplan.business.scenarios.daos.IOrderVersionDAO;
import org.libreplan.business.scenarios.daos.IScenarioDAO;
import org.libreplan.business.scenarios.entities.OrderVersion;
import org.libreplan.business.scenarios.entities.Scenario;
import org.libreplan.business.templates.daos.IOrderElementTemplateDAO;
import org.libreplan.business.templates.entities.OrderElementTemplate;
import org.libreplan.business.templates.entities.OrderTemplate;
import org.libreplan.business.users.daos.IOrderAuthorizationDAO;
import org.libreplan.business.users.daos.IUserDAO;
import org.libreplan.business.users.entities.OrderAuthorization;
import org.libreplan.business.users.entities.OrderAuthorizationType;
import org.libreplan.business.users.entities.User;
import org.libreplan.business.users.entities.UserRole;
import org.libreplan.web.calendars.BaseCalendarModel;
import org.libreplan.web.common.IntegrationEntityModel;
import org.libreplan.web.common.concurrentdetection.OnConcurrentModification;
import org.libreplan.web.email.IEmailNotificationModel;
import org.libreplan.web.logs.IIssueLogModel;
import org.libreplan.web.logs.IRiskLogModel;
import org.libreplan.web.orders.files.IOrderFileModel;
import org.libreplan.web.orders.labels.LabelsOnConversation;
import org.libreplan.web.planner.order.ISaveCommand.IBeforeSaveActions;
import org.libreplan.web.planner.order.PlanningStateCreator;
import org.libreplan.web.planner.order.PlanningStateCreator.PlanningState;
import org.libreplan.web.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zkoss.ganttz.IPredicate;
import org.zkoss.zk.ui.Desktop;

/**
 * Model for UI operations related to {@link Order}.
 * <br />
 * @author Óscar González Fernández <ogonzalez@igalia.com>
 * @author Diego Pino García <dpino@igalia.com>
 * @author Jacobo Aragunde Pérez <jaragunde@igalia.com>
 * @author Manuel Rego Casasnovas <rego@igalia.com>
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@OnConcurrentModification(goToPage = "/planner/index.zul;orders_list")
public class OrderModel extends IntegrationEntityModel implements IOrderModel {

    @Autowired
    private ICriterionTypeDAO criterionTypeDAO;

    @Autowired
    private IExternalCompanyDAO externalCompanyDAO;

    private final Map<CriterionType, List<Criterion>> mapCriterions = new HashMap<>();

    private List<ExternalCompany> externalCompanies = new ArrayList<>();

    @Autowired
    private IOrderDAO orderDAO;

    @Autowired
    private PlanningStateCreator planningStateCreator;

    private PlanningState planningState;

    private OrderElementTreeModel orderElementTreeModel;

    @Autowired
    private IOrderElementModel orderElementModel;

    @Autowired
    private ICriterionDAO criterionDAO;

    @Autowired
    private ILabelDAO labelDAO;

    @Autowired
    private IQualityFormDAO qualityFormDAO;

    @Autowired
    private IOrderElementDAO orderElementDAO;

    @Autowired
    private IOrderElementTemplateDAO templateDAO;

    @Autowired
    private IBaseCalendarDAO baseCalendarDAO;

    @Autowired
    private IConfigurationDAO configurationDAO;

    @Autowired
    private IUserDAO userDAO;

    @Autowired
    private IOrderAuthorizationDAO orderAuthorizationDAO;

    @Autowired
    private IScenarioDAO scenarioDAO;

    @Autowired
    private IScenarioManager scenarioManager;

    @Autowired
    private IOrderVersionDAO orderVersionDAO;

    @Autowired
    private IOrderFileModel orderFileModel;

    @Autowired
    private IRiskLogModel riskLogModel;

    @Autowired
    private IIssueLogModel issueLogModel;

    @Autowired
    private IEmailNotificationModel emailNotificationModel;

    private List<Order> orderList = new ArrayList<>();

    @Override
    @Transactional(readOnly = true)
    public List<Label> getLabels() {
        return getLabelsOnConversation().getLabels();
    }

    private LabelsOnConversation labelsOnConversation;

    private LabelsOnConversation getLabelsOnConversation() {
        if ( labelsOnConversation == null ) {
            labelsOnConversation = new LabelsOnConversation(labelDAO);
        }

        return labelsOnConversation;
    }

    private QualityFormsOnConversation qualityFormsOnConversation;

    private QualityFormsOnConversation getQualityFormsOnConversation() {
        if ( qualityFormsOnConversation == null ) {
            qualityFormsOnConversation = new QualityFormsOnConversation(qualityFormDAO);
        }

        return qualityFormsOnConversation;
    }

    @Override
    public List<QualityForm> getQualityForms() {
        return getQualityFormsOnConversation().getQualityForms();
    }

    @Override
    public void addLabel(Label label) {
        getLabelsOnConversation().addLabel(label);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> getOrders() {
        getLabelsOnConversation().reattachLabels();
        List<Order> orders = orderDAO.getOrdersByReadAuthorizationByScenario(
                SecurityUtils.getSessionUserLoginName(), // &line[getSessionUserLoginName]
                scenarioManager.getCurrent());

        initializeOrders(orders);
        return orders;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        getLabelsOnConversation().reattachLabels();
        List<Order> orders = orderDAO.getOrders();

        initializeOrders(orders);
        return orders;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> getOrders(Date startDate, Date endDate,
                                 List<Label> labels, List<Criterion> criteria,
                                 ExternalCompany customer, OrderStatusEnum state, Boolean excludeFinishedProject) {
        getLabelsOnConversation().reattachLabels();
        List<Order> orders = orderDAO
                .getOrdersByReadAuthorizationBetweenDatesByLabelsCriteriaCustomerAndState(
                        SecurityUtils.getSessionUserLoginName(), // &line[getSessionUserLoginName]
                        scenarioManager.getCurrent(), startDate, endDate,
                        labels, criteria, customer, state, excludeFinishedProject);

        initializeOrders(orders);

        return orders;
    }

    private void initializeOrders(List<Order> list) {
        for (Order order : list) {
            orderDAO.reattachUnmodifiedEntity(order);
            if (order.getCustomer() != null) {
                order.getCustomer().getName();
            }
            order.getAdvancePercentage();
            for (Label label : order.getLabels()) {
                label.getName();
            }
            order.getScenarios().size();
        }
        this.orderList = list;
    }

    private void loadCriterions() {
        mapCriterions.clear();
        List<CriterionType> criterionTypes = criterionTypeDAO.getCriterionTypes();
        for (CriterionType criterionType : criterionTypes) {
            List<Criterion> criterions = new ArrayList<>(criterionDAO.findByType(criterionType));

            mapCriterions.put(criterionType, criterions);
        }
    }

    @Override
    public Map<CriterionType, List<Criterion>> getMapCriterions(){
        final Map<CriterionType, List<Criterion>> result = new HashMap<>();
        result.putAll(mapCriterions);

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public void initEdit(Order orderToEdit, Desktop desktop) {
        Validate.notNull(orderToEdit);
        loadNeededDataForConversation();

        this.planningState =
                planningStateCreator.retrieveOrCreate(desktop, orderToEdit, planningState1 -> planningState1.reattach());

        Order order = this.planningState.getOrder();
        this.orderElementTreeModel = new OrderElementTreeModel(order);
        forceLoadAdvanceAssignmentsAndMeasurements(order);
        forceLoadCriterionRequirements(order);
        forceLoadCalendar(this.getCalendar());
        forceLoadCustomer(order.getCustomer());
        forceLoadDeliveringDates(order);
        forceLoadLabels(order);
        forceLoadMaterialAssignments(order);
        forceLoadTaskQualityForms(order);
        forceLoadEndDateCommunicationToCustomer(order);
        initOldCodes();
    }

    private void forceLoadEndDateCommunicationToCustomer(Order order) {
        if ( order != null ) {
            order.getEndDateCommunicationToCustomer().size();
        }
    }

    private void forceLoadDeliveringDates(Order order){
        order.getDeliveringDates().size();
    }

    private void forceLoadLabels(OrderElement orderElement) {
        orderElement.getLabels().size();
        for (OrderElement each : orderElement.getChildren()) {
            forceLoadLabels(each);
        }
    }

    private void forceLoadMaterialAssignments(OrderElement orderElement) {
        orderElement.getMaterialAssignments().size();
        for (OrderElement each : orderElement.getChildren()) {
            forceLoadMaterialAssignments(each);
        }
    }

    private void forceLoadTaskQualityForms(OrderElement orderElement) {
        orderElement.getTaskQualityForms().size();
        for (OrderElement each : orderElement.getChildren()) {
            forceLoadTaskQualityForms(each);
        }
    }

    private void forceLoadCustomer(ExternalCompany customer) {
        if (customer != null) {
            customer.getName();
        }
    }

    private void loadNeededDataForConversation() {
        loadCriterions();
        initializeCacheLabels();
        getQualityFormsOnConversation().initialize();
        loadExternalCompaniesAreClient();
    }

    private void reattachNeededDataForConversation() {
        reattachCriterions();
        reattachLabels();
        getQualityFormsOnConversation().reattach();
    }

    private void initializeCacheLabels() {
        getLabelsOnConversation().initializeLabels();
    }

    private static void forceLoadCriterionRequirements(OrderElement orderElement) {
        orderElement.getHoursGroups().size();

        for (HoursGroup hoursGroup : orderElement.getHoursGroups()) {
            attachDirectCriterionRequirement(hoursGroup.getDirectCriterionRequirement());
        }
        attachDirectCriterionRequirement(orderElement.getDirectCriterionRequirement());

        for (OrderElement child : orderElement.getChildren()) {
            forceLoadCriterionRequirements(child);
        }
    }

    private static void attachDirectCriterionRequirement(Set<DirectCriterionRequirement> requirements) {
        for (DirectCriterionRequirement requirement : requirements) {
            requirement.getChildren().size();
            requirement.getCriterion().getName();
            requirement.getCriterion().getType().getName();
        }
    }

    private void forceLoadAdvanceAssignmentsAndMeasurements(OrderElement orderElement) {
        for (DirectAdvanceAssignment directAdvanceAssignment : orderElement.getDirectAdvanceAssignments()) {
            directAdvanceAssignment.getAdvanceType().getUnitName();

            for (AdvanceMeasurement advanceMeasurement : directAdvanceAssignment.getAdvanceMeasurements()) {
                advanceMeasurement.getValue();
            }
        }

        if (orderElement instanceof OrderLineGroup) {
            for (IndirectAdvanceAssignment indirectAdvanceAssignment : orderElement.getIndirectAdvanceAssignments()) {
                indirectAdvanceAssignment.getAdvanceType().getUnitName();
            }

            for (OrderElement child : orderElement.getChildren()) {
                forceLoadAdvanceAssignmentsAndMeasurements(child);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void prepareForCreate(Desktop desktop) {
        loadNeededDataForConversation();
        this.planningState = planningStateCreator.createOn(desktop, Order.create());
        planningState.getOrder().setInitDate(new Date());

        initializeOrder();
        initializeCode();
        initializeCalendar();
    }

    private void initializeOrder() {
        this.orderElementTreeModel = new OrderElementTreeModel(planningState.getOrder());
    }

    private void initializeCode() {
        setDefaultCode();
        planningState.getOrder().setCodeAutogenerated(true);
    }

    private void initializeCalendar() {
        this.planningState.getOrder().setCalendar(getDefaultCalendar());
    }

    @Override
    @Transactional(readOnly = true)
    public void prepareCreationFrom(OrderTemplate template, Desktop desktop) {
        loadNeededDataForConversation();
        Order newOrder = createOrderFrom((OrderTemplate) templateDAO.findExistingEntity(template.getId()));

        newOrder.setCode(getOrder().getCode());
        newOrder.setCodeAutogenerated(true);
        newOrder.setName(getOrder().getName());
        newOrder.setCustomer(getOrder().getCustomer());
        newOrder.setCalendar(getCalendar());
        newOrder.setInitDate(getOrder().getInitDate());
        newOrder.setDescription(getOrder().getDescription());
        
        if ( getOrder().getDeadline() != null ) {
            newOrder.setDeadline(getOrder().getDeadline());
        }

        planningState = planningStateCreator.createOn(desktop, newOrder);
        forceLoadAdvanceAssignmentsAndMeasurements(planningState.getOrder());
        initializeOrder();
    }

    private Order createOrderFrom(OrderTemplate template) {
        return template.createOrder(scenarioManager.getCurrent());
    }

    private OrderElement createOrderElementFrom(OrderLineGroup parent, OrderElementTemplate template) {
        Validate.notNull(parent);

        return template.createElement(parent);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderElement createFrom(OrderLineGroup parent, OrderElementTemplate template) {
        reattachNeededDataForConversation();
        OrderElement result = createOrderElementFrom(parent, templateDAO.findExistingEntity(template.getId()));
        if ( isCodeAutogenerated() ) {
            setAllCodeToNull(result);
        }
        forceLoadAdvanceAssignmentsAndMeasurements(result);

        return result;
    }

    private void setAllCodeToNull(OrderElement result) {
        result.setCode(null);

        for (OrderElement each : result.getChildren()) {
            setAllCodeToNull(each);
        }
    }

    @Override
    public void save() {
        save(false);
    }

    @Override
    public void save(boolean showSaveMessage) {
        IBeforeSaveActions beforeSaveActions = () -> {
            reattachCalendar();
            reattachCriterions();
        };
        if ( showSaveMessage ) {
            this.planningState.getSaveCommand().save(beforeSaveActions);
        } else {
            this.planningState.getSaveCommand().save(beforeSaveActions, null);
        }
    }

    private void reattachCalendar() {
        if  ( planningState.getOrder().getCalendar() == null ) {
            return;
        }
        BaseCalendar calendar = planningState.getOrder().getCalendar();
        baseCalendarDAO.reattachUnmodifiedEntity(calendar);
    }

    private void reattachCriterions() {
        for (List<Criterion> list : mapCriterions.values()) {
            for (Criterion criterion : list) {
                criterionDAO.reattachUnmodifiedEntity(criterion);
            }
        }
    }

    @Override
    public Order getOrder() {
        return planningState.getOrder();
    }

    @Override
    @Transactional
    public void remove(Order detachedOrder) {
        Order order = orderDAO.findExistingEntity(detachedOrder.getId());

        removeNotifications(order);

        removeFiles(order);

        removeLogs(order);

        removeVersions(order);

        if ( order.hasNoVersions() ) {
            removeOrderFromDB(order);
        }
    }

    private void removeLogs(Order order) {
        riskLogModel.getByParent(order).forEach(riskLog -> riskLogModel.remove(riskLog));
        issueLogModel.getByParent(order).forEach(issueLog -> issueLogModel.remove(issueLog));
    }

    private void removeFiles(Order order) {
        orderFileModel.findByParent(order).forEach(orderFile -> orderFileModel.delete(orderFile));
    }

    private void removeNotifications(Order order) {

        for ( Scenario scenario: currentAndDerivedScenarios()) {
            order.useSchedulingDataFor(scenario.getOrderVersion(order));

            TaskElement taskElement = order.getTaskElement();
            if ( taskElement != null) {
                emailNotificationModel.deleteByProject(taskElement);
            }
        }
    }

    private void removeVersions(Order order) {
        Map<Long, OrderVersion> versionsRemovedById = new HashMap<>();
        List<Scenario> currentAndDerived = currentAndDerivedScenarios();

        for (Scenario each : currentAndDerived) {
            OrderVersion versionRemoved = order.disassociateFrom(each);
            if ( versionRemoved != null ) {
                versionsRemovedById.put(versionRemoved.getId(), versionRemoved);
            }
        }

        for (OrderVersion each : versionsRemovedById.values()) {
            if ( !order.isVersionUsed(each) ) {
                removeOrderVersionAt(each, currentAndDerived);
                removeOrderVersionFromDB(each);
            }
        }
    }

    private void removeOrderVersionAt(OrderVersion orderVersion, Collection<? extends Scenario> currentAndDerived) {
        for (Scenario each : currentAndDerived) {
            each.removeVersion(orderVersion);
        }
    }

    private List<Scenario> currentAndDerivedScenarios() {
        List<Scenario> scenariosToBeDisassociatedFrom = new ArrayList<>();
        Scenario currentScenario = scenarioManager.getCurrent();
        scenariosToBeDisassociatedFrom.add(currentScenario);
        scenariosToBeDisassociatedFrom.addAll(scenarioDAO.getDerivedScenarios(currentScenario));

        return scenariosToBeDisassociatedFrom;
    }

    private void removeOrderVersionFromDB(OrderVersion currentOrderVersion) {
        try {
            orderVersionDAO.remove(currentOrderVersion.getId());
        } catch (InstanceNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private void removeOrderFromDB(Order order) {
        try {
            orderDAO.remove(order.getId());
        } catch (InstanceNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public OrderElementTreeModel getOrderElementTreeModel() {
        return orderElementTreeModel;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderElementTreeModel getOrderElementsFilteredByPredicate(IPredicate predicate) {
        // Iterate through orderElements from order
        List<OrderElement> orderElements = new ArrayList<>();

        for (OrderElement orderElement : planningState.getOrder().getAllOrderElements()) {
            if (!orderElement.isNewObject()) {
                reattachOrderElement(orderElement);
            }

            // Accepts predicate, add it to list of orderElements
            if (predicate.accepts(orderElement)) {
                orderElements.add(orderElement);
            }
        }
        // Return list of filtered elements
        return new OrderElementTreeModel(planningState.getOrder(), orderElements);
    }

    private void reattachLabels() {
        getLabelsOnConversation().reattachLabels();
    }

    private void reattachOrderElement(OrderElement orderElement) {
        orderElementDAO.reattach(orderElement);
    }

    @Override
    @Transactional(readOnly = true)
    public IOrderElementModel getOrderElementModel(OrderElement orderElement) {
        reattachCriterions();
        orderElementModel.setCurrent(orderElement, this);

        return orderElementModel;
    }

    @Override
    public List<Criterion> getCriterionsFor(CriterionType criterionType) {
        return mapCriterions.get(criterionType);
    }

    @Override
    public void setPlanningState(PlanningState planningState) {
        this.planningState = planningState;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BaseCalendar> getBaseCalendars() {
        List<BaseCalendar> result = baseCalendarDAO.getBaseCalendars();

        for (BaseCalendar each : result) {
            BaseCalendarModel.forceLoadBaseCalendar(each);
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public BaseCalendar getDefaultCalendar() {
        Configuration configuration = configurationDAO.getConfiguration();
        if (configuration == null) {
            return null;
        }
        BaseCalendar defaultCalendar = configuration.getDefaultCalendar();
        forceLoadCalendar(defaultCalendar);

        return defaultCalendar;
    }

    private void forceLoadCalendar(BaseCalendar calendar) {
        BaseCalendarModel.forceLoadBaseCalendar(calendar);
    }

    @Override
    public BaseCalendar getCalendar() {
        if (planningState == null) {
            return null;
        }
        return planningState.getOrder().getCalendar();
    }

    @Override
    public void setCalendar(BaseCalendar calendar) {
        if (planningState != null) {
            planningState.getOrder().setCalendar(calendar);
        }
    }

    @Override
    public boolean isCodeAutogenerated() {
        if (planningState == null) {
            return false;
        }
        return planningState.getOrder().isCodeAutogenerated();
    }

    @Override
    public List<ExternalCompany> getExternalCompaniesAreClient() {
        return externalCompanies;
    }

    private void loadExternalCompaniesAreClient() {
        this.externalCompanies = externalCompanyDAO.getExternalCompaniesAreClient();
        Collections.sort(this.externalCompanies);
    }

    @Override
    public void setExternalCompany(ExternalCompany externalCompany) {
        if (this.getOrder() != null) {
            Order order = getOrder();
            order.setCustomer(externalCompany);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public String gettooltipText(Order order) {
        orderDAO.reattachUnmodifiedEntity(order);
        StringBuilder result = new StringBuilder();
        result.append(_("Progress") + ": ").append(getEstimatedAdvance(order)).append("% , ");
        result.append(_("Hours invested")).append(": ").append(getHoursAdvancePercentage(order)).append("%\n");

        if (!getDescription(order).equals("")) {
            result.append(" , " + _("Description") + ": " + getDescription(order) + "\n");
        }

        String labels = buildLabelsText(order);
        if (!labels.equals("")) {
            result.append(" , " + _("Labels") + ": " + labels);
        }

        return result.toString();
    }

    private String getDescription(Order order) {
        if (order.getDescription() != null) {
            return order.getDescription();
        }

        return "";
    }

    private BigDecimal getEstimatedAdvance(Order order) {
        return order.getAdvancePercentage().multiply(new BigDecimal(100));
    }

    private BigDecimal getHoursAdvancePercentage(Order order) {
        BigDecimal result;
        if (order != null) {
            result = orderElementDAO.getHoursAdvancePercentage(order);
        } else {
            result = new BigDecimal(0);
        }

        return result.multiply(new BigDecimal(100));
    }

    private String buildLabelsText(Order order) {
        StringBuilder result = new StringBuilder();
        Set<Label> labels = order.getLabels();

        if (!labels.isEmpty()) {
            for (Label label : labels) {
                result.append(label.getName()).append(",");
            }
            result.delete(result.length() - 1, result.length());
        }

        return result.toString();
    }

    /**
     * Operation to filter the order list.
     */

    @Override
    @Transactional(readOnly = true)
    public List<Order> getFilterOrders(OrderPredicate predicate) {
        reattachLabels();
        List<Order> filterOrderList = new ArrayList<>();

        for (Order order : orderList) {
            orderDAO.reattach(order);
            if (predicate.accepts(order)) {
                filterOrderList.add(order);
            }
        }

        return filterOrderList;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean userCanRead(Order order, String loginName) {
        if (SecurityUtils.isSuperuserOrUserInRoles(
                UserRole.ROLE_READ_ALL_PROJECTS,
                UserRole.ROLE_EDIT_ALL_PROJECTS)) {
            return true;
        }
        if (order.isNewObject()
                & SecurityUtils
                .isSuperuserOrUserInRoles(UserRole.ROLE_CREATE_PROJECTS)) {
            return true;
        }
        try {
            User user = userDAO.findByLoginName(loginName);
            for(OrderAuthorization authorization :
                    orderAuthorizationDAO.listByOrderUserAndItsProfiles(order, user)) {
                if(authorization.getAuthorizationType() ==
                        OrderAuthorizationType.READ_AUTHORIZATION ||
                        authorization.getAuthorizationType() ==
                                OrderAuthorizationType.WRITE_AUTHORIZATION) {
                    return true;
                }
            }
        }
        catch(InstanceNotFoundException e) {
            // This case shouldn't happen, because it would mean that there isn't a logged user
            //anyway, if it happenned we don't allow the user to pass
        }
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean userCanWrite(Order order) {
        return SecurityUtils.loggedUserCanWrite(order);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAlreadyInUse(OrderElement orderElement) {
        return orderElementDAO.isAlreadyInUseThisOrAnyOfItsChildren(orderElement);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAlreadyInUseAndIsOnlyInCurrentScenario(Order order) {
        return isAlreadyInUse(order) && (order.getScenarios().size() == 1);
    }

    @Override
    @Transactional(readOnly = true)
    public void useSchedulingDataForCurrentScenario(Order order) {
        orderDAO.reattach(order);
        order.useSchedulingDataFor(scenarioManager.getCurrent());
    }

    @Override
    public EntityNameEnum getEntityName() {
        return EntityNameEnum.ORDER;
    }

    @Override
    public Set<IntegrationEntity> getChildren() {
        Set<IntegrationEntity> children = new HashSet<>();
        if (planningState != null) {
            children.addAll(planningState.getOrder().getOrderElements());
        }

        return children;
    }

    @Override
    public IntegrationEntity getCurrentEntity() {
        return this.planningState.getOrder();
    }

    @Override
    public PlanningState getPlanningState() {
        return planningState;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasImputedExpenseSheetsThisOrAnyOfItsChildren(OrderElement order) {
        if (order.isNewObject()) {
            return false;
        }
        try {
            return orderElementDAO.hasImputedExpenseSheetThisOrAnyOfItsChildren(order.getId());
        } catch (InstanceNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void removeAskedEndDate(EndDateCommunication endDate) {
        Order order = getOrder();
        if (getOrder() != null && endDate != null) {
            order.removeAskedEndDate(endDate);
        }
    }

    @Override
    public SortedSet<EndDateCommunication> getEndDates() {
        Order order = getOrder();
        if (getOrder() != null) {
            return order.getEndDateCommunicationToCustomer();
        }

        return new TreeSet<>();
    }

    @Override
    public void addAskedEndDate(Date value) {
        if (getOrder() != null) {
            Order order = getOrder();

            EndDateCommunication askedEndDate = EndDateCommunication.create(new Date(), value, null);
            order.addAskedEndDate(askedEndDate);
        }
    }

    @Override
    public boolean alreadyExistsRepeatedEndDate(Date value) {
        if (getOrder() != null) {
            Order order = getOrder();
            if (order.getEndDateCommunicationToCustomer().isEmpty()) {
                return false;
            }

            EndDateCommunication endDateCommunicationToCustomer = order.getEndDateCommunicationToCustomer().first();
            Date currentEndDate = endDateCommunicationToCustomer.getEndDate();
            return (currentEndDate.compareTo(value) == 0);
        }

        return false;
    }

    public boolean isAnyTaskWithConstraint(PositionConstraintType type) {
        return !((planningState == null) || (planningState.getRootTask() == null)) &&
                planningState.getRootTask().isAnyTaskWithConstraint(type);

    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOnlyChildAndParentAlreadyInUseByHoursOrExpenses(OrderElement orderElement) {
        try {
            OrderLineGroup parent = orderElement.getParent();
            if (!parent.isOrder() && !parent.isNewObject() && parent.getChildren().size() == 1) {
                if (orderElementDAO.isAlreadyInUse(parent)) {
                    return true;
                }
                if (orderElementDAO.hasImputedExpenseSheet(parent.getId())) {
                    return true;
                }
            }
            return false;
        } catch (InstanceNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public User getUser() {
        User user;
        try {
            user = this.userDAO.findByLoginName(SecurityUtils
                    .getSessionUserLoginName()); // &line[getSessionUserLoginName]
        } catch (InstanceNotFoundException e) {
            throw new RuntimeException(e);
        }
        // Attach filter bandbox elements
        if (user.getProjectsFilterLabel() != null) {
            user.getProjectsFilterLabel().getFinderPattern();
        }

        return user;
    }

}
