/*
 * This file is part of LibrePlan
 *
 * Copyright (C) 2009-2010 Fundación para o Fomento da Calidade Industrial e
 *                         Desenvolvemento Tecnolóxico de Galicia
 * Copyright (C) 2010-2011 Igalia, S.L.
 * Copyright (C) 2011 ComtecSF, S.L.
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

package org.libreplan.web;

import java.util.HashMap;
import java.util.Locale;

import javax.servlet.http.HttpSession;

import org.libreplan.business.users.entities.User;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;
import org.zkoss.util.Locales;
import org.zkoss.web.servlet.Charsets;
import org.zkoss.zk.ui.Execution;
import org.zkoss.zk.ui.Executions;


public class I18nHelper {

    private static Locale defaultLang = Locale.ENGLISH;

    private static HashMap<Locale, I18n> localesCache = new HashMap<>();

    private I18nHelper() {
    }

    public static I18n getI18n() {
        setPreferredLocale();

        Locale locale = Locales.getCurrent();

        if ( localesCache.keySet().contains(locale) ) {
            return localesCache.get(locale);
        }

        I18n i18n = I18nFactory.getI18n(I18nHelper.class, locale, org.xnap.commons.i18n.I18nFactory.FALLBACK);

        // The language returned is not the same as the requested by the user
        if ( !locale.getLanguage().equals(i18n.getResources().getLocale().getLanguage()) ) {
            // Force it to be default language
            i18n = getDefaultI18n();
        }
        localesCache.put(Locales.getCurrent(), i18n);

        return i18n;
    }

    private static void setPreferredLocale() {
        Execution execution = Executions.getCurrent();
        if ( execution != null ) {
            Locale userLocale = getUserLocale();
            Charsets.setPreferredLocale((HttpSession) execution.getSession().getNativeSession(), userLocale); // &line[getSession]
            if ( userLocale != null ) {
                Locales.setThreadLocal(userLocale);
            }
        }
    }

    private static Locale getUserLocale() {
        User user = UserUtil.getUserFromSession();
        return (user != null) ? user.getApplicationLanguage().getLocale() : null;
    }

    private static I18n getDefaultI18n() {
        I18n i18n = localesCache.get(defaultLang);

        if ( i18n == null ) {
            i18n = I18nFactory.getI18n(I18nHelper.class, defaultLang, org.xnap.commons.i18n.I18nFactory.FALLBACK);
        }

        return i18n;
    }

    /**
     * TODO It should be changed since JDK9
     *
     * Use of '_' as an identifier might not be supported in releases after Java SE 8.
     *
     * @param str
     * @return Text depends on locale
     */
    public static String _(String str) {
        return getI18n().tr(str);
    }

    public static String _(String text, Object o1) {
        return getI18n().tr(text, o1);
    }

    public static String _(String text, Object o1, Object o2) {
        return getI18n().tr(text, o1, o2);
    }

    public static String _(String text, Object o1, Object o2, Object o3) {
        return getI18n().tr(text, o1, o2, o3);
    }

    public static String _(String text, Object o1, Object o2, Object o3, Object o4) {
        return getI18n().tr(text, o1, o2, o3, o4);
    }

    public static String _(String text, Object[] objects) {
        return getI18n().tr(text, objects);
    }

}
