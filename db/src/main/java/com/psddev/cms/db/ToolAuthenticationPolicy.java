package com.psddev.cms.db;

import java.util.Date;
import java.util.Map;

import javax.naming.ldap.LdapContext;

import com.psddev.dari.db.Query;
import com.psddev.dari.util.AuthenticationException;
import com.psddev.dari.util.AuthenticationPolicy;
import com.psddev.dari.util.LdapUtils;
import com.psddev.dari.util.NewPasswordPolicy;
import com.psddev.dari.util.ObjectUtils;
import com.psddev.dari.util.Password;
import com.psddev.dari.util.PasswordException;
import com.psddev.dari.util.PasswordPolicy;
import com.psddev.dari.util.Settings;

public class ToolAuthenticationPolicy implements AuthenticationPolicy {

    @Override
    public ToolUser authenticate(String username, String password) throws AuthenticationException {
        ToolUser user = Query.from(ToolUser.class).where("email = ? or username = ?", username, username).first();
        LdapContext context = LdapUtils.createContext();

        if (context != null &&
                LdapUtils.authenticate(context, username, password)) {
            if (user == null) {
                user = new ToolUser();
                user.setName(username);
                user.setUsername(username);
                user.setExternal(true);
                user.save();
            }

            return user;
        }

        if (user != null) {
            if (user.getPassword().check(password)) {
                long passwordExpirationDuration = Settings.get(long.class, "cms/tool/passwordExpirationDuration");
                if (passwordExpirationDuration > 0L ) {
                    passwordExpirationDuration = passwordExpirationDuration * 24 * 60 * 60 * 1000;
                    Date passwordChangedDate = user.getPasswordChangedDate();
                    if (passwordChangedDate == null || System.currentTimeMillis() - passwordExpirationDuration > passwordChangedDate.getTime()) {
                        user.setChangePasswordOnLogIn(true);
                        user.save();
                    }
                }
                return user;
            }

        } else if (!ObjectUtils.isBlank(username) &&
                (ObjectUtils.firstNonNull(
                        Settings.get(Boolean.class, "cms/tool/autoCreateUser"),
                        Settings.get(boolean.class, "cms/tool/isAutoCreateUser")) ||
                !Query.from(ToolUser.class).hasMoreThan(0))) {
            String name = username;
            int atAt = username.indexOf("@");

            if (atAt >= 0) {
                name = username.substring(0, atAt);
            }

            user = new ToolUser();
            NewPasswordPolicy newPasswordPolicy = NewPasswordPolicy.Static.getInstance(Settings.get(String.class, "cms/tool/newPasswordPolicy"));
            PasswordPolicy passwordPolicy = null;
            Password hashedPassword;
            if (newPasswordPolicy == null) {
                passwordPolicy = PasswordPolicy.Static.getInstance(Settings.get(String.class, "cms/tool/passwordPolicy"));
            }
            try {
                if (newPasswordPolicy != null || (newPasswordPolicy == null && passwordPolicy == null)) {
                    hashedPassword = Password.validateAndCreateCustom(newPasswordPolicy, user, null, null, password);
                } else {
                    hashedPassword = Password.validateAndCreateCustom(passwordPolicy, null, null, password);
                }
            } catch (PasswordException error) {
                throw new AuthenticationException(error);
            }

            user.setName(name);

            if (atAt >= 0) {
                user.setEmail(username);

            } else {
                user.setUsername(username);
            }

            user.setPassword(hashedPassword);
            user.save();

            return user;
        }

        throw new AuthenticationException(
                "Oops! No user with that username and password.");
    }

    @Override
    public void initialize(String settingsKey, Map<String, Object> settings) {
    }
}
