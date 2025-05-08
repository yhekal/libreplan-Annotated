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

package org.libreplan.web.users.services;

import java.util.Collections;

import org.springframework.security.authentication.dao.SaltSource;
import org.springframework.security.authentication.encoding.PasswordEncoder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * For maximum flexibility, the implementation uses the password encoder and the salt source
 * configured in the Spring Security configuration file
 * (in consequence, it is possible to change the configuration to use any password encoder and/or salt source
 * without modifying the implementation of this service).
 *
 * The only restriction the implementation imposes is that
 * when using a reflection-based salt source, the "username" property must be specified.
 *
 * @author Fernando Bellas Permuy <fbellas@udc.es>
 */

// &begin[DBPasswordEncoderService]
public class DBPasswordEncoderService implements IDBPasswordEncoderService {

    private SaltSource saltSource;
    // TODO resolve deprecated
    private PasswordEncoder passwordEncoder; // &line[PasswordEncoder]

    // &begin[setSaltSource]
    public void setSaltSource(SaltSource saltSource) {
        this.saltSource = saltSource;  // &line[saltSource]
    }
    // &end[setSaltSource]

    // TODO resolve deprecated
// &begin[setPasswordEncoder]
    public void setPasswordEncoder(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }
    // &end[setPasswordEncoder]

    @Override
    /**
     * The second parameter, <code>loginName</code>, is used as a salt if the
     * configured salt source is <code>ReflectionSaltSource</code>
     * (which must be configured to use "username" property as a salt).
     */

            // &begin[encodePassword]
    public String encodePassword(String clearPassword, String loginName) {

        /*
         * The only important parameter in User's constructor is "loginName",
         * which corresponds to the "username" property if the "saltSource" is
         * "ReflectionSaltSource". Note that "SystemWideSaltSource" ignores
         * the "user" passed as a parameter to "saltSource.getSalt"
         */
        UserDetails userDetails = new User(loginName, clearPassword, true, true, true, true, Collections.emptyList());

        Object salt = null;

        if ( saltSource != null ) {
            salt = saltSource.getSalt(userDetails);  // &line[getSalt]
        }

        return passwordEncoder.encodePassword(clearPassword, salt); // &line[PasswordEncoder]

    }
// &end[encodePassword]
}
// &end[DBPasswordEncoderService]
