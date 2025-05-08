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

package org.libreplan.web.common.components;

import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.libreplan.web.common.components.finders.IFinder;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zul.Combobox;
import org.zkoss.zul.Comboitem;

/**
 * Autocomplete component.
 *
 * Extends a {@link Combobox} component providing extra functionality for
 * filling the list of elements with entries, thanks to a class implementing {@link IFinder}.
 *
 * @author Diego Pino García <dpino@igalia.com>
 */
@SuppressWarnings("serial")
public class Autocomplete extends Combobox {

    private IFinder finder;

    public String getFinder() {
        return finder.getClass().toString();
    }

    public void setFinder(String classname) {
        finder = (IFinder) getBean(StringUtils.uncapitalize(classname));
        setModel(finder.getModel());
        setItemRenderer(finder.getItemRenderer());
        bindOnChangeAutocomplete(this);
    }

    /**
     * When there is only one possible item, autocomplete option automatically fills text with that option,
     * but it does not set the component with the option selected.
     *
     * To solve this problem, what I did was, when an onChange happens,
     * search among all options the text filled by in the TextBox.
     * If there is one that matches, select that option.
     * Otherwise, raise a WrongValueException prompting user to select a valid option.
     *
     * @param autocomplete
     */
    private void bindOnChangeAutocomplete(final Autocomplete autocomplete) {
        autocomplete.addEventListener("onChange", (EventListener) event -> {
            String text = autocomplete.getValue();
            Object object = getItemByText(text);
            autocomplete.setSelectedItem(object);
        });
    }

    /**
     * Searches for text among list of items, and returns item.value that matches.
     *
     * @param text
     * @return {@link Object}
     */
    public Object getItemByText(String text) {
        final List<Comboitem> items = this.getItems();
        for (Comboitem item: items) {
            final String itemtext = finder._toString(item.getValue());
            if ( itemtext.equalsIgnoreCase(text) ) {
                return item.getValue();
            }
        }
        return null;
    }

    public void setSelectedItem(Object object) {
        if ( object != null ) {
            this.setValue(finder._toString(object));
        }
    }

    public void clear() {
        this.setValue("");
        this.setSelectedItem(null);
        this.setModel(finder.getModel());
        this.invalidate();
    }

    private Object getBean(String classname) {
        HttpServletRequest servletRequest = (HttpServletRequest) Executions.getCurrent().getNativeRequest();
        ServletContext servletContext = servletRequest.getSession().getServletContext(); // &line[getSession]

        WebApplicationContext webApplicationContext =
                WebApplicationContextUtils.getWebApplicationContext(servletContext);

        return webApplicationContext.getBean(classname);
    }

}
