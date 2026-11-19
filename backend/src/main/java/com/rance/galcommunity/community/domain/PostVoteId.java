package com.rance.galcommunity.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public record PostVoteId(@Column(name = "post_id") Long postId,
                         @Column(name = "user_id") Long userId) implements Serializable {
}
