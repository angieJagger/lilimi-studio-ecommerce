package pl.lilimi.order.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import pl.lilimi.order.domain.DigitalDownloadToken;

import java.util.Optional;
import java.util.UUID;

public interface DigitalDownloadTokenRepository
  extends JpaRepository<DigitalDownloadToken, UUID> {

  Optional<DigitalDownloadToken> findByTokenHash(String tokenHash);
}
