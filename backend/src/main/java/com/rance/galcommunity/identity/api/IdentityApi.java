package com.rance.galcommunity.identity.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only user summary contract exposed as {@code identity :: api}.
 * Only ACTIVE users are returned; missing, banned, or deleted ids are omitted.
 */
public interface IdentityApi {

    /** Returns the ACTIVE user summary, or empty if missing/banned/deleted. */
    Optional<UserSummary> findUser(long id);

    /** Returns ACTIVE summaries keyed by user id; missing/banned/deleted ids are omitted. */
    Map<Long, UserSummary> findUsers(Collection<Long> ids);

    /** True only for an ACTIVE user whose email is verified; missing/banned ids are false. */
    boolean isEmailVerified(long id);
}
