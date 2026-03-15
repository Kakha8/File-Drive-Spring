package kakha.kudava.filedrivespring.repository;

import kakha.kudava.filedrivespring.model.ActionLogs;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ActionLogsRepository extends JpaRepository<ActionLogs, Long> {


}
