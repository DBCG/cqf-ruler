package org.opencds.cqf.qdm.fivepoint4.repository;

import org.opencds.cqf.qdm.fivepoint4.model.CareGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import javax.annotation.Nonnull;
import java.util.Optional;

@Repository
public interface CareGoalRepository extends BaseRepository<CareGoal>
{
//    @Nonnull
//    Optional<CareGoal> findBySystemId(@Nonnull String id);
}