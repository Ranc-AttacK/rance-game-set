package com.rance.galcommunity.identity.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface IdentityApi {
    Optional<UserSummary> findUser(long id);
    Map<Long, UserSummary> findUsers(Collection<Long> ids);
}
