/*
 * This file is part of LibrePlan
 *
 * Copyright (C) 2009-2010 Fundación para o Fomento da Calidade Industrial e
 *                         Desenvolvemento Tecnolóxico de Galicia
 * Copyright (C) 2010-2011 Igalia, S.L.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.libreplan.business.test.orders.entities;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.libreplan.business.BusinessGlobalNames.BUSINESS_SPRING_CONFIG_FILE;
import static org.libreplan.business.test.BusinessGlobalNames.BUSINESS_SPRING_CONFIG_TEST_FILE;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.UUID;

import javax.annotation.Resource;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;

import org.apache.commons.lang3.Validate;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.libreplan.business.IDataBootstrap;
import org.libreplan.business.advance.bootstrap.PredefinedAdvancedTypes;
import org.libreplan.business.advance.entities.AdvanceMeasurement;
import org.libreplan.business.advance.entities.AdvanceMeasurementComparator;
import org.libreplan.business.advance.entities.AdvanceType;
import org.libreplan.business.advance.entities.DirectAdvanceAssignment;
import org.libreplan.business.advance.entities.IndirectAdvanceAssignment;
import org.libreplan.business.advance.exceptions.DuplicateAdvanceAssignmentForOrderElementException;
import org.libreplan.business.advance.exceptions.DuplicateValueTrueReportGlobalAdvanceException;
import org.libreplan.business.common.exceptions.ValidationException;
import org.libreplan.business.orders.entities.Order;
import org.libreplan.business.orders.entities.OrderElement;
import org.libreplan.business.orders.entities.OrderLine;
import org.libreplan.business.orders.entities.OrderLineGroup;
import org.libreplan.business.requirements.entities.CriterionRequirement;
import org.libreplan.business.requirements.entities.DirectCriterionRequirement;
import org.libreplan.business.requirements.entities.IndirectCriterionRequirement;
import org.libreplan.business.resources.entities.Criterion;
import org.libreplan.business.resources.entities.CriterionType;
import org.libreplan.business.resources.entities.ResourceEnum;
import org.libreplan.business.scenarios.entities.OrderVersion;
import org.libreplan.business.test.planner.entities.TaskTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests for {@link OrderElement}.
 * <br />
 * @author Manuel Rego Casasnovas <mrego@igalia.com>
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { BUSINESS_SPRING_CONFIG_FILE, BUSINESS_SPRING_CONFIG_TEST_FILE })
public class OrderElementTest {

    @Resource
    private IDataBootstrap defaultAdvanceTypesBootstrapListener;

    private static OrderVersion mockedOrderVersion = TaskTest.mockOrderVersion();

    @Before
    public void loadRequiredData() {
        defaultAdvanceTypesBootstrapListener.loadRequiredData();
    }

    private Matcher<BigDecimal> sameValueAs(final BigDecimal value) {
        return new BaseMatcher<BigDecimal>() {

            @Override
            public boolean matches(Object value) {
                if (value instanceof BigDecimal) {
                    BigDecimal other = (BigDecimal) value;
                    return ((BigDecimal) value).compareTo(other) == 0;
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("must have the same value as " + value);
            }
        };
    }

    private static class Division {

        private final MathContext mathContext;

        private Division(MathContext mathContext) {
            Validate.notNull(mathContext);
            this.mathContext = mathContext;
        }

        public BigDecimal divide(int dividend, int divisor) {
            return new BigDecimal(dividend).divide(new BigDecimal(divisor),
                    mathContext);
        }
    }

    private Division division(int numberOfDecimals) {
        return new Division(new MathContext(numberOfDecimals, RoundingMode.HALF_UP));
    }

    private Division division = division(4);


    private Validator orderElementValidator = Validation.buildDefaultValidatorFactory().getValidator(); // &line[getValidator]

    private static OrderLine givenOrderLine(String name, String code, Integer hours) {
        OrderLine orderLine = OrderLine.createOrderLineWithUnfixedPercentage(hours);
        orderLine.setName(name);
        orderLine.setCode(code);
        orderLine.getHoursGroups().get(0).setCode("hours-group-" + UUID.randomUUID());

        return orderLine;
    }

    private static OrderLineGroup givenOrderLineGroupWithOneOrderLine(OrderVersion orderVersion, Integer hours) {
        OrderLineGroup orderLineGroup = OrderLineGroup.create();
        orderLineGroup.setName("OrderLineGroup1");
        orderLineGroup.setCode("1");
        orderLineGroup.useSchedulingDataFor(orderVersion);
        OrderLine orderLine = givenOrderLine("OrderLine1", "1.1", hours);
        orderLineGroup.add(orderLine);

        return orderLineGroup;
    }

    public static OrderLineGroup givenOrderLineGroupWithTwoOrderLines(Integer hours1, Integer hours2) {
        return givenOrderLineGroupWithTwoOrderLines(mockedOrderVersion, hours1, hours2);
    }

    public static OrderLineGroup givenOrderLineGroupWithTwoOrderLines(
            OrderVersion orderVersion, Integer hours1, Integer hours2) {

        OrderLineGroup orderLineGroup = givenOrderLineGroupWithOneOrderLine(orderVersion, hours1);

        OrderLine orderLine = givenOrderLine("OrderLine2", "1.2", hours2);

        orderLineGroup.add(orderLine);

        return orderLineGroup;
    }

    private static DirectAdvanceAssignment givenAdvanceAssignment(BigDecimal maxValue, AdvanceType advanceType) {
        DirectAdvanceAssignment advanceAssignment = DirectAdvanceAssignment.create();
        advanceAssignment.setMaxValue(maxValue);
        advanceAssignment.setAdvanceType(advanceType);
        advanceAssignment.setReportGlobalAdvance(false);

        return advanceAssignment;
    }

    public static void addAdvanceAssignmentWithoutMeasurement(
            OrderElement orderElement, AdvanceType advanceType, BigDecimal maxValue, boolean reportGlobalAdvance)

            throws DuplicateValueTrueReportGlobalAdvanceException, DuplicateAdvanceAssignmentForOrderElementException {

        DirectAdvanceAssignment advanceAssignment = givenAdvanceAssignment(maxValue, advanceType);
        advanceAssignment.setReportGlobalAdvance(reportGlobalAdvance);
        orderElement.addAdvanceAssignment(advanceAssignment);
    }

    public static void addAdvanceAssignmentWithMeasurement(OrderElement orderElement,
                                                           AdvanceType advanceType,
                                                           BigDecimal maxValue,
                                                           BigDecimal currentValue,
                                                           boolean reportGlobalAdvance)
            throws DuplicateValueTrueReportGlobalAdvanceException, DuplicateAdvanceAssignmentForOrderElementException {

        addAdvanceAssignmentWithMeasurement(orderElement, advanceType, maxValue, currentValue, reportGlobalAdvance, new LocalDate());
    }

    public static void addAdvanceAssignmentWithMeasurement(OrderElement orderElement, AdvanceType advanceType,
                                                           BigDecimal maxValue, BigDecimal currentValue,
                                                           boolean reportGlobalAdvance, LocalDate date)

            throws DuplicateValueTrueReportGlobalAdvanceException, DuplicateAdvanceAssignmentForOrderElementException {

        AdvanceMeasurement advanceMeasurement = AdvanceMeasurement.create();
        advanceMeasurement.setDate(date);
        advanceMeasurement.setValue(currentValue);

        DirectAdvanceAssignment advanceAssignment = givenAdvanceAssignment(maxValue, advanceType);
        advanceAssignment.setReportGlobalAdvance(reportGlobalAdvance);

        if (reportGlobalAdvance) {
            if (orderElement.getReportGlobalAdvanceAssignment() != null) {
                orderElement.removeReportGlobalAdvanceAssignment();
            }
        }
        orderElement.addAdvanceAssignment(advanceAssignment);
        advanceAssignment.addAdvanceMeasurements(advanceMeasurement);
        advanceMeasurement.setAdvanceAssignment(advanceAssignment);
    }

    private static AdvanceType givenAdvanceType(String name) {
        BigDecimal value = new BigDecimal(5000).setScale(2);
        BigDecimal precision = new BigDecimal(10).setScale(2);
        return AdvanceType.create(name, value, true, precision, true, false);
    }

    @Test
    @Transactional
    public void checkValidPropagation()throws ValidationException{
        OrderElement orderElement = givenOrderLineGroupWithTwoOrderLines(1000, 2000);
        Set<ConstraintViolation<OrderElement>> invalidValues = orderElementValidator.validate(orderElement);
        assertTrue(invalidValues.isEmpty());

        CriterionType type = CriterionType.create("", "");
        type.setResource(ResourceEnum.WORKER);
        Criterion criterion = Criterion.create(type);
        CriterionRequirement requirement = DirectCriterionRequirement.create(criterion);
        requirement.setOrderElement(orderElement);
        orderElement.addDirectCriterionRequirement(requirement);

        invalidValues = orderElementValidator.validate(orderElement);
        assertTrue(invalidValues.isEmpty());
    }

    @Test
    @Transactional
    public void checkAdvancePercentageEmptyOrderLine() {
        OrderLine orderLine = givenOrderLine("name", "code", 1000);
        assertThat(orderLine.getAdvancePercentage(), equalTo(BigDecimal.ZERO));
    }

    @Test
    @Transactional
    public void checkAdvancePercentageOrderLineWithAdvanceAssignmentWithoutMeasurement()
            throws DuplicateValueTrueReportGlobalAdvanceException, DuplicateAdvanceAssignmentForOrderElementException {

        OrderLine orderLine = givenOrderLine("name", "code", 1000);

        DirectAdvanceAssignment advanceAssignment =
                givenAdvanceAssignment(new BigDecimal(5000), PredefinedAdvancedTypes.UNITS.getType());

        orderLine.addAdvanceAssignment(advanceAssignment);

        assertThat(orderLine.getAdvancePercentage(), equalTo(BigDecimal.ZERO));
    }

    @Test
    @Transactional
    public void checkAdvancePercentageOrderLineWithTwoAssignments1()
            throws DuplicateValueTrueReportGlobalAdvanceException, DuplicateAdvanceAssignmentForOrderElementException {

        OrderLine orderLine = givenOrderLine("name", "code", 1000);

        addAdvanceAssignmentWithMeasurement(
                orderLine, givenAdvanceType("test1"), new BigDecimal(2000), new BigDecimal(200), true);

        addAdvanceAssignmentWithMeasurement(
                orderLine, givenAdvanceType("test2"), new BigDecimal(1000), new BigDecimal(600), false);

        assertThat(orderLine.getAdvancePercentage(), sameValueAs(division.divide(10, 4)));
    }

    @Test
    @Transactional
    public void checkAdvancePercentageOrderLineWithFutureAdvanceMeasurement()
            throws DuplicateValueTrueReportGlobalAdvanceException, DuplicateAdvanceAssignmentForOrderElementException {

        OrderLine orderLine = givenOrderLine("name", "code", 1000);

        LocalDate future = new LocalDate().plusWeeks(1);

        addAdvanceAssignmentWithMeasurement(
                orderLine, givenAdvanceType("test1"), new BigDecimal(2000), new BigDecimal(200), true, future);

        assertThat(orderLine.getAdvancePercentage(), sameValueAs(division.divide(4, 100)));
    }

    @Test
    @Transactional
    public void checkAdvancePercentageOrderLineWithTwoAssignments3()
            throws DuplicateValueTrueReportGlobalAdvanceException, DuplicateAdvanceAssignmentForOrderElementException {

        OrderLine orderLine = givenOrderLine("name", "code", 1000);

        addAdvanceAssignmentWithMeasurement(
                orderLine, PredefinedAdvancedTypes.UNITS.getType(), new BigDecimal(2000), new BigDecimal(200), false);

        addAdvanceAssignmentWithMeasurement(
                orderLine, PredefinedAdvancedTypes.PERCENTAGE.getType(), new BigDecimal(1000), new BigDecimal(600), true);

        assertThat(orderLine.getAdvancePercentage(), sameValueAs(division.divide(60, 100)));
    }

    @Test
    @Transactional
    public void checkAdvancePercentageOrderLineWithThreeAssignments()
            throws DuplicateValueTrueReportGlobalAdvanceException, DuplicateAdvanceAssignmentForOrderElementException {

        OrderLine orderLine = givenOrderLine("name", "code", 1000);

        addAdvanceAssignmentWithMeasurement(
                orderLine, givenAdvanceType("test1"), new BigDecimal(2000), new BigDecimal(200), false);

        addAdvanceAssignmentWithMeasurement(
                orderLine, givenAdvanceType("test3"), new BigDecimal(4000), new BigDecimal(800), true);

        addAdvanceAssignmentWithMeasurement(
                orderLine, givenAdvanceType("test2"), new BigDecimal(1000), new BigDecimal(600), false);

        assertThat(orderLine.getAdvancePercentage(), sameValueAs(division.divide(20, 100)));
    }

    @Test
    @Transactional
    public void checkAdvancePercentageOrderGroupLine1()
            throws DuplicateValueTrueReportGlobalAdvanceException, DuplicateAdvanceAssignmentForOrderElementException {

        OrderElement orderElement = givenOrderLineGroupWithTwoOrderLines(1000, 2000);

        List<OrderElement> children = orderElement.getChildren();

        AdvanceType advanceType1 =
                AdvanceType.create("test1", new BigDecimal(10000), true, new BigDecimal(1), true, false);

        addAdvanceAssignmentWithMeasurement(
                children.get(0), advanceType1, new BigDecimal(1000), new BigDecimal(400), true);

        AdvanceType advanceType2 =
                AdvanceType.create("test2", new BigDecimal(10000), true, new BigDecimal(1), true, false);

        addAdvanceAssignmentWithMeasurement(
                children.get(1), advanceType2, new BigDecimal(2000), new BigDecimal(200), true);

        Set<IndirectAdvanceAssignment> indirectAdvanceAssignments = orderElement.getIndirectAdvanceAssignments();

        for (IndirectAdvanceAssignment indirectAdvanceAssignment : indirectAdvanceAssignments) {
            if (indirectAdvanceAssignment.getAdvanceType().getUnitName().equals("test1")) {
                indirectAdvanceAssignment.setReportGlobalAdvance(true);
            } else {
                indirectAdvanceAssignment.setReportGlobalAdvance(false);
            }
        }
        assertThat(orderElement.getAdvancePercentage(), sameValueAs(division.divide(40, 100)));
    }

    @Test
    @Transactional
    public void checkAdvancePercentageOrderGroupLine2()
            throws DuplicateValueTrueReportGlobalAdvanceException, DuplicateAdvanceAssignmentForOrderElementException {

        OrderElement orderElement = givenOrderLineGroupWithTwoOrderLines(1000, 2000);

        List<OrderElement> children = orderElement.getChildren();

        AdvanceType advanceType1 = AdvanceType.create("test1", new BigDecimal(10000), true, new BigDecimal(1), true, false);
        addAdvanceAssignmentWithMeasurement(children.get(0), advanceType1, new BigDecimal(1000), new BigDecimal(400), true);

        AdvanceType advanceType2 = AdvanceType.create("test2", new BigDecimal(10000), true, new BigDecimal(1), true, false);
        addAdvanceAssignmentWithMeasurement(children.get(1), advanceType2, new BigDecimal(2000), new BigDecimal(200), true);

        Set<IndirectAdvanceAssignment> indirectAdvanceAssignments = orderElement.getIndirectAdvanceAssignments();
        for (IndirectAdvanceAssignment indirectAdvanceAssignment : indirectAdvanceAssignments) {
            if (indirectAdvanceAssignment.getAdvanceType().getUnitName().equals("test2")) {
                indirectAdvanceAssignment.setReportGlobalAdvance(true);
            } else {
                indirectAdvanceAssignment.setReportGlobalAdvance(false);
            }
        }
        assertThat(orderElement.getAdvancePercentage(), sameValueAs(division.divide(10, 100)));
    }

    @Test
    @Transactional
    public void checkAdvancePercentageOrderGroupLine3()
            throws DuplicateValueTrueReportGlobalAdvanceException, DuplicateAdvanceAssignmentForOrderElementException {

        OrderElement orderElement = givenOrderLineGroupWithTwoOrderLines(1000, 2000);

        List<OrderElement> children = orderElement.getChildren();

        AdvanceType advanceType1 = AdvanceType.create("test1", new BigDecimal(10000), true, new BigDecimal(1), true, false);
        addAdvanceAssignmentWithMeasurement(children.get(0), advanceType1, new BigDecimal(1000), new BigDecimal(400), true);

        AdvanceType advanceType2 = AdvanceType.create("test2", new BigDecimal(10000), true, new BigDecimal(1), true, false);
        addAdvanceAssignmentWithMeasurement(children.get(1), advanceType2, new BigDecimal(2000), new BigDecimal(200), true);
        assertThat(orderElement.getAdvancePercentage(), sameValueAs(division.divide(20, 100)));
    }

    @Test
    @Transactional
    public void checkAdvancePercentageOrderGroupLineSameAdvanceType()
            throws DuplicateValueTrueReportGlobalAdvanceException, DuplicateAdvanceAssignmentForOrderElementException {

        OrderElement orderElement = givenOrderLineGroupWithTwoOrderLines(2000, 3000);

        List<OrderElement> children = orderElement.getChildren();

        AdvanceType advanceType = PredefinedAdvancedTypes.UNITS.getType();

        addAdvanceAssignmentWithMeasurement(children.get(0), advanceType, new BigDecimal(1000), new BigDecimal(100), true);

        addAdvanceAssignmentWithMeasurement(children.get(1), advanceType, new BigDecimal(1000), new BigDecimal(300), true);

        Set<IndirectAdvanceAssignment> indirectAdvanceAssignments = orderElement.getIndirectAdvanceAssignments();

        for (IndirectAdvanceAssignment indirectAdvanceAssignment : indirectAdvanceAssignments) {
            if (indirectAdvanceAssignment.getAdvanceType().equals(advanceType)) {
                indirectAdvanceAssignment.setReportGlobalAdvance(true);
            } else {
                indirectAdvanceAssignment.setReportGlobalAdvance(false);
            }
        }

        assertThat(orderElement.getAdvancePercentage(), sameValueAs(division.divide(20, 100)));
    }

    @Test
    @Transactional
    public void checkAdvancePercentageOrderGroupLineSameAdvanceTypeChildren()
            throws DuplicateValueTrueReportGlobalAdvanceException, DuplicateAdvanceAssignmentForOrderElementException {

        OrderElement orderElement = givenOrderLineGroupWithTwoOrderLines(2000, 3000);

        List<OrderElement> children = orderElement.getChildren();

        AdvanceType advanceType = PredefinedAdvancedTypes.UNITS.getType();

        addAdvanceAssignmentWithMeasurement(children.get(0), advanceType, new BigDecimal(1000), new BigDecimal(100), true);

        addAdvanceAssignmentWithMeasurement(children.get(1), advanceType, new BigDecimal(1000), new BigDecimal(300), true);

        Set<IndirectAdvanceAssignment> indirectAdvanceAssignments = orderElement.getIndirectAdvanceAssignments();
        for (IndirectAdvanceAssignment indirectAdvanceAssignment : indirectAdvanceAssignments) {
            if (indirectAdvanceAssignment.getAdvanceType().equals(PredefinedAdvancedTypes.CHILDREN.getType())) {
                indirectAdvanceAssignment.setReportGlobalAdvance(true);
            } else {
                indirectAdvanceAssignment.setReportGlobalAdvance(false);
            }
        }

        assertThat(orderElement.getAdvancePercentage(), sameValueAs(division.divide(22, 100)));
    }

    @Test
    @Transactional
    public void checkAdvancePercentageOrderGroupLineWithAssignments1()
            throws DuplicateValueTrueReportGlobalAdvanceException, DuplicateAdvanceAssignmentForOrderElementException {

        OrderLineGroup orderLineGroup = givenOrderLineGroupWithTwoOrderLines(1000, 2000);

        List<OrderElement> children = orderLineGroup.getChildren();

        addAdvanceAssignmentWithMeasurement(
                children.get(0), PredefinedAdvancedTypes.UNITS.getType(), new BigDecimal(1000), new BigDecimal(400), true);

        addAdvanceAssignmentWithMeasurement(
                children.get(1), PredefinedAdvancedTypes.UNITS.getType(), new BigDecimal(2000), new BigDecimal(200), true);

        removeReportGlobalAdvanceFromChildrenAdvance(orderLineGroup);

        addAdvanceAssignmentWithMeasurement(
                orderLineGroup, PredefinedAdvancedTypes.PERCENTAGE.getType(), new BigDecimal(100), new BigDecimal(90), true);

        assertThat(orderLineGroup.getAdvancePercentage(), sameValueAs(division.divide(90, 100)));
    }

    @Test
    @Transactional
    public void checkAdvancePercentageOrderGroupLineWithAssignments2()
            throws DuplicateValueTrueReportGlobalAdvanceException, DuplicateAdvanceAssignmentForOrderElementException {

        OrderElement orderElement = givenOrderLineGroupWithTwoOrderLines(1000, 2000);

        List<OrderElement> children = orderElement.getChildren();

        addAdvanceAssignmentWithMeasurement(
                children.get(0), PredefinedAdvancedTypes.UNITS.getType(), new BigDecimal(1000), new BigDecimal(400), true);

        addAdvanceAssignmentWithMeasurement(
                children.get(1), PredefinedAdvancedTypes.UNITS.getType(), new BigDecimal(2000), new BigDecimal(200), true);

        addAdvanceAssignmentWithMeasurement(
                orderElement, PredefinedAdvancedTypes.PERCENTAGE.getType(), new BigDecimal(100), new BigDecimal(90), false);

        assertThat(orderElement.getAdvancePercentage(), sameValueAs(division.divide(20, 100)));
    }

    @Test
    @Transactional
    public void checkAdvanceMeasurementMerge()
            throws DuplicateValueTrueReportGlobalAdvanceException, DuplicateAdvanceAssignmentForOrderElementException {

        OrderElement orderElement = givenOrderLineGroupWithTwoOrderLines(1000, 2000);

        List<OrderElement> children = orderElement.getChildren();

        LocalDate one = new LocalDate(2009, 9, 1);
        LocalDate two = new LocalDate(2009, 9, 2);
        LocalDate three = new LocalDate(2009, 9, 3);
        LocalDate four = new LocalDate(2009, 9, 4);
        LocalDate five = new LocalDate(2009, 9, 5);

        AdvanceType advanceType = PredefinedAdvancedTypes.UNITS.getType();

        addAdvanceAssignmentWithMeasurements(
                children.get(0), advanceType,
                true, new BigDecimal(1000),
                one, new BigDecimal(200),
                three, new BigDecimal(400),
                five, new BigDecimal(500));

        addAdvanceAssignmentWithMeasurements(
                children.get(1), advanceType,
                true, new BigDecimal(1000),
                two, new BigDecimal(100),
                three, new BigDecimal(350),
                four, new BigDecimal(400));

        assertThat(orderElement.getAdvancePercentage(), sameValueAs(division.divide(4333, 10000)));

        Set<DirectAdvanceAssignment> directAdvanceAssignments = orderElement.getDirectAdvanceAssignments();
        assertThat(directAdvanceAssignments.size(), equalTo(0));

        Set<IndirectAdvanceAssignment> indirectAdvanceAssignments = orderElement.getIndirectAdvanceAssignments();
        assertThat(indirectAdvanceAssignments.size(), equalTo(2));

        DirectAdvanceAssignment advanceAssignment = null;
        for (IndirectAdvanceAssignment indirectAdvanceAssignment : indirectAdvanceAssignments) {
            if (indirectAdvanceAssignment.getAdvanceType().equals(advanceType)) {
                advanceAssignment = orderElement.calculateFakeDirectAdvanceAssignment(indirectAdvanceAssignment);
                break;
            }
        }
        assertThat(advanceAssignment.getMaxValue(), equalTo(new BigDecimal(2000)));

        SortedSet<AdvanceMeasurement> advanceMeasurements = advanceAssignment.getAdvanceMeasurements();
        assertThat(advanceMeasurements.size(), equalTo(5));

        ArrayList<AdvanceMeasurement> list = new ArrayList<>(advanceMeasurements);
        Collections.sort(list, new AdvanceMeasurementComparator());
        Collections.reverse(list);
        Iterator<AdvanceMeasurement> iterator = list.iterator();

        AdvanceMeasurement next = iterator.next();
        assertThat(next.getDate(), equalTo(one));
        assertThat(next.getValue(), equalTo(new BigDecimal(200)));

        next = iterator.next();
        assertThat(next.getDate(), equalTo(two));
        assertThat(next.getValue(), equalTo(new BigDecimal(300)));

        next = iterator.next();
        assertThat(next.getDate(), equalTo(three));
        assertThat(next.getValue(), equalTo(new BigDecimal(750)));

        next = iterator.next();
        assertThat(next.getDate(), equalTo(four));
        assertThat(next.getValue(), equalTo(new BigDecimal(800)));

        next = iterator.next();
        assertThat(next.getDate(), equalTo(five));
        assertThat(next.getValue(), equalTo(new BigDecimal(900)));

    }

    private static void addAdvanceAssignmentWithMeasurements(OrderElement orderElement, AdvanceType advanceType,
                                                             boolean reportGlobalAdvance, BigDecimal maxValue,
                                                             LocalDate date1, BigDecimal value1, LocalDate date2,
                                                             BigDecimal value2, LocalDate five, BigDecimal date3)

            throws DuplicateValueTrueReportGlobalAdvanceException, DuplicateAdvanceAssignmentForOrderElementException {

        DirectAdvanceAssignment advanceAssignment = givenAdvanceAssignment(maxValue, advanceType);
        advanceAssignment.setReportGlobalAdvance(reportGlobalAdvance);

        AdvanceMeasurement advanceMeasurement1 = AdvanceMeasurement.create();
        advanceMeasurement1.setDate(date1);
        advanceMeasurement1.setValue(value1);
        advanceMeasurement1.setAdvanceAssignment(advanceAssignment);

        AdvanceMeasurement advanceMeasurement2 = AdvanceMeasurement.create();
        advanceMeasurement2.setDate(date2);
        advanceMeasurement2.setValue(value2);
        advanceMeasurement2.setAdvanceAssignment(advanceAssignment);

        AdvanceMeasurement advanceMeasurement3 = AdvanceMeasurement.create();
        advanceMeasurement3.setDate(five);
        advanceMeasurement3.setValue(date3);
        advanceMeasurement3.setAdvanceAssignment(advanceAssignment);

        orderElement.addAdvanceAssignment(advanceAssignment);
        advanceAssignment.addAdvanceMeasurements(advanceMeasurement1);
        advanceAssignment.addAdvanceMeasurements(advanceMeasurement2);
        advanceAssignment.addAdvanceMeasurements(advanceMeasurement3);
    }

    @Test
    @Transactional
    public void checkGetAdvanceAssignmentsIdempotent()
            throws DuplicateValueTrueReportGlobalAdvanceException, DuplicateAdvanceAssignmentForOrderElementException {

        OrderElement orderElement = givenOrderLineGroupWithTwoOrderLines(1000, 2000);

        List<OrderElement> children = orderElement.getChildren();

        AdvanceType advanceType = PredefinedAdvancedTypes.UNITS.getType();

        addAdvanceAssignmentWithMeasurement(children.get(0), advanceType, new BigDecimal(1000), new BigDecimal(200), true);

        addAdvanceAssignmentWithMeasurement(children.get(1), advanceType, new BigDecimal(2000), new BigDecimal(400), true);

        Set<DirectAdvanceAssignment> directAdvanceAssignments = orderElement.getDirectAdvanceAssignments();
        assertThat(directAdvanceAssignments.size(), equalTo(0));

        Set<IndirectAdvanceAssignment> indirectAdvanceAssignments = orderElement.getIndirectAdvanceAssignments();
        assertThat(indirectAdvanceAssignments.size(), equalTo(2));

        DirectAdvanceAssignment advanceAssignment = null;
        for (IndirectAdvanceAssignment indirectAdvanceAssignment : indirectAdvanceAssignments) {
            if (indirectAdvanceAssignment.getAdvanceType().equals(advanceType)) {
                advanceAssignment = orderElement.calculateFakeDirectAdvanceAssignment(indirectAdvanceAssignment);
                break;
            }
        }
        assertThat(advanceAssignment.getMaxValue(), equalTo(new BigDecimal(3000)));

        assertThat(advanceAssignment.getAdvanceMeasurements().size(), equalTo(1));
        assertThat(advanceAssignment.getAdvanceMeasurements().iterator().next().getValue(), equalTo(new BigDecimal(600)));
    }

    @Test
    @Transactional
    public void checkAdvanceMeasurementMergeWithDifferentAdvanceTypes()
            throws DuplicateValueTrueReportGlobalAdvanceException, DuplicateAdvanceAssignmentForOrderElementException {

        OrderElement orderElement = givenOrderLineGroupWithTwoOrderLines(1000, 2000);

        List<OrderElement> children = orderElement.getChildren();

        LocalDate one = new LocalDate(2009, 9, 1);
        LocalDate two = new LocalDate(2009, 9, 2);
        LocalDate three = new LocalDate(2009, 9, 3);
        LocalDate four = new LocalDate(2009, 9, 4);
        LocalDate five = new LocalDate(2009, 9, 5);

        AdvanceType advanceType1 = AdvanceType.create("test1", new BigDecimal(10000), true, new BigDecimal(1), true, false);

        addAdvanceAssignmentWithMeasurements(
                children.get(0), advanceType1,
                true, new BigDecimal(1000),
                one, new BigDecimal(200),
                three, new BigDecimal(400),
                five, new BigDecimal(500));

        AdvanceType advanceType2 = AdvanceType.create("test2", new BigDecimal(10000), true, new BigDecimal(1), true, false);

        addAdvanceAssignmentWithMeasurements(
                children.get(1), advanceType2,
                true, new BigDecimal(1000),
                two, new BigDecimal(100),
                three, new BigDecimal(350),
                four, new BigDecimal(400));

        assertThat(orderElement.getAdvancePercentage(), sameValueAs(division.divide(4333, 10000)));

        Set<DirectAdvanceAssignment> directAdvanceAssignments = orderElement.getDirectAdvanceAssignments();
        assertThat(directAdvanceAssignments.size(), equalTo(0));

        Set<IndirectAdvanceAssignment> indirectAdvanceAssignments = orderElement.getIndirectAdvanceAssignments();
        assertThat(indirectAdvanceAssignments.size(), equalTo(3));

        DirectAdvanceAssignment advanceAssignment = null;
        for (IndirectAdvanceAssignment indirectAdvanceAssignment : indirectAdvanceAssignments) {

            if ( indirectAdvanceAssignment.getAdvanceType().getUnitName()
                    .equals(PredefinedAdvancedTypes.CHILDREN.getTypeName()) ) {

                advanceAssignment = orderElement.calculateFakeDirectAdvanceAssignment(indirectAdvanceAssignment);
                break;
            }
        }
        assertThat(advanceAssignment.getMaxValue(), equalTo(new BigDecimal(100)));

        SortedSet<AdvanceMeasurement> advanceMeasurements = advanceAssignment.getAdvanceMeasurements();
        assertThat(advanceMeasurements.size(), equalTo(5));

        ArrayList<AdvanceMeasurement> list = new ArrayList<>(advanceMeasurements);
        Collections.sort(list, new AdvanceMeasurementComparator());
        Collections.reverse(list);
        Iterator<AdvanceMeasurement> iterator = list.iterator();

        AdvanceMeasurement next = iterator.next();
        assertThat(next.getDate(), equalTo(one));

        assertThat(next.getValue(), sameValueAs(division.divide(66600, 10000)));

        next = iterator.next();
        assertThat(next.getDate(), equalTo(two));
        assertThat(next.getValue(), sameValueAs(division.divide(133300, 10000)));

        next = iterator.next();
        assertThat(next.getDate(), equalTo(three));
        assertThat(next.getValue(), sameValueAs(division.divide(366600, 10000)));

        next = iterator.next();
        assertThat(next.getDate(), equalTo(four));
        assertThat(next.getValue(), equalTo(new BigDecimal(40).setScale(4)));

        next = iterator.next();
        assertThat(next.getDate(), equalTo(five));
        assertThat(next.getValue(), sameValueAs(division.divide(4333, 100)));
    }

    @Test
    @Transactional
    public void checkGetAdvancePercentageTwoLevelOfDepth1()
            throws DuplicateValueTrueReportGlobalAdvanceException, DuplicateAdvanceAssignmentForOrderElementException {

        OrderLineGroup orderLineGroup_1 = OrderLineGroup.create();
        orderLineGroup_1.setName("OrderLineGroup 1");
        orderLineGroup_1.setCode("1");

        OrderLineGroup orderLineGroup_1_1 = OrderLineGroup.create();
        orderLineGroup_1_1.setName("OrderLineGroup 1.1");
        orderLineGroup_1_1.setCode("1.1");

        orderLineGroup_1.useSchedulingDataFor(mockedOrderVersion);

        OrderLine orderLine_1_1_1 = givenOrderLine("OrderLine 1.1.1", "1.1.1", 1000);

        orderLineGroup_1.add(orderLineGroup_1_1);
        orderLineGroup_1_1.add(orderLine_1_1_1);

        AdvanceType advanceType1 = AdvanceType.create("test1", new BigDecimal(10000), true, new BigDecimal(1), true, false);
        addAdvanceAssignmentWithMeasurement(orderLine_1_1_1, advanceType1, new BigDecimal(10), new BigDecimal(2), true);

        AdvanceType advanceType2 = AdvanceType.create("test2", new BigDecimal(10000), true, new BigDecimal(1), true, false);
        removeReportGlobalAdvanceFromChildrenAdvance(orderLineGroup_1_1);
        addAdvanceAssignmentWithMeasurement(orderLineGroup_1_1, advanceType2, new BigDecimal(100), new BigDecimal(50), true);

        assertThat(orderLineGroup_1.getDirectAdvanceAssignments().size(), equalTo(0));
        assertThat(orderLineGroup_1.getIndirectAdvanceAssignments().size(), equalTo(3));

        assertThat(orderLineGroup_1.getAdvancePercentage(), sameValueAs(division.divide(50, 100)));
    }

    @Test
    @Transactional
    public void checkGetAdvancePercentageTwoLevelOfDepth2()
            throws DuplicateValueTrueReportGlobalAdvanceException, DuplicateAdvanceAssignmentForOrderElementException {

        OrderLineGroup orderLineGroup_1 = OrderLineGroup.create();
        orderLineGroup_1.setName("OrderLineGroup 1");
        orderLineGroup_1.setCode("1");
        orderLineGroup_1.useSchedulingDataFor(mockedOrderVersion);
        OrderLineGroup orderLineGroup_1_1 = OrderLineGroup.create();
        orderLineGroup_1_1.setName("OrderLineGroup 1.1");
        orderLineGroup_1_1.setCode("1.1");

        OrderLine orderLine_1_1_1 = givenOrderLine("OrderLine 1.1.1", "1.1.1", 1000);

        orderLineGroup_1.add(orderLineGroup_1_1);
        orderLineGroup_1_1.add(orderLine_1_1_1);

        AdvanceType advanceType1 = AdvanceType.create("test1", new BigDecimal(10000), true, new BigDecimal(1), true, false);
        addAdvanceAssignmentWithMeasurement(orderLine_1_1_1, advanceType1, new BigDecimal(10), new BigDecimal(2), true);

        AdvanceType advanceType2 = AdvanceType.create("test2", new BigDecimal(10000), true, new BigDecimal(1), true, false);
        addAdvanceAssignmentWithMeasurement(orderLineGroup_1_1, advanceType2, new BigDecimal(100), new BigDecimal(50), false);

        assertThat(orderLineGroup_1.getDirectAdvanceAssignments().size(), equalTo(0));
        assertThat(orderLineGroup_1.getIndirectAdvanceAssignments().size(), equalTo(3));

        assertThat(orderLineGroup_1.getAdvancePercentage(), sameValueAs(division.divide(20, 100)));
    }

    @Test
    @Transactional
    public void checkAdvancePercentageOrderGroupLineWithPercentageAdvanceType()
            throws DuplicateValueTrueReportGlobalAdvanceException, DuplicateAdvanceAssignmentForOrderElementException {

        OrderElement orderElement = givenOrderLineGroupWithTwoOrderLines(1000, 2000);

        List<OrderElement> children = orderElement.getChildren();

        AdvanceType advanceType = PredefinedAdvancedTypes.PERCENTAGE.getType();
        addAdvanceAssignmentWithMeasurement(children.get(0), advanceType, new BigDecimal(100), new BigDecimal(40), true);

        addAdvanceAssignmentWithMeasurement(children.get(1), advanceType, new BigDecimal(100), new BigDecimal(20), true);

        assertThat(orderElement.getAdvancePercentage(), sameValueAs(division.divide(2666, 10000)));

        Set<IndirectAdvanceAssignment> indirectAdvanceAssignments = orderElement.getIndirectAdvanceAssignments();
        for (IndirectAdvanceAssignment indirectAdvanceAssignment : indirectAdvanceAssignments) {

            if ( indirectAdvanceAssignment.getAdvanceType().getUnitName()
                    .equals(PredefinedAdvancedTypes.PERCENTAGE.getTypeName()) ) {

                indirectAdvanceAssignment.setReportGlobalAdvance(true);
            } else {
                indirectAdvanceAssignment.setReportGlobalAdvance(false);
            }
        }
        assertThat(orderElement.getAdvancePercentage(), sameValueAs(division.divide(26, 4)));
    }

    @Test
    @Transactional
    public void checkAdvanceMeasurementMergePercentageAdvanceType()
            throws DuplicateValueTrueReportGlobalAdvanceException, DuplicateAdvanceAssignmentForOrderElementException {

        OrderElement orderElement = givenOrderLineGroupWithTwoOrderLines(1000, 2000);

        List<OrderElement> children = orderElement.getChildren();

        LocalDate one = new LocalDate(2009, 9, 1);
        LocalDate two = new LocalDate(2009, 9, 2);
        LocalDate three = new LocalDate(2009, 9, 3);
        LocalDate four = new LocalDate(2009, 9, 4);
        LocalDate five = new LocalDate(2009, 9, 5);

        AdvanceType advanceType = PredefinedAdvancedTypes.PERCENTAGE.getType();

        addAdvanceAssignmentWithMeasurements(
                children.get(0), advanceType,
                true, new BigDecimal(100),
                two, new BigDecimal(10),
                three, new BigDecimal(20),
                four, new BigDecimal(40));

        addAdvanceAssignmentWithMeasurements(
                children.get(1), advanceType,
                true, new BigDecimal(100),
                one, new BigDecimal(10),
                four, new BigDecimal(20),
                five, new BigDecimal(50));

        assertThat(orderElement.getAdvancePercentage(), sameValueAs(division.divide(4666, 10000)));

        Set<DirectAdvanceAssignment> directAdvanceAssignments = orderElement.getDirectAdvanceAssignments();
        assertThat(directAdvanceAssignments.size(), equalTo(0));

        Set<IndirectAdvanceAssignment> indirectAdvanceAssignments = orderElement.getIndirectAdvanceAssignments();
        assertThat(indirectAdvanceAssignments.size(), equalTo(2));

        DirectAdvanceAssignment advanceAssignment = null;
        for (IndirectAdvanceAssignment indirectAdvanceAssignment : indirectAdvanceAssignments) {
            if (indirectAdvanceAssignment.getAdvanceType().equals(advanceType)) {
                advanceAssignment = orderElement.calculateFakeDirectAdvanceAssignment(indirectAdvanceAssignment);
                break;
            }
        }
        assertThat(advanceAssignment.getMaxValue(), equalTo(new BigDecimal(100)));

        SortedSet<AdvanceMeasurement> advanceMeasurements = advanceAssignment.getAdvanceMeasurements();
        assertThat(advanceMeasurements.size(), equalTo(5));

        ArrayList<AdvanceMeasurement> list = new ArrayList<>(advanceMeasurements);
        Collections.sort(list, new AdvanceMeasurementComparator());
        Collections.reverse(list);
        Iterator<AdvanceMeasurement> iterator = list.iterator();

        AdvanceMeasurement next = iterator.next();
        assertThat(next.getDate(), equalTo(one));
        assertThat(next.getValue(), equalTo(new BigDecimal(6)));
        // FIXME real value should be: 6.66

        next = iterator.next();
        assertThat(next.getDate(), equalTo(two));
        assertThat(next.getValue(), equalTo(new BigDecimal(9)));
        // FIXME real value should be: 10

        next = iterator.next();
        assertThat(next.getDate(), equalTo(three));
        assertThat(next.getValue(), equalTo(new BigDecimal(12)));
        // FIXME real value should be: 13.33

        next = iterator.next();
        assertThat(next.getDate(), equalTo(four));
        assertThat(next.getValue(), equalTo(new BigDecimal(24)));
        // FIXME real value should be: 26.66

        next = iterator.next();
        assertThat(next.getDate(), equalTo(five));
        assertThat(next.getValue(), equalTo(new BigDecimal(44)));
        // FIXME real value should be: 46.66

    }

    @Test
    @Transactional
    public void checkCalculateFakeOrderLineGroup1()
            throws DuplicateValueTrueReportGlobalAdvanceException, DuplicateAdvanceAssignmentForOrderElementException {

        OrderElement orderElement = givenOrderLineGroupWithTwoOrderLines(5000, 1000);

        List<OrderElement> children = orderElement.getChildren();
        AdvanceType advanceType = PredefinedAdvancedTypes.UNITS.getType();
        addAdvanceAssignmentWithoutMeasurement(children.get(0), advanceType, new BigDecimal(1000), true);

        LocalDate one = new LocalDate(2009, 9, 1);
        LocalDate two = new LocalDate(2009, 9, 2);
        LocalDate three = new LocalDate(2009, 9, 3);

        addAdvanceAssignmentWithMeasurements(
                children.get(1), advanceType,
                true, new BigDecimal(10000),
                one, new BigDecimal(100),
                two, new BigDecimal(1000),
                three, new BigDecimal(5000));

        assertThat(orderElement.getAdvancePercentage(), sameValueAs(division.divide(833, 10000)));

        Set<IndirectAdvanceAssignment> indirectAdvanceAssignments = orderElement.getIndirectAdvanceAssignments();
        assertThat(indirectAdvanceAssignments.size(), equalTo(2));

        DirectAdvanceAssignment advanceAssignment = null;
        for (IndirectAdvanceAssignment indirectAdvanceAssignment : indirectAdvanceAssignments) {
            if (indirectAdvanceAssignment.getAdvanceType().equals(advanceType)) {
                advanceAssignment = orderElement.calculateFakeDirectAdvanceAssignment(indirectAdvanceAssignment);
                break;
            }
        }
        assertThat(advanceAssignment.getMaxValue(), equalTo(new BigDecimal(11000)));
        assertThat(advanceAssignment.getAdvanceMeasurements().size(), equalTo(3));
        assertThat(advanceAssignment.getLastAdvanceMeasurement().getValue(), equalTo(new BigDecimal(5000)));

        assertThat(advanceAssignment.getAdvancePercentage(), sameValueAs(division.divide(4545, 10000)));
    }

    @Test
    @Transactional
    public void checkCalculateFakeOrderLineGroup2()
            throws DuplicateValueTrueReportGlobalAdvanceException, DuplicateAdvanceAssignmentForOrderElementException {

        OrderElement orderElement = givenOrderLineGroupWithTwoOrderLines(1000, 5000);

        List<OrderElement> children = orderElement.getChildren();
        AdvanceType advanceType = PredefinedAdvancedTypes.UNITS.getType();

        LocalDate one = new LocalDate(2009, 9, 1);
        LocalDate two = new LocalDate(2009, 9, 2);
        LocalDate three = new LocalDate(2009, 9, 3);

        addAdvanceAssignmentWithMeasurements(
                children.get(0), advanceType,
                true, new BigDecimal(10000),
                one, new BigDecimal(100),
                two, new BigDecimal(1000),
                three, new BigDecimal(5000));

        addAdvanceAssignmentWithoutMeasurement(children.get(1), advanceType, new BigDecimal(1000), true);

        assertThat(orderElement.getAdvancePercentage(), sameValueAs(division.divide(833, 10000)));

        Set<IndirectAdvanceAssignment> indirectAdvanceAssignments = orderElement.getIndirectAdvanceAssignments();
        assertThat(indirectAdvanceAssignments.size(), equalTo(2));

        DirectAdvanceAssignment advanceAssignment = null;
        for (IndirectAdvanceAssignment indirectAdvanceAssignment : indirectAdvanceAssignments) {
            if (indirectAdvanceAssignment.getAdvanceType().equals(advanceType)) {
                advanceAssignment = orderElement.calculateFakeDirectAdvanceAssignment(indirectAdvanceAssignment);
                break;
            }
        }
        assertThat(advanceAssignment.getMaxValue(), equalTo(new BigDecimal(11000)));
        assertThat(advanceAssignment.getAdvanceMeasurements().size(), equalTo(3));
        assertThat(advanceAssignment.getLastAdvanceMeasurement().getValue(), equalTo(new BigDecimal(5000)));

        assertThat(advanceAssignment.getAdvancePercentage(), sameValueAs(division.divide(4545, 10000)));
    }

    public static void removeReportGlobalAdvanceFromChildrenAdvance(OrderLineGroup orderLineGroup) {
        for (IndirectAdvanceAssignment indirectAdvanceAssignment : orderLineGroup.getIndirectAdvanceAssignments()) {

            if ( indirectAdvanceAssignment.getAdvanceType().getUnitName()
                    .equals(PredefinedAdvancedTypes.CHILDREN.getTypeName()) ) {

                indirectAdvanceAssignment.setReportGlobalAdvance(false);
                break;
            }
        }
    }

    @Test
    @Transactional
    public  void checkChangeIndirectCriterionToInvalid() {
        Order order = Order.create();
        order.useSchedulingDataFor(mockedOrderVersion);
        OrderLineGroup container1 = OrderLineGroup.create();
        container1.useSchedulingDataFor(mockedOrderVersion);
        order.add(container1);
        OrderLineGroup container2 = OrderLineGroup.create();
        container2.useSchedulingDataFor(mockedOrderVersion);
        container1.add(container2);
        OrderLine line = OrderLine.createOrderLineWithUnfixedPercentage(100);
        line.useSchedulingDataFor(mockedOrderVersion);
        container2.add(line);

        CriterionType type = CriterionType.create("", "");
        type.setResource(ResourceEnum.WORKER);
        CriterionRequirement requirement = DirectCriterionRequirement.create(Criterion.create(type));
        order.addDirectCriterionRequirement(requirement);

        assertThat(container2.getCriterionRequirements().size(), equalTo(1));

        IndirectCriterionRequirement requirementAtContainer =
                (IndirectCriterionRequirement) container2.getCriterionRequirements().iterator().next();

        container2.setValidCriterionRequirement(requirementAtContainer, false);

        assertThat(container1.getCriterionRequirements().size(), equalTo(1));
        assertTrue(container1.getCriterionRequirements().iterator().next().isValid());

        assertThat(container2.getCriterionRequirements().size(), equalTo(1));
        assertFalse(container2.getCriterionRequirements().iterator().next().isValid());

        assertThat(line.getCriterionRequirements().size(), equalTo(1));
        assertFalse(line.getCriterionRequirements().iterator().next().isValid());

        assertThat(line.getHoursGroups().size(), equalTo(1));
        assertThat(line.getHoursGroups().get(0).getCriterionRequirements().size(), equalTo(1));
        assertFalse(line.getHoursGroups().get(0).getCriterionRequirements().iterator().next().isValid());
    }

    @Test
    @Transactional
    public void checkSpreadAdvanceInOrderLine()
            throws DuplicateValueTrueReportGlobalAdvanceException, DuplicateAdvanceAssignmentForOrderElementException {

        OrderLine orderLine = givenOrderLine("element", "element-code", 100);

        AdvanceType advanceType1 = PredefinedAdvancedTypes.PERCENTAGE.getType();
        AdvanceType advanceType2 = PredefinedAdvancedTypes.UNITS.getType();

        addAdvanceAssignmentWithoutMeasurement(orderLine, advanceType1, BigDecimal.TEN, true);
        addAdvanceAssignmentWithoutMeasurement(orderLine, advanceType2, BigDecimal.TEN, false);

        assertThat(orderLine.getReportGlobalAdvanceAssignment().getAdvanceType(), equalTo(advanceType1));
        assertNotNull(orderLine.getReportGlobalAdvanceAssignment());

        orderLine.removeAdvanceAssignment(orderLine.getAdvanceAssignmentByType(advanceType1));

        assertNotNull(orderLine.getReportGlobalAdvanceAssignment());
        assertThat(orderLine.getReportGlobalAdvanceAssignment().getAdvanceType(), equalTo(advanceType2));
    }

    @Test
    @Transactional
    public void checkPositiveBudgetInOrderLine() {
        OrderLine line = givenOrderLine("task", "code", 10);
        line.setBudget(new BigDecimal(100));
        assertThat(line.getBudget(), equalTo(new BigDecimal(100).setScale(2)));
    }

    @Test(expected = IllegalArgumentException.class)
    @Transactional
    public void checkNonNegativeBudgetInOrderLine() {
        OrderLine line = givenOrderLine("task", "code", 10);
        line.setBudget(new BigDecimal(-100));
    }

    @Test
    @Transactional
    public void checkBudgetInOrderLineGroup() {
        OrderLineGroup group = givenOrderLineGroupWithTwoOrderLines(20, 30);
        ((OrderLine) group.getChildren().get(0)).setBudget(new BigDecimal(50));
        ((OrderLine) group.getChildren().get(1)).setBudget(new BigDecimal(70));
        assertThat(group.getBudget(), equalTo(new BigDecimal(120).setScale(2)));
    }

}
