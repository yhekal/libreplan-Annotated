/*
 * This file is part of LibrePlan
 *
 * Copyright (C) 2009-2010 Fundación para o Fomento da Calidade Industrial e
 *                         Desenvolvemento Tecnolóxico de Galicia
 * Copyright (C) 2010-2012 Igalia, S.L.
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

package org.libreplan.web.error;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.util.GenericForwardComposer;
import org.zkoss.zul.Textbox;

public class PageForErrorOnEvent extends GenericForwardComposer {

    private static final Log LOG = LogFactory.getLog(PageForErrorOnEvent.class);

    private Component modalWindow;

    private Textbox stacktrace;

    private boolean isHelpLink = false;

    @Override
    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);
        comp.setAttribute("pageErrorController", this, true);
        logError();
        modalWindow = comp;
        if ( stacktrace != null ) {
            stacktrace.setValue(getStacktrace());
        }
    }

    private void logError() {
        String urlPath = (String) Executions.getCurrent().getAttribute("javax.servlet.forward.servlet_path");
        Throwable exception = (Throwable) Executions.getCurrent().getAttribute("javax.servlet.error.exception");
        String errorMessage = (String) Executions.getCurrent().getAttribute("javax.servlet.error.message");
        Integer code = (Integer) Executions.getCurrent().getAttribute("javax.servlet.error.status_code");

        if (urlPath != null && urlPath.contains("help")) {
            isHelpLink = true;
        }

        if ( code != null ) {
            errorMessage += " [Status Code: " + code + "]";
            if ( code == HttpServletResponse.SC_FORBIDDEN ) {
                String uri = (String) Executions.getCurrent().getAttribute("javax.servlet.error.request_uri");
                errorMessage += " [Request URI: " + uri + "]";
            }
        }
        LOG.error(errorMessage, exception);
    }

    public void onClick$continueWorking() {
        modalWindow.detach();
    }

    public void onClick$reload() {
        Executions.sendRedirect(null);
    }

    public void onClick$quitSession() {
        HttpServletRequest nativeRequest = (HttpServletRequest) Executions.getCurrent().getNativeRequest();
        nativeRequest.getSession().invalidate(); // &line[getSession]
        Executions.sendRedirect("/");
    }

    private String getStacktrace() {
        Throwable exception = (Throwable) Executions.getCurrent().getAttribute("javax.servlet.error.exception");
        if ( exception != null ) {
            Writer stacktrace = new StringWriter();
            exception.printStackTrace(new PrintWriter(stacktrace));

            return stacktrace.toString();
        }
        return "";
    }

    public boolean isVisibleOnHelpPage() {
        return isHelpLink;
    }
}
