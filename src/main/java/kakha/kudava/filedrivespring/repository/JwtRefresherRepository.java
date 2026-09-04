package kakha.kudava.filedrivespring.repository;

import jakarta.transaction.Transactional;
import kakha.kudava.filedrivespring.model.JwtRefresher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;


@Repository
public interface JwtRefresherRepository extends JpaRepository<JwtRefresher, Long> {
    Optional<JwtRefresher> findByTokenHashAndRevokedFalse(String hashedToken);

    @Query("select r.user.id from JwtRefresher r where r.tokenHash = :hash")
    Optional<Long> findOwnerId(@Param("hash") String hash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from JwtRefresher r where r.tokenHash = :hash and r.user.id = :userId")
    Optional<JwtRefresher> findForRotation(@Param("hash") String hash, @Param("userId") Long userId);

    @Transactional
    @Modifying
    @Query("""
    update JwtRefresher r
       set r.revoked = true
     where r.user.id = :userId
       and r.revoked = false
""")
    int revokeAllActiveByUserId(@Param("userId") Long userId);

    @Transactional
    @Modifying
    @Query("""
    delete from JwtRefresher r where r.user.id = :userId
""")
    void deleteAllTokensByUserId(@Param("userId") Long userId);
}
