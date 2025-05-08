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

package org.libreplan.web.common;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.libreplan.business.common.VersionInformation;
import org.libreplan.business.scenarios.IScenarioManager;
import org.libreplan.business.scenarios.entities.Scenario;
import org.libreplan.web.common.ITemplateModel.IOnFinished;
import org.libreplan.web.common.components.bandboxsearch.BandboxSearch;
import org.libreplan.web.security.SecurityUtils;
import org.libreplan.web.users.bootstrap.PredefinedUsers;
import org.libreplan.web.users.services.CustomUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.SuspendNotAllowedException;
import org.zkoss.zk.ui.util.GenericForwardComposer;
import org.zkoss.zul.Image;
import org.zkoss.zul.Window;

import java.util.Collections;
import java.util.List;

import static org.libreplan.web.I18nHelper._;

/**
 * Controller to manage UI operations from main template.
 *
 * @author Manuel Rego Casasnovas <mrego@igalia.com>
 * @author Vova Perebykivskyi <vova@libreplan-enterprise.com>
 */
@org.springframework.stereotype.Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class TemplateController extends GenericForwardComposer {

    private static final Log LOG = LogFactory.getLog(TemplateController.class);

    @Autowired
    private ITemplateModel templateModel;

    @Autowired
    private IScenarioManager scenarioManager;

    private Window window;

    private IMessagesForUser windowMessages;

    private String lastVersionNumber = "";

    private Image logo;

    @Override
    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);

        if ( templateModel.isScenariosVisible() ) {
            window = (Window) comp.getFellow("changeScenarioWindow");
            windowMessages = new MessagesForUser(window.getFellow("messagesContainer"));
        }
    }

    public Scenario getScenario() {
        return scenarioManager.getCurrent();
    }

    public void changeScenario() throws SuspendNotAllowedException, InterruptedException {
        window.doModal();
    }

    public List<Scenario> getScenarios() {
        return templateModel == null ? Collections.emptyList() : templateModel.getScenarios();
    }

    public String getCompanyLogoURL() {
        return templateModel == null || templateModel.getCompanyLogoURL() == null
                ? ""
                : templateModel.getCompanyLogoURL().trim();
    }

    /**
     * Setup logo from outside of Web Application Context.
     * Should be public!
     */
    public void setupLogoFromOutside() {
        if ( logo.getContent() == null ) {

            /* Try to find logo */
            if ( Util.logo == null ) {
                Util.findLogo();
            }

            logo.setContent(Util.logo);
        }
    }

    public void accept() {
        BandboxSearch scenarioBandboxSearch = (BandboxSearch) window.getFellow("scenarioBandboxSearch");
        Scenario scenario = (Scenario) scenarioBandboxSearch.getSelectedElement();

        templateModel.setScenario(SecurityUtils.getSessionUserLoginName(), scenario, new IOnFinished() { // &line[getSessionUserLoginName]
            @Override
            public void onWithoutErrorFinish() {
                window.setVisible(false);
                Executions.sendRedirect("/");
            }

            @Override
            public void errorHappened(Exception exceptionHappened) {
                LOG.error("error doing reassignation", exceptionHappened);
                windowMessages.showMessage(Level.ERROR, _("error doing reassignment: {0}", exceptionHappened));
            }
        });
    }

    public void cancel() {
        window.setVisible(false);
    }

    public Boolean getScenariosVisible() {
        return (templateModel != null) && templateModel.isScenariosVisible();
    }

    public String getDefaultPasswdAdminVisible() {
        return notChangedPasswordWarningDisplayPropertyFor(PredefinedUsers.ADMIN);
    }

    public String getDefaultPasswdWsreaderVisible() {
        return notChangedPasswordWarningDisplayPropertyFor(PredefinedUsers.WSREADER);
    }

    public String getDefaultPasswdWswriterVisible() {
        return notChangedPasswordWarningDisplayPropertyFor(PredefinedUsers.WSWRITER);
    }

    public String getDefaultPasswdWssubcontractingVisible() {
        return notChangedPasswordWarningDisplayPropertyFor(PredefinedUsers.WSSUBCONTRACTING);
    }

    public String getDefaultPasswdManagerVisible() {
        return notChangedPasswordWarningDisplayPropertyFor(PredefinedUsers.MANAGER);
    }

    public String getDefaultPasswdHresourcesVisible() {
        return notChangedPasswordWarningDisplayPropertyFor(PredefinedUsers.HRESOURCES);
    }

    public String getDefaultPasswdOutsourcingVisible() {
        return notChangedPasswordWarningDisplayPropertyFor(PredefinedUsers.OUTSOURCING);
    }

    public String getDefaultPasswdReportsVisible() {
        return notChangedPasswordWarningDisplayPropertyFor(PredefinedUsers.REPORTS);
    }

    private String notChangedPasswordWarningDisplayPropertyFor(PredefinedUsers mandatoryUser) {
        return asDisplayProperty(templateModel.hasChangedDefaultPassword(mandatoryUser));
    }

    private String asDisplayProperty(boolean passwordChanged) {
        return passwordChanged ? "none" : "inline";
    }

    public String getDefaultPasswdVisible() {
        return asDisplayProperty(!templateModel.adminPasswordChangedAndSomeOtherNotChanged());
    }

    public String getIdAdminUser() {
        return templateModel.getIdUser(PredefinedUsers.ADMIN.getLoginName());
    }

    public String getIdWsreaderUser() {
        return templateModel.getIdUser(PredefinedUsers.WSREADER.getLoginName());
    }

    public String getIdWswriterUser() {
        return templateModel.getIdUser(PredefinedUsers.WSWRITER.getLoginName());
    }

    public String getIdWssubcontractingUser() {
        return templateModel.getIdUser(PredefinedUsers.WSSUBCONTRACTING.getLoginName());
    }

    public String getIdManagerUser() {
        return templateModel.getIdUser(PredefinedUsers.MANAGER.getLoginName());
    }

    public String getIdHresourcesUser() {
        return templateModel.getIdUser(PredefinedUsers.HRESOURCES.getLoginName());
    }

    public String getIdOutsourcingUser() {
        return templateModel.getIdUser(PredefinedUsers.OUTSOURCING.getLoginName());
    }

    public String getIdReportsUser() {
        return templateModel.getIdUser(PredefinedUsers.REPORTS.getLoginName());
    }

    public boolean isUserAdmin() {
        return templateModel.isUserAdmin();
    }

    public boolean isNewVersionAvailable() {
        if ( templateModel.isCheckNewVersionEnabled() && VersionInformation.isNewVersionAvailable() ) {
            lastVersionNumber = VersionInformation.getLastVersion();
            return true;
        }

        return false;
    }

    public String getUsername() {
        CustomUser user = SecurityUtils.getLoggedUser();
        return (user == null) ? "" : user.getUsername();
    }

    /**
     * Should be public!
     * Used in template.zul
     */
    public String getVersionMessage() {
        return _("A new version ") + lastVersionNumber + _(" of LibrePlan is available. Please check next link for more information:");
    }

}
