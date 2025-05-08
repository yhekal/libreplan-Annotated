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

package org.libreplan.business.common.daos;

import java.util.List;

import org.hibernate.Query;
import org.libreplan.business.common.entities.Configuration;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * DAO for {@link Configuration}.
 *
 * @author Manuel Rego Casasnovas <mrego@igalia.com>
 */
@Repository
@Scope(BeanDefinition.SCOPE_SINGLETON)
public class ConfigurationDAO extends GenericDAOHibernate<Configuration, Long> implements IConfigurationDAO {

    @Override
    @Transactional(readOnly = true)
    public Configuration getConfiguration() {
        List<Configuration> list = list(Configuration.class);

        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    @Transactional(readOnly = true)
    public Configuration getConfigurationWithReadOnlyTransaction() {
        return getConfiguration();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
            // &begin[saveChangedDefaultPassword]
    public void saveChangedDefaultPassword(String user, boolean change) {
        user = user.substring(0, 1).toUpperCase() + user.substring(1).toLowerCase();
        String sql = "UPDATE Configuration e SET e.changedDefault" + user + "Password = :change";

        Query query = getSession().createQuery(sql); // &line[getSession]
        query.setParameter("change", change);
        query.executeUpdate();
    }
// &end[saveChangedDefaultPassword]
}
