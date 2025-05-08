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
package org.libreplan.business.workingday.hibernate;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.usertype.UserType;
import org.libreplan.business.workingday.EffortDuration;
import org.libreplan.business.workingday.EffortDuration.Granularity;

public class EffortDurationType implements UserType {

    private static final int[] SQL_TYPES = { Types.INTEGER };

    @Override
    public int[] sqlTypes() {
        return SQL_TYPES;
    }

    @Override
    public Class<?> returnedClass() {
        return EffortDuration.class;
    }

    @Override
    public boolean equals(Object x, Object y) throws HibernateException {
        return Objects.equals(x, y);
    }

    // &begin[hashCode]
    @Override
    public int hashCode(Object x) throws HibernateException {
        return x.hashCode();
    }
    // &end[hashCode]
    @Override
    public Object nullSafeGet(ResultSet rs, String[] names,
            SessionImplementor session, Object owner)
            throws HibernateException, SQLException {
        Integer seconds = StandardBasicTypes.INTEGER.nullSafeGet(rs, names[0],
                session);
        if (seconds == null) {
            return null;
        }
        return EffortDuration.elapsing(seconds, Granularity.SECONDS);
    }

    @Override
    public void nullSafeSet(PreparedStatement st, Object value, int index,

            SessionImplementor session) throws HibernateException, SQLException {
        EffortDuration duration = (EffortDuration) value;
        Integer seconds = duration != null ? duration.getSeconds() : null;
        StandardBasicTypes.INTEGER.nullSafeSet(st, seconds, index, session);
    }

    @Override
    public Object deepCopy(Object value) throws HibernateException {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(Object value) throws HibernateException {
        EffortDuration duration = (EffortDuration) value;
        return duration.getSeconds();
    }

    @Override
    public Object assemble(Serializable cached, Object owner)
            throws HibernateException {
        return EffortDuration.seconds((Integer) cached);
    }

    @Override
    public Object replace(Object original, Object target, Object owner)
            throws HibernateException {
        return original;
    }

}
