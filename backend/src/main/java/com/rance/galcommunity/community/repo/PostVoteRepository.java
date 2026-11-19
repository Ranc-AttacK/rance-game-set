package com.rance.galcommunity.community.repo;

import com.rance.galcommunity.community.domain.PostVote;
import com.rance.galcommunity.community.domain.PostVoteId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostVoteRepository extends JpaRepository<PostVote, PostVoteId> {

    @Query("select v.id.postId from PostVote v where v.id.userId = :userId and v.id.postId in :postIds")
    List<Long> findVotedPostIds(@Param("userId") long userId,
                                @Param("postIds") Collection<Long> postIds);

    @Query("select count(v) from PostVote v where v.id.postId = :postId")
    long countVotes(@Param("postId") long postId);
}
