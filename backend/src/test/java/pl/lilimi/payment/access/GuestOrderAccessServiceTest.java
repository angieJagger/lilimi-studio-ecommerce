package pl.lilimi.payment.access;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import pl.lilimi.order.persistence.OrderSubmissionRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuestOrderAccessServiceTest {

  @Mock
  private OrderSubmissionRepository submissions;

  @Test
  void shouldResolveSameOwnerForIndependentCopiesOfSameSession() {
    String sessionId = UUID.randomUUID().toString();
    var firstSession = new MockHttpSession(null, sessionId);
    var secondSession = new MockHttpSession(null, sessionId);
    var firstNode = new GuestOrderAccessService(submissions);
    var secondNode = new GuestOrderAccessService(submissions);

    assertThat(firstNode.submissionOwner(firstSession))
      .isEqualTo(secondNode.submissionOwner(secondSession));
  }

  @Test
  void shouldPreserveOwnerAfterSessionIdChanges() {
    var service = new GuestOrderAccessService(submissions);
    var session = new MockHttpSession();
    UUID owner = service.submissionOwner(session);

    session.changeSessionId();

    assertThat(service.submissionOwner(session)).isEqualTo(owner);
  }

  @Test
  void shouldAllowOrderOwnedByGuestWithoutSessionOrderList() {
    var service = new GuestOrderAccessService(submissions);
    var session = new MockHttpSession();
    UUID owner = service.submissionOwner(session);
    UUID orderId = UUID.randomUUID();

    when(submissions.belongsToGuest(orderId, owner)).thenReturn(true);

    assertThat(service.hasAccess(session, orderId)).isTrue();
    verify(submissions).belongsToGuest(orderId, owner);
  }

  @Test
  void shouldRejectOrderOwnedByAnotherGuest() {
    var service = new GuestOrderAccessService(submissions);
    var session = new MockHttpSession();
    UUID owner = service.submissionOwner(session);
    UUID orderId = UUID.randomUUID();

    when(submissions.belongsToGuest(orderId, owner)).thenReturn(false);

    assertThat(service.hasAccess(session, orderId)).isFalse();
    verify(submissions).belongsToGuest(orderId, owner);
  }

  @Test
  void shouldRejectInvalidatedSession() {
    var service = new GuestOrderAccessService(submissions);
    var session = new MockHttpSession();
    service.submissionOwner(session);
    session.invalidate();

    assertThat(service.hasAccess(session, UUID.randomUUID())).isFalse();
  }

  @Test
  void shouldKeepNewGuestOrdersOutOfSessionAttributes() {
    var service = new GuestOrderAccessService(submissions);
    var session = new MockHttpSession();
    UUID owner = service.submissionOwner(session);
    UUID orderId = UUID.randomUUID();

    service.grant(session, orderId);

    assertThat(session.getAttribute(
      GuestOrderAccessService.class.getName() + ".orders"
    )).isNull();

    when(submissions.belongsToGuest(orderId, owner)).thenReturn(true);

    assertThat(service.hasAccess(session, orderId)).isTrue();
  }
}
