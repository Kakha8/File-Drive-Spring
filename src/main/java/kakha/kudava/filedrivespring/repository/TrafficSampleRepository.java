package kakha.kudava.filedrivespring.repository;

import kakha.kudava.filedrivespring.model.TrafficSample;
import kakha.kudava.filedrivespring.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.time.Instant;
import java.util.List;

public interface TrafficSampleRepository extends JpaRepository<TrafficSample, Long> {
    @EntityGraph(attributePaths = "user")
    List<TrafficSample> findByOccurredAtBetweenOrderByOccurredAtAsc(Instant from, Instant to);

    List<TrafficSample> findByTransferIdAndUser(String transferId, User user);
}
