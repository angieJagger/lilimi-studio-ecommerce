package pl.lilimi.inquiry.application.admin;

import jakarta.persistence.criteria.Predicate;
import org.hibernate.query.criteria.JpaExpression;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import pl.lilimi.inquiry.api.admin.AdminInquiryDetailsResponse;
import pl.lilimi.inquiry.api.admin.AdminInquirySummaryResponse;
import pl.lilimi.inquiry.domain.InquiryStatus;
import pl.lilimi.inquiry.domain.ProjectInquiry;
import pl.lilimi.inquiry.persistence.ProjectInquiryRepository;
import pl.lilimi.inquiry.persistence.InquiryReadRepository;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminInquiryQueryService {

  private static final Set<String> VIEWS = Set.of(
    "active", "answered", "archive", "all"
  );

  private static final Set<String> PROJECT_TYPES = Set.of(
    "embroideredProduct",
    "digitizing",
    "website",
    "uxUi",
    "wordpress",
    "graphicDesign",
    "other"
  );

  private final ProjectInquiryRepository inquiries;
  private final InquiryReadRepository reads;

  public AdminInquiryQueryService(
    ProjectInquiryRepository inquiries,
    InquiryReadRepository reads
  ) {
    this.inquiries = inquiries;
    this.reads = reads;
  }

  @Transactional(readOnly = true)
  public Page<AdminInquirySummaryResponse> findInquiries(
    UUID administratorId,
    int page,
    int size,
    String view,
    String status,
    String projectType,
    LocalDate dateFrom,
    LocalDate dateTo,
    String search
  ) {
    if (page < 0 || size < 1 || size > 100) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Invalid pagination"
      );
    }

    if (view == null || !VIEWS.contains(view)) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Invalid inquiry view"
      );
    }

    if (
      projectType != null
        && !PROJECT_TYPES.contains(projectType)
    ) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Invalid project type"
      );
    }

    InquiryStatus selectedStatus = parseStatus(status);

    if (
      dateFrom != null
        && dateTo != null
        && dateFrom.isAfter(dateTo)
    ) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Invalid date range"
      );
    }

    String searchPattern = createSearchPattern(search);
    ZoneId zone = ZoneId.of("Europe/Warsaw");

    Instant from;
    Instant until;

    try {
      from = dateFrom == null
        ? null
        : dateFrom.atStartOfDay(zone).toInstant();

      until = dateTo == null
        ? null
        : dateTo.plusDays(1).atStartOfDay(zone).toInstant();
    } catch (DateTimeException exception) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Invalid date range"
      );
    }

    Specification<ProjectInquiry> filter =
      (root, query, builder) -> {
        var predicates = new ArrayList<Predicate>();

        switch (view) {
          case "active" -> predicates.add(
            root.get("status").in(
              InquiryStatus.NEW,
              InquiryStatus.IN_PROGRESS
            )
          );
          case "answered" -> predicates.add(
            builder.equal(
              root.get("status"),
              InquiryStatus.ANSWERED
            )
          );
          case "archive" -> predicates.add(
            builder.equal(
              root.get("status"),
              InquiryStatus.CLOSED
            )
          );
          default -> {
            // The all view does not restrict status.
          }
        }

        if (selectedStatus != null) {
          predicates.add(
            builder.equal(root.get("status"), selectedStatus)
          );
        }

        if (projectType != null) {
          predicates.add(
            builder.equal(root.get("projectType"), projectType)
          );
        }

        if (from != null) {
          predicates.add(
            builder.greaterThanOrEqualTo(
              root.<Instant>get("createdAt"),
              from
            )
          );
        }

        if (until != null) {
          predicates.add(
            builder.lessThan(
              root.<Instant>get("createdAt"),
              until
            )
          );
        }

        if (searchPattern != null) {
          var inquiryNumber = (
            (JpaExpression<UUID>) root.<UUID>get("id")
          ).cast(String.class);

          predicates.add(
            builder.or(
              builder.like(
                builder.lower(inquiryNumber),
                searchPattern,
                '!'
              ),
              builder.like(
                builder.lower(root.<String>get("email")),
                searchPattern,
                '!'
              ),
              builder.like(
                builder.lower(root.<String>get("name")),
                searchPattern,
                '!'
              )
            )
          );
        }

        return builder.and(
          predicates.toArray(Predicate[]::new)
        );
      };

    var sorting = Sort.by(
      Sort.Order.desc("createdAt"),
      Sort.Order.desc("id")
    );

    var result = inquiries.findAll(
      filter,
      PageRequest.of(page, size, sorting)
    );

    var inquiryIds = result.getContent().stream()
      .map(ProjectInquiry::getId)
      .toList();

    var readInquiryIds = reads.findReadIds(
      administratorId,
      inquiryIds
    );

    return result.map(inquiry ->
      AdminInquirySummaryResponse.from(
        inquiry,
        readInquiryIds.contains(inquiry.getId())
      )
    );
  }

  private String createSearchPattern(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }

    String normalized = value.strip();

    if (normalized.length() > 254) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Search phrase is too long"
      );
    }

    String escaped = normalized
      .toLowerCase(Locale.ROOT)
      .replace("!", "!!")
      .replace("%", "!%")
      .replace("_", "!_");

    return "%" + escaped + "%";
  }

  private InquiryStatus parseStatus(String value) {
    if (value == null) {
      return null;
    }

    try {
      return InquiryStatus.fromValue(value);
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(
        HttpStatus.BAD_REQUEST,
        "Invalid inquiry status"
      );
    }
  }

  @Transactional(readOnly = true)
  public AdminInquiryDetailsResponse findInquiry(UUID id) {
    var inquiry = inquiries.findById(id)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Inquiry not found"
      ));

    return AdminInquiryDetailsResponse.from(inquiry);
  }
}
