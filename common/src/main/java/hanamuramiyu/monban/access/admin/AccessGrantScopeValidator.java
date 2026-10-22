package hanamuramiyu.monban.access.admin;

import hanamuramiyu.monban.access.scope.AccessScope;

@FunctionalInterface
public interface AccessGrantScopeValidator {
    void validate(AccessScope scope);
}
